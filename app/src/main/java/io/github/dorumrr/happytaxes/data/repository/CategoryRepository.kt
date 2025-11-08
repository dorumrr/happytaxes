package io.github.dorumrr.happytaxes.data.repository

import android.util.Log
import io.github.dorumrr.happytaxes.data.config.toEntities
import io.github.dorumrr.happytaxes.data.loader.CountryConfigurationLoader
import io.github.dorumrr.happytaxes.data.local.dao.CategoryDao
import io.github.dorumrr.happytaxes.data.local.dao.TransactionDao
import io.github.dorumrr.happytaxes.data.mapper.CategoryMapper
import io.github.dorumrr.happytaxes.data.mapper.TransactionMapper
import io.github.dorumrr.happytaxes.domain.model.Category
import io.github.dorumrr.happytaxes.domain.model.EditHistoryEntry
import io.github.dorumrr.happytaxes.domain.model.FieldChange
import io.github.dorumrr.happytaxes.domain.model.TransactionType
import io.github.dorumrr.happytaxes.util.ConcurrencyHelper
import io.github.dorumrr.happytaxes.util.InputValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for category operations.
 *
 * Business rules:
 * - Pre-populated with country-specific tax categories (loaded from JSON configs)
 * - User can add custom categories
 * - Cannot delete categories with transactions (must be empty for permanent deletion)
 * - Multi-profile support (filters by current profile)
 */
