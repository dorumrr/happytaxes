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
import io.github.dorumrr.happytaxes.data.repository.PreferencesRepository
import io.github.dorumrr.happytaxes.data.repository.ProfileContext
import io.github.dorumrr.happytaxes.data.repository.TransactionRepository
import io.github.dorumrr.happytaxes.util.NotificationHelper
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * Background worker for checking data retention compliance.
 *
 * Checks for transactions that exceed the configured retention period
 * (default 6 years, configurable 6-10 years) and notifies the user.
 *
 * Runs weekly to check for old transactions.
 *
 * PRD Reference: Section 4.11 - "When transactions exceed retention period:
 * 'You have transactions older than 6 years. [Review] [Keep] [Archive]'"
 * PRD Reference: Section 4.17 - Data Retention & Archiving
 */
@HiltWorker
class DataRetentionWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val preferencesRepository: PreferencesRepository,
    private val profileContext: ProfileContext,
    private val transactionRepository: TransactionRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        try {
            // Get current profile ID
            val profileId = profileContext.getCurrentProfileIdOnce()

            // Get retention period from settings
            val retentionYears = preferencesRepository.getDataRetentionYears(profileId).first()
            
            // Calculate cutoff date (today - retention years)
            val today = LocalDate.now()
            val cutoffDate = today.minusYears(retentionYears.toLong())
            
            // Get all active transactions older than cutoff date
            val allTransactions = transactionRepository.getAllActive().first()
            val oldTransactions = allTransactions.filter { transaction ->
                transaction.date.isBefore(cutoffDate)
            }
            
            // Show notification if old transactions found
            if (oldTransactions.isNotEmpty()) {
                // Check if we already notified recently (don't spam)
                val lastNotificationDate = preferencesRepository.getLastRetentionWarningDate().first()
                val daysSinceLastNotification = if (lastNotificationDate != null) {
                    java.time.temporal.ChronoUnit.DAYS.between(lastNotificationDate, today)
                } else {
                    30 // Send if never sent before
                }
                
                // Only notify once per month
                if (daysSinceLastNotification >= 30) {
                    NotificationHelper.showDataRetentionWarning(
                        applicationContext,
                        oldTransactions.size,
                        retentionYears
                    )
                    preferencesRepository.setLastRetentionWarningDate(today)
                }
            }
            
            return Result.success()
        } catch (e: Exception) {
            // Log error but don't retry (data retention check is non-critical)
            return Result.failure()
        }
    }

    companion object {
        private const val WORK_NAME = "data_retention_worker"

        /**
         * Schedule data retention worker to run weekly.
         * Uses PeriodicWorkRequest with 7-day interval.
         *
         * @param context Application context
         */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<DataRetentionWorker>(
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
         * Cancel data retention worker.
         *
         * @param context Application context
         */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}

