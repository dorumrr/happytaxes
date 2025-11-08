package io.github.dorumrr.happytaxes.data.repository

import io.github.dorumrr.happytaxes.data.local.ProfileConstants
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Profile context manager.
 * 
 * Provides access to the current active profile ID throughout the app.
 * All repositories should use this to filter data by profile.
 * 
 * Business Rules:
 * - Current profile ID is stored in DataStore preferences
 * - Defaults to DEFAULT_PROFILE_ID if not set (first launch)
 * - Profile switching updates this value
 */
@Singleton
class ProfileContext @Inject constructor(
    private val preferencesRepository: PreferencesRepository
) {

    /**
     * Get current profile ID as Flow.
     * Returns DEFAULT_PROFILE_ID if not set.
     */
    fun getCurrentProfileId(): Flow<String> {
        return preferencesRepository.getCurrentProfileId().map { profileId ->
            profileId ?: ProfileConstants.DEFAULT_PROFILE_ID
        }
    }

    /**
     * Get current profile ID as suspend function.
     * Returns DEFAULT_PROFILE_ID if not set.
     * 
     * Use this in repositories for one-time queries.
     */
    suspend fun getCurrentProfileIdOnce(): String {
        return preferencesRepository.getCurrentProfileIdOnce()
            ?: ProfileConstants.DEFAULT_PROFILE_ID
    }

    /**
     * Switch to a different profile.
     * This updates the current profile ID in preferences.
     * 
     * @param profileId Profile ID to switch to
     */
    suspend fun switchProfile(profileId: String) {
        preferencesRepository.setCurrentProfileId(profileId)
    }
}

