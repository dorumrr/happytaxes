package io.github.dorumrr.happytaxes.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import io.github.dorumrr.happytaxes.MainActivity
import io.github.dorumrr.happytaxes.R

/**
 * Centralized notification management for HappyTaxes.
 *
 * Notification Channels:
 * - REMINDERS: Transaction and tax year reminders
 * - RECEIPTS: Orphaned receipt notifications
 *
 * PRD Reference: Section 4.11 - User Notifications & Reminders
 */
object NotificationHelper {

    // Notification Channel IDs
    private const val CHANNEL_REMINDERS = "reminders"
    private const val CHANNEL_RECEIPTS = "receipts"

    // Notification IDs
    const val NOTIFICATION_ID_TRANSACTION_REMINDER = 2001
    const val NOTIFICATION_ID_TAX_YEAR_REMINDER = 2002
    const val NOTIFICATION_ID_ORPHANED_RECEIPTS = 2004
    const val NOTIFICATION_ID_DATA_RETENTION = 2005

    /**
     * Initialize notification channels.
     * Must be called on app startup (Android 8.0+).
     *
     * @param context Application context
     */
    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Reminders channel (transaction and tax year reminders)
            val remindersChannel = NotificationChannel(
                CHANNEL_REMINDERS,
                "Reminders",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Transaction and tax year reminders"
                enableVibration(true)
            }

            // Receipts channel
            val receiptsChannel = NotificationChannel(
                CHANNEL_RECEIPTS,
                "Receipts",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Orphaned receipt notifications"
            }

            notificationManager.createNotificationChannels(
                listOf(remindersChannel, receiptsChannel)
            )
        }
    }

    /**
     * Show transaction reminder notification.
     * PRD: "Optional daily/weekly reminder to log transactions"
     *
     * @param context Application context
     */
    fun showTransactionReminder(context: Context) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Log your transactions")
            .setContentText("Don't forget to record today's business expenses and income")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID_TRANSACTION_REMINDER, notification)
    }

    /**
     * Show tax year-end reminder notification.
     * PRD: "30 days before: 'Tax year ending soon'"
     *      "7 days before: 'Review your transactions before 5 April'"
     *      "On tax year end: 'Tax year ended. Generate your P&L report.'"
     *
     * @param context Application context
     * @param daysUntilEnd Days until tax year end (30, 7, or 0)
     */
    fun showTaxYearReminder(context: Context, daysUntilEnd: Int) {
        val (title, message) = when (daysUntilEnd) {
            30 -> "Tax year ending soon" to "Your tax year ends on 5 April. Start reviewing your transactions."
            7 -> "Tax year ending in 7 days" to "Review your transactions before 5 April"
            0 -> "Tax year ended" to "Tax year ended. Generate your P&L report."
            else -> return // Invalid days
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID_TAX_YEAR_REMINDER, notification)
    }

    /**
     * Show orphaned receipts notification.
     * PRD: "X orphaned receipts found [Review] [Delete All]"
     *
     * @param context Application context
     * @param orphanedCount Number of orphaned receipts
     */
    fun showOrphanedReceiptsNotification(context: Context, orphanedCount: Int) {
        if (orphanedCount == 0) return

        val message = if (orphanedCount == 1) {
            "1 orphaned receipt found"
        } else {
            "$orphanedCount orphaned receipts found"
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_RECEIPTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Orphaned receipts detected")
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID_ORPHANED_RECEIPTS, notification)
    }

    /**
     * Show data retention warning notification.
     * PRD: "When transactions exceed retention period: 'You have transactions older than 6 years. [Review] [Keep] [Archive]'"
     *
     * @param context Application context
     * @param oldTransactionCount Number of transactions exceeding retention period
     * @param retentionYears Configured retention period in years
     */
    fun showDataRetentionWarning(context: Context, oldTransactionCount: Int, retentionYears: Int) {
        if (oldTransactionCount == 0) return

        val message = if (oldTransactionCount == 1) {
            "You have 1 transaction older than $retentionYears years"
        } else {
            "You have $oldTransactionCount transactions older than $retentionYears years"
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Data retention notice")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$message. Review and archive old transactions to comply with your retention policy."))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID_DATA_RETENTION, notification)
    }

    /**
     * Cancel a specific notification.
     *
     * @param context Application context
     * @param notificationId Notification ID to cancel
     */
    fun cancelNotification(context: Context, notificationId: Int) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(notificationId)
    }

    /**
     * Cancel all notifications.
     *
     * @param context Application context
     */
    fun cancelAllNotifications(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancelAll()
    }
}

