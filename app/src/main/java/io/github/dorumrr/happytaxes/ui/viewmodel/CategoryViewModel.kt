package io.github.dorumrr.happytaxes.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.dorumrr.happytaxes.data.repository.CategoryRepository
import io.github.dorumrr.happytaxes.data.repository.PreferencesRepository
import io.github.dorumrr.happytaxes.data.repository.ProfileContext
import io.github.dorumrr.happytaxes.domain.model.Category
import io.github.dorumrr.happytaxes.domain.model.TransactionType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for category management.
 * 
 * Manages:
 * - Category list (active only)
 * - Category creation and editing
 * - Category deletion (permanent, only if empty)
 * - Default category seeding
 */
@HiltViewModel
class CategoryViewModel @Inject constructor(
    private val categoryRepository: CategoryRepository,
    private val preferencesRepository: PreferencesRepository,
    private val profileContext: ProfileContext
) : ViewModel() {

    // ========== UI STATE ==========

    private val _uiState = MutableStateFlow(CategoryUiState())
    val uiState: StateFlow<CategoryUiState> = _uiState.asStateFlow()

    init {
        loadBaseCurrency()
    }

    /**
     * Load base currency from preferences.
     */
    private fun loadBaseCurrency() {
        viewModelScope.launch {
            val profileId = profileContext.getCurrentProfileIdOnce()
            val baseCurrency = preferencesRepository.getBaseCurrency(profileId).first()
            _uiState.value = _uiState.value.copy(baseCurrency = baseCurrency)
        }
    }
    
    // ========== DATA STREAMS ==========

    // Track if categories have been loaded at least once
    private val _categoriesLoaded = MutableStateFlow(false)
    val categoriesLoaded: StateFlow<Boolean> = _categoriesLoaded.asStateFlow()

    val allCategories: StateFlow<List<Category>> = categoryRepository.getAllActive()
        .onEach { categories ->
            // Mark as loaded after first emission (even if empty)
            if (!_categoriesLoaded.value) {
                _categoriesLoaded.value = true
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    
    val incomeCategories: StateFlow<List<Category>> = categoryRepository.getByType(TransactionType.INCOME)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    
    val expenseCategories: StateFlow<List<Category>> = categoryRepository.getByType(TransactionType.EXPENSE)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val decimalSeparator: StateFlow<String> = profileContext.getCurrentProfileId()
        .flatMapLatest { profileId ->
            preferencesRepository.getDecimalSeparator(profileId)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = "."
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val thousandSeparator: StateFlow<String> = profileContext.getCurrentProfileId()
        .flatMapLatest { profileId ->
            preferencesRepository.getThousandSeparator(profileId)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ","
        )

    // ========== USER ACTIONS ==========
    
    fun createCategory(
        name: String,
        type: TransactionType,
        icon: String? = null,
        color: String? = null
    ) {
        if (name.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "Category name is required")
            return
        }
        
        viewModelScope.launch {
            val result = categoryRepository.createCategory(
                name = name,
                type = type,
                icon = icon,
                color = color
            )
            
            result.onSuccess {
                _uiState.value = _uiState.value.copy(
                    isCreated = true,
                    error = null
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    error = error.message ?: "Failed to create category"
                )
            }
        }
    }
    
    fun updateCategory(
        id: String,
        name: String,
        type: io.github.dorumrr.happytaxes.domain.model.TransactionType,
        icon: String?,
        color: String?
    ) {
        if (name.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "Category name is required")
            return
        }

        viewModelScope.launch {
            val result = categoryRepository.updateCategory(
                id = id,
                name = name,
                type = type,
                icon = icon,
                color = color,
                displayOrder = 999 // Keep existing order, reordering not in Phase 1
            )

            result.onSuccess {
                _uiState.value = _uiState.value.copy(
                    isUpdated = true,
                    error = null
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    error = error.message ?: "Failed to update category"
                )
            }
        }
    }
    
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
    
    fun resetCreatedFlag() {
        _uiState.value = _uiState.value.copy(isCreated = false)
    }
    
    fun resetUpdatedFlag() {
        _uiState.value = _uiState.value.copy(isUpdated = false)
    }

    /**
     * Show add category dialog.
     */
    fun showAddDialog() {
        _uiState.value = _uiState.value.copy(
            showAddDialog = true,
            error = null
        )
    }

    /**
     * Hide add category dialog.
     */
    fun hideAddDialog() {
        _uiState.value = _uiState.value.copy(
            showAddDialog = false,
            error = null
        )
    }

    /**
     * Show edit category dialog.
     */
    fun showEditDialog(category: Category) {
        viewModelScope.launch {
            val count = getCategoryTransactionCount(category.id)
            _uiState.value = _uiState.value.copy(
                showEditDialog = true,
                selectedCategory = category,
                selectedCategoryTransactionCount = count,
                error = null
            )
        }
    }

    /**
     * Hide edit category dialog.
     */
    fun hideEditDialog() {
        _uiState.value = _uiState.value.copy(
            showEditDialog = false,
            selectedCategory = null,
            selectedCategoryTransactionCount = 0,
            error = null
        )
    }

    /**
     * Show delete confirmation dialog.
     */
    fun showDeleteDialog(category: Category) {
        _uiState.value = _uiState.value.copy(
            showDeleteDialog = true,
            categoryToDelete = category,
            deleteError = null
        )
    }

    /**
     * Hide delete confirmation dialog.
     */
    fun hideDeleteDialog() {
        _uiState.value = _uiState.value.copy(
            showDeleteDialog = false,
            categoryToDelete = null,
            deleteError = null
        )
    }

    /**
     * Delete a category permanently.
     * PRD Section 4.14: "Cannot delete if category has transactions (must be empty)"
     */
    fun deleteCategory(categoryId: String) {
        viewModelScope.launch {
            val result = categoryRepository.deleteCategory(categoryId)

            result.onSuccess {
                hideDeleteDialog()
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    deleteError = error.message ?: "Failed to delete category"
                )
            }
        }
    }

    /**
     * Get transaction count for a category.
     */
    suspend fun getCategoryTransactionCount(categoryId: String): Int {
        return categoryRepository.getCategoryTransactionCount(categoryId)
    }

    /**
     * Get total amount for a category.
     */
    suspend fun getCategoryTotalAmount(categoryId: String): java.math.BigDecimal {
        return categoryRepository.getCategoryTotalAmount(categoryId)
    }

    /**
     * Check if category can be deleted.
     */
    suspend fun canDeleteCategory(categoryId: String): Boolean {
        return categoryRepository.canDeleteCategory(categoryId)
    }

    /**
     * Move all transactions from one category to another.
     *
     * PRD Reference: Section 4.14 - Move transactions between categories (bulk action)
     */
    fun moveTransactions(fromCategoryName: String, toCategoryName: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                error = null,
                moveInProgress = true
            )

            val result = categoryRepository.moveTransactionsToCategory(
                fromCategoryName = fromCategoryName,
                toCategoryName = toCategoryName
            )

            result.fold(
                onSuccess = { count ->
                    _uiState.value = _uiState.value.copy(
                        moveInProgress = false,
                        moveSuccess = "Moved $count transaction(s) successfully",
                        showMoveDialog = false,
                        categoryToMove = null,
                        error = null
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        moveInProgress = false,
                        error = error.message ?: "Failed to move transactions"
                    )
                }
            )
        }
    }

    /**
     * Show move transactions dialog.
     */
    fun showMoveDialog(category: Category) {
        _uiState.value = _uiState.value.copy(
            showMoveDialog = true,
            categoryToMove = category
        )
    }

    /**
     * Hide move transactions dialog.
     */
    fun hideMoveDialog() {
        _uiState.value = _uiState.value.copy(
            showMoveDialog = false,
            categoryToMove = null,
            error = null
        )
    }

    /**
     * Clear move success message.
     */
    fun clearMoveSuccess() {
        _uiState.value = _uiState.value.copy(moveSuccess = null)
    }
}

/**
 * UI state for category management.
 */
data class CategoryUiState(
    val baseCurrency: String = "GBP",  // User's base currency (loaded from preferences)
    val isCreated: Boolean = false,
    val isUpdated: Boolean = false,
    val error: String? = null,
    val selectedCategory: Category? = null,
    val selectedCategoryTransactionCount: Int = 0,
    val showAddDialog: Boolean = false,
    val showEditDialog: Boolean = false,
    val showDeleteDialog: Boolean = false,
    val categoryToDelete: Category? = null,
    val deleteError: String? = null,
    val showMoveDialog: Boolean = false,
    val categoryToMove: Category? = null,
    val moveInProgress: Boolean = false,
    val moveSuccess: String? = null
)

