package io.github.dorumrr.happytaxes.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.dorumrr.happytaxes.data.repository.PreferencesRepository
import io.github.dorumrr.happytaxes.data.repository.ProfileContext
import io.github.dorumrr.happytaxes.data.repository.TransactionRepository
import io.github.dorumrr.happytaxes.domain.model.Transaction
import io.github.dorumrr.happytaxes.util.CsvExporter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject

/**
 * ViewModel for Archive screen.
 *
 * Handles:
 * - Loading old transactions based on retention period
 * - Exporting old transactions to ZIP (CSV + receipts)
 * - Optionally deleting old transactions after export
 *
 * PRD Reference: Section 4.17 - Data Retention & Archiving
 */
@HiltViewModel
class ArchiveViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val transactionRepository: TransactionRepository,
    private val preferencesRepository: PreferencesRepository,
    private val profileContext: ProfileContext,
    private val csvExporter: io.github.dorumrr.happytaxes.util.CsvExporter
) : ViewModel() {

    private val _uiState = MutableStateFlow(ArchiveUiState())
    val uiState: StateFlow<ArchiveUiState> = _uiState.asStateFlow()

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
        loadOldTransactions()
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

    /**
     * Load transactions older than retention period.
     */
    private fun loadOldTransactions() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true)

                val profileId = profileContext.getCurrentProfileIdOnce()
                val retentionYears = preferencesRepository.getDataRetentionYears(profileId).first()
                val cutoffDate = LocalDate.now().minusYears(retentionYears.toLong())

                transactionRepository.getTransactionsOlderThan(cutoffDate).collect { transactions ->
                    _uiState.value = _uiState.value.copy(
                        oldTransactions = transactions,
                        retentionYears = retentionYears,
                        cutoffDate = cutoffDate,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Failed to load old transactions: ${e.message}"
                )
            }
        }
    }

    /**
     * Export old transactions to ZIP file.
     * ZIP contains:
     * - transactions.csv (all old transactions)
     * - receipts/ folder (all receipt images)
     *
     * @param deleteAfterExport If true, delete transactions after successful export
     * @param onComplete Callback with File path of exported ZIP, or null if failed
     */
    fun exportToZip(deleteAfterExport: Boolean, onComplete: (File?) -> Unit) {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isExporting = true)

                val transactions = _uiState.value.oldTransactions
                if (transactions.isEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        isExporting = false,
                        errorMessage = "No transactions to export"
                    )
                    onComplete(null)
                    return@launch
                }

                // Get base currency for CSV header
                val profileId = profileContext.getCurrentProfileIdOnce()
                val baseCurrency = preferencesRepository.getBaseCurrency(profileId).first()

                // Export to CSV first
                val timestamp = System.currentTimeMillis()
                val csvResult = csvExporter.exportTransactions(
                    transactions,
                    "archive_$timestamp.csv",
                    baseCurrency
                )

                if (csvResult.isFailure) {
                    _uiState.value = _uiState.value.copy(
                        isExporting = false,
                        errorMessage = "Failed to export CSV: ${csvResult.exceptionOrNull()?.message}"
                    )
                    onComplete(null)
                    return@launch
                }

                val csvFile = csvResult.getOrNull()!!

                // Create ZIP file in cache directory
                val zipFile = File(context.cacheDir, "archive_$timestamp.zip")

                ZipOutputStream(zipFile.outputStream()).use { zipOut ->
                    // Add CSV file
                    zipOut.putNextEntry(ZipEntry("transactions.csv"))
                    csvFile.inputStream().use { input ->
                        input.copyTo(zipOut)
                    }
                    zipOut.closeEntry()

                    // Add receipt files
                    transactions.forEach { transaction ->
                        transaction.receiptPaths.forEach { receiptPath ->
                            val receiptFile = File(receiptPath)
                            if (receiptFile.exists()) {
                                zipOut.putNextEntry(ZipEntry("receipts/${receiptFile.name}"))
                                receiptFile.inputStream().use { input ->
                                    input.copyTo(zipOut)
                                }
                                zipOut.closeEntry()
                            }
                        }
                    }
                }

                // Delete CSV file (we only need the ZIP)
                csvFile.delete()

                // Delete transactions if requested
                if (deleteAfterExport) {
                    transactions.forEach { transaction ->
                        transactionRepository.softDelete(transaction.id)
                    }
                }

                _uiState.value = _uiState.value.copy(
                    isExporting = false,
                    exportSuccess = true
                )

                onComplete(zipFile)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isExporting = false,
                    errorMessage = "Failed to export: ${e.message}"
                )
                onComplete(null)
            }
        }
    }

    /**
     * Dismiss error message.
     */
    fun dismissError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    /**
     * Reset export success flag.
     */
    fun resetExportSuccess() {
        _uiState.value = _uiState.value.copy(exportSuccess = false)
    }
}

/**
 * UI state for Archive screen.
 */
data class ArchiveUiState(
    val oldTransactions: List<Transaction> = emptyList(),
    val retentionYears: Int = 6,
    val cutoffDate: LocalDate = LocalDate.now().minusYears(6),
    val baseCurrency: String = "GBP",  // User's base currency (loaded from preferences)
    val isLoading: Boolean = true,
    val isExporting: Boolean = false,
    val exportSuccess: Boolean = false,
    val errorMessage: String? = null
)

