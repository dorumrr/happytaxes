package io.github.dorumrr.happytaxes.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import io.github.dorumrr.happytaxes.domain.model.Transaction
import io.github.dorumrr.happytaxes.domain.model.TransactionType
import io.github.dorumrr.happytaxes.ui.component.ReceiptThumbnailGrid
import io.github.dorumrr.happytaxes.ui.theme.Alpha
import io.github.dorumrr.happytaxes.ui.theme.CornerRadius
import io.github.dorumrr.happytaxes.ui.theme.Dimensions
import io.github.dorumrr.happytaxes.ui.theme.Spacing
import io.github.dorumrr.happytaxes.ui.util.ProfileIconMapper
import io.github.dorumrr.happytaxes.ui.viewmodel.CategoryViewModel
import io.github.dorumrr.happytaxes.ui.viewmodel.TransactionDetailViewModel
import io.github.dorumrr.happytaxes.ui.viewmodel.TransactionListViewModel
import io.github.dorumrr.happytaxes.util.DateFormats
import kotlinx.coroutines.launch

/**
 * Main ledger screen showing transaction list with filters.
 *
 * Features:
 * - Transaction list (newest first)
 * - Search bar
 * - Filter chips (type, category, date range)
 * - FAB to add new transaction
 * - Swipe to delete
 * - Empty state
 * - Profile switcher with: Profile list
 * - Top bar actions: Search, Filter, Recently Deleted
 * - Bottom navigation: Transactions, Reports, Settings
 *
 * Note: Backup & Restore is now accessible via Settings > Manage Your Data
 * Note: Manage Categories is now accessible via Settings > General
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerScreen(
    onNavigateToDetail: (String?) -> Unit,
    onNavigateToDeleted: () -> Unit,
    onNavigateToSettings: () -> Unit = {},
    onNavigateToReports: () -> Unit = {},
    onNavigateToCategoryManagement: () -> Unit = {},
    onNavigateToCategorySetup: () -> Unit = {},
    onNavigateToProfiles: () -> Unit = {},
    onNavigateToBackupRestore: () -> Unit = {},
    onNavigateToReceiptViewer: (List<String>, Int, String?) -> Unit = { _, _, _ -> },
    scrollToTransactionId: String? = null,
    onScrollHandled: () -> Unit = {},
    viewModel: TransactionListViewModel = hiltViewModel(),
    categoryViewModel: CategoryViewModel = hiltViewModel(),
    usePagination: Boolean = true
) {
    val pagedTransactions = if (usePagination) {
        val pagingFlow = remember(viewModel) { viewModel.pagedTransactions }
        pagingFlow.collectAsLazyPagingItems()
    } else {
        null
    }
    val transactionsState = if (!usePagination) {
        viewModel.transactions.collectAsState()
    } else {
        remember { mutableStateOf(emptyList<Transaction>()) }
    }
    val transactions = transactionsState.value
    val drafts by viewModel.drafts.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val allCategories by categoryViewModel.allCategories.collectAsState()
    val recentSearches by viewModel.recentSearches.collectAsState()
    val currentProfile by viewModel.currentProfile.collectAsState()
    val allProfiles by viewModel.allProfiles.collectAsState()
    val addButtonPosition by viewModel.addButtonPosition.collectAsState()
    val baseCurrency by viewModel.baseCurrency.collectAsState()
    val decimalSeparator by viewModel.decimalSeparator.collectAsState()
    val thousandSeparator by viewModel.thousandSeparator.collectAsState()
    val categoriesLoaded by categoryViewModel.categoriesLoaded.collectAsState()

    var showSearchBar by remember { mutableStateOf(false) }
    var showFilterSheet by remember { mutableStateOf(false) }

    // Determine which buttons to show
    val showTopButton = addButtonPosition == "top" || addButtonPosition == "both"
    val showFabButton = addButtonPosition == "fab" || addButtonPosition == "both"

    // Check if current profile has categories, navigate to setup if not
    // IMPORTANT: Only navigate if categories are loaded AND empty (not just loading)
    // This prevents race condition where categories haven't loaded yet after app update
    LaunchedEffect(currentProfile?.id, allCategories, categoriesLoaded) {
        if (currentProfile != null && categoriesLoaded && allCategories.isEmpty()) {
            onNavigateToCategorySetup()
        }
    }

    // Handle back press when search is active
    BackHandler(enabled = showSearchBar) {
        // Track search if query is not empty
        if (uiState.searchQuery.isNotBlank()) {
            viewModel.trackSearch(uiState.searchQuery)
        }
        showSearchBar = false
        viewModel.onSearchQueryChanged("") // Clear search
    }

    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (showSearchBar) {
                SearchBar(
                    query = uiState.searchQuery,
                    onQueryChange = viewModel::onSearchQueryChanged,
                    onClose = {
                        // Track search if query is not empty
                        if (uiState.searchQuery.isNotBlank()) {
                            viewModel.trackSearch(uiState.searchQuery)
                        }
                        showSearchBar = false
                        viewModel.onSearchQueryChanged("")
                    },
                    recentSearches = recentSearches,
                    onSelectRecentSearch = { query ->
                        viewModel.onSearchQueryChanged(query)
                        viewModel.trackSearch(query)
                    },
                    onClearHistory = viewModel::clearSearchHistory,
                    onDeleteSearch = viewModel::deleteSearch
                )
            } else {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Start
                        ) {
                            // Profile switcher (only show if 2+ profiles exist)
                            if (allProfiles.size >= 2 && currentProfile != null) {
                                ProfileSwitcher(
                                    currentProfile = currentProfile!!,
                                    allProfiles = allProfiles,
                                    onSwitchProfile = viewModel::switchProfile,
                                    onManageProfiles = onNavigateToProfiles
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }

                            Text("Transactions")
                            if (showTopButton) {
                                IconButton(onClick = { onNavigateToDetail(null) }) {
                                    Icon(Icons.Default.Add, "Add Transaction")
                                }
                            }
                        }
                    },
                    actions = {
                        // Search, Filter, and Recently Deleted (near edge)
                        IconButton(onClick = { showSearchBar = true }) {
                            Icon(Icons.Default.Search, "Search")
                        }
                        IconButton(onClick = { showFilterSheet = true }) {
                            Icon(Icons.Default.FilterList, "Sort")
                        }
                        IconButton(onClick = { onNavigateToDeleted() }) {
                            Icon(Icons.Default.Delete, "Recently Deleted")
                        }
                    }
                )
            }
        },
        floatingActionButton = {
            if (showFabButton) {
                FloatingActionButton(
                    onClick = { onNavigateToDetail(null) },
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(Icons.Default.Add, "Add Transaction")
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
            // Active filters and search chips
            if (uiState.filterType != null || uiState.filterCategory != null ||
                uiState.filterDraftsOnly != null || uiState.searchQuery.isNotEmpty()) {
                FilterChipsRow(
                    filterType = uiState.filterType,
                    filterCategory = uiState.filterCategory,
                    filterDraftsOnly = uiState.filterDraftsOnly,
                    searchQuery = uiState.searchQuery,
                    onClearFilters = viewModel::clearFilters,
                    onClearSearch = { viewModel.onSearchQueryChanged("") }
                )
            }
            
            // Draft banner (hidden when draft filter is active)
            if (drafts.isNotEmpty() && uiState.filterDraftsOnly != true) {
                DraftBanner(
                    draftCount = drafts.size,
                    onClick = { viewModel.showDraftsOnly() }
                )
            }

            // Transaction list
            if (usePagination) {
                pagedTransactions?.let { lazyItems ->
                    when (val refreshState = lazyItems.loadState.refresh) {
                        is LoadState.Loading -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                        is LoadState.Error -> {
                            EmptyState(
                                message = refreshState.error.message
                                    ?: "Failed to load transactions",
                                actionText = "Retry",
                                onAction = lazyItems::retry
                            )
                        }
                        else -> {
                            when {
                                lazyItems.itemCount == 0 && uiState.searchQuery.isBlank() -> {
                                    EmptyState(
                                        message = "No transactions yet",
                                        actionText = "Add your first transaction",
                                        onAction = { onNavigateToDetail(null) }
                                    )
                                }
                                lazyItems.itemCount == 0 -> {
                                    EmptyState(
                                        message = "No transactions found",
                                        actionText = "Clear filters",
                                        onAction = viewModel::clearFilters
                                    )
                                }
                                else -> {
                                    PagedTransactionList(
                                        pagedTransactions = lazyItems,
                                        baseCurrency = baseCurrency,
                                        decimalSeparator = decimalSeparator,
                                        thousandSeparator = thousandSeparator,
                                        onTransactionClick = { transaction ->
                                            onNavigateToDetail(transaction.id)
                                        },
                                        onTransactionDelete = viewModel::deleteTransaction,
                                        onNavigateToReceiptViewer = onNavigateToReceiptViewer,
                                        scrollToTransactionId = scrollToTransactionId,
                                        onScrollHandled = onScrollHandled
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                when {
                    uiState.isLoading -> {
                        // Show loading spinner on first load
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                    transactions.isEmpty() && uiState.searchQuery.isBlank() -> {
                        EmptyState(
                            message = "No transactions yet",
                            actionText = "Add your first transaction",
                            onAction = { onNavigateToDetail(null) }
                        )
                    }
                    transactions.isEmpty() -> {
                        EmptyState(
                            message = "No transactions found",
                            actionText = "Clear filters",
                            onAction = viewModel::clearFilters
                        )
                    }
                    else -> {
                        TransactionList(
                            transactions = transactions,
                            baseCurrency = baseCurrency,
                            decimalSeparator = decimalSeparator,
                            thousandSeparator = thousandSeparator,
                            onTransactionClick = { transaction ->
                                onNavigateToDetail(transaction.id)
                            },
                            onTransactionDelete = viewModel::deleteTransaction,
                            onNavigateToReceiptViewer = onNavigateToReceiptViewer,
                            scrollToTransactionId = scrollToTransactionId,
                            onScrollHandled = onScrollHandled
                        )
                    }
                }
            }
            
            // Clickable scrim to dismiss search when clicking outside
            if (showSearchBar) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null
                        ) {
                            showSearchBar = false
                            viewModel.onSearchQueryChanged("")
                        }
                )
            }
        }

        // Filter bottom sheet
        if (showFilterSheet) {
            FilterBottomSheet(
                uiState = uiState,
                categories = allCategories,
                viewModel = viewModel,
                onDismiss = { showFilterSheet = false },
                onFilterTypeChanged = viewModel::onFilterTypeChanged,
                onFilterCategoryChanged = viewModel::onFilterCategoryChanged,
                onClearFilters = viewModel::clearFilters
            )
        }

        // Error snackbar
        uiState.error?.let { error ->
            LaunchedEffect(error) {
                snackbarHostState.showSnackbar(
                    message = error,
                    duration = SnackbarDuration.Short
                )
                viewModel.clearError()
            }
        }

    }
}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    recentSearches: List<io.github.dorumrr.happytaxes.data.local.entity.SearchHistoryEntity>,
    onSelectRecentSearch: (String) -> Unit,
    onClearHistory: () -> Unit,
    onDeleteSearch: (Long) -> Unit
) {
    var showRecentSearches by remember { mutableStateOf(false) }

    Column {
        TopAppBar(
            title = {
                TextField(
                    value = query,
                    onValueChange = onQueryChange,
                    placeholder = { Text("Search: amount, description, notes...") },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    ),
                    trailingIcon = {
                        Row {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { onQueryChange("") }) {
                                    Icon(Icons.Default.Close, "Clear search")
                                }
                            }
                            if (recentSearches.isNotEmpty()) {
                                IconButton(onClick = { showRecentSearches = !showRecentSearches }) {
                                    Icon(
                                        if (showRecentSearches) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                        "Recent searches"
                                    )
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            navigationIcon = {
                IconButton(onClick = onClose) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Close search")
                }
            }
        )

        // Recent searches dropdown
        if (showRecentSearches && recentSearches.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.medium, vertical = Spacing.small)
            ) {
                Column(
                    modifier = Modifier.padding(Spacing.small)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.small, vertical = Spacing.extraSmall),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Recent Searches",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(onClick = {
                            onClearHistory()
                            showRecentSearches = false
                        }) {
                            Text("Clear All", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    HorizontalDivider()

                    recentSearches.forEach { searchHistory ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelectRecentSearch(searchHistory.query)
                                    showRecentSearches = false
                                }
                                .padding(horizontal = Spacing.small, vertical = Spacing.mediumSmall),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = null,
                                    modifier = Modifier.size(Dimensions.iconMediumSmall),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(Spacing.mediumSmall))
                                Text(
                                    text = searchHistory.query,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            IconButton(
                                onClick = { onDeleteSearch(searchHistory.id) },
                                modifier = Modifier.size(Dimensions.iconLarge)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Remove",
                                    modifier = Modifier.size(Dimensions.iconSmall)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterChipsRow(
    filterType: TransactionType?,
    filterCategory: String?,
    filterDraftsOnly: Boolean?,
    searchQuery: String,
    onClearFilters: () -> Unit,
    onClearSearch: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.medium, vertical = Spacing.small),
        horizontalArrangement = Arrangement.spacedBy(Spacing.small)
    ) {
        // Search chip
        if (searchQuery.isNotEmpty()) {
            FilterChip(
                selected = true,
                onClick = onClearSearch,
                label = { Text("Search: \"$searchQuery\"") },
                trailingIcon = {
                    Icon(Icons.Default.Close, "Clear search", Modifier.size(Dimensions.iconSmall))
                }
            )
        }

        // Type filter chip
        filterType?.let {
            FilterChip(
                selected = true,
                onClick = onClearFilters,
                label = { Text(it.name) },
                trailingIcon = {
                    Icon(Icons.Default.Close, "Remove filter", Modifier.size(Dimensions.iconSmall))
                }
            )
        }

        // Category filter chip
        filterCategory?.let {
            FilterChip(
                selected = true,
                onClick = onClearFilters,
                label = { Text(it) },
                trailingIcon = {
                    Icon(Icons.Default.Close, "Remove filter", Modifier.size(Dimensions.iconSmall))
                }
            )
        }

        // Drafts filter chip
        if (filterDraftsOnly == true) {
            FilterChip(
                selected = true,
                onClick = onClearFilters,
                label = { Text("Drafts Only") },
                trailingIcon = {
                    Icon(Icons.Default.Close, "Remove filter", Modifier.size(Dimensions.iconSmall))
                }
            )
        }

        // Clear all button (only show if multiple filters active)
        val activeFiltersCount = listOfNotNull(
            if (searchQuery.isNotEmpty()) 1 else null,
            filterType,
            filterCategory,
            if (filterDraftsOnly == true) 1 else null
        ).size
        if (activeFiltersCount > 1) {
            TextButton(onClick = {
                onClearFilters()
                onClearSearch()
            }) {
                Text("Clear all")
            }
        }
    }
}

@Composable
private fun DraftBanner(
    draftCount: Int,
    onClick: () -> Unit
) {
    // Minimal approach: no border, no icon, just red dot + text
    // Subtle background color with reduced padding
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(horizontal = Spacing.medium, vertical = Spacing.small),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.small)
        ) {
            // Small colored indicator
            Box(
                modifier = Modifier
                    .size(4.dp)
                    .background(
                        MaterialTheme.colorScheme.error,
                        shape = CircleShape
                    )
            )
            Text(
                text = "$draftCount draft transaction${if (draftCount > 1) "s" else ""}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun TransactionList(
    transactions: List<Transaction>,
    baseCurrency: String,
    decimalSeparator: String,
    thousandSeparator: String,
    onTransactionClick: (Transaction) -> Unit,
    onTransactionDelete: (String) -> Unit,
    onNavigateToReceiptViewer: (List<String>, Int, String?) -> Unit,
    scrollToTransactionId: String? = null,
    onScrollHandled: () -> Unit = {}
) {
    val listState = rememberLazyListState()

    // Track the first transaction ID to detect new additions
    val firstTransactionId = remember { mutableStateOf(transactions.firstOrNull()?.id) }

    // Scroll to specific transaction (after edit/add) - only when scrollToTransactionId changes
    LaunchedEffect(scrollToTransactionId) {
        if (scrollToTransactionId != null) {
            val index = transactions.indexOfFirst { it.id == scrollToTransactionId }
            if (index >= 0) {
                // Calculate center position (show item in middle of screen)
                val layoutInfo = listState.layoutInfo
                if (layoutInfo.visibleItemsInfo.isNotEmpty()) {
                    // Scroll so the item appears at 1/3 from top
                    listState.animateScrollToItem(
                        index = index,
                        scrollOffset = -layoutInfo.viewportSize.height / 3
                    )
                } else {
                    // Fallback if layout info not available yet
                    listState.animateScrollToItem(index)
                }
            }
            // Clear the scroll target after animation completes
            onScrollHandled()
        }
    }

    // Scroll to top when new transaction added (first ID changed)
    LaunchedEffect(transactions.firstOrNull()?.id) {
        val currentFirstId = transactions.firstOrNull()?.id
        if (currentFirstId != null &&
            currentFirstId != firstTransactionId.value &&
            firstTransactionId.value != null) { // Only if we had a previous value
            firstTransactionId.value = currentFirstId
            if (listState.firstVisibleItemIndex > 0) {
                listState.animateScrollToItem(0)
            }
        } else if (firstTransactionId.value == null) {
            // Initialize on first load
            firstTransactionId.value = currentFirstId
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            bottom = Dimensions.fabWithBottomNavPadding
        ) // Extra bottom padding for FAB + bottom nav
    ) {
        items(transactions, key = { it.id }) { transaction ->
            TransactionListItem(
                transaction = transaction,
                baseCurrency = baseCurrency,
                decimalSeparator = decimalSeparator,
                thousandSeparator = thousandSeparator,
                onClick = { onTransactionClick(transaction) },
                onDelete = { onTransactionDelete(transaction.id) },
                onReceiptClick = { index ->
                    // Pass transaction ID for scroll positioning
                    // (delete button controlled by NavGraph)
                    onNavigateToReceiptViewer(transaction.receiptPaths, index, transaction.id)
                }
            )
        }
    }
}

/**
 * Paged transaction list using Paging 3 for efficient large dataset handling.
 * Loads 50 items at a time, reducing memory usage and improving scroll performance.
 */
