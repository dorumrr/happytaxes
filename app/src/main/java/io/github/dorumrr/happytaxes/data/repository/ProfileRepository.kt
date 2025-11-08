package io.github.dorumrr.happytaxes.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.dorumrr.happytaxes.data.local.ProfileConstants
import io.github.dorumrr.happytaxes.data.local.dao.CategoryDao
import io.github.dorumrr.happytaxes.data.local.dao.ProfileDao
import io.github.dorumrr.happytaxes.data.local.dao.SearchHistoryDao
import io.github.dorumrr.happytaxes.data.local.dao.TransactionDao
import io.github.dorumrr.happytaxes.data.mapper.ProfileMapper
import io.github.dorumrr.happytaxes.domain.model.Profile
import io.github.dorumrr.happytaxes.util.ConcurrencyHelper
import io.github.dorumrr.happytaxes.util.FileManager
import io.github.dorumrr.happytaxes.util.InputValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for profile operations.
 *
 * Business Rules:
 * - Unlimited profiles allowed
 * - Cannot delete last remaining profile
 * - Deleting profile cascades to ALL associated data:
 *   - Transactions (active, deleted, drafts)
 *   - Categories (seeded + custom)
 *   - Search history
 *   - Receipt files
 * - Users can customize profile icon and color
 */
