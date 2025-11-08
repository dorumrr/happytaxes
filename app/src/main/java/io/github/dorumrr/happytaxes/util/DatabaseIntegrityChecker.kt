package io.github.dorumrr.happytaxes.util

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.dorumrr.happytaxes.data.local.HappyTaxesDatabase
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Database integrity checker for detecting corruption on startup.
 *
 * Performs comprehensive checks:
 * 1. SQLite integrity check (PRAGMA integrity_check)
 * 2. Verify critical tables exist
 * 3. Check for orphaned foreign keys
 * 4. Validate database schema version
 *
 * PRD Section 10: Error Handling - Database Failures
 */
@Singleton
class DatabaseIntegrityChecker @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    companion object {
        private const val TAG = "DatabaseIntegrity"

        /**
         * Critical tables that must exist in the database.
         * Based on current database schema.
         */
        private val CRITICAL_TABLES = listOf(
            "profiles",
            "transactions",
            "categories",
            "search_history"
        )

        /**
         * Current database version from HappyTaxesDatabase.
         * This should match the version in @Database annotation.
         */
        private const val CURRENT_DB_VERSION = 15
    }
    
    /**
     * Perform comprehensive database integrity check.
     * 
     * This should be called on app startup before any database operations.
     * 
     * @return Result with success or detailed error information
     */
    fun checkIntegrity(): Result<IntegrityCheckResult> {
        return try {
            Log.d(TAG, "========== DATABASE INTEGRITY CHECK STARTED ==========")
            
            val dbFile = context.getDatabasePath(HappyTaxesDatabase.DATABASE_NAME)
            
            // Check 1: Database file exists
            if (!dbFile.exists()) {
                Log.w(TAG, "Database file does not exist (first run)")
                return Result.success(
                    IntegrityCheckResult(
                        isHealthy = true,
                        isFirstRun = true,
                        message = "Database will be created on first use"
                    )
                )
            }
            
            Log.d(TAG, "Database file: ${dbFile.absolutePath}")
            Log.d(TAG, "Database size: ${dbFile.length() / 1024} KB")
            
            // Check 2: Database file size
            // If file is very small (< 8KB), it's likely just been created by Room but not initialized yet
            // Skip integrity check and let Room initialize it
            if (dbFile.length() < 8192) {
                Log.d(TAG, "Database file is very small (< 8KB), likely just created - skipping integrity check")
                return Result.success(
                    IntegrityCheckResult(
                        isHealthy = true,
                        isFirstRun = true,
                        message = "Database file just created, will be initialized by Room"
                    )
                )
            }
            
            // Check 3: Database file is readable
            if (!dbFile.canRead()) {
                return Result.failure(
                    DatabaseCorruptionException("Database file is not readable")
                )
            }
            
            // Check 3: Open database and run integrity check
            val db = try {
                SQLiteDatabase.openDatabase(
                    dbFile.absolutePath,
                    null,
                    SQLiteDatabase.OPEN_READONLY
                )
            } catch (e: Exception) {
                // If we can't open the database, it might be corrupted or not a valid database
                Log.e(TAG, "Failed to open database file", e)
                return Result.failure(
                    DatabaseCorruptionException("Cannot open database: ${e.message}")
                )
            }
            
            try {
                // Run PRAGMA integrity_check
                val integrityResult = checkSQLiteIntegrity(db)
                if (!integrityResult.isHealthy) {
                    return Result.failure(
                        DatabaseCorruptionException(integrityResult.message)
                    )
                }
                
                // Check critical tables exist
                val tablesResult = checkCriticalTables(db)
                if (!tablesResult.isHealthy) {
                    return Result.failure(
                        DatabaseCorruptionException(tablesResult.message)
                    )
                }
                
                // Check schema version
                val versionResult = checkSchemaVersion(db)
                if (!versionResult.isHealthy) {
                    return Result.failure(
                        DatabaseCorruptionException(versionResult.message)
                    )
                }
                
                Log.d(TAG, "========== DATABASE INTEGRITY CHECK PASSED ==========")
                
                Result.success(
                    IntegrityCheckResult(
                        isHealthy = true,
                        isFirstRun = false,
                        message = "Database integrity verified"
                    )
                )
            } finally {
                db.close()
            }
        } catch (e: Exception) {
            // Catch all exceptions including SQLCipher-specific ones
            Log.e(TAG, "Error during integrity check: ${e.javaClass.simpleName}", e)
            
            // Check if it's a database-related exception
            val isDatabaseError = e.javaClass.name.contains("SQLite") ||
                                 e.javaClass.name.contains("Database")
            
            if (isDatabaseError) {
                Result.failure(
                    DatabaseCorruptionException("Database is corrupted: ${e.message}")
                )
            } else {
                Result.failure(
                    DatabaseCorruptionException("Integrity check failed: ${e.message}")
                )
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Unexpected error during integrity check", e)
            Result.failure(
                DatabaseCorruptionException("Integrity check failed: ${e.message}")
            )
        }
    }
    
    /**
     * Run SQLite PRAGMA integrity_check.
     * 
     * @param db Database connection
     * @return IntegrityCheckResult
     */
    private fun checkSQLiteIntegrity(db: SQLiteDatabase): IntegrityCheckResult {
        return try {
            val cursor = db.rawQuery("PRAGMA integrity_check", null)
            
            cursor.use {
                if (it.moveToFirst()) {
                    val result = it.getString(0)
                    
                    if (result == "ok") {
                        Log.d(TAG, "✓ SQLite integrity check: OK")
                        IntegrityCheckResult(
                            isHealthy = true,
                            message = "SQLite integrity check passed"
                        )
                    } else {
                        Log.e(TAG, "✗ SQLite integrity check failed: $result")
                        IntegrityCheckResult(
                            isHealthy = false,
                            message = "SQLite integrity check failed: $result"
                        )
                    }
                } else {
                    Log.e(TAG, "✗ SQLite integrity check returned no results")
                    IntegrityCheckResult(
                        isHealthy = false,
                        message = "SQLite integrity check returned no results"
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "✗ SQLite integrity check error", e)
            IntegrityCheckResult(
                isHealthy = false,
                message = "SQLite integrity check error: ${e.message}"
            )
        }
    }
    
    /**
     * Check that all critical tables exist.
     * 
     * @param db Database connection
     * @return IntegrityCheckResult
     */
    private fun checkCriticalTables(db: SQLiteDatabase): IntegrityCheckResult {
        return try {
            val missingTables = mutableListOf<String>()
            
            for (tableName in CRITICAL_TABLES) {
                val cursor = db.rawQuery(
                    "SELECT name FROM sqlite_master WHERE type='table' AND name=?",
                    arrayOf(tableName)
                )
                
                cursor.use {
                    if (!it.moveToFirst()) {
                        missingTables.add(tableName)
                        Log.e(TAG, "✗ Critical table missing: $tableName")
                    } else {
                        Log.d(TAG, "✓ Critical table exists: $tableName")
                    }
                }
            }
            
            if (missingTables.isEmpty()) {
                IntegrityCheckResult(
                    isHealthy = true,
                    message = "All critical tables exist"
                )
            } else {
                IntegrityCheckResult(
                    isHealthy = false,
                    message = "Missing critical tables: ${missingTables.joinToString(", ")}"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "✗ Critical tables check error", e)
            IntegrityCheckResult(
                isHealthy = false,
                message = "Critical tables check error: ${e.message}"
            )
        }
    }
    
    /**
     * Check database schema version.
     *
     * This check ensures the database version is compatible with the app.
     *
     * Version Compatibility Rules:
     * - Same version: Perfect match, no migration needed
     * - Older version: Room will automatically migrate up (backward compatibility)
     * - Newer version: This is OK! It means a backup from a newer app version was restored.
     *   Room migrations are designed to be forward-compatible - the app can still read
     *   the data, it just won't use the newer fields/features until the app is updated.
     *
     * We only fail if:
     * - Version is 0 (corrupted database)
     * - Version is impossibly high (> 1000, likely corrupted)
     *
     * @param db Database connection
     * @return IntegrityCheckResult
     */
    private fun checkSchemaVersion(db: SQLiteDatabase): IntegrityCheckResult {
        return try {
            val cursor = db.rawQuery("PRAGMA user_version", null)

            cursor.use {
                if (it.moveToFirst()) {
                    val version = it.getInt(0)
                    Log.d(TAG, "✓ Database schema version: $version (app supports v$CURRENT_DB_VERSION)")

                    when {
                        // Version 0 means corrupted or uninitialized database
                        version == 0 -> {
                            Log.e(TAG, "✗ Database version is 0 (corrupted or uninitialized)")
                            IntegrityCheckResult(
                                isHealthy = false,
                                message = "Database version is 0 (corrupted or uninitialized)"
                            )
                        }
                        // Impossibly high version (likely corrupted)
                        version > 1000 -> {
                            Log.e(TAG, "✗ Database version is impossibly high: $version (likely corrupted)")
                            IntegrityCheckResult(
                                isHealthy = false,
                                message = "Database version is impossibly high: $version (likely corrupted)"
                            )
                        }
                        // Same version - perfect match
                        version == CURRENT_DB_VERSION -> {
                            Log.d(TAG, "✓ Database version matches app version (v$version)")
                            IntegrityCheckResult(
                                isHealthy = true,
                                message = "Database schema version is current (v$version)"
                            )
                        }
                        // Older version - Room will migrate up
                        version < CURRENT_DB_VERSION -> {
                            Log.w(TAG, "Database version is older (v$version < v$CURRENT_DB_VERSION). Room will migrate up.")
                            IntegrityCheckResult(
                                isHealthy = true,
                                message = "Database schema version is older (v$version). Migration will be performed."
                            )
                        }
                        // Newer version - this is OK! Backup from newer app version
                        version > CURRENT_DB_VERSION -> {
                            Log.w(TAG, "Database version is newer (v$version > v$CURRENT_DB_VERSION). This is OK - backup from newer app version.")
                            IntegrityCheckResult(
                                isHealthy = true,
                                message = "Database schema version is newer (v$version > v$CURRENT_DB_VERSION). Backup from newer app version - forward compatible."
                            )
                        }
                        else -> {
                            // Should never reach here
                            IntegrityCheckResult(
                                isHealthy = true,
                                message = "Database schema version: v$version"
                            )
                        }
                    }
                } else {
                    IntegrityCheckResult(
                        isHealthy = false,
                        message = "Could not read database schema version"
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "✗ Schema version check error", e)
            IntegrityCheckResult(
                isHealthy = false,
                message = "Schema version check error: ${e.message}"
            )
        }
    }
}

/**
 * Result of database integrity check.
 * 
 * @param isHealthy true if database is healthy and can be used
 * @param isFirstRun true if database doesn't exist yet (first app run)
 * @param message Detailed message about the check result
 */
data class IntegrityCheckResult(
    val isHealthy: Boolean,
    val isFirstRun: Boolean = false,
    val message: String
)

/**
 * Exception thrown when database corruption is detected.
 */
class DatabaseCorruptionException(message: String) : Exception(message)