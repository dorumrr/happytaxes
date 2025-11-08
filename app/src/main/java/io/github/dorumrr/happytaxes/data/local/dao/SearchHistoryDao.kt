package io.github.dorumrr.happytaxes.data.local.dao

import androidx.room.*
import io.github.dorumrr.happytaxes.data.local.entity.SearchHistoryEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for search history operations.
 *
 * Features:
 * - Store search queries
 * - Get recent searches (last 20)
 * - Clear history
 * - Auto-update timestamp on duplicate query
 *
 * PRD Reference: Section 4.9 - "Store last 20 searches locally"
 */
@Dao
interface SearchHistoryDao {

    /**
     * Insert or update search query.
     * If query already exists, updates timestamp.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(searchHistory: SearchHistoryEntity)

    /**
     * Get search by query for a specific profile.
     */
    @Query("SELECT * FROM search_history WHERE profileId = :profileId AND query = :query")
    suspend fun getByQuery(profileId: String, query: String): SearchHistoryEntity?

    /**
     * Update timestamp for existing search.
     */
    @Query("UPDATE search_history SET timestamp = :timestamp WHERE profileId = :profileId AND query = :query")
    suspend fun updateTimestamp(profileId: String, query: String, timestamp: Long)

    /**
     * Get recent searches (last 20, most recent first) for a specific profile.
     */
    @Query("""
        SELECT * FROM search_history
        WHERE profileId = :profileId
        ORDER BY timestamp DESC
        LIMIT 20
    """)
    fun getRecentSearches(profileId: String): Flow<List<SearchHistoryEntity>>

    /**
     * Get recent searches as list (for one-time queries) for a specific profile.
     */
    @Query("""
        SELECT * FROM search_history
        WHERE profileId = :profileId
        ORDER BY timestamp DESC
        LIMIT 20
    """)
    suspend fun getRecentSearchesList(profileId: String): List<SearchHistoryEntity>

    /**
     * Delete a specific search query.
     */
    @Query("DELETE FROM search_history WHERE id = :id")
    suspend fun delete(id: Long)

    /**
     * Clear all search history.
     */
    @Query("DELETE FROM search_history")
    suspend fun clearAll()

    /**
     * Delete old searches beyond the 20 most recent for a specific profile.
     * Called after each insert to maintain limit.
     */
    @Query("""
        DELETE FROM search_history
        WHERE profileId = :profileId AND id NOT IN (
            SELECT id FROM search_history
            WHERE profileId = :profileId
            ORDER BY timestamp DESC
            LIMIT 20
        )
    """)
    suspend fun deleteOldSearches(profileId: String)

    /**
     * Get search count for a specific profile.
     */
    @Query("SELECT COUNT(*) FROM search_history WHERE profileId = :profileId")
    suspend fun getCount(profileId: String): Int

    /**
     * Delete all search history for a specific profile.
     * Used when deleting a profile.
     */
    @Query("DELETE FROM search_history WHERE profileId = :profileId")
    suspend fun deleteByProfileId(profileId: String)
}

