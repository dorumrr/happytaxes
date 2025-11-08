package io.github.dorumrr.happytaxes.util

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * File manager for receipt storage and management.
 *
 * Directory Structure (Multi-Profile):
 * /data/data/io.github.dorumrr.happytaxes/files/receipts/{profileId}/YYYY-MM/
 *
 * File Naming:
 * {transactionId}_{timestamp}.jpg
 *
 * Example:
 * /receipts/business-profile-default-uuid/2025-10/abc123_1696723200000.jpg
 *
 * Business Rules:
 * - Store in internal storage (private, no permissions needed)
 * - Organize by profile, then year-month for easy management
 * - Use JPEG format for compressed images
 * - Preserve EXIF data during compression
 * - Support multiple receipts per transaction
 * - Complete isolation between profiles
 */
@Singleton
class FileManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    companion object {
        private const val RECEIPTS_DIR = "receipts"
        private const val FILE_EXTENSION = ".jpg"
    }
    
    /**
     * Get the base receipts directory.
     * Creates if doesn't exist.
     */
    fun getReceiptsBaseDir(): File {
        val dir = File(context.filesDir, RECEIPTS_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /**
     * Get the receipts directory for a specific profile.
     * Creates if doesn't exist.
     *
     * @param profileId Profile ID
     * @return Directory: /receipts/{profileId}/
     */
    fun getProfileReceiptsDir(profileId: String): File {
        val dir = File(getReceiptsBaseDir(), profileId)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /**
     * Get the receipts directory for a specific date and profile.
     * Creates if doesn't exist.
     *
     * @param profileId Profile ID
     * @param date Transaction date
     * @return Directory: /receipts/{profileId}/YYYY-MM/
     */
    fun getReceiptsDir(profileId: String, date: LocalDate): File {
        val yearMonth = date.format(DateFormats.YEAR_MONTH)
        val dir = File(getProfileReceiptsDir(profileId), yearMonth)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }
    
    /**
     * Generate a unique receipt filename.
     *
     * @param transactionId Transaction ID
     * @return Filename: {transactionId}_{timestamp}.jpg
     * @throws IllegalArgumentException if transactionId is invalid
     */
    fun generateReceiptFilename(transactionId: String): String {
        // Validate transaction ID
        require(transactionId.isNotBlank()) { "Transaction ID cannot be blank" }
        require(transactionId.length <= 100) { "Transaction ID too long (max 100 characters)" }
        require(!transactionId.contains("/") && !transactionId.contains("\\")) {
            "Transaction ID cannot contain path separators"
        }
        require(!transactionId.contains("..")) {
            "Transaction ID cannot contain path traversal sequences"
        }

        val timestamp = System.currentTimeMillis()
        return "${transactionId}_${timestamp}${FILE_EXTENSION}"
    }
    
    /**
     * Get the full path for a receipt file.
     *
     * @param profileId Profile ID
     * @param date Transaction date
     * @param filename Receipt filename
     * @return Full path: /receipts/{profileId}/YYYY-MM/{filename}
     */
    fun getReceiptPath(profileId: String, date: LocalDate, filename: String): String {
        val dir = getReceiptsDir(profileId, date)
        return File(dir, filename).absolutePath
    }
    
    /**
     * Delete a receipt file.
     * 
     * @param path Absolute path to receipt file
     * @return true if deleted successfully
     */
    fun deleteReceipt(path: String): Boolean {
        val file = File(path)
        return if (file.exists()) {
            file.delete()
        } else {
            false
        }
    }
    
    /**
     * Check if a receipt file exists.
     * 
     * @param path Absolute path to receipt file
     * @return true if file exists
     */
    fun receiptExists(path: String): Boolean {
        return File(path).exists()
    }
    
    /**
     * Get receipt file size in bytes.
     * 
     * @param path Absolute path to receipt file
     * @return File size in bytes, or 0 if file doesn't exist
     */
    fun getReceiptSize(path: String): Long {
        val file = File(path)
        return if (file.exists()) file.length() else 0L
    }
    
    /**
     * Get all receipts for a specific month and profile.
     *
     * @param profileId Profile ID
     * @param date Any date in the target month
     * @return List of receipt file paths
     */
    fun getReceiptsForMonth(profileId: String, date: LocalDate): List<String> {
        val dir = getReceiptsDir(profileId, date)
        return dir.listFiles()
            ?.filter { it.isFile && it.extension == "jpg" }
            ?.map { it.absolutePath }
            ?: emptyList()
    }

    /**
     * Get all receipts for a specific transaction.
     *
     * @param transactionId Transaction ID
     * @param profileId Profile ID
     * @param date Transaction date
     * @return List of receipt file paths
     */
    fun getReceiptsForTransaction(transactionId: String, profileId: String, date: LocalDate): List<String> {
        val dir = getReceiptsDir(profileId, date)
        return dir.listFiles()
            ?.filter { it.isFile && it.name.startsWith(transactionId) }
            ?.map { it.absolutePath }
            ?: emptyList()
    }
    
    /**
     * Delete all receipts for a specific transaction.
     *
     * @param transactionId Transaction ID
     * @param profileId Profile ID
     * @param date Transaction date
     * @return Number of receipts deleted
     */
    fun deleteReceiptsForTransaction(transactionId: String, profileId: String, date: LocalDate): Int {
        val receipts = getReceiptsForTransaction(transactionId, profileId, date)
        var deletedCount = 0
        receipts.forEach { path ->
            if (deleteReceipt(path)) {
                deletedCount++
            }
        }
        return deletedCount
    }
    
    /**
     * Get total storage used by receipts in bytes (all profiles).
     *
     * @return Total size in bytes
     */
    fun getTotalReceiptsSize(): Long {
        val baseDir = getReceiptsBaseDir()
        return calculateDirectorySize(baseDir)
    }

    /**
     * Get total storage used by receipts for a specific profile.
     *
     * @param profileId Profile ID
     * @return Total size in bytes
     */
    fun getProfileReceiptsSize(profileId: String): Long {
        val profileDir = getProfileReceiptsDir(profileId)
        return calculateDirectorySize(profileDir)
    }
    
    /**
     * Calculate directory size recursively.
     */
    private fun calculateDirectorySize(directory: File): Long {
        var size = 0L
        directory.listFiles()?.forEach { file ->
            size += if (file.isDirectory) {
                calculateDirectorySize(file)
            } else {
                file.length()
            }
        }
        return size
    }
    
    /**
     * Clean up empty month directories for a specific profile.
     *
     * @param profileId Profile ID
     * @return Number of directories deleted
     */
    fun cleanupEmptyDirectories(profileId: String): Int {
        val profileDir = getProfileReceiptsDir(profileId)
        var deletedCount = 0
        profileDir.listFiles()?.forEach { dir ->
            if (dir.isDirectory && (dir.listFiles()?.isEmpty() == true)) {
                if (dir.delete()) {
                    deletedCount++
                }
            }
        }
        return deletedCount
    }

    /**
     * Clean up empty month directories for all profiles.
     *
     * @return Number of directories deleted
     */
    fun cleanupAllEmptyDirectories(): Int {
        val baseDir = getReceiptsBaseDir()
        var deletedCount = 0

        // Iterate through profile directories
        baseDir.listFiles()?.forEach { profileDir ->
            if (profileDir.isDirectory) {
                // Clean up empty month directories within each profile
                profileDir.listFiles()?.forEach { monthDir ->
                    if (monthDir.isDirectory && (monthDir.listFiles()?.isEmpty() == true)) {
                        if (monthDir.delete()) {
                            deletedCount++
                        }
                    }
                }

                // Delete profile directory if it's now empty
                if (profileDir.listFiles()?.isEmpty() == true) {
                    if (profileDir.delete()) {
                        deletedCount++
                    }
                }
            }
        }

        return deletedCount
    }
    
    /**
     * Get URI for a receipt file (for sharing/viewing).
     * Uses FileProvider for Android 7+ compatibility.
     *
     * @param path Absolute path to receipt file
     * @return Content URI via FileProvider
     */
    fun getReceiptUri(path: String): Uri {
        val file = File(path)
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }
    
    /**
     * Format file size for display.
     * 
     * @param bytes Size in bytes
     * @return Formatted string (e.g., "1.5 MB")
     */
    fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
            else -> String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        }
    }
}

