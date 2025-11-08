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
import io.github.dorumrr.happytaxes.data.repository.TransactionRepository
import io.github.dorumrr.happytaxes.util.NotificationHelper
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.Month
import java.util.concurrent.TimeUnit

/**
 * Background worker for checking and sending notifications.
 *
 * Checks:
 * - Transaction reminders (daily/weekly based on user preference)
 * - Tax year-end reminders (30 days, 7 days, on end date)
 *
 * Runs daily at 9 PM (smart timing for evening reminders).
 *
 * PRD Reference: Section 4.11 - User Notifications & Reminders
 */
@HiltWorker
class NotificationWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val preferencesRepository: PreferencesRepository,
    private val transactionRepository: TransactionRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        try {
            // Check if notifications are enabled
            val notificationsEnabled = preferencesRepository.getNotificationsEnabled().first()
            if (!notificationsEnabled) {
                return Result.success()
            }

            // Check transaction reminders
            checkTransactionReminders()

            // Check tax year-end reminders
            checkTaxYearReminders()

            return Result.success()
        } catch (e: Exception) {
            // Log error but don't retry (notifications are non-critical)
            return Result.failure()
        }
    }

    /**
     * Check if transaction reminder should be sent.
     * PRD: "Optional daily/weekly reminder to log transactions"
     */
    private suspend fun checkTransactionReminders() {
        val reminderEnabled = preferencesRepository.getTransactionReminderEnabled().first()
        if (!reminderEnabled) return

        val reminderFrequency = preferencesRepository.getTransactionReminderFrequency().first()
        val lastReminderDate = preferencesRepository.getLastTransactionReminderDate().first()
        val today = LocalDate.now()

        val shouldSendReminder = when (reminderFrequency) {
            "daily" -> lastReminderDate != today
            "weekly" -> {
                // Send reminder once per week (e.g., every Monday)
                val daysSinceLastReminder = if (lastReminderDate != null) {
                    java.time.temporal.ChronoUnit.DAYS.between(lastReminderDate, today)
                } else {
                    7 // Send if never sent before
                }
                daysSinceLastReminder >= 7
            }
            else -> false
        }

        if (shouldSendReminder) {
            NotificationHelper.showTransactionReminder(applicationContext)
            preferencesRepository.setLastTransactionReminderDate(today)
        }
    }

    /**
     * Check if tax year-end reminder should be sent.
     * PRD: "30 days before (5 March): 'Tax year ending soon'"
     *      "7 days before (29 March): 'Review your transactions before 5 April'"
     *      "On tax year end (5 April): 'Tax year ended. Generate your P&L report.'"
     */
    private suspend fun checkTaxYearReminders() {
        val today = LocalDate.now()
        val currentYear = today.year
        
        // UK tax year ends on 5 April
        val taxYearEnd = LocalDate.of(
            if (today.month.value < 4 || (today.month == Month.APRIL && today.dayOfMonth <= 5)) {
                currentYear
            } else {
                currentYear + 1
            },
            4,
            5
        )

        val daysUntilEnd = java.time.temporal.ChronoUnit.DAYS.between(today, taxYearEnd).toInt()

        // Check if we should send reminder
        val lastTaxYearReminderDate = preferencesRepository.getLastTaxYearReminderDate().first()
        val lastReminderType = preferencesRepository.getLastTaxYearReminderType().first()

        when (daysUntilEnd) {
            30 -> {
                // 30 days before (5 March)
                if (lastReminderType != "30_days" || lastTaxYearReminderDate != today) {
                    NotificationHelper.showTaxYearReminder(applicationContext, 30)
                    preferencesRepository.setLastTaxYearReminderDate(today)
                    preferencesRepository.setLastTaxYearReminderType("30_days")
                }
            }
            7 -> {
                // 7 days before (29 March)
                if (lastReminderType != "7_days" || lastTaxYearReminderDate != today) {
                    NotificationHelper.showTaxYearReminder(applicationContext, 7)
                    preferencesRepository.setLastTaxYearReminderDate(today)
                    preferencesRepository.setLastTaxYearReminderType("7_days")
                }
            }
            0 -> {
                // On tax year end (5 April)
                if (lastReminderType != "end_date" || lastTaxYearReminderDate != today) {
                    NotificationHelper.showTaxYearReminder(applicationContext, 0)
                    preferencesRepository.setLastTaxYearReminderDate(today)
                    preferencesRepository.setLastTaxYearReminderType("end_date")
                }
            }
        }
    }

    companion object {
        private const val WORK_NAME = "notification_worker"

        /**
         * Schedule notification worker to run daily at 9 PM.
         * Uses PeriodicWorkRequest with 24-hour interval.
         *
         * @param context Application context
         */
        fun schedule(context: Context) {
            // Calculate initial delay to run at 9 PM
            val now = java.time.LocalDateTime.now()
            val today9PM = now.toLocalDate().atTime(21, 0)
            val next9PM = if (now.isBefore(today9PM)) {
                today9PM // Run today at 9 PM
            } else {
                today9PM.plusDays(1) // Run tomorrow at 9 PM
            }

            val initialDelayMinutes = java.time.Duration.between(now, next9PM).toMinutes()

            val request = PeriodicWorkRequestBuilder<NotificationWorker>(
                repeatInterval = 24,
                repeatIntervalTimeUnit = TimeUnit.HOURS
            )
                .setInitialDelay(initialDelayMinutes, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP, // Keep existing schedule
                    request
                )
        }

        /**
         * Cancel notification worker.
         *
         * @param context Application context
         */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}