@Composable
private fun PagedTransactionList(
    pagedTransactions: LazyPagingItems<Transaction>,
    baseCurrency: String,
    decimalSeparator: String,
    thousandSeparator: String,
    onTransactionClick: (Transaction) -> Unit,
    onTransactionDelete: (String) -> Unit,
    onNavigateToReceiptViewer: (List<String>, Int, String?) -> Unit,
    scrollToTransactionId: String? = null,
    onScrollHandled: () -> Unit = {}
) {
    val listState = rememberLazyListState()

    // Track the first transaction ID to detect new additions
    val firstTransactionId = remember { mutableStateOf(pagedTransactions.itemSnapshotList.items.firstOrNull()?.id) }

    // Scroll to specific transaction (after edit/add) - only when scrollToTransactionId changes
    LaunchedEffect(scrollToTransactionId) {
        if (scrollToTransactionId != null) {
            // Find the index in the paged list
            val items = pagedTransactions.itemSnapshotList.items
            val index = items.indexOfFirst { it.id == scrollToTransactionId }
            if (index >= 0) {
                // Calculate center position (show item in middle of screen)
                val layoutInfo = listState.layoutInfo
                if (layoutInfo.visibleItemsInfo.isNotEmpty()) {
                    // Scroll so the item appears at 1/3 from top
                    listState.animateScrollToItem(
                        index = index,
                        scrollOffset = -layoutInfo.viewportSize.height / 3
                    )
                } else {
                    // Fallback if layout info not available yet
                    listState.animateScrollToItem(index)
                }
            }
            // Clear the scroll target after animation completes
            onScrollHandled()
        }
    }

    // Scroll to top when new transaction added (first ID changed)
    LaunchedEffect(pagedTransactions.itemSnapshotList.items.firstOrNull()?.id) {
        val currentFirstId = pagedTransactions.itemSnapshotList.items.firstOrNull()?.id
        if (currentFirstId != null &&
            currentFirstId != firstTransactionId.value &&
            firstTransactionId.value != null) { // Only if we had a previous value
            firstTransactionId.value = currentFirstId
            if (listState.firstVisibleItemIndex > 0) {
                listState.animateScrollToItem(0)
            }
        } else if (firstTransactionId.value == null) {
            // Initialize on first load
            firstTransactionId.value = currentFirstId
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            bottom = Dimensions.fabWithBottomNavPadding
        )
    ) {
        items(
            count = pagedTransactions.itemCount,
            key = pagedTransactions.itemKey { it.id }
        ) { index ->
            val transaction = pagedTransactions[index]
            transaction?.let {
                TransactionListItem(
                    transaction = it,
                    baseCurrency = baseCurrency,
                    decimalSeparator = decimalSeparator,
                    thousandSeparator = thousandSeparator,
                    onClick = { onTransactionClick(it) },
                    onDelete = { onTransactionDelete(it.id) },
                    onReceiptClick = { receiptIndex ->
                        // Pass transaction ID for scroll positioning
                        // (delete button controlled by NavGraph)
                        onNavigateToReceiptViewer(it.receiptPaths, receiptIndex, it.id)
                    }
                )
            }
        }

        // Show a subtle loading indicator when appending next page
        if (pagedTransactions.loadState.append is LoadState.Loading) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = Spacing.small),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Loading...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun TransactionListItem(
    transaction: Transaction,
    baseCurrency: String,
    decimalSeparator: String,
    thousandSeparator: String,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onReceiptClick: (Int) -> Unit = {}
) {
    // Edge-to-edge list item with divider (Material 3 style)
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
        ) {
            // Foreground (transaction content)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onClick)
                    .padding(horizontal = Spacing.medium, vertical = Spacing.mediumSmall),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Category icon (if no receipt) or receipt thumbnail
                if (transaction.receiptPaths.isNotEmpty()) {
                    ReceiptThumbnailGrid(
                        receiptPaths = transaction.receiptPaths,
                        modifier = Modifier.padding(end = Spacing.mediumSmall),
                        onReceiptClick = onReceiptClick
                    )
                } else {
                    // Show category icon when no receipt (same size as receipt thumbnail)
                    Box(
                        modifier = Modifier
                            .size(Dimensions.receiptThumbnail)
                            .background(
                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                shape = MaterialTheme.shapes.small
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Receipt,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(Dimensions.iconMedium)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(Spacing.mediumSmall))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = transaction.category,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Normal
                    )
                    transaction.description?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = transaction.date.format(DateFormats.DISPLAY_DATE),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.width(Spacing.small))

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = transaction.getDisplayAmount(baseCurrency, decimalSeparator, thousandSeparator),
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (transaction.type == TransactionType.INCOME) {
                            MaterialTheme.colorScheme.tertiary
                        } else {
                            MaterialTheme.colorScheme.error
                        }
                    )
                    if (transaction.isDraft) {
                        Spacer(modifier = Modifier.height(Spacing.extraSmall))
                        Badge(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        ) {
                            Text(
                                text = "DRAFT",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(
                                    horizontal = Spacing.verySmall,
                                    vertical = Spacing.extraExtraSmall
                                )
                            )
                        }
                    }
                }
            }
        }

        // Divider between items (edge-to-edge)
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant
        )
    }
}

