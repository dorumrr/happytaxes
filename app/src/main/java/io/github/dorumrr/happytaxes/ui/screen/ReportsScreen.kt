package io.github.dorumrr.happytaxes.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.dorumrr.happytaxes.ui.theme.Spacing
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.dorumrr.happytaxes.data.model.*
import io.github.dorumrr.happytaxes.ui.viewmodel.ReportsViewModel
import io.github.dorumrr.happytaxes.util.CurrencyFormatter
import java.math.BigDecimal
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Reports screen.
 *
 * Displays Profit & Loss reports with export options.
 * Accessible via bottom navigation bar.
 *
 * PRD Reference: Section 4.16 - Reporting Enhancements
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    onNavigateBack: () -> Unit,
    viewModel: ReportsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val availableCategories by viewModel.availableCategories.collectAsState()
    val decimalSeparator by viewModel.decimalSeparator.collectAsState()
    val thousandSeparator by viewModel.thousandSeparator.collectAsState()
    val scrollState = rememberScrollState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var showFiltersDialog by remember { mutableStateOf(false) }

    // Refresh available tax years when screen is displayed
    LaunchedEffect(Unit) {
        viewModel.refreshAvailableTaxYears()
    }

    // Show export success snackbar
    LaunchedEffect(uiState.exportSuccess) {
        uiState.exportSuccess?.let { message ->
            snackbarHostState.showSnackbar(
                message = "$message - Saved to Downloads folder",
                duration = SnackbarDuration.Long
            )
            viewModel.clearExportSuccess()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Reports") },
                actions = {
                    // Filter button
                    IconButton(onClick = { showFiltersDialog = true }) {
                        Badge(
                            containerColor = if (uiState.filters != ReportFilters()) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            }
                        ) {
                            Icon(Icons.Default.FilterList, "Filters")
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        if (uiState.isLoading && uiState.reportData == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(Spacing.medium)
        ) {
            // View mode toggle (Yearly/Monthly)
            ViewModeToggle(
                isYearlyView = uiState.isYearlyView,
                onViewModeChanged = { viewModel.toggleViewMode(it) }
            )

            Spacer(modifier = Modifier.height(Spacing.medium))

            // Period selector (Tax Year or Month)
            if (uiState.isYearlyView) {
                TaxYearSelector(
                    selectedTaxYear = uiState.selectedTaxYear,
                    availableTaxYears = viewModel.getAvailableTaxYears(),
                    onTaxYearSelected = { viewModel.selectTaxYear(it) },
                    viewModel = viewModel
                )
            } else {
                MonthSelector(
                    selectedMonth = uiState.selectedMonth,
                    selectedYear = uiState.selectedMonthYear,
                    viewModel = viewModel,
                    onMonthSelected = { year, month -> viewModel.selectMonthForReport(year, month) }
                )
            }

            Spacer(modifier = Modifier.height(Spacing.large))

            // Loading state
            if (uiState.isLoading && uiState.reportData != null) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            // Error state
            uiState.error?.let { error ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = error,
                        modifier = Modifier.padding(Spacing.medium),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            // Report data
            uiState.reportData?.let { reportData ->
                // Summary card
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(Spacing.medium)
                    ) {
                        Text(
                            text = reportData.getPeriodString(),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(Spacing.medium))

                        // Income
                        SummaryRow(
                            label = "Total Income",
                            amount = reportData.totalIncome,
                            isPositive = true,
                            baseCurrency = uiState.baseCurrency,
                            decimalSeparator = decimalSeparator,
                            thousandSeparator = thousandSeparator
                        )

                        Spacer(modifier = Modifier.height(Spacing.small))

                        // Expenses
                        SummaryRow(
                            label = "Total Expenses",
                            amount = reportData.totalExpenses,
                            isPositive = false,
                            baseCurrency = uiState.baseCurrency,
                            decimalSeparator = decimalSeparator,
                            thousandSeparator = thousandSeparator
                        )

                        Spacer(modifier = Modifier.height(Spacing.small))

                        HorizontalDivider()

                        Spacer(modifier = Modifier.height(Spacing.small))

                        // Net Profit
                        SummaryRow(
                            label = "Net Profit",
                            amount = reportData.netProfit,
                            isPositive = reportData.netProfit >= BigDecimal.ZERO,
                            isBold = true,
                            baseCurrency = uiState.baseCurrency,
                            decimalSeparator = decimalSeparator,
                            thousandSeparator = thousandSeparator
                        )
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.medium))

                // Category breakdown
                if (reportData.categoryBreakdown.isNotEmpty()) {
                    Text(
                        text = "Category Breakdown",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(Spacing.small))

                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(Spacing.medium)
                        ) {
                            reportData.categoryBreakdown.take(10).forEach { category ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = category.categoryName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = "${CurrencyFormatter.formatAmount(
                                            amount = category.totalAmount,
                                            currencyCode = uiState.baseCurrency,
                                            decimalSeparator = decimalSeparator,
                                            thousandSeparator = thousandSeparator
                                        )} (${String.format("%.1f", category.percentage)}%)",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                                if (category != reportData.categoryBreakdown.take(10).last()) {
                                    Spacer(modifier = Modifier.height(Spacing.small))
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(Spacing.large))
                }

                // Export buttons
                Text(
                    text = "Export Options",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(Spacing.small))

                ExportButtons(
                    isExporting = uiState.isExporting,
                    onExportCsv = { viewModel.exportCsv() },
                    onExportPdf = { viewModel.exportPdf() },
                    onExportZip = { viewModel.exportZip() }
                )

                // Export error
                uiState.exportError?.let { error ->
                    Spacer(modifier = Modifier.height(Spacing.small))
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        // Filters dialog
        if (showFiltersDialog) {
            FiltersDialog(
                filters = uiState.filters,
                availableCategories = availableCategories,
                onDismiss = { showFiltersDialog = false },
                onTransactionTypeFilterChanged = { viewModel.setTransactionTypeFilter(it) },
                onReceiptFilterChanged = { viewModel.setReceiptFilter(it) },
                onClearFilters = { viewModel.clearFilters() }
            )
        }
    }
}

/**
 * View mode toggle between Yearly and Monthly.
 */
@Composable
private fun ViewModeToggle(
    isYearlyView: Boolean,
    onViewModeChanged: (Boolean) -> Unit
) {
    Column {
        Text(
            text = "View Mode",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(Spacing.small))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.small)
        ) {
            // Yearly button
            FilterChip(
                selected = isYearlyView,
                onClick = { onViewModeChanged(true) },
                label = { Text("Yearly") },
                modifier = Modifier.weight(1f)
            )

            // Monthly button
            FilterChip(
                selected = !isYearlyView,
                onClick = { onViewModeChanged(false) },
                label = { Text("Monthly") },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * Tax year selector dropdown.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaxYearSelector(
    selectedTaxYear: Int?,
    availableTaxYears: List<Int>,
    onTaxYearSelected: (Int) -> Unit,
    viewModel: ReportsViewModel
) {
    var expanded by remember { mutableStateOf(false) }

    Column {
        Text(
            text = "Tax Year",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(Spacing.small))

        // Tax year dropdown
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { 
                expanded = it
                if (it) {
                    // Refresh years when user opens the dropdown
                    viewModel.refreshAvailableTaxYears()
                }
            }
        ) {
            OutlinedTextField(
                value = selectedTaxYear?.let { "$it-${(it + 1).toString().takeLast(2)} Tax Year" } ?: "",
                onValueChange = {},
                readOnly = true,
                label = { Text("Tax Year") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                availableTaxYears.forEach { year ->
                    DropdownMenuItem(
                        text = { Text("$year-${(year + 1).toString().takeLast(2)} Tax Year") },
                        onClick = {
                            onTaxYearSelected(year)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

/**
 * Month selector dropdown.
 * Shows only months that have transactions, in reverse chronological order.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MonthSelector(
    selectedMonth: Int?,
    selectedYear: Int?,
    viewModel: ReportsViewModel,
    onMonthSelected: (Int, Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var availableMonths by remember { mutableStateOf<List<Pair<Int, Int>>>(emptyList()) }
    val scope = rememberCoroutineScope()

    val monthNames = listOf(
        1 to "January", 2 to "February", 3 to "March", 4 to "April",
        5 to "May", 6 to "June", 7 to "July", 8 to "August",
        9 to "September", 10 to "October", 11 to "November", 12 to "December"
    )

    // Load available months
    LaunchedEffect(Unit) {
        availableMonths = viewModel.getAvailableMonths()
    }

    Column {
        Text(
            text = "Month",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(Spacing.small))

        // Month dropdown
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { 
                expanded = it 
                if (it) {
                    scope.launch {
                        availableMonths = viewModel.getAvailableMonths()
                    }
                }
            }
        ) {
            OutlinedTextField(
                value = if (selectedMonth != null && selectedYear != null) {
                    val monthName = monthNames.find { it.first == selectedMonth }?.second ?: ""
                    "$selectedYear $monthName"
                } else {
                    ""
                },
                onValueChange = {},
                readOnly = true,
                label = { Text("Select Month") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.heightIn(max = 400.dp)
            ) {
                // Show months in reverse chronological order (newest first)
                availableMonths.forEach { (year, month) ->
                    val monthName = monthNames.find { it.first == month }?.second ?: ""
                    DropdownMenuItem(
                        text = { Text("$year $monthName") },
                        onClick = {
                            onMonthSelected(year, month)
                            expanded = false
                        }
                    )
                }

                if (availableMonths.isEmpty()) {
                    Text(
                        text = "No transactions found",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(Spacing.medium)
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryRow(
    label: String,
    amount: BigDecimal,
    isPositive: Boolean,
    isBold: Boolean = false,
    baseCurrency: String,
    decimalSeparator: String,
    thousandSeparator: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal
        )
        Text(
            text = CurrencyFormatter.formatAmount(
                amount = amount,
                currencyCode = baseCurrency,
                decimalSeparator = decimalSeparator,
                thousandSeparator = thousandSeparator
            ),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
            color = if (isBold) {
                if (isPositive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurface
            }
        )
    }
}

@Composable
private fun ExportButtons(
    isExporting: Boolean,
    onExportCsv: () -> Unit,
    onExportPdf: () -> Unit,
    onExportZip: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(Spacing.small)
    ) {
        Button(
            onClick = onExportCsv,
            enabled = !isExporting,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Download, contentDescription = null)
            Spacer(modifier = Modifier.width(Spacing.small))
            Text("Export CSV")
        }

        Button(
            onClick = onExportPdf,
            enabled = !isExporting,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Download, contentDescription = null)
            Spacer(modifier = Modifier.width(Spacing.small))
            Text("Export PDF")
        }

        Button(
            onClick = onExportZip,
            enabled = !isExporting,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Download, contentDescription = null)
            Spacer(modifier = Modifier.width(Spacing.small))
            Text("Export ZIP (CSV + PDF + Receipts)")
        }
    }
}

/**
 * Filters dialog.
 *
 * PRD Reference: Section 4.16 - Report Filters
 */
@Composable
private fun FiltersDialog(
    filters: ReportFilters,
    availableCategories: List<io.github.dorumrr.happytaxes.domain.model.Category>,
    onDismiss: () -> Unit,
    onTransactionTypeFilterChanged: (TransactionTypeFilter) -> Unit,
    onReceiptFilterChanged: (ReceiptFilter) -> Unit,
    onClearFilters: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Report Filters") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Transaction Type Filter
                Text(
                    text = "Transaction Type",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(Spacing.small))

                TransactionTypeFilter.values().forEach { filter ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = Spacing.extraSmall),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = filters.transactionType == filter,
                            onClick = { onTransactionTypeFilterChanged(filter) }
                        )
                        Spacer(modifier = Modifier.width(Spacing.small))
                        Text(
                            text = when (filter) {
                                TransactionTypeFilter.ALL -> "All Transactions"
                                TransactionTypeFilter.INCOME_ONLY -> "Income Only"
                                TransactionTypeFilter.EXPENSES_ONLY -> "Expenses Only"
                            },
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.medium))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(Spacing.medium))

                // Receipt Filter
                Text(
                    text = "Receipt Status",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(Spacing.small))

                ReceiptFilter.values().forEach { filter ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = Spacing.extraSmall),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = filters.receiptFilter == filter,
                            onClick = { onReceiptFilterChanged(filter) }
                        )
                        Spacer(modifier = Modifier.width(Spacing.small))
                        Text(
                            text = when (filter) {
                                ReceiptFilter.ALL -> "All Transactions"
                                ReceiptFilter.WITH_RECEIPTS -> "With Receipts Only"
                                ReceiptFilter.WITHOUT_RECEIPTS -> "Without Receipts Only"
                            },
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.medium))

                // Active filters summary
                if (filters != ReportFilters()) {
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(Spacing.medium))
                    Text(
                        text = "Active Filters",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(Spacing.small))

                    if (filters.transactionType != TransactionTypeFilter.ALL) {
                        Text(
                            text = "• ${when (filters.transactionType) {
                                TransactionTypeFilter.INCOME_ONLY -> "Income only"
                                TransactionTypeFilter.EXPENSES_ONLY -> "Expenses only"
                                else -> ""
                            }}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    if (filters.receiptFilter != ReceiptFilter.ALL) {
                        Text(
                            text = "• ${when (filters.receiptFilter) {
                                ReceiptFilter.WITH_RECEIPTS -> "With receipts only"
                                ReceiptFilter.WITHOUT_RECEIPTS -> "Without receipts only"
                                else -> ""
                            }}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        },
        dismissButton = {
            if (filters != ReportFilters()) {
                TextButton(onClick = {
                    onClearFilters()
                    onDismiss()
                }) {
                    Text("Clear All")
                }
            }
        }
    )
}
