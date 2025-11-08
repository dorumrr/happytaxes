package io.github.dorumrr.happytaxes.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.dorumrr.happytaxes.data.local.dao.TransactionDao
import io.github.dorumrr.happytaxes.data.mapper.TransactionMapper
import io.github.dorumrr.happytaxes.data.model.*
import io.github.dorumrr.happytaxes.data.repository.CategoryRepository
import io.github.dorumrr.happytaxes.data.repository.ReportRepository
import io.github.dorumrr.happytaxes.domain.model.Category
import io.github.dorumrr.happytaxes.domain.model.Transaction
import io.github.dorumrr.happytaxes.util.CsvExporter
import io.github.dorumrr.happytaxes.util.PdfExporter
import io.github.dorumrr.happytaxes.util.ZipExporter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * ViewModel for reports screen.
 *
 * Manages:
 * - Report generation
 * - Export operations (CSV, PDF, ZIP)
 * - Period selection
 * - Loading states
 *
 * PRD Reference: Section 4.16 - Reporting Enhancements
 */
@HiltViewModel
class ReportsViewModel @Inject constructor(
    private val reportRepository: ReportRepository,
    private val categoryRepository: CategoryRepository,
    private val transactionDao: TransactionDao,
    private val preferencesRepository: io.github.dorumrr.happytaxes.data.repository.PreferencesRepository,
    private val profileContext: io.github.dorumrr.happytaxes.data.repository.ProfileContext,
    private val csvExporter: CsvExporter,
    private val pdfExporter: PdfExporter,
    private val zipExporter: ZipExporter
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReportsUiState())
    val uiState: StateFlow<ReportsUiState> = _uiState.asStateFlow()

    // Available categories for filtering
    private val _availableCategories = MutableStateFlow<List<Category>>(emptyList())
    val availableCategories: StateFlow<List<Category>> = _availableCategories.asStateFlow()

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

    init {
        _uiState.update { it.copy(isLoading = true) }
        // Load available tax years and current tax year
        viewModelScope.launch {
            val availableTaxYears = reportRepository.getAvailableTaxYears()
            val currentTaxYear = reportRepository.getCurrentTaxYear()
            val profileId = profileContext.getCurrentProfileIdOnce()
            val baseCurrency = preferencesRepository.getBaseCurrency(profileId).first()

            _uiState.update {
                it.copy(
                    availableTaxYears = availableTaxYears,
                    baseCurrency = baseCurrency
                )
            }

            selectTaxYear(currentTaxYear)
        }

        // Load available categories
        viewModelScope.launch {
            categoryRepository.getAllActive().collect { categories ->
                _availableCategories.value = categories
            }
        }
    }

    /**
     * Refresh available tax years (call after adding/deleting transactions).
     */
    fun refreshAvailableTaxYears() {
        viewModelScope.launch {
            val availableTaxYears = reportRepository.getAvailableTaxYears()
            _uiState.update {
                it.copy(availableTaxYears = availableTaxYears)
            }
        }
    }

    /**
     * Select tax year.
     */
    fun selectTaxYear(year: Int) {
        viewModelScope.launch {
            val (startDate, endDate) = reportRepository.getTaxYearRange(year)
            _uiState.update {
                it.copy(
                    selectedPeriod = ReportPeriod.TAX_YEAR,
                    startDate = startDate,
                    endDate = endDate,
                    selectedTaxYear = year,
                    isYearlyView = true
                )
            }
            generateReport()
        }
    }

    /**
     * Toggle between Yearly and Monthly view.
     */
    fun toggleViewMode(isYearly: Boolean) {
        _uiState.update { it.copy(isYearlyView = isYearly) }

        if (isYearly) {
            // Switch to yearly view - use current tax year
            _uiState.value.selectedTaxYear?.let { selectTaxYear(it) }
        } else {
            // Switch to monthly view - use current month
            val now = LocalDate.now()
            selectMonthForReport(now.year, now.monthValue)
        }
    }

    /**
     * Select month for monthly report view.
     */
    fun selectMonthForReport(year: Int, month: Int) {
        viewModelScope.launch {
            val (startDate, endDate) = reportRepository.getMonthlyRange(year, month)
            _uiState.update {
                it.copy(
                    selectedPeriod = ReportPeriod.MONTHLY,
                    startDate = startDate,
                    endDate = endDate,
                    selectedMonth = month,
                    selectedMonthYear = year,
                    isYearlyView = false
                )
            }
            generateReport()
        }
    }

    /**
     * Select monthly period.
     */
    fun selectMonth(year: Int, month: Int) {
        val (startDate, endDate) = reportRepository.getMonthlyRange(year, month)
        _uiState.update {
            it.copy(
                selectedPeriod = ReportPeriod.MONTHLY,
                startDate = startDate,
                endDate = endDate
            )
        }
        generateReport()
    }

    /**
     * Select quarterly period.
     */
    fun selectQuarter(year: Int, quarter: Int) {
        val (startDate, endDate) = reportRepository.getQuarterlyRange(year, quarter)
        _uiState.update {
            it.copy(
                selectedPeriod = ReportPeriod.QUARTERLY,
                startDate = startDate,
                endDate = endDate
            )
        }
        generateReport()
    }

    /**
     * Select custom date range.
     */
    fun selectCustomRange(startDate: LocalDate, endDate: LocalDate) {
        _uiState.update {
            it.copy(
                selectedPeriod = ReportPeriod.CUSTOM,
                startDate = startDate,
                endDate = endDate
            )
        }
        generateReport()
    }

    /**
     * Generate report for selected period.
     */
    private fun generateReport() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            val result = reportRepository.generateReport(
                startDate = _uiState.value.startDate,
                endDate = _uiState.value.endDate,
                period = _uiState.value.selectedPeriod,
                filters = _uiState.value.filters
            )

            result.fold(
                onSuccess = { reportData ->
                    _uiState.update {
                        it.copy(
                            reportData = reportData,
                            isLoading = false
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            error = error.message ?: "Failed to generate report",
                            isLoading = false
                        )
                    }
                }
            )
        }
    }

    /**
     * Export report to CSV.
     */
    fun exportCsv() {
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true, exportError = null) }

            try {
                val profileId = profileContext.getCurrentProfileIdOnce()

                // Get transactions for period
                val transactionEntities = transactionDao.getByDateRange(
                    profileId,
                    _uiState.value.startDate,
                    _uiState.value.endDate
                )

                // Collect first value from Flow and transform
                val transactions = transactionEntities.first()
                    .map { entity -> TransactionMapper.toDomain(entity) }
                    .filter { transaction -> !transaction.isDraft }  // Exclude drafts

                // Get base currency for CSV header
                val baseCurrency = preferencesRepository.getBaseCurrency(profileId).first()

                // Generate filename
                val filename = generateFilename("csv")

                // Export
                val result = csvExporter.exportTransactions(transactions, filename, baseCurrency)

                result.fold(
                    onSuccess = { file ->
                        _uiState.update {
                            it.copy(
                                lastExportPath = file.absolutePath,
                                isExporting = false,
                                exportSuccess = "CSV exported successfully"
                            )
                        }
                    },
                    onFailure = { error ->
                        _uiState.update {
                            it.copy(
                                exportError = error.message ?: "Failed to export CSV",
                                isExporting = false
                            )
                        }
                    }
                )
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        exportError = e.message ?: "Failed to export CSV",
                        isExporting = false
                    )
                }
            }
        }
    }

    /**
     * Export report to PDF.
     */
    fun exportPdf() {
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true, exportError = null) }

            try {
                val reportData = _uiState.value.reportData
                if (reportData == null) {
                    _uiState.update {
                        it.copy(
                            exportError = "No report data available",
                            isExporting = false
                        )
                    }
                    return@launch
                }

                val profileId = profileContext.getCurrentProfileIdOnce()

                // Get transactions for period
                val transactionEntities = transactionDao.getByDateRange(
                    profileId,
                    _uiState.value.startDate,
                    _uiState.value.endDate
                )

                // Collect first value from Flow and transform
                val transactions = transactionEntities.first()
                    .map { entity -> TransactionMapper.toDomain(entity) }
                    .filter { transaction -> !transaction.isDraft }  // Exclude drafts

                // Generate filename
                val filename = generateFilename("pdf")

                // Get base currency and separators
                val baseCurrency = preferencesRepository.getBaseCurrency(profileId).first()
                val decimalSep = preferencesRepository.getDecimalSeparator(profileId).first()
                val thousandSep = preferencesRepository.getThousandSeparator(profileId).first()

                // Export
                val result = pdfExporter.exportReport(reportData, transactions, filename, baseCurrency, decimalSep, thousandSep)

                result.fold(
                    onSuccess = { filePath ->
                        _uiState.update {
                            it.copy(
                                lastExportPath = filePath,
                                isExporting = false,
                                exportSuccess = "PDF exported successfully"
                            )
                        }
                    },
                    onFailure = { error ->
                        _uiState.update {
                            it.copy(
                                exportError = error.message ?: "Failed to export PDF",
                                isExporting = false
                            )
                        }
                    }
                )
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        exportError = e.message ?: "Failed to export PDF",
                        isExporting = false
                    )
                }
            }
        }
    }

    /**
     * Export report to ZIP (CSV + PDF + receipts).
     */
    fun exportZip() {
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true, exportError = null) }

            try {
                val reportData = _uiState.value.reportData
                if (reportData == null) {
                    _uiState.update {
                        it.copy(
                            exportError = "No report data available",
                            isExporting = false
                        )
                    }
                    return@launch
                }

                val profileId = profileContext.getCurrentProfileIdOnce()

                // Get transactions for period
                val transactionEntities = transactionDao.getByDateRange(
                    profileId,
                    _uiState.value.startDate,
                    _uiState.value.endDate
                )

                // Collect first value from Flow and transform
                val transactions = transactionEntities.first()
                    .map { entity -> TransactionMapper.toDomain(entity) }
                    .filter { transaction -> !transaction.isDraft }  // Exclude drafts

                // Get base currency and separators for export
                val baseCurrency = preferencesRepository.getBaseCurrency(profileId).first()
                val decimalSep = preferencesRepository.getDecimalSeparator(profileId).first()
                val thousandSep = preferencesRepository.getThousandSeparator(profileId).first()

                // Export CSV
                val csvFilename = generateFilename("csv")
                val csvResult = csvExporter.exportTransactions(transactions, csvFilename, baseCurrency)
                val csvFile = csvResult.getOrThrow()

                // Export PDF
                val pdfFilename = generateFilename("pdf")
                val pdfResult = pdfExporter.exportReport(reportData, transactions, pdfFilename, baseCurrency, decimalSep, thousandSep)
                val pdfPath = pdfResult.getOrThrow()

                // Export ZIP
                val zipFilename = generateFilename("zip")
                val zipResult = zipExporter.exportReport(csvFile.absolutePath, pdfPath, transactions, zipFilename)

                zipResult.fold(
                    onSuccess = { filePath ->
                        _uiState.update {
                            it.copy(
                                lastExportPath = filePath,
                                isExporting = false,
                                exportSuccess = "ZIP exported successfully"
                            )
                        }
                    },
                    onFailure = { error ->
                        _uiState.update {
                            it.copy(
                                exportError = error.message ?: "Failed to export ZIP",
                                isExporting = false
                            )
                        }
                    }
                )
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        exportError = e.message ?: "Failed to export ZIP",
                        isExporting = false
                    )
                }
            }
        }
    }

    /**
     * Generate filename for export.
     */
    private fun generateFilename(extension: String): String {
        val reportData = _uiState.value.reportData
        val periodString = reportData?.getPeriodString()?.replace(" ", "_")?.replace("/", "-")
            ?: "Report"
        val timestamp = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
        return "HappyTaxes_${periodString}_${timestamp}.$extension"
    }

    /**
     * Clear export success message.
     */
    fun clearExportSuccess() {
        _uiState.update { it.copy(exportSuccess = null) }
    }

    /**
     * Clear export error message.
     */
    fun clearExportError() {
        _uiState.update { it.copy(exportError = null) }
    }

    /**
     * Get available tax years from UI state.
     */
    fun getAvailableTaxYears(): List<Int> {
        return _uiState.value.availableTaxYears
    }

    /**
     * Toggle category in include filter.
     */
    fun toggleIncludeCategory(category: String) {
        _uiState.update { state ->
            val newIncludeCategories = if (category in state.filters.includeCategories) {
                state.filters.includeCategories - category
            } else {
                state.filters.includeCategories + category
            }
            state.copy(
                filters = state.filters.copy(includeCategories = newIncludeCategories)
            )
        }
        generateReport()
    }

    /**
     * Toggle category in exclude filter.
     */
    fun toggleExcludeCategory(category: String) {
        _uiState.update { state ->
            val newExcludeCategories = if (category in state.filters.excludeCategories) {
                state.filters.excludeCategories - category
            } else {
                state.filters.excludeCategories + category
            }
            state.copy(
                filters = state.filters.copy(excludeCategories = newExcludeCategories)
            )
        }
        generateReport()
    }

    /**
     * Set transaction type filter.
     */
    fun setTransactionTypeFilter(filter: TransactionTypeFilter) {
        _uiState.update { state ->
            state.copy(
                filters = state.filters.copy(transactionType = filter)
            )
        }
        generateReport()
    }

    /**
     * Set receipt filter.
     */
    fun setReceiptFilter(filter: ReceiptFilter) {
        _uiState.update { state ->
            state.copy(
                filters = state.filters.copy(receiptFilter = filter)
            )
        }
        generateReport()
    }

    /**
     * Clear all filters.
     */
    fun clearFilters() {
        _uiState.update { state ->
            state.copy(filters = ReportFilters())
        }
        generateReport()
    }

    /**
     * Get available months with transactions.
     * Returns list of (year, month) pairs in reverse chronological order.
     */
    suspend fun getAvailableMonths(): List<Pair<Int, Int>> {
        return reportRepository.getAvailableMonths()
    }
}

/**
 * UI state for reports screen.
 */
data class ReportsUiState(
    val selectedPeriod: ReportPeriod = ReportPeriod.TAX_YEAR,
    val startDate: LocalDate = LocalDate.now(),
    val endDate: LocalDate = LocalDate.now(),
    val selectedTaxYear: Int? = null,
    val availableTaxYears: List<Int> = emptyList(),
    val reportData: ReportData? = null,
    val baseCurrency: String = "GBP",  // User's base currency (loaded from preferences)
    val isLoading: Boolean = false,
    val error: String? = null,
    val isExporting: Boolean = false,
    val exportError: String? = null,
    val exportSuccess: String? = null,
    val lastExportPath: String? = null,
    val filters: ReportFilters = ReportFilters(),
    // View mode: true = Yearly (Tax Year), false = Monthly
    val isYearlyView: Boolean = true,
    val selectedMonth: Int? = null,  // 1-12 for monthly view
    val selectedMonthYear: Int? = null  // Year for monthly view
)
