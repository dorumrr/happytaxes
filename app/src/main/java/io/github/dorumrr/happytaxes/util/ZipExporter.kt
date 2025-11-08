package io.github.dorumrr.happytaxes.util

import android.content.Context
import android.os.Environment
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.dorumrr.happytaxes.domain.model.Transaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.TimeoutException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ZIP export utility.
 *
 * Exports CSV + PDF + receipts to ZIP file.
 *
 * PRD Reference: Section 4.16 - Export Formats (ZIP)
 */
@Singleton
class ZipExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fileManager: FileManager
) {
    
    companion object {
        /**
         * Export timeout: 120 seconds (2 minutes) for ZIP creation.
         * ZIP includes CSV + PDF + all receipts, needs most time.
         * Allows for ~1000 receipts at average 100KB each.
         */
        private const val EXPORT_TIMEOUT_MS = 120_000L
    }

    /**
     * Export report to ZIP file in Downloads folder.
     *
     * @param csvPath Path to CSV file
     * @param pdfPath Path to PDF file
     * @param transactions List of transactions (for receipt paths)
     * @param filename Output filename
     * @return Result with file path or error
     */
    suspend fun exportReport(
        csvPath: String,
        pdfPath: String,
        transactions: List<Transaction>,
        filename: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            // Apply timeout to prevent indefinite hangs
            withTimeout(EXPORT_TIMEOUT_MS) {
            // Get Downloads directory
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) {
                downloadsDir.mkdirs()
            }

            // Create ZIP file in Downloads
            val zipFile = File(downloadsDir, filename)
            ZipOutputStream(FileOutputStream(zipFile)).use { zipOut ->
                // Add CSV file
                val csvFile = File(csvPath)
                if (csvFile.exists()) {
                    addFileToZip(zipOut, csvFile, csvFile.name)
                }

                // Add PDF file
                val pdfFile = File(pdfPath)
                if (pdfFile.exists()) {
                    addFileToZip(zipOut, pdfFile, pdfFile.name)
                }

                // Add receipts
                val receiptPaths = transactions.flatMap { it.receiptPaths }.distinct()
                receiptPaths.forEach { receiptPath ->
                    val receiptFile = File(receiptPath)
                    if (receiptFile.exists()) {
                        // Preserve directory structure: receipts/YYYY-MM/filename.jpg
                        val relativePath = if (receiptPath.contains("/receipts/")) {
                            receiptPath.substringAfter("/receipts/")
                        } else {
                            receiptFile.name
                        }
                        addFileToZip(zipOut, receiptFile, "receipts/$relativePath")
                    }
                }
                }

                Result.success(zipFile.absolutePath)
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            Result.failure(TimeoutException("ZIP export timed out after ${EXPORT_TIMEOUT_MS / 1000} seconds. Try exporting fewer transactions or receipts."))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Add file to ZIP archive.
     */
    private fun addFileToZip(zipOut: ZipOutputStream, file: File, entryName: String) {
        FileInputStream(file).use { fis ->
            val zipEntry = ZipEntry(entryName)
            zipOut.putNextEntry(zipEntry)

            val buffer = ByteArray(1024)
            var length: Int
            while (fis.read(buffer).also { length = it } > 0) {
                zipOut.write(buffer, 0, length)
            }

            zipOut.closeEntry()
        }
    }

    /**
     * Get file size.
     */
    fun getFileSize(filePath: String): Long {
        val file = File(filePath)
        return if (file.exists()) file.length() else 0
    }

    /**
     * Format file size for display.
     * Delegates to FileManager for consistent formatting across the app.
     */
    fun formatFileSize(bytes: Long): String {
        return fileManager.formatFileSize(bytes)
    }
}

