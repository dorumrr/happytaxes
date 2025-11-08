package io.github.dorumrr.happytaxes.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.dorumrr.happytaxes.data.repository.ProfileContext
import io.github.dorumrr.happytaxes.data.repository.ProfileRepository
import io.github.dorumrr.happytaxes.domain.model.Profile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for Profile Selection screen.
 *
 * Features:
 * - Display available profiles (Business and/or Personal)
 * - Select profile to switch to
 * - Navigate to main screen after selection
 *
 * Business Rules:
 * - This screen is shown at app startup if 2 profiles exist
 * - If only 1 profile exists, app goes directly to main screen
 * - Selected profile becomes the current active profile
 */
@HiltViewModel
class ProfileSelectionViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val profileContext: ProfileContext
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileSelectionUiState())
    val uiState: StateFlow<ProfileSelectionUiState> = _uiState.asStateFlow()

    init {
        loadProfiles()
    }

    /**
     * Load all available profiles.
     */
    private fun loadProfiles() {
        viewModelScope.launch {
            profileRepository.getAllProfiles().collect { profiles ->
                _uiState.value = _uiState.value.copy(
                    profiles = profiles,
                    isLoading = false
                )
            }
        }
    }

    /**
     * Select a profile and set it as current.
     */
    fun selectProfile(profileId: String) {
        viewModelScope.launch {
            profileContext.switchProfile(profileId)
            _uiState.value = _uiState.value.copy(
                selectedProfileId = profileId,
                navigationComplete = true
            )
        }
    }

    /**
     * Reset navigation state (for recomposition).
     */
    fun resetNavigation() {
        _uiState.value = _uiState.value.copy(navigationComplete = false)
    }
}

/**
 * UI state for Profile Selection screen.
 */
data class ProfileSelectionUiState(
    val profiles: List<Profile> = emptyList(),
    val selectedProfileId: String? = null,
    val isLoading: Boolean = true,
    val navigationComplete: Boolean = false
)

