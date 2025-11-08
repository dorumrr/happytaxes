package io.github.dorumrr.happytaxes.data.repository

import android.net.Uri
import io.github.dorumrr.happytaxes.util.ConcurrencyHelper
import io.github.dorumrr.happytaxes.util.FileManager
import io.github.dorumrr.happytaxes.util.ImageCompressor
import io.github.dorumrr.happytaxes.util.StorageHelper
import java.io.File
import java.io.IOException
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for receipt file operations.
 *
 * Responsibilities:
 * - Save receipts from camera or gallery
 * - Compress images before saving
 * - Delete receipts
 * - Retrieve receipt paths
 * - Manage receipt storage
 *
 * Business Rules:
 * - Receipts stored in /receipts/{profileId}/YYYY-MM/ directory
 * - File naming: {transactionId}_{timestamp}.jpg
 * - Support multiple receipts per transaction
 * - Auto-compress images (max 1600px, 65% JPEG quality)
 * - Preserve EXIF data during compression
 * - Complete isolation between profiles
 */
@Singleton
class ReceiptRepository @Inject constructor(
    private val fileManager: FileManager,
    private val imageCompressor: ImageCompressor,
    private val profileContext: ProfileContext,
    private val storageHelper: StorageHelper
) {
    
    /**
     * Save a receipt from URI (camera or gallery).
     *
     * Process:
     * 1. Get current profile ID
     * 2. Compress image (max 1600px, 65% JPEG quality)
     * 3. Preserve EXIF data
     * 4. Save compressed image to /receipts/{profileId}/YYYY-MM/
     *
     * @param sourceUri Source URI
     * @param transactionId Transaction ID
     * @param date Transaction date
     * @return Result with receipt path or error
     */
    suspend fun saveReceipt(
        sourceUri: Uri,
        transactionId: String,
        date: LocalDate
    ): Result<String> {
        return ConcurrencyHelper.withReceiptLock {
            try {
            // Step 1: Check storage space before proceeding
            val storageCheck = storageHelper.checkSpaceForReceipt()
            if (storageCheck.isFailure) {
                return@withReceiptLock Result.failure(
                    storageCheck.exceptionOrNull()
                        ?: IOException("Insufficient storage space")
                )
            }

            // Step 2: Get current profile ID
            val profileId = profileContext.getCurrentProfileIdOnce()

            // Step 3: Generate destination file path
            val filename = fileManager.generateReceiptFilename(transactionId)
            val destDir = fileManager.getReceiptsDir(profileId, date)
            val destFile = File(destDir, filename)

            // Step 4: Compress image with EXIF preservation
            val compressResult = imageCompressor.compressImage(sourceUri, destFile)

            if (compressResult.isFailure) {
                return@withReceiptLock Result.failure(
                    compressResult.exceptionOrNull()
                        ?: IOException("Image compression failed")
                )
            }

                // Step 5: Return path to compressed image
                val compressedFile = compressResult.getOrThrow()
                Result.success(compressedFile.absolutePath)
            } catch (e: IOException) {
                Result.failure(e)
            } catch (e: Exception) {
                Result.failure(IOException("Failed to save receipt: ${e.message}", e))
            }
        }
    }
    
    /**
     * Delete a receipt file.
     * 
     * @param path Absolute path to receipt
     * @return Result with success or error
     */
    suspend fun deleteReceipt(path: String): Result<Unit> {
        return ConcurrencyHelper.withReceiptLock {
            try {
                val deleted = fileManager.deleteReceipt(path)
                if (deleted) {
                    Result.success(Unit)
                } else {
                    Result.failure(IOException("Receipt file not found or could not be deleted"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    /**
     * Delete all receipts for a transaction.
     *
     * @param transactionId Transaction ID
     * @param date Transaction date
     * @return Result with number of receipts deleted
     */
    suspend fun deleteReceiptsForTransaction(
        transactionId: String,
        date: LocalDate
    ): Result<Int> {
        return ConcurrencyHelper.withReceiptLock {
            try {
                val profileId = profileContext.getCurrentProfileIdOnce()
                val count = fileManager.deleteReceiptsForTransaction(transactionId, profileId, date)
                Result.success(count)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    /**
     * Check if a receipt exists.
     * 
     * @param path Absolute path to receipt
     * @return true if receipt exists
     */
    fun receiptExists(path: String): Boolean {
        return fileManager.receiptExists(path)
    }
    
    /**
     * Get all receipts for a transaction.
     *
     * @param transactionId Transaction ID
     * @param date Transaction date
     * @return List of receipt paths
     */
    suspend fun getReceiptsForTransaction(transactionId: String, date: LocalDate): List<String> {
        val profileId = profileContext.getCurrentProfileIdOnce()
        return fileManager.getReceiptsForTransaction(transactionId, profileId, date)
    }

    /**
     * Get all receipts for a specific month.
     *
     * @param date Any date in the target month
     * @return List of receipt paths
     */
    suspend fun getReceiptsForMonth(date: LocalDate): List<String> {
        val profileId = profileContext.getCurrentProfileIdOnce()
        return fileManager.getReceiptsForMonth(profileId, date)
    }
    
    /**
     * Get receipt file size.
     * 
     * @param path Absolute path to receipt
     * @return File size in bytes
     */
    fun getReceiptSize(path: String): Long {
        return fileManager.getReceiptSize(path)
    }
    
    /**
     * Get total storage used by all receipts (all profiles).
     *
     * @return Total size in bytes
     */
    fun getTotalReceiptsSize(): Long {
        return fileManager.getTotalReceiptsSize()
    }

    /**
     * Get total storage used by receipts for current profile.
     *
     * @return Total size in bytes
     */
    suspend fun getProfileReceiptsSize(): Long {
        val profileId = profileContext.getCurrentProfileIdOnce()
        return fileManager.getProfileReceiptsSize(profileId)
    }

    /**
     * Get formatted file size for display.
     *
     * @param bytes Size in bytes
     * @return Formatted string (e.g., "1.5 MB")
     */
    fun formatFileSize(bytes: Long): String {
        return fileManager.formatFileSize(bytes)
    }

    /**
     * Clean up empty month directories for current profile.
     *
     * @return Number of directories deleted
     */
    suspend fun cleanupEmptyDirectories(): Result<Int> {
        return try {
            val profileId = profileContext.getCurrentProfileIdOnce()
            val count = fileManager.cleanupEmptyDirectories(profileId)
            Result.success(count)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Clean up empty month directories for all profiles.
     *
     * @return Number of directories deleted
     */
    suspend fun cleanupAllEmptyDirectories(): Result<Int> {
        return try {
            val count = fileManager.cleanupAllEmptyDirectories()
            Result.success(count)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Get URI for a receipt (for viewing/sharing).
     * 
     * @param path Absolute path to receipt
     * @return File URI
     */
    fun getReceiptUri(path: String): Uri {
        return fileManager.getReceiptUri(path)
    }
    
    /**
     * Validate receipt path format.
     * 
     * @param path Receipt path
     * @return true if path format is valid
     */
    fun isValidReceiptPath(path: String): Boolean {
        return path.contains("/receipts/") && path.endsWith(".jpg")
    }
    
    /**
     * Extract transaction ID from receipt filename.
     * 
     * @param path Receipt path
     * @return Transaction ID or null if invalid format
     */
    fun extractTransactionId(path: String): String? {
        val filename = path.substringAfterLast("/")
        val parts = filename.split("_")
        return if (parts.size >= 2) parts[0] else null
    }
    
    /**
     * Extract timestamp from receipt filename.
     * 
     * @param path Receipt path
     * @return Timestamp in milliseconds or null if invalid format
     */
    fun extractTimestamp(path: String): Long? {
        val filename = path.substringAfterLast("/")
        val parts = filename.split("_")
        return if (parts.size >= 2) {
            parts[1].removeSuffix(".jpg").toLongOrNull()
        } else {
            null
        }
    }
}

