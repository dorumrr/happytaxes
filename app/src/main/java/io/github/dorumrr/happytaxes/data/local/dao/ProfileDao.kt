package io.github.dorumrr.happytaxes.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import io.github.dorumrr.happytaxes.data.local.entity.ProfileEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for profile operations.
 *
 * Business Rules:
 * - Maximum 2 profiles allowed
 * - Cannot delete last remaining profile
 * - Profile deletion cascades to all associated data
 */
@Dao
interface ProfileDao {

    /**
     * Get all profiles.
     * Returns Flow for reactive updates.
     */
    @Query("SELECT * FROM profiles ORDER BY createdAt ASC")
    fun getAllProfiles(): Flow<List<ProfileEntity>>

    /**
     * Get profile by ID.
     */
    @Query("SELECT * FROM profiles WHERE id = :profileId")
    suspend fun getProfileById(profileId: String): ProfileEntity?

    /**
     * Get profile by name (case-insensitive).
     */
    @Query("SELECT * FROM profiles WHERE name = :name COLLATE NOCASE")
    suspend fun getProfileByName(name: String): ProfileEntity?

    /**
     * Get count of profiles.
     * Used to enforce 2-profile limit.
     */
    @Query("SELECT COUNT(*) FROM profiles")
    suspend fun getProfileCount(): Int

    /**
     * Insert new profile.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: ProfileEntity)

    /**
     * Update existing profile.
     */
    @Update
    suspend fun updateProfile(profile: ProfileEntity)

    /**
     * Delete profile by ID.
     * WARNING: Caller must ensure associated data is deleted first.
     */
    @Query("DELETE FROM profiles WHERE id = :profileId")
    suspend fun deleteProfile(profileId: String)

    /**
     * Delete ALL profiles.
     * WARNING: Only use during app reset. Bypasses all safety checks.
     */
    @Query("DELETE FROM profiles")
    suspend fun deleteAllProfiles()

    /**
     * Check if profile exists.
     */
    @Query("SELECT EXISTS(SELECT 1 FROM profiles WHERE id = :profileId)")
    suspend fun profileExists(profileId: String): Boolean
}

