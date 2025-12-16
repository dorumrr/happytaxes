package io.github.dorumrr.happytaxes.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.dorumrr.happytaxes.domain.model.Transaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVPrinter
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.io.OutputStreamWriter
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

    // Use ISO 8601 date format (YYYY-MM-DD) for universal compatibility
    // This format is unambiguous and correctly parsed by all spreadsheet applications
    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    
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
     * @return Result with file path string or error
     */
    suspend fun exportTransactions(
        transactions: List<Transaction>,
        filename: String,
        baseCurrency: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            // Apply timeout to prevent indefinite hangs
            withTimeout(EXPORT_TIMEOUT_MS) {
                // Write to Downloads (MediaStore for Android 10+, File API for Android 9-)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    exportCsvUsingMediaStore(context, filename, transactions, baseCurrency)
                } else {
                    exportCsvUsingFileApi(filename, transactions, baseCurrency)
                }
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            Result.failure(TimeoutException("CSV export timed out after ${EXPORT_TIMEOUT_MS / 1000} seconds. Try exporting fewer transactions."))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Export CSV using MediaStore API (Android 10+).
     * Required for Scoped Storage compliance.
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun exportCsvUsingMediaStore(
        context: Context,
        filename: String,
        transactions: List<Transaction>,
        baseCurrency: String
    ): Result<String> {
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            ?: return Result.failure(IOException("Failed to create CSV file in Downloads"))

        return try {
            resolver.openOutputStream(uri)?.use { outputStream ->
                val writer = OutputStreamWriter(outputStream)
                writeCsvContent(writer, transactions, baseCurrency)
                writer.flush()
            } ?: return Result.failure(IOException("Failed to open output stream"))

            Result.success("Downloads/$filename")
        } catch (e: Exception) {
            // Clean up on failure
            resolver.delete(uri, null, null)
            Result.failure(e)
        }
    }

    /**
     * Export CSV using File API (Android 9 and below).
     * Legacy approach for devices without Scoped Storage.
     */
    private fun exportCsvUsingFileApi(
        filename: String,
        transactions: List<Transaction>,
        baseCurrency: String
    ): Result<String> {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!downloadsDir.exists()) {
            downloadsDir.mkdirs()
        }

        val csvFile = File(downloadsDir, filename)
        val writer = FileWriter(csvFile)
        writeCsvContent(writer, transactions, baseCurrency)
        writer.close()

        return Result.success(csvFile.absolutePath)
    }

    /**
     * Write CSV content to a Writer.
     * Shared logic for both MediaStore and File API approaches.
     */
    private fun writeCsvContent(
        writer: java.io.Writer,
        transactions: List<Transaction>,
        baseCurrency: String
    ) {
        val csvFormat = CSVFormat.DEFAULT.builder()
            .setHeader(
                "Date",
                "Type",
                "Category",
                "Description",
                "Amount ($baseCurrency)",
                "Tax Deductible",
                "Receipt",
                "Notes",
                "Draft"
            )
            .build()

        val csvPrinter = CSVPrinter(writer, csvFormat)

        transactions.forEach { transaction ->
            csvPrinter.printRecord(
                transaction.date.format(dateFormatter),
                transaction.type.name,
                transaction.category,
                transaction.description ?: "",
                transaction.amount.toPlainString(),
                when {
                    transaction.type == io.github.dorumrr.happytaxes.domain.model.TransactionType.INCOME -> "N/A"
                    transaction.isTaxDeductible -> "Yes"
                    else -> "No"
                },
                if (transaction.receiptPaths.isNotEmpty()) "Yes" else "No",
                transaction.notes ?: "",
                if (transaction.isDraft) "Yes" else "No"
            )
        }

        csvPrinter.flush()
    }

    /**
     * Export transactions to cache directory (for ZIP export).
     * Used when creating ZIP files that need to read back the CSV.
     *
     * @param transactions List of transactions
     * @param filename Output filename
     * @param baseCurrency User's base currency code (e.g., "GBP", "USD", "EUR")
     * @return Result with File object or error
     */
    suspend fun exportTransactionsToCache(
        transactions: List<Transaction>,
        filename: String,
        baseCurrency: String
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val cacheFile = File(context.cacheDir, filename)
            val writer = FileWriter(cacheFile)

            // Define CSV format with dynamic base currency in header
            val csvFormat = CSVFormat.DEFAULT.builder()
                .setHeader(
                    "Date",
                    "Type",
                    "Category",
                    "Description",
                    "Amount ($baseCurrency)",
                    "Tax Deductible",
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
                        when {
                            transaction.type == io.github.dorumrr.happytaxes.domain.model.TransactionType.INCOME -> "N/A"
                            transaction.isTaxDeductible -> "Yes"
                            else -> "No"
                        },
                        if (transaction.receiptPaths.isNotEmpty()) "Yes" else "No",
                        transaction.notes ?: "",
                        if (transaction.isDraft) "Yes" else "No"
                    )
                }
            }

            Result.success(cacheFile)
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