@Singleton
class CategoryRepository @Inject constructor(
    private val categoryDao: CategoryDao,
    private val transactionDao: TransactionDao,
    private val countryConfigLoader: CountryConfigurationLoader,
    private val preferencesRepository: PreferencesRepository,
    private val profileContext: ProfileContext
) {
    companion object {
        private const val TAG = "CategoryRepository"
    }
    
    // ========== CREATE ==========
    
    /**
     * Create a new custom category.
     *
     * Business rules:
     * - Category name must not be empty
     * - Category name must be unique within the profile (case-insensitive)
     * - Same name in different profiles is allowed
     */
    suspend fun createCategory(
        name: String,
        type: TransactionType,
        icon: String? = null,
        color: String? = null,
        displayOrder: Int = 999
    ): Result<Category> {
        return ConcurrencyHelper.withCategoryLock {
            try {
            val profileId = profileContext.getCurrentProfileIdOnce()

            // Validate name is not empty
            if (name.isBlank()) {
                return@withCategoryLock Result.failure(IllegalArgumentException("Category name cannot be empty"))
            }

            // Check for duplicate name in current profile (case-insensitive)
            val existing = categoryDao.getByNameAndProfile(name.trim(), profileId)
            if (existing != null) {
                return@withCategoryLock Result.failure(
                    IllegalStateException("Category '$name' already exists in this profile")
                )
            }

            val now = Instant.now()
            val category = Category(
                id = UUID.randomUUID().toString(),
                name = name.trim(),
                type = type,
                icon = icon,
                color = color,
                taxFormReference = null,
                taxFormDescription = null,
                countryCode = null, // Custom categories are not country-specific
                isCustom = true,
                isArchived = false,
                displayOrder = displayOrder,
                createdAt = now,
                updatedAt = now
            )

                categoryDao.insert(CategoryMapper.toEntity(category, profileId))
                Result.success(category)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    // ========== READ ==========

    suspend fun getById(id: String): Category? {
        return categoryDao.getById(id)?.let { CategoryMapper.toDomain(it) }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun getAllActive(): Flow<List<Category>> {
        return profileContext.getCurrentProfileId().flatMapLatest { profileId ->
            categoryDao.getAllActive(profileId).map { entities ->
                entities.map { CategoryMapper.toDomain(it) }
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun getByType(type: TransactionType): Flow<List<Category>> {
        val entityType = when (type) {
            TransactionType.INCOME ->
                io.github.dorumrr.happytaxes.data.local.entity.TransactionType.INCOME
            TransactionType.EXPENSE ->
                io.github.dorumrr.happytaxes.data.local.entity.TransactionType.EXPENSE
        }
        return profileContext.getCurrentProfileId().flatMapLatest { profileId ->
            categoryDao.getByType(profileId, entityType).map { entities ->
                entities.map { CategoryMapper.toDomain(it) }
            }
        }
    }
    
    // ========== UPDATE ==========
    
    /**
     * Update an existing category.
     *
     * Business rules:
     * - Category name must not be empty
     * - Category name must be unique within the profile (case-insensitive)
     * - Same name in different profiles is allowed
     * - Can keep the same name (when only changing other fields)
     * - If type changes, all transactions in this category are updated to match the new type
     */
    suspend fun updateCategory(
        id: String,
        name: String,
        type: TransactionType,
        icon: String?,
        color: String?,
        displayOrder: Int
    ): Result<Category> {
        return ConcurrencyHelper.withCategoryLock {
            try {
            val profileId = profileContext.getCurrentProfileIdOnce()
            val existing = getById(id)
                ?: return@withCategoryLock Result.failure(IllegalArgumentException("Category not found"))

            // Validate name is not empty
            if (name.isBlank()) {
                return@withCategoryLock Result.failure(IllegalArgumentException("Category name cannot be empty"))
            }

            // Check for duplicate name in current profile (case-insensitive)
            // Only check if name is changing
            if (name.trim() != existing.name) {
                val duplicate = categoryDao.getByNameAndProfile(name.trim(), profileId)
                if (duplicate != null && duplicate.id != id) {
                    return@withCategoryLock Result.failure(
                        IllegalStateException("Category '$name' already exists in this profile")
                    )
                }
            }

            val now = Instant.now()
            val nameChanged = name.trim() != existing.name
            val typeChanged = type != existing.type

            val updated = existing.copy(
                name = name.trim(),
                type = type,
                icon = icon,
                color = color,
                displayOrder = displayOrder,
                updatedAt = now
            )

                // Update category entity
                categoryDao.update(CategoryMapper.toEntity(updated, profileId))

                // If name or type changed, update all transactions in this category
                if (nameChanged || typeChanged) {
                    val transactionEntities = transactionDao.getByCategory(profileId, existing.name).first()
                    
                    if (transactionEntities.isNotEmpty()) {
                        val updatedTransactionEntities = transactionEntities.map { entity ->
                            val transaction = TransactionMapper.toDomain(entity)
                            
                            // Build list of changes for edit history
                            val changes = mutableListOf<FieldChange>()
                            if (nameChanged) {
                                changes.add(FieldChange(
                                    field = "category",
                                    from = existing.name,
                                    to = name.trim()
                                ))
                            }
                            if (typeChanged) {
                                changes.add(FieldChange(
                                    field = "type",
                                    from = existing.type.toString(),
                                    to = type.toString()
                                ))
                            }
                            
                            // Add edit history entry
                            val editHistoryEntry = EditHistoryEntry(
                                editedAt = now,
                                changes = changes
                            )
                            
                            val updatedTransaction = transaction.copy(
                                category = name.trim(),
                                type = type,
                                updatedAt = now,
                                editHistory = transaction.editHistory + editHistoryEntry
                            )
                            
                            TransactionMapper.toEntity(updatedTransaction, profileId)
                        }
                        
                        // Bulk update all transactions
                        transactionDao.updateAll(updatedTransactionEntities)
                        
                        Log.d(TAG, "Updated ${updatedTransactionEntities.size} transactions for category change: ${existing.name} → ${name.trim()}, type: ${existing.type} → $type")
                    }
                }

                Result.success(updated)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    // ========== SEED DATA ==========

    /**
     * Seed default categories based on selected country.
     * Called on first app launch.
     *
     * Loads categories from country-specific JSON configuration files:
     * - GB: UK HMRC SA103 categories
     * - US: IRS Schedule C categories
     * - CA: CRA T2125 categories
     * - AU: ATO categories
     * - NZ: IRD categories
     * - RO: Romanian PFA categories
     * - OTHER: Universal minimal categories
     */
    suspend fun seedDefaultCategories(): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            val profileId = profileContext.getCurrentProfileIdOnce()

            // Get selected country from preferences
            val countryCode = preferencesRepository.getSelectedCountry(profileId).first()

            // Load country configuration
            val config = countryConfigLoader.loadCountryConfig(countryCode)

            if (config != null) {
                // Convert categories from config to entities
                val categoryEntities = config.categories.toEntities(countryCode, profileId)

                // Insert into database
                categoryDao.insertAll(categoryEntities)

                Log.d(TAG, "Seeded ${categoryEntities.size} default categories for country: $countryCode, profileId: $profileId")
                Result.success(Unit)
            } else {
                // Fallback to default categories if config not found
                Log.w(TAG, "Country configuration not found for $countryCode, using default categories")
                val defaultConfig = countryConfigLoader.loadCountryConfig("OTHER")
                if (defaultConfig != null) {
                    val categoryEntities = defaultConfig.categories.toEntities("OTHER", profileId)
                    categoryDao.insertAll(categoryEntities)
                    Log.d(TAG, "Seeded ${categoryEntities.size} default categories for OTHER, profileId: $profileId")
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("Failed to load default categories"))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to seed default categories", e)
            Result.failure(e)
        }
    }

    /**
     * Remove all seeded (non-custom) categories that have no transactions.
     * This is used when cleaning up unused categories.
     * Custom categories are never removed by this method.
     */
    suspend fun removeUnusedSeededCategories(): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            val profileId = profileContext.getCurrentProfileIdOnce()

            // Get all active categories
            val allCategories = categoryDao.getAllActive(profileId).first()

            // Filter to only seeded (non-custom) categories
            val seededCategories = allCategories.filter { !it.isCustom }

            var removedCount = 0

            seededCategories.forEach { categoryEntity ->
                // Check if category has any transactions
                val transactionCount = transactionDao.countByCategory(profileId, categoryEntity.name)

                if (transactionCount == 0) {
                    // No transactions using this category, safe to delete
                    categoryDao.delete(categoryEntity.id)
                    removedCount++
                    Log.d(TAG, "Removed unused seeded category: ${categoryEntity.name}")
                }
            }

            Log.d(TAG, "Removed $removedCount unused seeded categories")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove unused seeded categories", e)
            Result.failure(e)
        }
    }

    /**
     * Remove ALL categories (both seeded and custom).
     * This is used when wiping the app clean (e.g., app reset).
     * WARNING: This will delete ALL categories regardless of transaction count.
     */
    suspend fun removeAllCategories(): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            val profileId = profileContext.getCurrentProfileIdOnce()

            // Get all active categories
            val allCategories = categoryDao.getAllActive(profileId).first()

            Log.d(TAG, "Removing ALL ${allCategories.size} categories (seeded + custom)")

            allCategories.forEach { categoryEntity ->
                categoryDao.delete(categoryEntity.id)
                Log.d(TAG, "Removed category: ${categoryEntity.name} (isCustom=${categoryEntity.isCustom})")
            }

            Log.d(TAG, "Removed all ${allCategories.size} categories")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove all categories", e)
            Result.failure(e)
        }
    }

    // ========== CATEGORY USAGE STATISTICS ==========

    /**
     * Get transaction count for a category.
     * PRD Section 4.14: "Show transaction count per category"
     *
     * NOTE: Transactions store category NAME, not ID
     */
    suspend fun getCategoryTransactionCount(categoryId: String): Int {
        val profileId = profileContext.getCurrentProfileIdOnce()
        // Get category name from ID
        val category = categoryDao.getById(categoryId) ?: return 0
        return transactionDao.countByCategory(profileId, category.name)
    }

    /**
     * Get total amount for a category.
     * PRD Section 4.14: "Show total amount per category"
     *
     * NOTE: Transactions store category NAME, not ID
     */
    suspend fun getCategoryTotalAmount(categoryId: String): BigDecimal {
        val profileId = profileContext.getCurrentProfileIdOnce()
        // Get category name from ID
        val category = categoryDao.getById(categoryId) ?: return BigDecimal.ZERO
        return transactionDao.getTotalAmountByCategory(profileId, category.name) ?: BigDecimal.ZERO
    }

    /**
     * Check if category can be deleted.
     * PRD Section 4.14: "Cannot delete if category has transactions (must be empty)"
     */
    suspend fun canDeleteCategory(categoryId: String): Boolean {
        val count = getCategoryTransactionCount(categoryId)
        return count == 0
    }

    /**
     * Check if all transactions for a category are demo transactions.
     * Used during onboarding to allow deletion of categories with only demo data.
     */
    private suspend fun hasOnlyDemoTransactions(categoryId: String): Boolean {
        val profileId = profileContext.getCurrentProfileIdOnce()
        val category = categoryDao.getById(categoryId) ?: return false

        // Get all transactions for this category
        val transactions = transactionDao.getByCategory(profileId, category.name).first()

        if (transactions.isEmpty()) {
            return true // No transactions = can delete
        }

        // Check if ALL transactions are demo transactions using isDemoData flag
        return transactions.all { entity -> entity.isDemoData }
    }

    /**
     * Delete a category permanently.
     * Only allowed if category has no transactions.
     * PRD Section 4.14: "Cannot delete if category has transactions"
     * PRD Section 4.14: "must keep at least 1 income + 1 expense category"
     */
    suspend fun deleteCategory(categoryId: String): Result<Unit> {
        return ConcurrencyHelper.withCategoryLock {
            try {
            // Check if category has transactions
            if (!canDeleteCategory(categoryId)) {
                return@withCategoryLock Result.failure(IllegalStateException("Cannot delete category with transactions"))
            }

            // Check minimum category requirement
            val profileId = profileContext.getCurrentProfileIdOnce()
            val category = getById(categoryId)
                ?: return@withCategoryLock Result.failure(IllegalArgumentException("Category not found"))

            // Count categories of the same type
            val allCategories = categoryDao.getAllActive(profileId).map { entities ->
                entities.map { CategoryMapper.toDomain(it) }
            }.first()

            val categoriesOfSameType = allCategories.filter { it.type == category.type }

            if (categoriesOfSameType.size <= 1) {
                return@withCategoryLock Result.failure(IllegalStateException(
                    "Cannot delete the last ${category.type.name.lowercase()} category. " +
                    "You must keep at least one category of each type."
                ))
            }

                categoryDao.delete(categoryId)
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * Delete a category during onboarding.
     * If the category has only demo transactions, delete both the category and its demo transactions.
     * If the category has real user transactions, prevent deletion.
     * PRD Section 4.14: "must keep at least 1 income + 1 expense category"
     */
    suspend fun deleteCategoryDuringOnboarding(categoryId: String): Result<Unit> {
        return ConcurrencyHelper.withCategoryLock {
            try {
                val profileId = profileContext.getCurrentProfileIdOnce()
                val category = getById(categoryId)
                    ?: return@withCategoryLock Result.failure(IllegalArgumentException("Category not found"))

                // Check minimum category requirement
                val allCategories = categoryDao.getAllActive(profileId).map { entities ->
                    entities.map { CategoryMapper.toDomain(it) }
                }.first()

                val categoriesOfSameType = allCategories.filter { it.type == category.type }

                if (categoriesOfSameType.size <= 1) {
                    return@withCategoryLock Result.failure(IllegalStateException(
                        "Cannot delete the last ${category.type.name.lowercase()} category. " +
                        "You must keep at least one category of each type."
                    ))
                }

                // Check if category has transactions
                val transactionCount = getCategoryTransactionCount(categoryId)

                if (transactionCount == 0) {
                    // No transactions, safe to delete
                    categoryDao.delete(categoryId)
                    Log.d(TAG, "Deleted category $categoryId with no transactions")
                    return@withCategoryLock Result.success(Unit)
                }

                // Category has transactions - check if they're all demo transactions
                if (!hasOnlyDemoTransactions(categoryId)) {
                    return@withCategoryLock Result.failure(IllegalStateException(
                        "Cannot delete category with real transactions. " +
                        "This category contains transactions you've created."
                    ))
                }

                // All transactions are demo transactions - delete them along with the category
                val transactions = transactionDao.getByCategory(profileId, category.name).first()
                Log.d(TAG, "Deleting category $categoryId with ${transactions.size} demo transactions")

                transactions.forEach { transaction ->
                    transactionDao.hardDelete(transaction.id)
                }

                categoryDao.delete(categoryId)
                Log.d(TAG, "Successfully deleted category $categoryId and its demo transactions")

                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete category during onboarding", e)
                Result.failure(e)
            }
        }
    }

    // ========== BULK OPERATIONS ==========

    /**
     * Move all transactions from one category to another.
     *
     * Business rules:
     * - Source and destination categories must have same transaction type
     * - Updates all transactions in bulk
     * - Adds edit history entry for each transaction
     * - Cannot move to archived category
     *
     * PRD Reference: Section 4.14 - Move transactions between categories (bulk action)
     *
     * @param fromCategoryName Source category name
     * @param toCategoryName Destination category name
     * @return Result with number of transactions moved or error
     */
    suspend fun moveTransactionsToCategory(
        fromCategoryName: String,
        toCategoryName: String
    ): Result<Int> = withContext(Dispatchers.IO) {
        ConcurrencyHelper.withCategoryLock {
            try {
            // Validate categories exist
            val fromCategory = categoryDao.getByName(fromCategoryName)
                ?: return@withCategoryLock Result.failure(Exception("Source category not found"))

            val toCategory = categoryDao.getByName(toCategoryName)
                ?: return@withCategoryLock Result.failure(Exception("Destination category not found"))

            // Validate same transaction type
            if (fromCategory.type != toCategory.type) {
                return@withCategoryLock Result.failure(
                    Exception("Cannot move transactions between different types (Income/Expense)")
                )
            }

            // Validate destination is not archived
            if (toCategory.isArchived) {
                return@withCategoryLock Result.failure(
                    Exception("Cannot move transactions to archived category")
                )
            }

            val profileId = profileContext.getCurrentProfileIdOnce()

            // Get all transactions with source category
            val transactionEntities = transactionDao.getByCategory(profileId, fromCategoryName).first()

            if (transactionEntities.isEmpty()) {
                return@withCategoryLock Result.success(0)
            }

            // Update each transaction
            val now = Instant.now()
            val updatedEntities = transactionEntities.map { entity ->
                val transaction = TransactionMapper.toDomain(entity)

                // Add edit history entry
                val editHistoryEntry = EditHistoryEntry(
                    editedAt = now,
                    changes = listOf(
                        FieldChange(
                            field = "category",
                            from = fromCategoryName,
                            to = toCategoryName
                        )
                    )
                )

                val updatedTransaction = transaction.copy(
                    category = toCategoryName,
                    updatedAt = now,
                    editHistory = transaction.editHistory + editHistoryEntry
                )

                TransactionMapper.toEntity(updatedTransaction)
            }

                // Bulk update
                transactionDao.updateAll(updatedEntities)

                Result.success(updatedEntities.size)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}

