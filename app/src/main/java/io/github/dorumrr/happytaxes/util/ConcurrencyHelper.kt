package io.github.dorumrr.happytaxes.util

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Helper class to prevent concurrent operations that could cause race conditions.
 *
 * Uses Kotlin's Mutex to ensure only one operation of each type runs at a time.
 * This prevents data corruption from simultaneous writes to the same resources.
 *
 * Example race conditions prevented:
 * - Two transactions being created simultaneously with same data (duplicates)
 * - Category being deleted while transactions are being moved to it
 * - Profile being deleted while transactions are being created
 * - Backup being created while data is being modified
 *
 * Usage:
 * ```kotlin
 * suspend fun createTransaction(...): Result<Transaction> {
 *     return ConcurrencyHelper.withTransactionLock {
 *         // Critical section - only one transaction operation at a time
 *         transactionDao.insert(...)
 *     }
 * }
 * ```
 */
object ConcurrencyHelper {
    
    /**
     * Mutex for transaction operations (create, update, delete).
     * Prevents race conditions like:
     * - Duplicate detection failing due to concurrent creates
     * - Transaction being updated while being deleted
     */
    private val transactionMutex = Mutex()
    
    /**
     * Mutex for category operations (create, update, delete, bulk moves).
     * Prevents race conditions like:
     * - Category being deleted while transactions are being moved to it
     * - Duplicate category names from concurrent creates
     */
    private val categoryMutex = Mutex()
    
    /**
     * Mutex for profile operations (create, delete, switch).
     * Prevents race conditions like:
     * - Profile being deleted while transactions are being created
     * - Profile being switched while data is being loaded
     */
    private val profileMutex = Mutex()
    
    /**
     * Mutex for backup/restore operations.
     * Prevents race conditions like:
     * - Backup being created while data is being modified
     * - Restore happening while backup is being created
     */
    private val backupMutex = Mutex()
    
    /**
     * Mutex for receipt file operations.
     * Prevents race conditions like:
     * - Receipt being deleted while being read
     * - Multiple receipts being saved with same filename
     */
    private val receiptMutex = Mutex()
    
    /**
     * Execute transaction operation with lock.
     * Ensures only one transaction operation runs at a time.
     */
    suspend fun <T> withTransactionLock(block: suspend () -> T): T {
        return transactionMutex.withLock {
            block()
        }
    }
    
    /**
     * Execute category operation with lock.
     * Ensures only one category operation runs at a time.
     */
    suspend fun <T> withCategoryLock(block: suspend () -> T): T {
        return categoryMutex.withLock {
            block()
        }
    }
    
    /**
     * Execute profile operation with lock.
     * Ensures only one profile operation runs at a time.
     */
    suspend fun <T> withProfileLock(block: suspend () -> T): T {
        return profileMutex.withLock {
            block()
        }
    }
    
    /**
     * Execute backup/restore operation with lock.
     * Ensures only one backup/restore operation runs at a time.
     */
    suspend fun <T> withBackupLock(block: suspend () -> T): T {
        return backupMutex.withLock {
            block()
        }
    }
    
    /**
     * Execute receipt file operation with lock.
     * Ensures only one receipt file operation runs at a time.
     */
    suspend fun <T> withReceiptLock(block: suspend () -> T): T {
        return receiptMutex.withLock {
            block()
        }
    }
    
    /**
     * Check if transaction lock is currently held.
     * Useful for debugging or showing UI indicators.
     */
    val isTransactionLocked: Boolean
        get() = transactionMutex.isLocked
    
    /**
     * Check if category lock is currently held.
     */
    val isCategoryLocked: Boolean
        get() = categoryMutex.isLocked
    
    /**
     * Check if profile lock is currently held.
     */
    val isProfileLocked: Boolean
        get() = profileMutex.isLocked
    
    /**
     * Check if backup lock is currently held.
     */
    val isBackupLocked: Boolean
        get() = backupMutex.isLocked
    
    /**
     * Check if receipt lock is currently held.
     */
    val isReceiptLocked: Boolean
        get() = receiptMutex.isLocked
}