@Composable
private fun EmptyState(
    message: String,
    actionText: String,
    onAction: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.extraLarge),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(Spacing.medium))
        Button(onClick = onAction) {
            Text(actionText)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterBottomSheet(
    uiState: io.github.dorumrr.happytaxes.ui.viewmodel.TransactionListUiState,
    categories: List<io.github.dorumrr.happytaxes.domain.model.Category>,
    viewModel: TransactionListViewModel,
    onDismiss: () -> Unit,
    onFilterTypeChanged: (TransactionType?) -> Unit,
    onFilterCategoryChanged: (String?) -> Unit,
    onClearFilters: () -> Unit
) {
    var expandedCategoryDropdown by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.medium)
        ) {
            Text(
                text = "Filters",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = Spacing.medium)
            )

            // Type filter - Auto-apply on selection
            Text("Transaction Type", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(Spacing.small))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.small)
            ) {
                FilterChip(
                    selected = uiState.filterType == null,
                    onClick = {
                        onFilterTypeChanged(null)
                        onDismiss()
                    },
                    label = { Text("All") }
                )
                FilterChip(
                    selected = uiState.filterType == TransactionType.INCOME,
                    onClick = {
                        onFilterTypeChanged(TransactionType.INCOME)
                        onDismiss()
                    },
                    label = { Text("Income") }
                )
                FilterChip(
                    selected = uiState.filterType == TransactionType.EXPENSE,
                    onClick = {
                        onFilterTypeChanged(TransactionType.EXPENSE)
                        onDismiss()
                    },
                    label = { Text("Expense") }
                )
            }

            Spacer(modifier = Modifier.height(Spacing.large))

            // Draft filter - Auto-apply on selection
            Text("Status", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(Spacing.small))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.small)
            ) {
                FilterChip(
                    selected = uiState.filterDraftsOnly == null,
                    onClick = {
                        viewModel.onFilterDraftsChanged(null)
                        onDismiss()
                    },
                    label = { Text("All") }
                )
                FilterChip(
                    selected = uiState.filterDraftsOnly == true,
                    onClick = {
                        viewModel.onFilterDraftsChanged(true)
                        onDismiss()
                    },
                    label = { Text("Drafts Only") }
                )
            }

            Spacer(modifier = Modifier.height(Spacing.large))

            // Category filter - Dropdown with auto-apply
            Text("Category", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(Spacing.small))

            ExposedDropdownMenuBox(
                expanded = expandedCategoryDropdown,
                onExpandedChange = { expandedCategoryDropdown = it }
            ) {
                OutlinedTextField(
                    value = uiState.filterCategory ?: "All Categories",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Select Category") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedCategoryDropdown) },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                )

                ExposedDropdownMenu(
                    expanded = expandedCategoryDropdown,
                    onDismissRequest = { expandedCategoryDropdown = false }
                ) {
                    // "All Categories" option
                    DropdownMenuItem(
                        text = { Text("All Categories", style = MaterialTheme.typography.bodyLarge) },
                        onClick = {
                            onFilterCategoryChanged(null)
                            expandedCategoryDropdown = false
                            onDismiss()
                        }
                    )

                    HorizontalDivider()

                    // Income categories
                    val incomeCategories = categories.filter { it.type == TransactionType.INCOME }.sortedBy { it.name }
                    if (incomeCategories.isNotEmpty()) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    "INCOME",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            },
                            onClick = {},
                            enabled = false
                        )
                        incomeCategories.forEach { category ->
                            DropdownMenuItem(
                                text = { Text("  ${category.name}") },
                                onClick = {
                                    onFilterCategoryChanged(category.name)
                                    expandedCategoryDropdown = false
                                    onDismiss()
                                }
                            )
                        }

                        HorizontalDivider()
                    }

                    // Expense categories
                    val expenseCategories = categories.filter { it.type == TransactionType.EXPENSE }.sortedBy { it.name }
                    if (expenseCategories.isNotEmpty()) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    "EXPENSES",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            },
                            onClick = {},
                            enabled = false
                        )
                        expenseCategories.forEach { category ->
                            DropdownMenuItem(
                                text = { Text("  ${category.name}") },
                                onClick = {
                                    onFilterCategoryChanged(category.name)
                                    expandedCategoryDropdown = false
                                    onDismiss()
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(Spacing.large))

            // Clear filters button
            OutlinedButton(
                onClick = {
                    onClearFilters()
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Clear All Filters")
            }

            Spacer(modifier = Modifier.height(Spacing.extraLarge))
        }
    }
}

