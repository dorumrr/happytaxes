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
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
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
                // Write to Downloads (MediaStore for Android 10+, File API for Android 9-)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    exportZipUsingMediaStore(context, filename, csvPath, pdfPath, transactions)
                } else {
                    exportZipUsingFileApi(filename, csvPath, pdfPath, transactions)
                }
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            Result.failure(TimeoutException("ZIP export timed out after ${EXPORT_TIMEOUT_MS / 1000} seconds. Try exporting fewer transactions or receipts."))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Export ZIP using MediaStore API (Android 10+).
     * Required for Scoped Storage compliance.
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun exportZipUsingMediaStore(
        context: Context,
        filename: String,
        csvPath: String,
        pdfPath: String,
        transactions: List<Transaction>
    ): Result<String> {
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/zip")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            ?: return Result.failure(IOException("Failed to create ZIP file in Downloads"))

        return try {
            resolver.openOutputStream(uri)?.use { outputStream ->
                ZipOutputStream(outputStream).use { zipOut ->
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
            } ?: return Result.failure(IOException("Failed to open output stream"))

            Result.success("Downloads/$filename")
        } catch (e: Exception) {
            // Clean up on failure
            resolver.delete(uri, null, null)
            Result.failure(e)
        }
    }

    /**
     * Export ZIP using File API (Android 9 and below).
     * Legacy approach for devices without Scoped Storage.
     */
    private fun exportZipUsingFileApi(
        filename: String,
        csvPath: String,
        pdfPath: String,
        transactions: List<Transaction>
    ): Result<String> {
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

        return Result.success(zipFile.absolutePath)
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

