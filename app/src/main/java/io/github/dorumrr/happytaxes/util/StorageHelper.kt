package io.github.dorumrr.happytaxes.util

import android.content.Context
import android.os.StatFs
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Helper class for storage-related operations and checks.
 * 
 * Provides utilities for:
 * - Checking available storage space
 * - Validating sufficient space before operations
 * - Formatting storage sizes for display
 * 
 * PRD Section 10: Error Handling - Storage Failures
 * PRD Section 2.12: "Low storage (< 100MB)"
 */
@Singleton
class StorageHelper @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fileManager: FileManager
) {
    
    companion object {
        /**
         * Minimum free space required for app operations (100 MB).
         * PRD Section 2.12: "Low storage (< 100MB)"
         */
        private const val MIN_FREE_SPACE_BYTES = 100L * 1024 * 1024 // 100 MB
        
        /**
         * Safety margin for file operations (10 MB).
         * Ensures we don't fill storage completely.
         */
        private const val SAFETY_MARGIN_BYTES = 10L * 1024 * 1024 // 10 MB
        
        /**
         * Estimated size for a compressed receipt image (500 KB average).
         * Based on max 1600px, 65% JPEG quality compression.
         */
        private const val ESTIMATED_RECEIPT_SIZE = 500L * 1024 // 500 KB
        
        /**
         * Estimated size for database backup (varies, but use 50 MB as safe estimate).
         */
        private const val ESTIMATED_BACKUP_SIZE = 50L * 1024 * 1024 // 50 MB
    }
    
    /**
     * Get available storage space in bytes.
     * 
     * @return Available space in bytes
     */
    fun getAvailableSpace(): Long {
        val filesDir = context.filesDir
        val stat = StatFs(filesDir.absolutePath)
        return stat.availableBytes
    }
    
    /**
     * Get total storage space in bytes.
     * 
     * @return Total space in bytes
     */
    fun getTotalSpace(): Long {
        val filesDir = context.filesDir
        val stat = StatFs(filesDir.absolutePath)
        return stat.totalBytes
    }
    
    /**
     * Check if there is sufficient storage space available.
     * 
     * @param requiredBytes Required space in bytes (optional, defaults to MIN_FREE_SPACE)
     * @return true if sufficient space available
     */
    fun hasSufficientSpace(requiredBytes: Long = MIN_FREE_SPACE_BYTES): Boolean {
        val available = getAvailableSpace()
        return available >= (requiredBytes + SAFETY_MARGIN_BYTES)
    }
    
    /**
     * Check if there is sufficient space for saving a receipt.
     * 
     * @param estimatedSize Estimated size of the receipt (optional)
     * @return Result with success or storage error
     */
    fun checkSpaceForReceipt(estimatedSize: Long = ESTIMATED_RECEIPT_SIZE): Result<Unit> {
        val required = estimatedSize + SAFETY_MARGIN_BYTES
        return if (hasSufficientSpace(required)) {
            Result.success(Unit)
        } else {
            val available = getAvailableSpace()
            Result.failure(
                InsufficientStorageException(
                    "Insufficient storage space. Required: ${formatFileSize(required)}, " +
                    "Available: ${formatFileSize(available)}"
                )
            )
        }
    }
    
    /**
     * Check if there is sufficient space for creating a backup.
     * 
     * @param estimatedSize Estimated size of the backup (optional)
     * @return Result with success or storage error
     */
    fun checkSpaceForBackup(estimatedSize: Long = ESTIMATED_BACKUP_SIZE): Result<Unit> {
        val required = estimatedSize + SAFETY_MARGIN_BYTES
        return if (hasSufficientSpace(required)) {
            Result.success(Unit)
        } else {
            val available = getAvailableSpace()
            Result.failure(
                InsufficientStorageException(
                    "Insufficient storage space for backup. Required: ${formatFileSize(required)}, " +
                    "Available: ${formatFileSize(available)}. " +
                    "Please free up space and try again."
                )
            )
        }
    }
    
    /**
     * Check if storage is critically low (below minimum threshold).
     * Used for warning notifications.
     * 
     * PRD Section 2.12: "Low storage (< 100MB)"
     * 
     * @return true if storage is critically low
     */
    fun isStorageCriticallyLow(): Boolean {
        return !hasSufficientSpace(MIN_FREE_SPACE_BYTES)
    }
    
    /**
     * Get storage status as a human-readable string.
     * 
     * @return Storage status string (e.g., "1.2 GB available of 32 GB")
     */
    fun getStorageStatus(): String {
        val available = getAvailableSpace()
        val total = getTotalSpace()
        return "${formatFileSize(available)} available of ${formatFileSize(total)}"
    }
    
    /**
     * Format file size for display.
     * Delegates to FileManager for consistent formatting across the app.
     *
     * @param bytes Size in bytes
     * @return Formatted string (e.g., "1.5 MB")
     */
    fun formatFileSize(bytes: Long): String {
        return fileManager.formatFileSize(bytes)
    }
    
    /**
     * Calculate actual file size if it exists.
     * 
     * @param file File to check
     * @return File size in bytes, or 0 if file doesn't exist
     */
    fun getFileSize(file: File): Long {
        return if (file.exists()) file.length() else 0L
    }
    
    /**
     * Calculate directory size recursively.
     * 
     * @param directory Directory to calculate
     * @return Total size in bytes
     */
    fun getDirectorySize(directory: File): Long {
        var size = 0L
        directory.listFiles()?.forEach { file ->
            size += if (file.isDirectory) {
                getDirectorySize(file)
            } else {
                file.length()
            }
        }
        return size
    }
}

/**
 * Exception thrown when there is insufficient storage space.
 */
class InsufficientStorageException(message: String) : Exception(message)