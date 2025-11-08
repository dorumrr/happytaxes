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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for Profile Management screen.
 *
 * Features:
 * - Display all profiles
 * - Create new profile (Business or Personal)
 * - Delete profile with double confirmation
 * - Switch between profiles
 *
 * Business Rules:
 * - Maximum 2 profiles allowed
 * - Cannot delete last remaining profile
 * - Deleting profile removes ALL associated data
 */
@HiltViewModel
class ProfileManagementViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val profileContext: ProfileContext,
    private val categoryRepository: io.github.dorumrr.happytaxes.data.repository.CategoryRepository,
    private val preferencesRepository: io.github.dorumrr.happytaxes.data.repository.PreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileManagementUiState())
    val uiState: StateFlow<ProfileManagementUiState> = _uiState.asStateFlow()

    init {
        loadProfiles()
        loadCurrentProfile()
    }

    /**
     * Load all profiles.
     */
    private fun loadProfiles() {
        viewModelScope.launch {
            profileRepository.getAllProfiles().collect { profiles ->
                val canDelete = profileRepository.canDeleteProfile()

                _uiState.value = _uiState.value.copy(
                    profiles = profiles,
                    canDeleteProfile = canDelete,
                    isLoading = false
                )
            }
        }
    }

    /**
     * Load current active profile.
     */
    private fun loadCurrentProfile() {
        viewModelScope.launch {
            profileContext.getCurrentProfileId().collect { profileId ->
                _uiState.value = _uiState.value.copy(currentProfileId = profileId)
            }
        }
    }

    /**
     * Switch to a different profile.
     */
    fun switchProfile(profileId: String) {
        viewModelScope.launch {
            profileContext.switchProfile(profileId)
        }
    }

    /**
     * Show create profile dialog.
     */
    fun showCreateProfileDialog() {
        _uiState.value = _uiState.value.copy(
            showCreateDialog = true,
            profileNameToCreate = "",
            profileIconToCreate = Profile.ICON_BUSINESS,
            profileColorToCreate = Profile.COLOR_BUSINESS,
            createError = null
        )
    }

    /**
     * Hide create profile dialog.
     */
    fun hideCreateProfileDialog() {
        _uiState.value = _uiState.value.copy(
            showCreateDialog = false,
            profileNameToCreate = "",
            createError = null
        )
    }

    /**
     * Update profile name to create.
     */
    fun updateProfileName(name: String) {
        _uiState.value = _uiState.value.copy(profileNameToCreate = name)
    }

    /**
     * Update profile icon to create.
     */
    fun updateProfileIcon(icon: String) {
        _uiState.value = _uiState.value.copy(profileIconToCreate = icon)
    }

    /**
     * Update profile color to create.
     */
    fun updateProfileColor(color: String) {
        _uiState.value = _uiState.value.copy(profileColorToCreate = color)
    }

    /**
     * Create new profile.
     */
    fun createProfile() {
        viewModelScope.launch {
            val name = _uiState.value.profileNameToCreate.trim()
            val icon = _uiState.value.profileIconToCreate
            val color = _uiState.value.profileColorToCreate

            if (name.isBlank()) {
                _uiState.value = _uiState.value.copy(createError = "Profile name cannot be empty")
                return@launch
            }

            _uiState.value = _uiState.value.copy(isCreating = true, createError = null)

            val result = profileRepository.createProfile(name, icon, color)

            result.fold(
                onSuccess = { profile ->
                    // Switch to newly created profile
                    switchProfile(profile.id)

                    // Set default preferences for new profile (copy from existing profile)
                    val existingProfiles = profileRepository.getAllProfiles().first()
                    val existingProfile = existingProfiles.firstOrNull { it.id != profile.id }

                    if (existingProfile != null) {
                        // Copy preferences from existing profile
                        val country = preferencesRepository.getSelectedCountry(existingProfile.id).first()
                        val taxPeriodStart = preferencesRepository.getTaxPeriodStart(existingProfile.id).first()
                        val taxPeriodEnd = preferencesRepository.getTaxPeriodEnd(existingProfile.id).first()
                        val currency = preferencesRepository.getBaseCurrency(existingProfile.id).first()

                        preferencesRepository.setSelectedCountry(profile.id, country)
                        preferencesRepository.setTaxPeriod(profile.id, taxPeriodStart, taxPeriodEnd)
                        preferencesRepository.setBaseCurrency(profile.id, currency)
                    }

                    // DO NOT seed categories here - user will set them up via Category Setup Screen
                    // Categories should only be seeded during onboarding, not when creating additional profiles

                    _uiState.value = _uiState.value.copy(
                        isCreating = false,
                        showCreateDialog = false,
                        profileNameToCreate = ""
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isCreating = false,
                        createError = error.message ?: "Failed to create profile"
                    )
                }
            )
        }
    }

    /**
     * Show edit profile dialog.
     */
    fun showEditProfileDialog(profile: Profile) {
        _uiState.value = _uiState.value.copy(
            showEditDialog = true,
            profileToEdit = profile.id,
            profileNameToEdit = profile.name,
            profileIconToEdit = profile.icon,
            profileColorToEdit = profile.color,
            editError = null
        )
    }

    /**
     * Hide edit profile dialog.
     */
    fun hideEditProfileDialog() {
        _uiState.value = _uiState.value.copy(
            showEditDialog = false,
            profileToEdit = null,
            profileNameToEdit = "",
            editError = null
        )
    }

    /**
     * Update profile name to edit.
     */
    fun updateEditProfileName(name: String) {
        _uiState.value = _uiState.value.copy(profileNameToEdit = name)
    }

    /**
     * Update profile icon to edit.
     */
    fun updateEditProfileIcon(icon: String) {
        _uiState.value = _uiState.value.copy(profileIconToEdit = icon)
    }

    /**
     * Update profile color to edit.
     */
    fun updateEditProfileColor(color: String) {
        _uiState.value = _uiState.value.copy(profileColorToEdit = color)
    }

    /**
     * Update existing profile.
     */
    fun updateProfile() {
        viewModelScope.launch {
            val profileId = _uiState.value.profileToEdit ?: return@launch
            val name = _uiState.value.profileNameToEdit.trim()
            val icon = _uiState.value.profileIconToEdit
            val color = _uiState.value.profileColorToEdit

            if (name.isBlank()) {
                _uiState.value = _uiState.value.copy(editError = "Profile name cannot be empty")
                return@launch
            }

            _uiState.value = _uiState.value.copy(isEditing = true, editError = null)

            val result = profileRepository.updateProfile(profileId, name, icon, color)

            result.fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(
                        isEditing = false,
                        showEditDialog = false,
                        profileToEdit = null,
                        editError = null
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isEditing = false,
                        editError = error.message ?: "Failed to update profile"
                    )
                }
            )
        }
    }

    /**
     * Show delete confirmation dialog.
     */
    fun showDeleteConfirmation(profileId: String) {
        _uiState.value = _uiState.value.copy(
            showDeleteConfirmation = true,
            profileToDelete = profileId
        )
    }

    /**
     * Hide delete confirmation dialog.
     */
    fun hideDeleteConfirmation() {
        _uiState.value = _uiState.value.copy(
            showDeleteConfirmation = false,
            showFinalDeleteConfirmation = false,
            profileToDelete = null,
            deleteError = null
        )
    }

    /**
     * Show final delete confirmation (second step).
     */
    fun showFinalDeleteConfirmation() {
        _uiState.value = _uiState.value.copy(
            showDeleteConfirmation = false,
            showFinalDeleteConfirmation = true
        )
    }

    /**
     * Delete profile (after double confirmation).
     */
    fun deleteProfile() {
        viewModelScope.launch {
            val profileId = _uiState.value.profileToDelete ?: return@launch

            _uiState.value = _uiState.value.copy(isDeleting = true, deleteError = null)

            // Check if we're deleting the current active profile
            val currentProfileId = profileContext.getCurrentProfileIdOnce()
            val isDeletingCurrentProfile = profileId == currentProfileId

            val result = profileRepository.deleteProfile(profileId)

            result.fold(
                onSuccess = {
                    // If we deleted the current profile, switch to the remaining profile
                    if (isDeletingCurrentProfile) {
                        val remainingProfiles = profileRepository.getAllProfiles().first()
                        if (remainingProfiles.isNotEmpty()) {
                            profileContext.switchProfile(remainingProfiles[0].id)
                        }
                    }

                    _uiState.value = _uiState.value.copy(
                        isDeleting = false,
                        showFinalDeleteConfirmation = false,
                        profileToDelete = null
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isDeleting = false,
                        deleteError = error.message ?: "Failed to delete profile"
                    )
                }
            )
        }
    }
}

/**
 * UI state for Profile Management screen.
 */
data class ProfileManagementUiState(
    val profiles: List<Profile> = emptyList(),
    val currentProfileId: String = "",
    val canDeleteProfile: Boolean = false,
    val isLoading: Boolean = true,
    val showCreateDialog: Boolean = false,
    val profileNameToCreate: String = "",
    val profileIconToCreate: String = Profile.ICON_BUSINESS,
    val profileColorToCreate: String = Profile.COLOR_BUSINESS,
    val isCreating: Boolean = false,
    val createError: String? = null,
    val showEditDialog: Boolean = false,
    val profileToEdit: String? = null,
    val profileNameToEdit: String = "",
    val profileIconToEdit: String = Profile.ICON_BUSINESS,
    val profileColorToEdit: String = Profile.COLOR_BUSINESS,
    val isEditing: Boolean = false,
    val editError: String? = null,
    val showDeleteConfirmation: Boolean = false,
    val showFinalDeleteConfirmation: Boolean = false,
    val profileToDelete: String? = null,
    val isDeleting: Boolean = false,
    val deleteError: String? = null
)
