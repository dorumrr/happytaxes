package io.github.dorumrr.happytaxes.data.local.dao

import androidx.paging.PagingSource
import androidx.room.*
import io.github.dorumrr.happytaxes.data.local.entity.TransactionEntity
import io.github.dorumrr.happytaxes.data.local.entity.TransactionType
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate

/**
 * DAO for transaction operations.
 * 
 * Business Rules:
 * - Soft delete: set isDeleted=true, deletedAt=now
 * - Hard delete: only on app reset or after 30 days
 * - Draft system: isDraft=true for expenses without receipts
 * - Duplicate detection: ±7 days, same amount, same category
 */
@Dao
interface TransactionDao {
    
    // ========== CREATE ==========
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transaction: TransactionEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(transactions: List<TransactionEntity>)
    
    // ========== READ ==========

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: String): TransactionEntity?

    @Query("""
        SELECT * FROM transactions
        WHERE profileId = :profileId
        AND isDeleted = 0
        AND description LIKE '%' || :searchQuery || '%'
        ORDER BY date DESC, createdAt DESC
    """)
    suspend fun searchByDescription(profileId: String, searchQuery: String): List<TransactionEntity>

    @Query("SELECT COUNT(*) FROM transactions WHERE profileId = :profileId AND isDeleted = 0")
    suspend fun getTransactionCountForProfile(profileId: String): Int

    @Query("SELECT * FROM transactions WHERE id = :id")
    fun getByIdFlow(id: String): Flow<TransactionEntity?>

    @Query("""
        SELECT * FROM transactions
        WHERE profileId = :profileId AND isDeleted = 0
        ORDER BY date DESC, createdAt DESC
    """)
    fun getAllActive(profileId: String): Flow<List<TransactionEntity>>

    /**
     * Get all active transactions with pagination support.
     *
     * Pagination improves performance for large datasets (10,000+ transactions).
     * Page size: 50 transactions per page.
     *
     * @param profileId Profile ID to filter by
     * @return PagingSource for Room to handle pagination
     */
    @Query("""
        SELECT * FROM transactions
        WHERE profileId = :profileId AND isDeleted = 0
        ORDER BY date DESC, createdAt DESC
    """)
    fun getAllActivePaged(profileId: String): PagingSource<Int, TransactionEntity>

    /**
     * Get paginated transactions with filters applied at database level.
     * 
     * This prevents crashes from client-side filtering on paged data.
     * All filters use NULL-safe checks (filter only if parameter is not NULL).
     * Search uses SQLite LIKE operator for simple pattern matching.
     * 
     * PRD Section 2.9: Search & Filtering
     * PRD Section 14.5: Performance - Database-level filtering
     * 
     * @param profileId Profile ID to filter by
     * @param filterType Optional transaction type filter (INCOME/EXPENSE)
     * @param filterCategory Optional category name filter (exact match)
     * @param filterDraftsOnly Optional draft status filter (true = drafts only, false = non-drafts, null = all)
     * @param filterStartDate Optional start date for date range filter
     * @param filterEndDate Optional end date for date range filter
     * @param searchQuery Optional search query (searches description, notes, amount as text - NOT category)
     * @return PagingSource for Room to handle pagination with filters
     */
    @Query("""
        SELECT * FROM transactions
        WHERE profileId = :profileId
        AND isDeleted = 0
        AND (:filterType IS NULL OR type = :filterType)
        AND (:filterCategory IS NULL OR category = :filterCategory)
        AND (:filterDraftsOnly IS NULL OR isDraft = :filterDraftsOnly)
        AND (:filterStartDate IS NULL OR date >= :filterStartDate)
        AND (:filterEndDate IS NULL OR date <= :filterEndDate)
        AND (:searchQuery IS NULL OR
             description LIKE '%' || :searchQuery || '%' OR
             notes LIKE '%' || :searchQuery || '%' OR
             CAST(amount AS TEXT) LIKE '%' || :searchQuery || '%')
        ORDER BY date DESC, createdAt DESC
    """)
    fun getAllActivePagedWithFilters(
        profileId: String,
        filterType: TransactionType?,
        filterCategory: String?,
        filterDraftsOnly: Boolean?,
        filterStartDate: LocalDate?,
        filterEndDate: LocalDate?,
        searchQuery: String?
    ): PagingSource<Int, TransactionEntity>

    @Query("""
        SELECT * FROM transactions
        WHERE profileId = :profileId AND isDeleted = 0 AND isDraft = 0
        ORDER BY date DESC, createdAt DESC
    """)
    fun getAllActiveNonDraft(profileId: String): Flow<List<TransactionEntity>>

    @Query("""
        SELECT * FROM transactions
        WHERE profileId = :profileId AND isDeleted = 0 AND isDraft = 1
        ORDER BY date DESC, createdAt DESC
    """)
    fun getAllDrafts(profileId: String): Flow<List<TransactionEntity>>

    @Query("""
        SELECT * FROM transactions
        WHERE profileId = :profileId AND isDeleted = 1
        ORDER BY deletedAt DESC
    """)
    fun getAllDeleted(profileId: String): Flow<List<TransactionEntity>>
    
    @Query("""
        SELECT * FROM transactions
        WHERE profileId = :profileId AND isDeleted = 0
        AND date BETWEEN :startDate AND :endDate
        ORDER BY date DESC, createdAt DESC
    """)
    fun getByDateRange(profileId: String, startDate: LocalDate, endDate: LocalDate): Flow<List<TransactionEntity>>

    @Query("""
        SELECT * FROM transactions
        WHERE profileId = :profileId AND isDeleted = 0
        AND type = :type
        ORDER BY date DESC, createdAt DESC
    """)
    fun getByType(profileId: String, type: TransactionType): Flow<List<TransactionEntity>>

    @Query("""
        SELECT * FROM transactions
        WHERE profileId = :profileId AND isDeleted = 0
        AND category = :category
        ORDER BY date DESC, createdAt DESC
    """)
    fun getByCategory(profileId: String, category: String): Flow<List<TransactionEntity>>
    
    // ========== DUPLICATE DETECTION ==========

    @Query("""
        SELECT * FROM transactions
        WHERE profileId = :profileId AND isDeleted = 0
        AND date BETWEEN :startDate AND :endDate
        AND amount = :amount
        AND category = :category
        AND id != :excludeId
        LIMIT 1
    """)
    suspend fun findPotentialDuplicate(
        profileId: String,
        startDate: LocalDate,
        endDate: LocalDate,
        amount: String,
        category: String,
        excludeId: String
    ): TransactionEntity?
    
    // ========== UPDATE ==========
    
    @Update
    suspend fun update(transaction: TransactionEntity)

    @Update
    suspend fun updateAll(transactions: List<TransactionEntity>)

    @Query("""
        UPDATE transactions
        SET isDraft = 0, receiptPaths = :receiptPaths, updatedAt = :updatedAt
        WHERE id = :id
    """)
    suspend fun markAsNonDraft(id: String, receiptPaths: String, updatedAt: Instant)
    
    // ========== SOFT DELETE ==========
    
    @Query("""
        UPDATE transactions 
        SET isDeleted = 1, deletedAt = :deletedAt, updatedAt = :updatedAt
        WHERE id = :id
    """)
    suspend fun softDelete(id: String, deletedAt: Instant, updatedAt: Instant)
    
    @Query("""
        UPDATE transactions 
        SET isDeleted = 0, deletedAt = NULL, updatedAt = :updatedAt
        WHERE id = :id
    """)
    suspend fun restore(id: String, updatedAt: Instant)
    
    // ========== HARD DELETE ==========
    
    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun hardDelete(id: String)
    
    @Query("DELETE FROM transactions WHERE isDeleted = 1 AND deletedAt < :cutoffDate")
    suspend fun hardDeleteOldDeleted(cutoffDate: Instant)
    
    @Query("DELETE FROM transactions")
    suspend fun deleteAll()
    
    // ========== STATISTICS ==========

    @Query("""
        SELECT COUNT(*) FROM transactions
        WHERE profileId = :profileId AND isDeleted = 0 AND isDraft = 0
    """)
    fun getActiveCount(profileId: String): Flow<Int>

    @Query("""
        SELECT COUNT(*) FROM transactions
        WHERE profileId = :profileId AND isDeleted = 0 AND isDraft = 1
    """)
    fun getDraftCount(profileId: String): Flow<Int>

    @Query("""
        SELECT COUNT(*) FROM transactions
        WHERE profileId = :profileId AND isDeleted = 1
    """)
    fun getDeletedCount(profileId: String): Flow<Int>

    @Query("""
        SELECT COUNT(*) FROM transactions
        WHERE profileId = :profileId AND isDeleted = 0
    """)
    suspend fun getTransactionCount(profileId: String): Int

    // ========== CATEGORY STATISTICS ==========

    /**
     * Count transactions by category.
     * PRD Section 4.14: "Show transaction count per category"
     * PRD Section 4.16: "Exclude drafts: Only confirmed transactions in reports"
     */
    @Query("""
        SELECT COUNT(*) FROM transactions
        WHERE profileId = :profileId AND category = :categoryId AND isDeleted = 0 AND isDraft = 0
    """)
    suspend fun countByCategory(profileId: String, categoryId: String): Int

    /**
     * Get total amount by category (in base currency).
     * PRD Section 4.14: "Show total amount per category"
     * PRD Section 4.16: "Exclude drafts: Only confirmed transactions in reports"
     */
    @Query("""
        SELECT SUM(amount) FROM transactions
        WHERE profileId = :profileId AND category = :categoryId AND isDeleted = 0 AND isDraft = 0
    """)
    suspend fun getTotalAmountByCategory(profileId: String, categoryId: String): java.math.BigDecimal?

    // ========== DATA RETENTION ==========

    /**
     * Get transactions before a specific date.
     * Used for data retention compliance.
     * PRD Section 4.17: "Archive old transactions (export to ZIP and remove from active DB)"
     */
    @Query("""
        SELECT * FROM transactions
        WHERE profileId = :profileId AND date < :cutoffDate AND isDeleted = 0
        ORDER BY date DESC
    """)
    fun getTransactionsBeforeDate(profileId: String, cutoffDate: LocalDate): Flow<List<TransactionEntity>>
}