@Singleton
class ProfileRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val profileDao: ProfileDao,
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao,
    private val searchHistoryDao: SearchHistoryDao,
    private val fileManager: FileManager
) {

    // ========== READ ==========

    /**
     * Get all profiles.
     * Returns Flow for reactive updates.
     */
    fun getAllProfiles(): Flow<List<Profile>> {
        return profileDao.getAllProfiles().map { entities ->
            entities.map { ProfileMapper.toDomain(it) }
        }
    }

    /**
     * Get profile by ID.
     */
    suspend fun getProfileById(profileId: String): Profile? {
        return profileDao.getProfileById(profileId)?.let { ProfileMapper.toDomain(it) }
    }

    /**
     * Get profile by name.
     */
    suspend fun getProfileByName(name: String): Profile? {
        return profileDao.getProfileByName(name)?.let { ProfileMapper.toDomain(it) }
    }

    /**
     * Get profile count.
     */
    suspend fun getProfileCount(): Int {
        return profileDao.getProfileCount()
    }

    /**
     * Check if profile exists.
     */
    suspend fun profileExists(profileId: String): Boolean {
        return profileDao.profileExists(profileId)
    }

    /**
     * Check if can delete profile.
     * Returns true if profile count > 1 (cannot delete last profile).
     */
    suspend fun canDeleteProfile(): Boolean {
        return getProfileCount() > 1
    }

    // ========== CREATE ==========

    /**
     * Create default profiles (Business and Personal) during onboarding.
     * This method checks if the default Business profile from migration exists,
     * and reuses it instead of creating a duplicate.
     *
     * @return List of profiles (Business first, then Personal)
     */
    suspend fun createDefaultProfiles(): List<Profile> {
        val now = Instant.now()

        // Check if the default Business profile from migration exists
        val existingBusinessProfile = profileDao.getProfileById(ProfileConstants.DEFAULT_PROFILE_ID)

        val businessProfile = if (existingBusinessProfile != null) {
            // Use existing profile from migration
            ProfileMapper.toDomain(existingBusinessProfile)
        } else {
            // Create new Business profile (fallback)
            val newBusinessProfile = Profile(
                id = ProfileConstants.DEFAULT_PROFILE_ID,
                name = Profile.NAME_BUSINESS,
                icon = Profile.ICON_BUSINESS,
                color = Profile.COLOR_BUSINESS,
                createdAt = now,
                updatedAt = now
            )
            profileDao.insertProfile(ProfileMapper.toEntity(newBusinessProfile))
            newBusinessProfile
        }

        // Check if Personal profile already exists (prevent duplicates)
        val existingPersonalProfile = profileDao.getProfileByName(Profile.NAME_PERSONAL)

        val personalProfile = if (existingPersonalProfile != null) {
            // Use existing Personal profile
            ProfileMapper.toDomain(existingPersonalProfile)
        } else {
            // Create new Personal profile
            val newPersonalProfile = Profile(
                id = UUID.randomUUID().toString(),
                name = Profile.NAME_PERSONAL,
                icon = Profile.ICON_PERSONAL,
                color = Profile.COLOR_PERSONAL,
                createdAt = now,
                updatedAt = now
            )
            profileDao.insertProfile(ProfileMapper.toEntity(newPersonalProfile))
            newPersonalProfile
        }

        return listOf(businessProfile, personalProfile)
    }

    /**
     * Create a new profile.
     *
     * Business rules:
     * - Profile name must not be empty
     * - Profile name must be unique
     * - Icon and color must be valid
     *
     * @param name Profile name
     * @param icon Profile icon (from Profile.AVAILABLE_ICONS)
     * @param color Profile color (from Profile.AVAILABLE_COLORS)
     * @return Result with created Profile or error
     */
    suspend fun createProfile(name: String, icon: String, color: String): Result<Profile> {
        return ConcurrencyHelper.withProfileLock {
            try {
            // Validate profile name
            if (name.isBlank()) {
                return@withProfileLock Result.failure(
                    IllegalArgumentException("Profile name cannot be empty")
                )
            }

            // Check if profile with this name already exists
            val existing = getProfileByName(name)
            if (existing != null) {
                return@withProfileLock Result.failure(
                    IllegalStateException("Profile '$name' already exists")
                )
            }

            // Validate icon
            if (icon !in Profile.AVAILABLE_ICONS) {
                return@withProfileLock Result.failure(
                    IllegalArgumentException("Invalid icon: $icon")
                )
            }

            // Validate color
            if (color !in Profile.AVAILABLE_COLORS) {
                return@withProfileLock Result.failure(
                    IllegalArgumentException("Invalid color: $color")
                )
            }

            // Create profile
            val now = Instant.now()
            val profile = Profile(
                id = UUID.randomUUID().toString(),
                name = name,
                icon = icon,
                color = color,
                createdAt = now,
                updatedAt = now
            )

                profileDao.insertProfile(ProfileMapper.toEntity(profile))

                Result.success(profile)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    // ========== UPDATE ==========

    /**
     * Update an existing profile.
     *
     * Business rules:
     * - Profile name must not be empty
     * - Profile name must be unique (unless keeping the same name)
     * - Icon and color must be valid
     *
     * @param profileId Profile ID to update
     * @param name New profile name
     * @param icon New profile icon (from Profile.AVAILABLE_ICONS)
     * @param color New profile color (from Profile.AVAILABLE_COLORS)
     * @return Result with updated Profile or error
     */
    suspend fun updateProfile(
        profileId: String,
        name: String,
        icon: String,
        color: String
    ): Result<Profile> {
        return ConcurrencyHelper.withProfileLock {
            try {
                // Verify profile exists
                val existing = getProfileById(profileId)
                    ?: return@withProfileLock Result.failure(
                        IllegalArgumentException("Profile not found")
                    )

                // Validate profile name
                if (name.isBlank()) {
                    return@withProfileLock Result.failure(
                        IllegalArgumentException("Profile name cannot be empty")
                    )
                }

                // Check if profile with this name already exists (only if name is changing)
                if (name != existing.name) {
                    val duplicate = getProfileByName(name)
                    if (duplicate != null) {
                        return@withProfileLock Result.failure(
                            IllegalStateException("Profile '$name' already exists")
                        )
                    }
                }

                // Validate icon
                if (icon !in Profile.AVAILABLE_ICONS) {
                    return@withProfileLock Result.failure(
                        IllegalArgumentException("Invalid icon: $icon")
                    )
                }

                // Validate color
                if (color !in Profile.AVAILABLE_COLORS) {
                    return@withProfileLock Result.failure(
                        IllegalArgumentException("Invalid color: $color")
                    )
                }

                // Update profile
                val updated = existing.copy(
                    name = name,
                    icon = icon,
                    color = color,
                    updatedAt = Instant.now()
                )

                profileDao.updateProfile(ProfileMapper.toEntity(updated))

                Result.success(updated)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    // ========== DELETE ==========

    /**
     * Delete a profile and ALL associated data.
     *
     * Business rules:
     * - Cannot delete last remaining profile
     * - Deletes ALL transactions (active, deleted, drafts)
     * - Deletes ALL categories (seeded + custom)
     * - Deletes ALL search history
     * - Deletes ALL receipt files
     *
     * @param profileId Profile ID to delete
     * @return Result with success or error
     */
    suspend fun deleteProfile(profileId: String): Result<Unit> = withContext(Dispatchers.IO) {
        ConcurrencyHelper.withProfileLock {
            try {
            // Validate can delete
            if (!canDeleteProfile()) {
                return@withProfileLock Result.failure(
                    IllegalStateException("Cannot delete last remaining profile")
                )
            }

            // Verify profile exists
            if (!profileExists(profileId)) {
                return@withProfileLock Result.failure(
                    IllegalArgumentException("Profile not found")
                )
            }

            // 1. Delete all transactions for this profile
            val transactions = transactionDao.getAllActive(profileId).first()
            transactions.forEach { transaction ->
                transactionDao.hardDelete(transaction.id)
            }

            val deletedTransactions = transactionDao.getAllDeleted(profileId).first()
            deletedTransactions.forEach { transaction ->
                transactionDao.hardDelete(transaction.id)
            }

            // 2. Delete all categories for this profile
            val categories = categoryDao.getAllActive(profileId).first()
            categories.forEach { category ->
                categoryDao.delete(category.id)
            }

            // 3. Delete all search history for this profile
            searchHistoryDao.deleteByProfileId(profileId)

            // 4. Delete all receipt files for this profile
            deleteProfileReceiptFiles(profileId)

                // 5. Delete the profile itself
                profileDao.deleteProfile(profileId)

                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * Delete all receipt files for a profile.
     * Deletes the entire /receipts/{profileId}/ directory.
     */
    private fun deleteProfileReceiptFiles(profileId: String) {
        try {
            val receiptsBaseDir = fileManager.getReceiptsBaseDir()
            val profileReceiptsDir = File(receiptsBaseDir, profileId)

            if (profileReceiptsDir.exists() && profileReceiptsDir.isDirectory) {
                profileReceiptsDir.deleteRecursively()
            }
        } catch (e: Exception) {
            // Log error but don't fail the operation
            e.printStackTrace()
        }
    }

    // ========== RESET ==========

    /**
     * Reset ALL app data (app reset only).
     * 
     * DANGEROUS: Bypasses all safety checks. Only use for "Delete Everything" feature.
     * 
     * Deletes (in order):
     * 1. ALL transactions (across all profiles)
     * 2. ALL categories (across all profiles)
     * 3. ALL search history (across all profiles)
     * 4. ALL receipt files (physical files)
     * 5. ALL profiles
     * 
     * After this:
     * - Database is completely empty
     * - All physical receipt files deleted
     * - App should navigate to onboarding
     * 
     * @return Result with success or error
     */
    suspend fun resetAllData(): Result<Unit> = withContext(Dispatchers.IO) {
        ConcurrencyHelper.withProfileLock {
            try {
                // 1. Delete ALL transactions (across all profiles)
                transactionDao.deleteAll()
                
                // 2. Delete ALL categories (across all profiles)
                categoryDao.deleteAll()
                
                // 3. Delete ALL search history (across all profiles)
                searchHistoryDao.clearAll()
                
                // 4. Delete ALL receipt files (physical files)
                deleteAllReceiptFiles()
                
                // 5. Delete ALL profiles
                profileDao.deleteAllProfiles()
                
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * Delete all receipt files from storage (all profiles).
     * Used only during app reset.
     */
    private fun deleteAllReceiptFiles() {
        try {
            val receiptsBaseDir = fileManager.getReceiptsBaseDir()
            if (receiptsBaseDir.exists() && receiptsBaseDir.isDirectory) {
                receiptsBaseDir.deleteRecursively()
            }
        } catch (e: Exception) {
            // Log error but don't fail the operation
            e.printStackTrace()
        }
    }
}

/**
 * Exception thrown when profile limit is reached.
 */
class ProfileLimitException(message: String) : Exception(message)

/**
 * Exception thrown when trying to delete last profile.
 */
class LastProfileException(message: String) : Exception(message)

