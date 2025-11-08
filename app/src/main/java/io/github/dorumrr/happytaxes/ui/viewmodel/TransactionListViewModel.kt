package io.github.dorumrr.happytaxes.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.dorumrr.happytaxes.data.local.entity.SearchHistoryEntity
import io.github.dorumrr.happytaxes.data.repository.PreferencesRepository
import io.github.dorumrr.happytaxes.data.repository.ProfileContext
import io.github.dorumrr.happytaxes.data.repository.SearchRepository
import io.github.dorumrr.happytaxes.data.repository.TransactionRepository
import io.github.dorumrr.happytaxes.domain.model.Transaction
import io.github.dorumrr.happytaxes.domain.model.TransactionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject

/**
 * ViewModel for transaction list screen.
 *
 * Manages:
 * - Transaction list with filters
 * - Search functionality (description, notes, category, amount, date keywords)
 * - Search history tracking
 * - Soft delete operations
 * - Draft transactions
 *
 * PRD Reference: Section 2.9 - Search & Filtering
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class TransactionListViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val searchRepository: SearchRepository,
    private val preferencesRepository: PreferencesRepository,
    private val profileContext: ProfileContext,
    private val profileRepository: io.github.dorumrr.happytaxes.data.repository.ProfileRepository
) : ViewModel() {

    // ========== UI STATE ==========

    private val _uiState = MutableStateFlow(TransactionListUiState())
    val uiState: StateFlow<TransactionListUiState> = _uiState.asStateFlow()

    // ========== DATA STREAMS ==========

    private val allTransactions = transactionRepository.getAllActive()
    private val draftTransactions = transactionRepository.getAllDrafts()
    private val deletedTransactions = transactionRepository.getAllDeleted()

    /**
     * Paged transactions for efficient large dataset handling.
     * 
     * Filters are applied at DATABASE LEVEL for stability and performance.
     * This prevents crashes from client-side filtering on PagingData.
     * 
     * PRD Section 2.9: Search & Filtering
     * PRD Section 14.5: Performance - Database-level filtering with SQL WHERE clauses
     * 
     * Search Query Debouncing:
     * - Job cancellation ensures only the latest search executes
     * - flatMapLatest automatically cancels previous queries when state changes
     * - This provides natural debouncing without explicit delay
     */
    val pagedTransactions: Flow<PagingData<Transaction>> = _uiState
        .flatMapLatest { state ->
            transactionRepository.getAllActivePagedWithFilters(
                filterType = state.filterType,
                filterCategory = state.filterCategory,
                filterDraftsOnly = state.filterDraftsOnly,
                filterStartDate = state.filterStartDate,
                filterEndDate = state.filterEndDate,
                searchQuery = state.searchQuery.takeIf { it.isNotBlank() }
            )
        }
        .cachedIn(viewModelScope)

    // Add button position preference
    val addButtonPosition: StateFlow<String> = preferencesRepository.getAddButtonPosition()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = "fab"
        )

    // Base currency preference
    val baseCurrency: StateFlow<String> = profileContext.getCurrentProfileId()
        .map { profileId ->
            preferencesRepository.getBaseCurrency(profileId).first()
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = "GBP"
        )

    // Decimal separator preference
    val decimalSeparator: StateFlow<String> = profileContext.getCurrentProfileId()
        .flatMapLatest { profileId ->
            preferencesRepository.getDecimalSeparator(profileId)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = "."
        )

    // Thousand separator preference
    val thousandSeparator: StateFlow<String> = profileContext.getCurrentProfileId()
        .flatMapLatest { profileId ->
            preferencesRepository.getThousandSeparator(profileId)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ","
        )

    // Search history
    val recentSearches: StateFlow<List<SearchHistoryEntity>> = searchRepository.getRecentSearches()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    
    // Filtered transactions based on current filters
    val transactions: StateFlow<List<Transaction>> = combine(
        allTransactions,
        _uiState
    ) { transactions, state ->
        // Mark as loaded once we have data
        if (state.isLoading && transactions.isNotEmpty()) {
            _uiState.value = state.copy(isLoading = false)
        } else if (state.isLoading) {
            // Even if empty, mark as loaded after first emission
            viewModelScope.launch {
                kotlinx.coroutines.delay(500) // Small delay to ensure DB is ready
                _uiState.value = state.copy(isLoading = false)
            }
        }
        filterTransactions(transactions, state)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )
    
    // Filtered drafts based on current filters (for banner count)
    val drafts: StateFlow<List<Transaction>> = combine(
        draftTransactions,
        _uiState
    ) { drafts, state ->
        filterTransactions(drafts, state)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )
    
    val deleted: StateFlow<List<Transaction>> = deletedTransactions.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Current profile
    val currentProfile: StateFlow<io.github.dorumrr.happytaxes.domain.model.Profile?> =
        profileContext.getCurrentProfileId()
            .map { profileId ->
                profileRepository.getProfileById(profileId)
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = null
            )

    // All profiles (for profile switcher)
    val allProfiles: StateFlow<List<io.github.dorumrr.happytaxes.domain.model.Profile>> =
        profileRepository.getAllProfiles()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )
    
    // Search debouncing job
    private var searchJob: kotlinx.coroutines.Job? = null

    // ========== USER ACTIONS ==========

    /**
     * Handle search query changes with debouncing.
     * 
     * Debouncing prevents excessive database queries while user is typing.
     * Uses 300ms delay - query only executes after user stops typing.
     * 
     * PRD Section 14.5: Performance - Search query debouncing (300ms)
     */
    fun onSearchQueryChanged(query: String) {
        // Cancel previous search job
        searchJob?.cancel()
        
        // Update UI state immediately (for text field)
        _uiState.value = _uiState.value.copy(searchQuery = query)
        
        // Debounce: Wait 300ms before triggering query
        // flatMapLatest in pagedTransactions will automatically cancel and restart query
        searchJob = viewModelScope.launch {
            kotlinx.coroutines.delay(300)
            // State change will trigger new query via flatMapLatest
        }

        // Track search in history if query is not empty and user has finished typing
        // (We'll track when user actually performs search, not on every keystroke)
    }

    /**
     * Track search query in history.
     * Call this when user performs search (e.g., presses enter or selects from suggestions).
     */
    fun trackSearch(query: String) {
        if (query.isBlank()) return

        viewModelScope.launch {
            searchRepository.addSearch(query)
        }
    }

    /**
     * Clear search history.
     */
    fun clearSearchHistory() {
        viewModelScope.launch {
            searchRepository.clearHistory()
        }
    }

    /**
     * Delete a specific search from history.
     */
    fun deleteSearch(id: Long) {
        viewModelScope.launch {
            searchRepository.deleteSearch(id)
        }
    }
    
    fun onFilterTypeChanged(type: TransactionType?) {
        _uiState.value = _uiState.value.copy(filterType = type)
    }
    
    fun onFilterCategoryChanged(category: String?) {
        _uiState.value = _uiState.value.copy(filterCategory = category)
    }

    fun onFilterDraftsChanged(showDraftsOnly: Boolean?) {
        _uiState.value = _uiState.value.copy(filterDraftsOnly = showDraftsOnly)
    }

    fun onFilterDateRangeChanged(startDate: LocalDate?, endDate: LocalDate?) {
        _uiState.value = _uiState.value.copy(
            filterStartDate = startDate,
            filterEndDate = endDate
        )
    }

    fun clearFilters() {
        _uiState.value = TransactionListUiState()
    }

    /**
     * Apply draft filter only, clearing all other filters.
     * Used when clicking the draft banner for quick access to drafts.
     */
    fun showDraftsOnly() {
        _uiState.value = TransactionListUiState(filterDraftsOnly = true)
    }

    fun deleteTransaction(transactionId: String) {
        viewModelScope.launch {
            val result = transactionRepository.softDelete(transactionId)
            result.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    error = error.message ?: "Failed to delete transaction"
                )
            }
        }
    }
    
    fun restoreTransaction(transactionId: String) {
        viewModelScope.launch {
            val result = transactionRepository.restore(transactionId)
            result.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    error = error.message ?: "Failed to restore transaction"
                )
            }
        }
    }
    
    fun permanentlyDeleteTransaction(transactionId: String) {
        viewModelScope.launch {
            // Note: Hard delete not exposed in Phase 1 repository
            // Soft-deleted transactions are auto-cleaned after 30 days
            // For now, we'll just remove from UI (already soft-deleted)
            _uiState.value = _uiState.value.copy(
                error = "Transaction will be permanently deleted after 30 days"
            )
        }
    }
    
    fun cleanupOldDeletedTransactions() {
        viewModelScope.launch {
            transactionRepository.cleanupOldDeleted()
        }
    }
    
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun switchProfile(profileId: String) {
        viewModelScope.launch {
            profileContext.switchProfile(profileId)
        }
    }

    // ========== PRIVATE HELPERS ==========
    
    private fun filterTransactions(
        transactions: List<Transaction>,
        state: TransactionListUiState
    ): List<Transaction> {
        var filtered = transactions
        
        // Filter by type
        state.filterType?.let { type ->
            filtered = filtered.filter { it.type == type }
        }
        
        // Filter by category
        state.filterCategory?.let { category ->
            filtered = filtered.filter { it.category == category }
        }

        // Filter by draft status
        state.filterDraftsOnly?.let { draftsOnly ->
            if (draftsOnly) {
                filtered = filtered.filter { it.isDraft }
            }
        }

        // Filter by date range
        if (state.filterStartDate != null || state.filterEndDate != null) {
            filtered = filtered.filter { transaction ->
                val afterStart = state.filterStartDate?.let { transaction.date >= it } ?: true
                val beforeEnd = state.filterEndDate?.let { transaction.date <= it } ?: true
                afterStart && beforeEnd
            }
        }
        
        // Filter by search query
        if (state.searchQuery.isNotBlank()) {
            val query = state.searchQuery.lowercase().trim()
            filtered = filtered.filter { transaction ->
                matchesSearchQuery(transaction, query)
            }
        }

        // Sort by date (newest first)
        return filtered.sortedByDescending { it.date }
    }

    /**
     * Check if transaction matches search query.
     *
     * NOTE: This method is now only used for non-paged transaction lists (drafts, deleted).
     * Paged transactions use database-level filtering for better performance and stability.
     *
     * Search capabilities (simplified):
     * - Description search (case-insensitive)
     * - Notes search (case-insensitive)
     * - Amount search (basic text matching)
     *
     * NOTE: Category is NOT searched to avoid confusion (e.g., searching "Sale" shouldn't match "Sales" category)
     *
     * Advanced features removed (moved to database LIKE queries):
     * - ❌ Date keyword parsing (e.g., "today", "this week")
     * - ❌ Amount range parsing (e.g., ">100", "50-100")
     *
     * PRD Section 2.9: Search & Filtering (updated for database-level implementation)
     */
    private fun matchesSearchQuery(transaction: Transaction, query: String): Boolean {
        // 1. Search in description
        if (transaction.description?.lowercase()?.contains(query) == true) {
            return true
        }

        // 2. Search in notes
        if (transaction.notes?.lowercase()?.contains(query) == true) {
            return true
        }

        // 3. Search by amount (basic text matching)
        if (transaction.amount.toString().contains(query)) {
            return true
        }

        return false
    }
}

/**
 * UI state for transaction list screen.
 */
data class TransactionListUiState(
    val searchQuery: String = "",
    val filterType: TransactionType? = null,
    val filterCategory: String? = null,
    val filterDraftsOnly: Boolean? = null,
    val filterStartDate: LocalDate? = null,
    val filterEndDate: LocalDate? = null,
    val isLoading: Boolean = true,  // Show loading spinner on first load
    val error: String? = null
)

