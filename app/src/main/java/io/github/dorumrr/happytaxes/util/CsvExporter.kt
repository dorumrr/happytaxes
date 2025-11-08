package io.github.dorumrr.happytaxes.util

import android.content.Context
import android.os.Environment
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.dorumrr.happytaxes.domain.model.Transaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVPrinter
import java.io.File
import java.io.FileWriter
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeoutException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * CSV export utility.
 *
 * Exports transaction data to CSV format.
 *
 * PRD Reference: Section 4.16 - Export Formats (CSV)
 */
@Singleton
class CsvExporter @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    
    companion object {
        /**
         * Export timeout: 30 seconds for CSV generation.
         * Reasonable for up to 10,000 transactions.
         */
        private const val EXPORT_TIMEOUT_MS = 30_000L
    }

    /**
     * Export transactions to CSV file in Downloads folder.
     *
     * @param transactions List of transactions
     * @param filename Output filename
     * @param baseCurrency User's base currency code (e.g., "GBP", "USD", "EUR")
     * @return Result with File object or error
     */
    suspend fun exportTransactions(
        transactions: List<Transaction>,
        filename: String,
        baseCurrency: String
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            // Apply timeout to prevent indefinite hangs
            withTimeout(EXPORT_TIMEOUT_MS) {
            // Get Downloads directory
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) {
                downloadsDir.mkdirs()
            }

            // Create CSV file in Downloads
            val csvFile = File(downloadsDir, filename)
            val writer = FileWriter(csvFile)

            // Define CSV format with dynamic base currency in header
            val csvFormat = CSVFormat.DEFAULT.builder()
                .setHeader(
                    "Date",
                    "Type",
                    "Category",
                    "Description",
                    "Amount ($baseCurrency)",
                    "Receipt",
                    "Notes",
                    "Draft"
                )
                .build()

            // Write data
            CSVPrinter(writer, csvFormat).use { printer ->
                transactions.forEach { transaction ->
                    printer.printRecord(
                        transaction.date.format(dateFormatter),
                        transaction.type.name,
                        transaction.category,
                        transaction.description ?: "",
                        transaction.amount.toPlainString(),
                        if (transaction.receiptPaths.isNotEmpty()) "Yes" else "No",
                        transaction.notes ?: "",
                        if (transaction.isDraft) "Yes" else "No"
                    )
                }
                }

                Result.success(csvFile)
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            Result.failure(TimeoutException("CSV export timed out after ${EXPORT_TIMEOUT_MS / 1000} seconds. Try exporting fewer transactions."))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get exports directory.
     */
    fun getExportsDir(): File {
        val exportsDir = File(context.filesDir, "exports")
        if (!exportsDir.exists()) {
            exportsDir.mkdirs()
        }
        return exportsDir
    }

    /**
     * Delete export file.
     */
    suspend fun deleteExport(filePath: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val file = File(filePath)
            if (file.exists()) {
                file.delete()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get all export files.
     */
    fun getAllExports(): List<File> {
        val exportsDir = getExportsDir()
        return exportsDir.listFiles()?.toList() ?: emptyList()
    }
}

