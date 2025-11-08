package io.github.dorumrr.happytaxes.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * Search history entity for storing recent searches.
 *
 * Features:
 * - Store last 20 searches per profile
 * - Track timestamp for sorting
 * - Unique queries per profile (no duplicates)
 * - Multi-profile: Each search history belongs to exactly one profile
 *
 * PRD Reference: Section 4.9 - "Store last 20 searches locally"
 */
@Entity(
    tableName = "search_history",
    indices = [
        Index(value = ["profileId"]),
        Index(value = ["profileId", "query"], unique = true),
        Index(value = ["timestamp"])
    ]
)
data class SearchHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /**
     * Profile association (multi-profile support).
     */
    val profileId: String,

    /**
     * Search query text.
     * Unique constraint per profile ensures no duplicate queries.
     */
    val query: String,

    /**
     * Timestamp when search was performed.
     * Used for sorting (most recent first).
     */
    val timestamp: Instant = Instant.now()
)

