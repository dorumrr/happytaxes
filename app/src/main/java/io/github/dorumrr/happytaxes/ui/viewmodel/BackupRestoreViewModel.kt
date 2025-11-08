package io.github.dorumrr.happytaxes.ui.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.dorumrr.happytaxes.data.local.HappyTaxesDatabase
import io.github.dorumrr.happytaxes.data.repository.TransactionRepository
import io.github.dorumrr.happytaxes.util.ConcurrencyHelper
import io.github.dorumrr.happytaxes.util.StorageHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject

/**
 * ViewModel for Backup and Restore screen.
 *
 * Handles:
 * - Creating full backup (database + receipts) to ZIP
 * - Restoring from backup ZIP (wipes all data first)
 * - Progress tracking for both operations
 *
 * PRD Reference: Section 4.17 - Data Retention & Archiving
 */
@HiltViewModel
class BackupRestoreViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: HappyTaxesDatabase,
    private val transactionRepository: TransactionRepository,
    private val storageHelper: StorageHelper
) : ViewModel() {

    private val _uiState = MutableStateFlow(BackupRestoreUiState())
    val uiState: StateFlow<BackupRestoreUiState> = _uiState.asStateFlow()

    companion object {
        private const val TAG = "BackupRestoreVM"
    }

    /**
     * Create a full backup of all data.
     *
     * Backup includes:
     * - Database file (all transactions, categories, currency rates)
     * - DataStore preferences (app settings, initialization flags)
     * - All receipt images
     *
     * @return File path of created ZIP, or null if failed
     */
    fun createBackup(onComplete: (File?) -> Unit) {
        viewModelScope.launch {
            ConcurrencyHelper.withBackupLock {
                try {
                    Log.d(TAG, "========== BACKUP STARTED ==========")

                // Check storage space before proceeding
                val storageCheck = storageHelper.checkSpaceForBackup()
                if (storageCheck.isFailure) {
                    val errorMsg = storageCheck.exceptionOrNull()?.message
                        ?: "Insufficient storage space for backup"
                    Log.e(TAG, "Backup failed: $errorMsg")
                    _uiState.value = _uiState.value.copy(
                        isBackingUp = false,
                        backupProgress = 0,
                        errorMessage = errorMsg
                    )
                    onComplete(null)
                    return@withBackupLock
                }

                // Log current transaction count BEFORE backup
                val transactionsBefore = transactionRepository.getAllActive().first()
                Log.d(TAG, "BEFORE BACKUP: Active transactions count = ${transactionsBefore.size}")
                transactionsBefore.forEachIndexed { index, tx ->
                    Log.d(TAG, "  Transaction $index: id=${tx.id}, type=${tx.type}, amount=${tx.amount}, desc=${tx.description}, isDraft=${tx.isDraft}")
                }

                _uiState.value = _uiState.value.copy(
                    isBackingUp = true,
                    backupProgress = 0,
                    errorMessage = null
                )

                withContext(Dispatchers.IO) {
                    // CRITICAL: Close database to ensure all WAL changes are flushed
                    Log.d(TAG, "Closing database to flush WAL...")
                    database.close()
                    Log.d(TAG, "Database closed")

                    // Checkpoint database to flush WAL to main file
                    Log.d(TAG, "Checkpointing database...")
                    val dbFile = context.getDatabasePath(HappyTaxesDatabase.DATABASE_NAME)
                    try {
                        val db = android.database.sqlite.SQLiteDatabase.openDatabase(
                            dbFile.absolutePath,
                            null,
                            android.database.sqlite.SQLiteDatabase.OPEN_READWRITE
                        )
                        db.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null)?.close()
                        db.close()
                        Log.d(TAG, "Database checkpointed with TRUNCATE")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to checkpoint database: ${e.message}", e)
                    }

                    // Create backup filename with timestamp
                    val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
                    val backupFile = File(context.cacheDir, "happytaxes_backup_$timestamp.zip")
                    Log.d(TAG, "Backup file: ${backupFile.absolutePath}")

                    ZipOutputStream(FileOutputStream(backupFile)).use { zipOut ->
                        // Progress: 0-20% - Database files (main + WAL + SHM)
                        _uiState.value = _uiState.value.copy(backupProgress = 5)

                        // Add main database file
                        val dbFile = context.getDatabasePath(HappyTaxesDatabase.DATABASE_NAME)
                        Log.d(TAG, "Database file: ${dbFile.absolutePath}, exists=${dbFile.exists()}, size=${dbFile.length()} bytes")
                        if (dbFile.exists()) {
                            zipOut.putNextEntry(ZipEntry("database/${dbFile.name}"))
                            FileInputStream(dbFile).use { input ->
                                input.copyTo(zipOut)
                            }
                            zipOut.closeEntry()
                            Log.d(TAG, "Main database backed up successfully")
                        } else {
                            Log.e(TAG, "Database file does not exist!")
                        }

                        _uiState.value = _uiState.value.copy(backupProgress = 10)

                        // Add WAL file if it exists (should be empty after checkpoint, but backup anyway)
                        val walFile = File(dbFile.path + "-wal")
                        Log.d(TAG, "WAL file: ${walFile.absolutePath}, exists=${walFile.exists()}, size=${walFile.length()} bytes")
                        if (walFile.exists() && walFile.length() > 0) {
                            zipOut.putNextEntry(ZipEntry("database/${walFile.name}"))
                            FileInputStream(walFile).use { input ->
                                input.copyTo(zipOut)
                            }
                            zipOut.closeEntry()
                            Log.d(TAG, "WAL file backed up")
                        } else {
                            Log.d(TAG, "WAL file not backed up (doesn't exist or is empty)")
                        }

                        _uiState.value = _uiState.value.copy(backupProgress = 15)

                        // Add SHM file if it exists
                        val shmFile = File(dbFile.path + "-shm")
                        Log.d(TAG, "SHM file: ${shmFile.absolutePath}, exists=${shmFile.exists()}, size=${shmFile.length()} bytes")
                        if (shmFile.exists() && shmFile.length() > 0) {
                            zipOut.putNextEntry(ZipEntry("database/${shmFile.name}"))
                            FileInputStream(shmFile).use { input ->
                                input.copyTo(zipOut)
                            }
                            zipOut.closeEntry()
                            Log.d(TAG, "SHM file backed up")
                        } else {
                            Log.d(TAG, "SHM file not backed up (doesn't exist or is empty)")
                        }

                        _uiState.value = _uiState.value.copy(backupProgress = 20)

                        // Progress: 20-30% - DataStore preferences
                        val dataStoreDir = File(context.filesDir.parent, "datastore")
                        Log.d(TAG, "DataStore dir: ${dataStoreDir.absolutePath}, exists=${dataStoreDir.exists()}")
                        if (dataStoreDir.exists()) {
                            val dataStoreFiles = dataStoreDir.walkTopDown().filter { it.isFile }.toList()
                            Log.d(TAG, "DataStore files count: ${dataStoreFiles.size}")
                            dataStoreFiles.forEach { file ->
                                val relativePath = file.relativeTo(dataStoreDir).path
                                Log.d(TAG, "  Backing up DataStore file: $relativePath, size=${file.length()} bytes")
                                zipOut.putNextEntry(ZipEntry("datastore/$relativePath"))
                                FileInputStream(file).use { input ->
                                    input.copyTo(zipOut)
                                }
                                zipOut.closeEntry()
                            }
                            Log.d(TAG, "DataStore backed up successfully")
                        } else {
                            Log.w(TAG, "DataStore directory does not exist")
                        }

                        _uiState.value = _uiState.value.copy(backupProgress = 30)

                        // Progress: 30-100% - Receipts
                        val receiptsDir = File(context.filesDir, "receipts")
                        Log.d(TAG, "Receipts dir: ${receiptsDir.absolutePath}, exists=${receiptsDir.exists()}")
                        if (receiptsDir.exists()) {
                            val allFiles = receiptsDir.walkTopDown().filter { it.isFile }.toList()
                            val totalFiles = allFiles.size
                            Log.d(TAG, "Receipt files count: $totalFiles")

                            allFiles.forEachIndexed { index, file ->
                                val relativePath = file.relativeTo(receiptsDir).path
                                Log.d(TAG, "  Backing up receipt: $relativePath, size=${file.length()} bytes")
                                zipOut.putNextEntry(ZipEntry("receipts/$relativePath"))
                                FileInputStream(file).use { input ->
                                    input.copyTo(zipOut)
                                }
                                zipOut.closeEntry()

                                // Update progress (30% to 100%)
                                val progress = 30 + ((index + 1) * 70 / totalFiles)
                                _uiState.value = _uiState.value.copy(backupProgress = progress)
                            }
                            Log.d(TAG, "Receipts backed up successfully")
                        } else {
                            Log.w(TAG, "Receipts directory does not exist")
                        }

                        _uiState.value = _uiState.value.copy(backupProgress = 100)
                    }
    
                    Log.d(TAG, "Backup ZIP created: ${backupFile.absolutePath}, size=${backupFile.length()} bytes")
                    
                    // Validate backup integrity before completing
                    Log.d(TAG, "Validating backup integrity...")
                    val validationResult = validateBackup(backupFile)
                    
                    if (validationResult.isSuccess) {
                        Log.d(TAG, "Backup validation successful")
                        Log.d(TAG, "========== BACKUP COMPLETED ==========")
                        
                        _uiState.value = _uiState.value.copy(
                            isBackingUp = false,
                            backupProgress = 0
                        )
                        
                        onComplete(backupFile)
                    } else {
                        val errorMsg = validationResult.exceptionOrNull()?.message
                            ?: "Backup validation failed"
                        Log.e(TAG, "Backup validation failed: $errorMsg")
                        
                        // Delete invalid backup
                        backupFile.delete()
                        
                        _uiState.value = _uiState.value.copy(
                            isBackingUp = false,
                            backupProgress = 0,
                            errorMessage = "Backup validation failed: $errorMsg. Please try again."
                        )
                        
                        onComplete(null)
                    }
                }
                } catch (e: Exception) {
                    _uiState.value = _uiState.value.copy(
                        isBackingUp = false,
                        backupProgress = 0,
                        errorMessage = "Backup failed: ${e.message}"
                    )
                    onComplete(null)
                }
            }
        }
    }

    /**
     * Restore from a backup ZIP file.
     *
     * WARNING: This will WIPE ALL EXISTING DATA before restoring.
     *
     * CRITICAL: After restore completes, the app MUST be restarted for the
     * restored database to be properly opened. The database connection is
     * closed during the wipe process and cannot be reopened while the app
     * is running.
     *
     * @param backupUri URI of the backup ZIP file
     * @param onComplete Callback with success/failure
     */
    fun restoreBackup(backupUri: Uri, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            ConcurrencyHelper.withBackupLock {
                try {
                    Log.d(TAG, "========== RESTORE STARTED ==========")
                Log.d(TAG, "Backup URI: $backupUri")

                // Log current transaction count BEFORE restore
                val transactionsBefore = transactionRepository.getAllActive().first()
                Log.d(TAG, "BEFORE RESTORE: Active transactions count = ${transactionsBefore.size}")
                transactionsBefore.forEachIndexed { index, tx ->
                    Log.d(TAG, "  Transaction $index: id=${tx.id}, type=${tx.type}, amount=${tx.amount}, desc=${tx.description}, isDraft=${tx.isDraft}")
                }

                _uiState.value = _uiState.value.copy(
                    isRestoring = true,
                    restoreProgress = 0,
                    errorMessage = null
                )

                withContext(Dispatchers.IO) {
                    // Progress: 0-10% - Validation and copy ZIP
                    _uiState.value = _uiState.value.copy(restoreProgress = 5)

                    // Copy ZIP to temp location
                    val tempZip = File(context.cacheDir, "restore_temp.zip")
                    Log.d(TAG, "Copying ZIP to temp location: ${tempZip.absolutePath}")
                    context.contentResolver.openInputStream(backupUri)?.use { input ->
                        FileOutputStream(tempZip).use { output ->
                            input.copyTo(output)
                        }
                    }
                    Log.d(TAG, "ZIP copied, size=${tempZip.length()} bytes")

                    _uiState.value = _uiState.value.copy(restoreProgress = 10)

                    // Progress: 10-30% - Extract to temp directory first
                    val tempRestoreDir = File(context.cacheDir, "restore_temp_data")
                    Log.d(TAG, "Temp restore dir: ${tempRestoreDir.absolutePath}")
                    if (tempRestoreDir.exists()) {
                        Log.d(TAG, "Cleaning existing temp restore dir")
                        tempRestoreDir.deleteRecursively()
                    }
                    tempRestoreDir.mkdirs()

                    Log.d(TAG, "Extracting ZIP contents...")
                    ZipInputStream(FileInputStream(tempZip)).use { zipIn ->
                        var entry = zipIn.nextEntry
                        var fileCount = 0

                        while (entry != null) {
                            if (!entry.isDirectory) {
                                val file = File(tempRestoreDir, entry.name)
                                file.parentFile?.mkdirs()
                                FileOutputStream(file).use { output ->
                                    zipIn.copyTo(output)
                                }
                                Log.d(TAG, "  Extracted: ${entry.name}, size=${file.length()} bytes")
                                fileCount++
                            }
                            zipIn.closeEntry()
                            entry = zipIn.nextEntry
                        }
                        Log.d(TAG, "Extracted $fileCount files from ZIP")
                    }

                    tempZip.delete()
                    Log.d(TAG, "Temp ZIP deleted")
                    _uiState.value = _uiState.value.copy(restoreProgress = 30)

                    // Progress: 30-50% - Wipe existing data
                    // CRITICAL: This closes the database connection
                    Log.d(TAG, "========== WIPING EXISTING DATA ==========")
                    wipeAllData()
                    Log.d(TAG, "========== WIPE COMPLETE ==========")
                    _uiState.value = _uiState.value.copy(restoreProgress = 50)

                    // Progress: 50-100% - Copy restored files to final locations
                    Log.d(TAG, "========== COPYING RESTORED FILES ==========")

                    // Main database file
                    val restoredDbFile = File(tempRestoreDir, "database/${HappyTaxesDatabase.DATABASE_NAME}")
                    Log.d(TAG, "Restored DB file: ${restoredDbFile.absolutePath}, exists=${restoredDbFile.exists()}, size=${restoredDbFile.length()} bytes")
                    if (restoredDbFile.exists()) {
                        val targetDbFile = context.getDatabasePath(HappyTaxesDatabase.DATABASE_NAME)
                        Log.d(TAG, "Target DB file: ${targetDbFile.absolutePath}")
                        targetDbFile.parentFile?.mkdirs()
                        restoredDbFile.copyTo(targetDbFile, overwrite = true)
                        Log.d(TAG, "Main database restored, size=${targetDbFile.length()} bytes")
                    } else {
                        Log.e(TAG, "Restored database file not found in backup!")
                    }
                    _uiState.value = _uiState.value.copy(restoreProgress = 55)

                    // WAL file (if exists in backup)
                    val restoredWalFile = File(tempRestoreDir, "database/${HappyTaxesDatabase.DATABASE_NAME}-wal")
                    Log.d(TAG, "Restored WAL file: ${restoredWalFile.absolutePath}, exists=${restoredWalFile.exists()}, size=${restoredWalFile.length()} bytes")
                    if (restoredWalFile.exists()) {
                        val targetWalFile = File(context.getDatabasePath(HappyTaxesDatabase.DATABASE_NAME).path + "-wal")
                        restoredWalFile.copyTo(targetWalFile, overwrite = true)
                        Log.d(TAG, "WAL file restored, size=${targetWalFile.length()} bytes")
                    } else {
                        Log.d(TAG, "No WAL file in backup")
                    }
                    _uiState.value = _uiState.value.copy(restoreProgress = 58)

                    // SHM file (if exists in backup)
                    val restoredShmFile = File(tempRestoreDir, "database/${HappyTaxesDatabase.DATABASE_NAME}-shm")
                    Log.d(TAG, "Restored SHM file: ${restoredShmFile.absolutePath}, exists=${restoredShmFile.exists()}, size=${restoredShmFile.length()} bytes")
                    if (restoredShmFile.exists()) {
                        val targetShmFile = File(context.getDatabasePath(HappyTaxesDatabase.DATABASE_NAME).path + "-shm")
                        restoredShmFile.copyTo(targetShmFile, overwrite = true)
                        Log.d(TAG, "SHM file restored, size=${targetShmFile.length()} bytes")
                    } else {
                        Log.d(TAG, "No SHM file in backup")
                    }
                    _uiState.value = _uiState.value.copy(restoreProgress = 60)

                    // DataStore preferences
                    val restoredDataStoreDir = File(tempRestoreDir, "datastore")
                    Log.d(TAG, "Restored DataStore dir: ${restoredDataStoreDir.absolutePath}, exists=${restoredDataStoreDir.exists()}")
                    if (restoredDataStoreDir.exists()) {
                        val targetDataStoreDir = File(context.filesDir.parent, "datastore")
                        Log.d(TAG, "Target DataStore dir: ${targetDataStoreDir.absolutePath}")
                        targetDataStoreDir.mkdirs()
                        val dataStoreFiles = restoredDataStoreDir.walkTopDown().filter { it.isFile }.toList()
                        Log.d(TAG, "Restoring ${dataStoreFiles.size} DataStore files")
                        restoredDataStoreDir.copyRecursively(targetDataStoreDir, overwrite = true)
                        Log.d(TAG, "DataStore restored")
                    } else {
                        Log.w(TAG, "Restored DataStore directory not found in backup")
                    }
                    _uiState.value = _uiState.value.copy(restoreProgress = 75)

                    // Receipts
                    val restoredReceiptsDir = File(tempRestoreDir, "receipts")
                    Log.d(TAG, "Restored receipts dir: ${restoredReceiptsDir.absolutePath}, exists=${restoredReceiptsDir.exists()}")
                    if (restoredReceiptsDir.exists()) {
                        val targetReceiptsDir = File(context.filesDir, "receipts")
                        Log.d(TAG, "Target receipts dir: ${targetReceiptsDir.absolutePath}")
                        targetReceiptsDir.mkdirs()
                        val receiptFiles = restoredReceiptsDir.walkTopDown().filter { it.isFile }.toList()
                        Log.d(TAG, "Restoring ${receiptFiles.size} receipt files")
                        restoredReceiptsDir.copyRecursively(targetReceiptsDir, overwrite = true)
                        Log.d(TAG, "Receipts restored")
                    } else {
                        Log.w(TAG, "Restored receipts directory not found in backup")
                    }
                    _uiState.value = _uiState.value.copy(restoreProgress = 90)

                    // Cleanup temp directory
                    Log.d(TAG, "Cleaning up temp directory")
                    tempRestoreDir.deleteRecursively()

                    // Verify restored database file
                    val finalDbFile = context.getDatabasePath(HappyTaxesDatabase.DATABASE_NAME)
                    Log.d(TAG, "Final DB file verification:")
                    Log.d(TAG, "  Path: ${finalDbFile.absolutePath}")
                    Log.d(TAG, "  Exists: ${finalDbFile.exists()}")
                    Log.d(TAG, "  Size: ${finalDbFile.length()} bytes")
                    Log.d(TAG, "  Readable: ${finalDbFile.canRead()}")
                    Log.d(TAG, "  Writable: ${finalDbFile.canWrite()}")

                    // List all files in database directory
                    val dbDir = finalDbFile.parentFile
                    if (dbDir != null && dbDir.exists()) {
                        Log.d(TAG, "Database directory contents:")
                        dbDir.listFiles()?.forEach { file ->
                            Log.d(TAG, "    ${file.name}: ${file.length()} bytes")
                        }
                    }

                    Log.d(TAG, "========== RESTORE COMPLETED ==========")
                    Log.d(TAG, "App must be restarted for changes to take effect")

                    _uiState.value = _uiState.value.copy(
                        restoreProgress = 100,
                        isRestoring = false
                    )

                    onComplete(true)
                }
                } catch (e: Exception) {
                    _uiState.value = _uiState.value.copy(
                        isRestoring = false,
                        restoreProgress = 0,
                        errorMessage = "Restore failed: ${e.message}"
                    )
                    onComplete(false)
                }
            }
        }
    }

    /**
     * Wipe all existing data before restore.
     *
     * Deletes:
     * - Database files (main, WAL, SHM)
     * - DataStore preferences (settings, initialization flags)
     * - Receipt files
     */
    private suspend fun wipeAllData() {
        withContext(Dispatchers.IO) {
            // Close database
            Log.d(TAG, "Closing database...")
            database.close()
            Log.d(TAG, "Database closed")

            // Delete database files
            val dbFile = context.getDatabasePath(HappyTaxesDatabase.DATABASE_NAME)
            Log.d(TAG, "Deleting database files:")
            Log.d(TAG, "  Main DB: ${dbFile.absolutePath}, exists=${dbFile.exists()}")
            val dbDeleted = dbFile.delete()
            Log.d(TAG, "  Main DB deleted: $dbDeleted")

            val shmFile = File(dbFile.path + "-shm")
            Log.d(TAG, "  SHM file: ${shmFile.absolutePath}, exists=${shmFile.exists()}")
            val shmDeleted = shmFile.delete()
            Log.d(TAG, "  SHM deleted: $shmDeleted")

            val walFile = File(dbFile.path + "-wal")
            Log.d(TAG, "  WAL file: ${walFile.absolutePath}, exists=${walFile.exists()}")
            val walDeleted = walFile.delete()
            Log.d(TAG, "  WAL deleted: $walDeleted")

            // Delete DataStore preferences
            val dataStoreDir = File(context.filesDir.parent, "datastore")
            Log.d(TAG, "Deleting DataStore: ${dataStoreDir.absolutePath}, exists=${dataStoreDir.exists()}")
            if (dataStoreDir.exists()) {
                val dataStoreDeleted = dataStoreDir.deleteRecursively()
                Log.d(TAG, "  DataStore deleted: $dataStoreDeleted")
            }

            // Delete all receipts
            val receiptsDir = File(context.filesDir, "receipts")
            Log.d(TAG, "Deleting receipts: ${receiptsDir.absolutePath}, exists=${receiptsDir.exists()}")
            if (receiptsDir.exists()) {
                val receiptsDeleted = receiptsDir.deleteRecursively()
                Log.d(TAG, "  Receipts deleted: $receiptsDeleted")
            }

            Log.d(TAG, "All data wiped")
        }
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
    
    /**
     * Validate backup ZIP file integrity.
     *
     * Checks:
     * 1. ZIP file is readable
     * 2. Contains expected directories (database/, datastore/, receipts/)
     * 3. Database file exists and has valid SQLite header
     * 4. ZIP is not corrupted
     *
     * @param backupFile Backup ZIP file to validate
     * @return Result with success or validation error
     */
    private fun validateBackup(backupFile: File): Result<Unit> {
        return try {
            // Check 1: File exists and is readable
            if (!backupFile.exists() || !backupFile.canRead()) {
                return Result.failure(Exception("Backup file is not readable"))
            }
            
            // Check 2: File size is reasonable (> 1KB, < 5GB)
            val fileSize = backupFile.length()
            if (fileSize < 1024) {
                return Result.failure(Exception("Backup file is too small (${fileSize} bytes)"))
            }
            if (fileSize > 5L * 1024 * 1024 * 1024) {
                return Result.failure(Exception("Backup file is too large (${fileSize / (1024 * 1024)} MB)"))
            }
            
            // Check 3: ZIP is valid and contains expected structure
            val expectedEntries = mutableSetOf<String>()
            var hasDatabaseDir = false
            var hasMainDatabase = false
            
            ZipInputStream(FileInputStream(backupFile)).use { zipIn ->
                var entry = zipIn.nextEntry
                var entryCount = 0
                
                while (entry != null) {
                    entryCount++
                    val entryName = entry.name
                    expectedEntries.add(entryName)
                    
                    // Check for required directories/files
                    if (entryName.startsWith("database/")) {
                        hasDatabaseDir = true
                    }
                    if (entryName == "database/${HappyTaxesDatabase.DATABASE_NAME}") {
                        hasMainDatabase = true
                        
                        // Validate SQLite header (first 16 bytes should be "SQLite format 3\u0000")
                        val header = ByteArray(16)
                        val bytesRead = zipIn.read(header)
                        
                        if (bytesRead == 16) {
                            val headerString = String(header, 0, 15, Charsets.US_ASCII)
                            if (!headerString.startsWith("SQLite format 3")) {
                                return Result.failure(Exception("Database file has invalid SQLite header"))
                            }
                        } else {
                            return Result.failure(Exception("Database file is too small or corrupted"))
                        }
                    }
                    
                    zipIn.closeEntry()
                    entry = zipIn.nextEntry
                }
                
                // Check minimum entry count (should have at least database file)
                if (entryCount == 0) {
                    return Result.failure(Exception("Backup ZIP is empty"))
                }
            }
            
            // Check 4: Required files present
            if (!hasDatabaseDir) {
                return Result.failure(Exception("Backup missing database directory"))
            }
            if (!hasMainDatabase) {
                return Result.failure(Exception("Backup missing main database file"))
            }
            
            Log.d(TAG, "Backup validation passed:")
            Log.d(TAG, "  File size: ${fileSize / 1024} KB")
            Log.d(TAG, "  Entries: ${expectedEntries.size}")
            Log.d(TAG, "  Has database: $hasMainDatabase")
            
            Result.success(Unit)
        } catch (e: java.util.zip.ZipException) {
            Result.failure(Exception("Backup ZIP is corrupted: ${e.message}"))
        } catch (e: Exception) {
            Result.failure(Exception("Backup validation failed: ${e.message}"))
        }
    }
}

/**
 * UI state for Backup and Restore screen.
 */
data class BackupRestoreUiState(
    val isBackingUp: Boolean = false,
    val backupProgress: Int = 0,
    val isRestoring: Boolean = false,
    val restoreProgress: Int = 0,
    val errorMessage: String? = null
)

