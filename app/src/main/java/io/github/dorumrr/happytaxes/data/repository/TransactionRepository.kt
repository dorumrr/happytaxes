package io.github.dorumrr.happytaxes.data.repository

import android.content.Context
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.dorumrr.happytaxes.data.local.dao.TransactionDao
import io.github.dorumrr.happytaxes.data.mapper.TransactionMapper
import io.github.dorumrr.happytaxes.domain.model.EditHistoryEntry
import io.github.dorumrr.happytaxes.domain.model.FieldChange
import io.github.dorumrr.happytaxes.domain.model.Transaction
import io.github.dorumrr.happytaxes.domain.model.TransactionType
import io.github.dorumrr.happytaxes.util.ConcurrencyHelper
import io.github.dorumrr.happytaxes.util.InputValidator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for transaction operations.
 *
 * Implements business logic:
 * - Soft delete with 30-day retention
 * - Draft system (expenses without receipts)
 * - Duplicate detection (±7 days, same amount, same category)
 * - Edit history tracking (from/to values)
 * - Receipt requirement validation
 * - Multi-profile support (filters by current profile)
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class TransactionRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val transactionDao: TransactionDao,
    private val profileContext: ProfileContext
) {

    companion object {
        /**
         * Duplicate detection window: ±7 days from transaction date.
         * Business rule: Transactions within this window with same amount and category
         * are considered potential duplicates.
         */
        private const val DUPLICATE_DETECTION_DAYS = 7L
    }

    // ========== CREATE ==========
    
    /**
     * Create a new transaction.
     *
     * Business rules:
     * - Generates UUID for new transaction (or uses provided ID)
     * - Expenses without receipts are saved as drafts
     * - Validates receipt requirement
     * - Checks for potential duplicates
     *
     * @param id Optional transaction ID (used when receipts already saved with temp ID)
     * @param amount Amount in user's base currency (what bank statement shows)
     */
    suspend fun createTransaction(
        id: String? = null,
        date: LocalDate,
        type: TransactionType,
        category: String,
        description: String?,
        notes: String?,
        amount: java.math.BigDecimal,
        receiptPaths: List<String> = emptyList(),
        skipDuplicateCheck: Boolean = false
    ): Result<Transaction> {
        return ConcurrencyHelper.withTransactionLock {
            try {
                // Validate inputs
                InputValidator.validateAmount(amount).onFailure { return@withTransactionLock Result.failure(it) }
                InputValidator.validateDate(date).onFailure { return@withTransactionLock Result.failure(it) }
                InputValidator.validateDescription(description).onFailure { return@withTransactionLock Result.failure(it) }
                InputValidator.validateNotes(notes).onFailure { return@withTransactionLock Result.failure(it) }
                InputValidator.validateCategoryName(category).onFailure { return@withTransactionLock Result.failure(it) }
                
                val profileId = profileContext.getCurrentProfileIdOnce()

                // Validate receipt requirement
                val isDraft = type == TransactionType.EXPENSE && receiptPaths.isEmpty()

            // Check for duplicates (±7 days, same amount, same category)
            // Skip duplicate check for demo data seeding to allow realistic transaction volumes
            if (!skipDuplicateCheck) {
                val duplicate = checkForDuplicate(date, amount, category, "")
                if (duplicate != null) {
                    return@withTransactionLock Result.failure(
                        DuplicateTransactionException(
                            "Potential duplicate found: ${duplicate.description} on ${duplicate.date}"
                        )
                    )
                }
            }

            val now = Instant.now()
            val transaction = Transaction(
                id = id ?: UUID.randomUUID().toString(),
                date = date,
                type = type,
                category = category,
                description = description,
                notes = notes,
                amount = amount,
                receiptPaths = receiptPaths,
                isDraft = isDraft,
                isDeleted = false,
                deletedAt = null,
                createdAt = now,
                updatedAt = now,
                editHistory = emptyList()
            )

            transactionDao.insert(TransactionMapper.toEntity(transaction, profileId))

                Result.success(transaction)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    // ========== READ ==========

    suspend fun getById(id: String): Transaction? {
        return transactionDao.getById(id)?.let { TransactionMapper.toDomain(it) }
    }

    fun getByIdFlow(id: String): Flow<Transaction?> {
        return transactionDao.getByIdFlow(id).map { entity ->
            entity?.let { TransactionMapper.toDomain(it) }
        }
    }

    fun getAllActive(): Flow<List<Transaction>> {
        return profileContext.getCurrentProfileId().map { profileId ->
            transactionDao.getAllActive(profileId).map { entities ->
                entities.map { TransactionMapper.toDomain(it) }
            }
        }.flatMapLatest { it }
    }

    /**
     * Get all active transactions with pagination support.
     *
     * Improves performance for large datasets (10,000+ transactions).
     * Uses Paging 3 library with 50 items per page.
     *
     * @return Flow of PagingData for efficient loading
     */
    fun getAllActivePaged(): Flow<PagingData<Transaction>> {
        return profileContext.getCurrentProfileId().flatMapLatest { profileId ->
            Pager(
                config = PagingConfig(
                    pageSize = 50,
                    enablePlaceholders = false,
                    initialLoadSize = 50
                ),
                pagingSourceFactory = { transactionDao.getAllActivePaged(profileId) }
            ).flow.map { pagingData ->
                pagingData.map { entity -> TransactionMapper.toDomain(entity) }
            }
        }
    }

    /**
     * Get paginated active transactions with filters applied at database level.
     * 
     * Filters are applied in the SQL query for better performance and stability.
     * This prevents crashes from client-side filtering on PagingData.
     * 
     * PRD Section 2.9: Search & Filtering
     * PRD Section 14.5: Performance - Database-level filtering
     * 
     * @param filterType Optional transaction type filter (INCOME/EXPENSE)
     * @param filterCategory Optional category name filter
     * @param filterDraftsOnly Optional draft status filter
     * @param filterStartDate Optional start date for date range
     * @param filterEndDate Optional end date for date range
     * @param searchQuery Optional search query (pass NULL if blank)
     * @return Flow of PagingData for efficient loading with filters
     */
    fun getAllActivePagedWithFilters(
        filterType: TransactionType?,
        filterCategory: String?,
        filterDraftsOnly: Boolean?,
        filterStartDate: LocalDate?,
        filterEndDate: LocalDate?,
        searchQuery: String?
    ): Flow<PagingData<Transaction>> {
        return profileContext.getCurrentProfileId().flatMapLatest { profileId ->
            // Convert domain type to entity type
            val entityType = when (filterType) {
                TransactionType.INCOME -> io.github.dorumrr.happytaxes.data.local.entity.TransactionType.INCOME
                TransactionType.EXPENSE -> io.github.dorumrr.happytaxes.data.local.entity.TransactionType.EXPENSE
                null -> null
            }

            Pager(
                config = PagingConfig(
                    pageSize = 50,
                    enablePlaceholders = false,
                    initialLoadSize = 50
                ),
                pagingSourceFactory = {
                    transactionDao.getAllActivePagedWithFilters(
                        profileId = profileId,
                        filterType = entityType,
                        filterCategory = filterCategory,
                        filterDraftsOnly = filterDraftsOnly,
                        filterStartDate = filterStartDate,
                        filterEndDate = filterEndDate,
                        searchQuery = searchQuery?.takeIf { it.isNotBlank() } // Pass NULL if blank
                    )
                }
            ).flow.map { pagingData ->
                pagingData.map { entity -> TransactionMapper.toDomain(entity) }
            }
        }
    }

    fun getAllActiveNonDraft(): Flow<List<Transaction>> {
        return profileContext.getCurrentProfileId().map { profileId ->
            transactionDao.getAllActiveNonDraft(profileId).map { entities ->
                entities.map { TransactionMapper.toDomain(it) }
            }
        }.flatMapLatest { it }
    }

    fun getAllDrafts(): Flow<List<Transaction>> {
        return profileContext.getCurrentProfileId().map { profileId ->
            transactionDao.getAllDrafts(profileId).map { entities ->
                entities.map { TransactionMapper.toDomain(it) }
            }
        }.flatMapLatest { it }
    }

    fun getAllDeleted(): Flow<List<Transaction>> {
        return profileContext.getCurrentProfileId().map { profileId ->
            transactionDao.getAllDeleted(profileId).map { entities ->
                entities.map { TransactionMapper.toDomain(it) }
            }
        }.flatMapLatest { it }
    }

    fun getByDateRange(startDate: LocalDate, endDate: LocalDate): Flow<List<Transaction>> {
        return profileContext.getCurrentProfileId().map { profileId ->
            transactionDao.getByDateRange(profileId, startDate, endDate).map { entities ->
                entities.map { TransactionMapper.toDomain(it) }
            }
        }.flatMapLatest { it }
    }

    fun getByType(type: TransactionType): Flow<List<Transaction>> {
        val entityType = when (type) {
            TransactionType.INCOME ->
                io.github.dorumrr.happytaxes.data.local.entity.TransactionType.INCOME
            TransactionType.EXPENSE ->
                io.github.dorumrr.happytaxes.data.local.entity.TransactionType.EXPENSE
        }
        return profileContext.getCurrentProfileId().map { profileId ->
            transactionDao.getByType(profileId, entityType).map { entities ->
                entities.map { TransactionMapper.toDomain(it) }
            }
        }.flatMapLatest { it }
    }

    fun getByCategory(category: String): Flow<List<Transaction>> {
        return profileContext.getCurrentProfileId().map { profileId ->
            transactionDao.getByCategory(profileId, category).map { entities ->
                entities.map { TransactionMapper.toDomain(it) }
            }
        }.flatMapLatest { it }
    }
    
    // ========== UPDATE ==========
    
    /**
     * Update an existing transaction.
     * 
     * Business rules:
     * - Tracks changes in edit history (from/to values)
     * - Updates timestamp
     * - Validates receipt requirement
     * - Checks for duplicates if amount/date/category changed
     */
    suspend fun updateTransaction(
        id: String,
        date: LocalDate,
        category: String,
        description: String?,
        notes: String?,
        amount: java.math.BigDecimal,
        receiptPaths: List<String> = emptyList(),
        isDraft: Boolean? = null  // null = don't change, true/false = set value
    ): Result<Transaction> {
        return ConcurrencyHelper.withTransactionLock {
            try {
            android.util.Log.d("HappyTaxes", "updateTransaction: id=$id, isDraft=$isDraft, receiptPaths=${receiptPaths.size}")
            val existing = getById(id)
                ?: return@withTransactionLock Result.failure(IllegalArgumentException("Transaction not found"))
            android.util.Log.d("HappyTaxes", "updateTransaction: existing found, isDraft=${existing.isDraft}, receiptPaths=${existing.receiptPaths.size}")

            // Track changes for edit history
            val changes = mutableListOf<FieldChange>()

            if (existing.date != date) {
                changes.add(FieldChange("date", existing.date.toString(), date.toString()))
            }
            if (existing.category != category) {
                changes.add(FieldChange("category", existing.category, category))
            }
            if (existing.description != description) {
                changes.add(
                    FieldChange(
                        "description",
                        existing.description ?: "",
                        description ?: ""
                    )
                )
            }
            if (existing.notes != notes) {
                changes.add(FieldChange("notes", existing.notes ?: "", notes ?: ""))
            }
            if (existing.amount != amount) {
                changes.add(
                    FieldChange(
                        "amount",
                        existing.amount.toPlainString(),
                        amount.toPlainString()
                    )
                )
            }

            // Check for duplicates if amount/date/category changed
            if (existing.date != date || existing.amount != amount ||
                existing.category != category) {
                val duplicate = checkForDuplicate(date, amount, category, id)
                if (duplicate != null) {
                    return@withTransactionLock Result.failure(
                        DuplicateTransactionException(
                            "Potential duplicate: ${duplicate.description} on ${duplicate.date}"
                        )
                    )
                }
            }

            // Update edit history if there are changes
            val updatedHistory = if (changes.isNotEmpty()) {
                existing.editHistory + EditHistoryEntry(
                    editedAt = Instant.now(),
                    changes = changes
                )
            } else {
                existing.editHistory
            }

            // Determine draft status
            // If isDraft parameter is provided, use it; otherwise auto-determine
            val finalIsDraft = isDraft ?: (existing.type == TransactionType.EXPENSE && receiptPaths.isEmpty())

            val updated = existing.copy(
                date = date,
                category = category,
                description = description,
                notes = notes,
                amount = amount,
                receiptPaths = receiptPaths,
                isDraft = finalIsDraft,
                updatedAt = Instant.now(),
                editHistory = updatedHistory
            )

            android.util.Log.d("HappyTaxes", "updateTransaction: updating entity, finalIsDraft=$finalIsDraft, receiptPaths=${receiptPaths.size}")
            val entityToUpdate = TransactionMapper.toEntity(updated)
            android.util.Log.d("HappyTaxes", "updateTransaction: entity isDraft=${entityToUpdate.isDraft}, profileId=${entityToUpdate.profileId}")
            transactionDao.update(entityToUpdate)
            android.util.Log.d("HappyTaxes", "updateTransaction: update complete, transaction saved")

            // Verify the update worked by querying the transaction
            val verifyEntity = transactionDao.getById(id)
            android.util.Log.d("HappyTaxes", "updateTransaction: verification - entity isDraft=${verifyEntity?.isDraft}, receiptPaths=${verifyEntity?.receiptPaths}")

            // Test search functionality
            profileContext.getCurrentProfileId().first().let { currentProfileId ->
                android.util.Log.d("HappyTaxes", "updateTransaction: testing search with profileId=$currentProfileId, description='${updated.description}'")
                val searchResults = transactionDao.searchByDescription(currentProfileId, updated.description ?: "")
                android.util.Log.d("HappyTaxes", "updateTransaction: search test for '${updated.description}' found ${searchResults.size} results")
                searchResults.forEach { result ->
                    android.util.Log.d("HappyTaxes", "updateTransaction: search result - id=${result.id}, description='${result.description}', isDraft=${result.isDraft}")
                }

                // Also test by getting the specific transaction to see what's actually stored
                val specificTransaction = transactionDao.getById(id)
                android.util.Log.d("HappyTaxes", "updateTransaction: direct query - id=${specificTransaction?.id}, description='${specificTransaction?.description}', profileId=${specificTransaction?.profileId}")
            }

            Result.success(updated)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    /**
     * Add receipt to draft transaction.
     * Converts draft to regular transaction.
     */
    suspend fun addReceiptToDraft(id: String, receiptPath: String): Result<Unit> {
        return ConcurrencyHelper.withTransactionLock {
            try {
                transactionDao.markAsNonDraft(id, receiptPath, Instant.now())
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    // ========== SOFT DELETE ==========
    
    /**
     * Soft delete a transaction.
     * Sets isDeleted=true and deletedAt timestamp.
     */
    suspend fun softDelete(id: String): Result<Unit> {
        return ConcurrencyHelper.withTransactionLock {
            try {
                val now = Instant.now()
                transactionDao.softDelete(id, now, now)

                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    /**
     * Restore a soft-deleted transaction.
     */
    suspend fun restore(id: String): Result<Unit> {
        return ConcurrencyHelper.withTransactionLock {
            try {
                transactionDao.restore(id, Instant.now())

                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * Hard delete a transaction permanently.
     * Use with caution - this cannot be undone.
     */
    suspend fun hardDelete(id: String): Result<Unit> {
        return ConcurrencyHelper.withTransactionLock {
            try {
                transactionDao.hardDelete(id)
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    /**
     * Hard delete old soft-deleted transactions (30+ days).
     * Called by background cleanup job.
     */
    suspend fun cleanupOldDeleted(): Result<Unit> {
        return try {
            val cutoffDate = Instant.now().minusSeconds(30L * 24 * 60 * 60) // 30 days
            transactionDao.hardDeleteOldDeleted(cutoffDate)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // ========== DUPLICATE DETECTION ==========
    
    /**
     * Check for potential duplicate transactions.
     *
     * Business rule: ±7 days, same amount, same category
     */
    private suspend fun checkForDuplicate(
        date: LocalDate,
        amount: java.math.BigDecimal,
        category: String,
        excludeId: String
    ): Transaction? {
        val profileId = profileContext.getCurrentProfileIdOnce()
        val startDate = date.minusDays(DUPLICATE_DETECTION_DAYS)
        val endDate = date.plusDays(DUPLICATE_DETECTION_DAYS)

        return transactionDao.findPotentialDuplicate(
            profileId = profileId,
            startDate = startDate,
            endDate = endDate,
            amount = amount.toPlainString(),
            category = category,
            excludeId = excludeId
        )?.let { TransactionMapper.toDomain(it) }
    }
    
    // ========== STATISTICS ==========

    fun getActiveCount(): Flow<Int> {
        return profileContext.getCurrentProfileId().flatMapLatest { profileId ->
            transactionDao.getActiveCount(profileId)
        }
    }

    fun getDraftCount(): Flow<Int> {
        return profileContext.getCurrentProfileId().flatMapLatest { profileId ->
            transactionDao.getDraftCount(profileId)
        }
    }

    fun getDeletedCount(): Flow<Int> {
        return profileContext.getCurrentProfileId().flatMapLatest { profileId ->
            transactionDao.getDeletedCount(profileId)
        }
    }

    // ========== DATA RETENTION ==========

    /**
     * Get transactions older than the specified cutoff date.
     * Used for data retention compliance.
     *
     * @param cutoffDate Transactions before this date are considered old
     * @return Flow of old transactions
     */
    fun getTransactionsOlderThan(cutoffDate: LocalDate): Flow<List<Transaction>> {
        return profileContext.getCurrentProfileId().flatMapLatest { profileId ->
            transactionDao.getTransactionsBeforeDate(profileId, cutoffDate)
                .map { entities -> entities.map { TransactionMapper.toDomain(it) } }
        }
    }

    suspend fun insertTransactionsBulk(transactions: List<Transaction>) {
        if (transactions.isEmpty()) return
        ConcurrencyHelper.withTransactionLock {
            val profileId = profileContext.getCurrentProfileIdOnce()
            val entities = transactions.map { TransactionMapper.toEntity(it, profileId) }
            transactionDao.insertAll(entities)
        }
    }
}

/**
 * Exception thrown when a duplicate transaction is detected.
 */
class DuplicateTransactionException(message: String) : Exception(message)
