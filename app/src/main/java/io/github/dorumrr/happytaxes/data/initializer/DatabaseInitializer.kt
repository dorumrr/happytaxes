package io.github.dorumrr.happytaxes.data.initializer

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.dorumrr.happytaxes.data.local.dao.TransactionDao
import io.github.dorumrr.happytaxes.data.repository.CategoryRepository
import io.github.dorumrr.happytaxes.data.repository.PreferencesRepository
import io.github.dorumrr.happytaxes.data.repository.TransactionRepository
import io.github.dorumrr.happytaxes.util.DatabaseIntegrityChecker
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Database initializer for first-time app setup.
 *
 * Responsibilities:
 * - Seed default categories on first launch (if user chooses pre-populated)
 * - Track initialization state
 * - Ensure seeding only happens once
 */
@Singleton
class DatabaseInitializer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataStore: DataStore<Preferences>,
    private val categoryRepository: CategoryRepository,
    private val transactionRepository: TransactionRepository,
    private val integrityChecker: DatabaseIntegrityChecker,
    private val transactionDao: TransactionDao,
    private val preferencesRepository: PreferencesRepository
) {

    companion object {
        private val KEY_CATEGORIES_SEEDED = booleanPreferencesKey("categories_seeded")
        private const val TAG = "DatabaseInitializer"
    }

    /**
     * Initialize database with default data.
     * Called once on app startup.
     *
     * NOTE: Categories are NOT seeded during app startup to avoid race condition.
     * Categories are seeded AFTER onboarding completes with the user's selected country.
     * This ensures users get the correct categories for their country.
     */
    suspend fun initialize() {
        Log.d(TAG, "========== DATABASE INITIALIZATION ==========")
        
        // CRITICAL: Check database integrity BEFORE any operations
        Log.d(TAG, "Running database integrity check...")
        val integrityResult = integrityChecker.checkIntegrity()
        
        if (integrityResult.isFailure) {
            val error = integrityResult.exceptionOrNull()
            Log.e(TAG, "DATABASE CORRUPTION DETECTED: ${error?.message}", error)
            
            // CRITICAL ERROR: Database is corrupted
            // The app should show a critical error dialog and prevent usage
            // until user restores from backup or resets the app
            throw error ?: Exception("Database integrity check failed")
        }
        
        val checkResult = integrityResult.getOrThrow()
        Log.d(TAG, "Database integrity check result: ${checkResult.message}")
        
        if (checkResult.isFirstRun) {
            Log.d(TAG, "First run detected - database will be created")
        }

        // Check database state
        val dbFile = context.getDatabasePath("happytaxes.db")
        Log.d(TAG, "Database file: ${dbFile.absolutePath}")
        Log.d(TAG, "  Exists: ${dbFile.exists()}")
        Log.d(TAG, "  Size: ${dbFile.length()} bytes")

        // Try to query transaction count using Room
        try {
            val transactionCount = transactionRepository.getAllActive().first().size
            Log.d(TAG, "  Transaction count (via Room): $transactionCount")
        } catch (e: Exception) {
            Log.e(TAG, "  Failed to query transactions via Room: ${e.message}", e)
        }

        // Try direct SQLite query to see what's actually in the database
        if (dbFile.exists()) {
            try {
                val db = android.database.sqlite.SQLiteDatabase.openDatabase(
                    dbFile.absolutePath,
                    null,
                    android.database.sqlite.SQLiteDatabase.OPEN_READONLY
                )

                // Query transaction count directly
                val cursor = db.rawQuery("SELECT COUNT(*) FROM transactions", null)
                if (cursor.moveToFirst()) {
                    val count = cursor.getInt(0)
                    Log.d(TAG, "  Transaction count (direct SQLite): $count")
                }
                cursor.close()

                // Query active transaction count
                val cursor2 = db.rawQuery("SELECT COUNT(*) FROM transactions WHERE isDeleted = 0", null)
                if (cursor2.moveToFirst()) {
                    val count = cursor2.getInt(0)
                    Log.d(TAG, "  Active transaction count (direct SQLite): $count")
                }
                cursor2.close()

                // List all tables
                val cursor3 = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table'", null)
                val tables = mutableListOf<String>()
                while (cursor3.moveToNext()) {
                    tables.add(cursor3.getString(0))
                }
                cursor3.close()
                Log.d(TAG, "  Tables in database: ${tables.joinToString(", ")}")

                db.close()
            } catch (e: Exception) {
                Log.e(TAG, "  Failed to query database directly: ${e.message}", e)
            }
        }

        // Check onboarding completion status
        val onboardingComplete = dataStore.data
            .map { preferences -> preferences[booleanPreferencesKey("onboarding_complete")] ?: false }
            .first()

        Log.d(TAG, "Onboarding complete: $onboardingComplete")

        // Only seed categories if onboarding is complete
        // This prevents seeding with default "US" country before user selects their country
        if (onboardingComplete) {
            val categoriesSeeded = dataStore.data
                .map { preferences -> preferences[KEY_CATEGORIES_SEEDED] ?: false }
                .first()

            Log.d(TAG, "Categories seeded flag: $categoriesSeeded")

            if (!categoriesSeeded) {
                Log.d(TAG, "Seeding categories after onboarding...")
                // Seed default categories with user's selected country
                val result = categoryRepository.seedDefaultCategories()

                if (result.isSuccess) {
                    Log.d(TAG, "Categories seeded successfully")
                    // Mark as seeded
                    dataStore.edit { preferences ->
                        preferences[KEY_CATEGORIES_SEEDED] = true
                    }
                } else {
                    // Log error but don't crash
                    // User can still use the app and add custom categories
                    result.exceptionOrNull()?.let { error ->
                        Log.e(TAG, "Failed to seed categories: ${error.message}", error)
                    }
                }
            } else {
                Log.d(TAG, "Categories already seeded, skipping")
            }
        } else {
            Log.d(TAG, "Onboarding not complete, skipping category seeding (will seed after onboarding)")
        }

        // Fix profile ID mismatch issue (one-time migration)
        fixProfileIdMismatch()

        Log.d(TAG, "========== DATABASE INITIALIZATION COMPLETE ==========")
    }

    /**
     * Check if database has been initialized.
     */
    suspend fun isInitialized(): Boolean {
        return dataStore.data
            .map { preferences ->
                preferences[KEY_CATEGORIES_SEEDED] ?: false
            }
            .first()
    }

    /**
     * Fix profile ID mismatch issue.
     *
     * This is a one-time migration to fix the issue where:
     * - Migration creates default profile with ID "business-profile-default-uuid"
     * - Onboarding creates new profiles with random UUIDs
     * - Current profile ID points to new profile, but data is in old profile
     *
     * Solution: Set current profile ID to the default profile ID if it contains data.
     */
    private suspend fun fixProfileIdMismatch() {
        val fixAppliedKey = booleanPreferencesKey("profile_id_mismatch_fix_applied")

        val fixAlreadyApplied = dataStore.data
            .map { preferences -> preferences[fixAppliedKey] ?: false }
            .first()

        if (fixAlreadyApplied) {
            Log.d(TAG, "Profile ID mismatch fix already applied, skipping")
            return
        }

        Log.d(TAG, "Checking for profile ID mismatch...")

        try {
            // Check if default profile has any transactions
            val defaultProfileId = "business-profile-default-uuid"
            val currentProfileId = preferencesRepository.getCurrentProfileIdOnce()

            Log.d(TAG, "Default profile ID: $defaultProfileId")
            Log.d(TAG, "Current profile ID: $currentProfileId")

            if (currentProfileId != null && currentProfileId != defaultProfileId) {
                // Count transactions in default profile
                val defaultProfileTransactionCount = transactionDao.getTransactionCountForProfile(defaultProfileId)
                Log.d(TAG, "Transactions in default profile: $defaultProfileTransactionCount")

                if (defaultProfileTransactionCount > 0) {
                    Log.d(TAG, "Found data in default profile, switching current profile to default profile")
                    preferencesRepository.setCurrentProfileId(defaultProfileId)
                    Log.d(TAG, "Profile ID mismatch fixed")
                } else {
                    Log.d(TAG, "No data in default profile, keeping current profile")
                }
            } else {
                Log.d(TAG, "No profile ID mismatch detected")
            }

            // Mark fix as applied
            dataStore.edit { preferences ->
                preferences[fixAppliedKey] = true
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to fix profile ID mismatch: ${e.message}", e)
        }
    }

    /**
     * Reset initialization state (for testing or app reset).
     */
    suspend fun reset() {
        dataStore.edit { preferences ->
            preferences.remove(KEY_CATEGORIES_SEEDED)
        }
    }

    /**
     * Seed default categories after onboarding completes.
     * This ensures categories are seeded with the user's selected country.
     */
    suspend fun seedCategoriesAfterOnboarding() {
        Log.d(TAG, "Seeding categories after onboarding...")
        val result = categoryRepository.seedDefaultCategories()

        if (result.isSuccess) {
            Log.d(TAG, "Categories seeded successfully after onboarding")
            dataStore.edit { preferences ->
                preferences[KEY_CATEGORIES_SEEDED] = true
            }
        } else {
            result.exceptionOrNull()?.let { error ->
                Log.e(TAG, "Failed to seed categories after onboarding: ${error.message}", error)
            }
        }
    }
}
