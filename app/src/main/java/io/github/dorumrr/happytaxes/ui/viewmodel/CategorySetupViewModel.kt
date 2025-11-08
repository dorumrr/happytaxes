package io.github.dorumrr.happytaxes.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.dorumrr.happytaxes.data.repository.CategoryRepository
import io.github.dorumrr.happytaxes.data.repository.ProfileContext
import io.github.dorumrr.happytaxes.data.repository.ProfileRepository
import io.github.dorumrr.happytaxes.domain.model.Category
import io.github.dorumrr.happytaxes.domain.model.TransactionType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for standalone category setup screen.
 *
 * Used when user switches to a profile that has no categories.
 * Requires at least 1 income + 1 expense category.
 */
@HiltViewModel
class CategorySetupViewModel @Inject constructor(
    private val categoryRepository: CategoryRepository,
    private val profileContext: ProfileContext,
    private val profileRepository: ProfileRepository
) : ViewModel() {

    // Category list
    private val _categories = MutableStateFlow<List<Category>>(emptyList())
    val categories: StateFlow<List<Category>> = _categories.asStateFlow()

    // Minimum categories check
    private val _hasMinimumCategories = MutableStateFlow(false)
    val hasMinimumCategories: StateFlow<Boolean> = _hasMinimumCategories.asStateFlow()

    // Error state
    private val _categoryError = MutableStateFlow<String?>(null)
    val categoryError: StateFlow<String?> = _categoryError.asStateFlow()

    // Current profile name
    private val _currentProfileName = MutableStateFlow<String?>(null)
    val currentProfileName: StateFlow<String?> = _currentProfileName.asStateFlow()

    init {
        loadCategories()
        loadCurrentProfile()
    }

    /**
     * Load categories for current profile.
     */
    fun loadCategories() {
        viewModelScope.launch {
            val allCategories = categoryRepository.getAllActive().first()
            _categories.value = allCategories
            checkMinimumCategories(allCategories)
        }
    }

    /**
     * Load current profile information.
     */
    private fun loadCurrentProfile() {
        viewModelScope.launch {
            val profileId = profileContext.getCurrentProfileIdOnce()
            val profile = profileRepository.getProfileById(profileId)
            _currentProfileName.value = profile?.name
        }
    }

    /**
     * Check if minimum categories exist (1 income + 1 expense).
     */
    private fun checkMinimumCategories(categories: List<Category>) {
        val hasIncome = categories.any { it.type == TransactionType.INCOME }
        val hasExpense = categories.any { it.type == TransactionType.EXPENSE }
        _hasMinimumCategories.value = hasIncome && hasExpense
    }

    /**
     * Create a new category.
     */
    fun createCategory(name: String, type: TransactionType, icon: String?, color: String?) {
        viewModelScope.launch {
            _categoryError.value = null // Clear previous errors
            val result = categoryRepository.createCategory(name, type, icon, color)
            result.onSuccess {
                loadCategories()
            }.onFailure { error ->
                _categoryError.value = error.message ?: "Failed to create category"
            }
        }
    }

    /**
     * Update a category.
     */
    fun updateCategory(
        id: String,
        name: String,
        type: TransactionType,
        icon: String?,
        color: String?
    ) {
        viewModelScope.launch {
            _categoryError.value = null // Clear previous errors
            val result = categoryRepository.updateCategory(
                id = id,
                name = name,
                type = type,
                icon = icon,
                color = color,
                displayOrder = 999
            )
            result.onSuccess {
                loadCategories()
            }.onFailure { error ->
                _categoryError.value = error.message ?: "Failed to update category"
            }
        }
    }

    /**
     * Delete a category during onboarding.
     * Allows deletion of categories with demo transactions.
     */
    fun deleteCategory(categoryId: String) {
        viewModelScope.launch {
            _categoryError.value = null // Clear previous errors
            val result = categoryRepository.deleteCategoryDuringOnboarding(categoryId)
            result.onSuccess {
                loadCategories()
            }.onFailure { error ->
                _categoryError.value = error.message ?: "Failed to delete category"
            }
        }
    }

    /**
     * Clear category error message.
     */
    fun clearCategoryError() {
        _categoryError.value = null
    }
}

