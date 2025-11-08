package io.github.dorumrr.happytaxes.data.repository

import io.github.dorumrr.happytaxes.data.local.dao.SearchHistoryDao
import io.github.dorumrr.happytaxes.data.local.entity.SearchHistoryEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for search history operations.
 *
 * Features:
 * - Track search queries
 * - Get recent searches (last 20)
 * - Clear history
 * - Auto-cleanup old searches
 * - Multi-profile support (filters by current profile)
 *
 * PRD Reference: Section 4.9 - "Store last 20 searches locally"
 */
@Singleton
class SearchRepository @Inject constructor(
    private val searchHistoryDao: SearchHistoryDao,
    private val profileContext: ProfileContext
) {

    /**
     * Add search query to history.
     * If query already exists, updates timestamp.
     * Automatically cleans up old searches beyond 20.
     *
     * @param query Search query text
     */
    suspend fun addSearch(query: String) {
        if (query.isBlank()) return

        val profileId = profileContext.getCurrentProfileIdOnce()
        val trimmedQuery = query.trim()
        val now = Instant.now()

        // Check if query already exists for this profile
        val existing = searchHistoryDao.getByQuery(profileId, trimmedQuery)

        if (existing != null) {
            // Update timestamp for existing search
            searchHistoryDao.updateTimestamp(profileId, trimmedQuery, now.toEpochMilli())
        } else {
            // Insert new search
            val searchHistory = SearchHistoryEntity(
                profileId = profileId,
                query = trimmedQuery,
                timestamp = now
            )
            searchHistoryDao.insert(searchHistory)
        }

        // Clean up old searches (keep only last 20 per profile)
        searchHistoryDao.deleteOldSearches(profileId)
    }

    /**
     * Get recent searches (last 20, most recent first) for current profile.
     *
     * @return Flow of recent searches
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun getRecentSearches(): Flow<List<SearchHistoryEntity>> {
        return profileContext.getCurrentProfileId().flatMapLatest { profileId ->
            searchHistoryDao.getRecentSearches(profileId)
        }
    }

    /**
     * Get recent searches as list (for one-time queries) for current profile.
     *
     * @return List of recent searches
     */
    suspend fun getRecentSearchesList(): List<SearchHistoryEntity> {
        val profileId = profileContext.getCurrentProfileIdOnce()
        return searchHistoryDao.getRecentSearchesList(profileId)
    }

    /**
     * Delete a specific search from history.
     *
     * @param id Search history ID
     */
    suspend fun deleteSearch(id: Long) {
        searchHistoryDao.delete(id)
    }

    /**
     * Clear all search history for current profile.
     */
    suspend fun clearHistory() {
        searchHistoryDao.clearAll()
    }

    /**
     * Get search count for current profile.
     *
     * @return Number of searches in history
     */
    suspend fun getSearchCount(): Int {
        val profileId = profileContext.getCurrentProfileIdOnce()
        return searchHistoryDao.getCount(profileId)
    }
}

