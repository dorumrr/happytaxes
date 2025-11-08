package io.github.dorumrr.happytaxes

import android.app.Application
import android.util.Log
import dagger.hilt.android.HiltAndroidApp
import io.github.dorumrr.happytaxes.data.initializer.DatabaseInitializer
import io.github.dorumrr.happytaxes.data.notification.DataRetentionWorker
import io.github.dorumrr.happytaxes.data.notification.NotificationWorker
import io.github.dorumrr.happytaxes.data.notification.OrphanedReceiptWorker
import io.github.dorumrr.happytaxes.util.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Application class for HappyTaxes.
 *
 * Annotated with @HiltAndroidApp to enable Hilt dependency injection.
 *
 * Responsibilities:
 * - Initialize database with default data on first launch
 * - Initialize notification channels
 * - Schedule background workers (notifications, data retention)
 */
@HiltAndroidApp
class HappyTaxesApplication : Application() {

    @Inject
    lateinit var databaseInitializer: DatabaseInitializer

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        private const val TAG = "HappyTaxesApp"
    }

    override fun onCreate() {
        super.onCreate()

        Log.d(TAG, "========== APP STARTED ==========")
        Log.d(TAG, "Build type: ${if (BuildConfig.DEBUG) "DEBUG" else "RELEASE"}")

        // Initialize notification channels (Phase 6)
        NotificationHelper.createNotificationChannels(this)

        // Initialize database with default data
        applicationScope.launch {
            Log.d(TAG, "Initializing database...")
            databaseInitializer.initialize()
            Log.d(TAG, "Database initialized")
        }

        // Schedule NotificationWorker for daily reminders (Phase 6)
        NotificationWorker.schedule(this)

        // Schedule OrphanedReceiptWorker for weekly checks (Phase 6)
        OrphanedReceiptWorker.schedule(this)

        // Schedule DataRetentionWorker for weekly retention checks (Phase 6)
        DataRetentionWorker.schedule(this)
    }
}