/**
 * Profile switcher component for app bar.
 * Shows only icon + caret. Dropdown shows icon + name for each profile + Manage Profiles.
 */
@Composable
private fun ProfileSwitcher(
    currentProfile: io.github.dorumrr.happytaxes.domain.model.Profile,
    allProfiles: List<io.github.dorumrr.happytaxes.domain.model.Profile>,
    onSwitchProfile: (String) -> Unit,
    onManageProfiles: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    val profileColor = Color(android.graphics.Color.parseColor(currentProfile.color))
    val icon = ProfileIconMapper.getIcon(currentProfile.icon)

    Box {
        // Current profile button - ONLY icon + caret
        IconButton(
            onClick = { showMenu = true },
            modifier = Modifier.size(40.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = "Switch profile",
                    tint = profileColor,
                    modifier = Modifier.size(24.dp)
                )
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = profileColor,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // Profile menu - shows icon + name for each profile
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            allProfiles.forEach { profile ->
                val isActive = profile.id == currentProfile.id
                val color = Color(android.graphics.Color.parseColor(profile.color))
                val profileIcon = ProfileIconMapper.getIcon(profile.icon)

                DropdownMenuItem(
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = profileIcon,
                                contentDescription = profile.name,
                                tint = color
                            )
                            Text(
                                text = profile.name,
                                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
                            )
                            if (isActive) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Active",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    },
                    onClick = {
                        if (!isActive) {
                            onSwitchProfile(profile.id)
                        }
                        showMenu = false
                    },
                    enabled = !isActive
                )
            }

            // Divider before Manage Profiles
            HorizontalDivider()

            // Manage Profiles menu item
            DropdownMenuItem(
                text = { Text("Manage Profiles") },
                onClick = {
                    showMenu = false
                    onManageProfiles()
                },
                leadingIcon = {
                    Icon(Icons.Default.AccountCircle, contentDescription = null)
                }
            )
        }
    }
}
