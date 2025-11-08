package io.github.dorumrr.happytaxes.data.notification

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.github.dorumrr.happytaxes.data.repository.TransactionRepository
import io.github.dorumrr.happytaxes.util.NotificationHelper
import kotlinx.coroutines.flow.first
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Background worker for detecting orphaned receipts.
 *
 * Orphaned receipts are receipt files that exist in storage but have no
 * corresponding transaction reference in the database.
 *
 * Runs weekly to check for orphaned receipts and notify user.
 *
 * PRD Reference: Section 4.12 - "Weekly background check for receipt files
 * with no transaction reference"
 */
@HiltWorker
class OrphanedReceiptWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val transactionRepository: TransactionRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        try {
            // Get all receipt files from storage
            val receiptsDir = File(applicationContext.filesDir, "receipts")
            if (!receiptsDir.exists()) {
                return Result.success()
            }

            val receiptFiles = receiptsDir.listFiles()?.filter { it.isFile } ?: emptyList()
            if (receiptFiles.isEmpty()) {
                return Result.success()
            }

            // Get all transactions (active + deleted) with receipts
            // We check both because soft-deleted transactions (30-day retention) still have receipts
            val activeTransactions = transactionRepository.getAllActive().first()
            val deletedTransactions = transactionRepository.getAllDeleted().first()
            val allTransactions = activeTransactions + deletedTransactions

            val referencedReceiptPaths = allTransactions
                .flatMap { transaction -> transaction.receiptPaths } // Flatten all receipt paths
                .map { path -> File(path).name } // Get just the filename
                .toSet()

            // Find orphaned receipts (files not referenced by any transaction)
            val orphanedReceipts = receiptFiles.filter { file ->
                file.name !in referencedReceiptPaths
            }

            // Show notification if orphaned receipts found
            if (orphanedReceipts.isNotEmpty()) {
                NotificationHelper.showOrphanedReceiptsNotification(
                    applicationContext,
                    orphanedReceipts.size
                )
            }

            return Result.success()
        } catch (e: Exception) {
            // Log error but don't retry (orphaned receipt detection is non-critical)
            return Result.failure()
        }
    }

    companion object {
        private const val WORK_NAME = "orphaned_receipt_worker"

        /**
         * Schedule orphaned receipt worker to run weekly.
         * Uses PeriodicWorkRequest with 7-day interval.
         *
         * @param context Application context
         */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<OrphanedReceiptWorker>(
                repeatInterval = 7,
                repeatIntervalTimeUnit = TimeUnit.DAYS
            )
                .build()

            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP, // Keep existing schedule
                    request
                )
        }

        /**
         * Cancel orphaned receipt worker.
         *
         * @param context Application context
         */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}

