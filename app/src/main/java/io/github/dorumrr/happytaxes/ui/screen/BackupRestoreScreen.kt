package io.github.dorumrr.happytaxes.ui.screen

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import io.github.dorumrr.happytaxes.ui.theme.Spacing
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.dorumrr.happytaxes.ui.util.rememberDebouncedNavigation
import io.github.dorumrr.happytaxes.ui.viewmodel.BackupRestoreViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Backup and Restore screen for manual data management.
 *
 * Features:
 * - Download a copy of your data (export to ZIP)
 * - Upload a previously made backup (import from ZIP)
 *
 * PRD Reference: Section 4.17 - Data Retention & Archiving
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupRestoreScreen(
    onNavigateBack: () -> Unit,
    viewModel: BackupRestoreViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    var showRestoreWarningDialog by remember { mutableStateOf(false) }
    var showRestartDialog by remember { mutableStateOf(false) }
    var showSuccessMessage by remember { mutableStateOf(false) }
    var successMessageText by remember { mutableStateOf("") }

    // Store selected URI for restore
    var selectedRestoreUri by remember { mutableStateOf<Uri?>(null) }

    // File picker for restore
    val restoreFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            selectedRestoreUri = it
            showRestoreWarningDialog = true
        }
    }

    // Handle error messages
    uiState.errorMessage?.let { error ->
        LaunchedEffect(error) {
            // Error will be shown in UI, dismiss after showing
            kotlinx.coroutines.delay(5000)
            viewModel.dismissError()
        }
    }
    val snackbarHostState = remember { SnackbarHostState() }

    // Debounced navigation to prevent double-clicks
    val debouncedBack = rememberDebouncedNavigation(onNavigate = onNavigateBack)

    // Show success message
    LaunchedEffect(showSuccessMessage) {
        if (showSuccessMessage) {
            snackbarHostState.showSnackbar(
                message = successMessageText,
                duration = SnackbarDuration.Long
            )
            showSuccessMessage = false
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("Backup and Restore") },
                navigationIcon = {
                    IconButton(onClick = debouncedBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            // Important Section
            BackupRestoreSection(title = "Important") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.medium, vertical = Spacing.mediumSmall),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(Spacing.large)
                    )

                    Spacer(modifier = Modifier.width(Spacing.medium))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Data Replacement Warning",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = "Restoring a backup will replace all current data. Download a backup of your current data first if you want to keep it.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            HorizontalDivider()

            // Storage Info Section
            BackupRestoreSection(title = "Storage") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.medium, vertical = Spacing.mediumSmall),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(Spacing.large)
                    )

                    Spacer(modifier = Modifier.width(Spacing.medium))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Local Storage",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Backups are stored locally on your device. For additional safety, consider copying the backup file to cloud storage or another device.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            HorizontalDivider()

            // Download Backup Section
            BackupRestoreSection(title = "Download Backup") {
                // Description
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.medium, vertical = Spacing.mediumSmall),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudDownload,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(Spacing.large)
                    )

                    Spacer(modifier = Modifier.width(Spacing.medium))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Export Your Data",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Create a ZIP file with all transactions, receipts, categories, and settings",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Progress or Button
                if (uiState.isBackingUp) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.medium, vertical = Spacing.small),
                        verticalArrangement = Arrangement.spacedBy(Spacing.small)
                    ) {
                        LinearProgressIndicator(
                            progress = { uiState.backupProgress / 100f },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = "Creating backup... ${uiState.backupProgress}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "⚠️ Do not leave this screen or lock your device",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.medium, vertical = Spacing.small),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Button(
                            onClick = {
                                viewModel.createBackup { backupFile ->
                                    backupFile?.let { file ->
                                        // Copy to Downloads folder
                                        val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(
                                            android.os.Environment.DIRECTORY_DOWNLOADS
                                        )
                                        val destFile = java.io.File(downloadsDir, file.name)

                                        try {
                                            file.copyTo(destFile, overwrite = true)
                                            // Show success message
                                            successMessageText = "Data has been downloaded to your Downloads folder on your device"
                                            showSuccessMessage = true
                                        } catch (e: Exception) {
                                            successMessageText = "Failed to save backup: ${e.message}"
                                            showSuccessMessage = true
                                        }
                                    }
                                }
                            },
                            enabled = !uiState.isRestoring
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudDownload,
                                contentDescription = null,
                                modifier = Modifier.size(Spacing.mediumLarge)
                            )
                            Spacer(modifier = Modifier.width(Spacing.small))
                            Text("Download a Copy of Your Data")
                        }
                    }
                }
            }

            HorizontalDivider()

            // Restore Backup Section
            BackupRestoreSection(title = "Restore Backup") {
                // Description
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.medium, vertical = Spacing.mediumSmall),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudUpload,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(Spacing.large)
                    )

                    Spacer(modifier = Modifier.width(Spacing.medium))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Import Your Data",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Upload a previously downloaded backup file to restore all data",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Progress or Button
                if (uiState.isRestoring) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.medium, vertical = Spacing.small),
                        verticalArrangement = Arrangement.spacedBy(Spacing.small)
                    ) {
                        LinearProgressIndicator(
                            progress = { uiState.restoreProgress / 100f },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = "Restoring backup... ${uiState.restoreProgress}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "⚠️ Do not leave this screen or lock your device",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.medium, vertical = Spacing.small),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Button(
                            onClick = {
                                restoreFilePicker.launch("application/zip")
                            },
                            enabled = !uiState.isBackingUp
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudUpload,
                                contentDescription = null,
                                modifier = Modifier.size(Spacing.mediumLarge)
                            )
                            Spacer(modifier = Modifier.width(Spacing.small))
                            Text("Upload a Previously Made Backup")
                        }
                    }
                }
            }

            // Error message
            uiState.errorMessage?.let { error ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Spacing.medium),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                    shape = MaterialTheme.shapes.small
                ) {
                    Row(
                        modifier = Modifier.padding(Spacing.medium),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.small),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(Spacing.mediumLarge)
                        )
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }

    // Restore warning dialog
    if (showRestoreWarningDialog) {
        AlertDialog(
            onDismissRequest = { showRestoreWarningDialog = false },
            icon = {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = {
                Text("Restore Backup?")
            },
            text = {
                Text("This will PERMANENTLY DELETE all current data and replace it with the backup. This action cannot be undone.\n\nMake sure you have saved a backup of your current data if you want to keep it.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRestoreWarningDialog = false
                        selectedRestoreUri?.let { uri ->
                            viewModel.restoreBackup(uri) { success ->
                                if (success) {
                                    showRestartDialog = true
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Restore and Wipe Data")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreWarningDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Restart required dialog
    if (showRestartDialog) {
        AlertDialog(
            onDismissRequest = { /* Cannot dismiss */ },
            title = {
                Text("Restore Complete")
            },
            text = {
                Text("Your data has been restored successfully. The app must be restarted for changes to take effect.\n\nPlease close and reopen the app.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        // Exit the app
                        android.os.Process.killProcess(android.os.Process.myPid())
                    }
                ) {
                    Text("Close App")
                }
            }
        )
    }
}

/**
 * Section with title for backup/restore screen (matching SettingsSection style).
 */
@Composable
private fun BackupRestoreSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = Spacing.medium, vertical = Spacing.mediumSmall)
        )
        content()
    }
}
