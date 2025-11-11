package io.github.dorumrr.happytaxes.ui.screen

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Process
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import kotlin.system.exitProcess
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.dorumrr.happytaxes.R
import io.github.dorumrr.happytaxes.ui.theme.CornerRadius
import io.github.dorumrr.happytaxes.ui.theme.Dimensions
import io.github.dorumrr.happytaxes.ui.theme.Spacing
import io.github.dorumrr.happytaxes.ui.viewmodel.SettingsViewModel

private const val TAG = "SettingsScreen"

/**
 * Settings screen for app configuration.
 * Accessible via bottom navigation bar.
 *
 * Sections:
 * - Tax & Currency (tax year, retention period)
 * - General (manage categories, default transaction type, add button position)
 * - Notifications (enable/disable, reminder frequency)
 * - OCR Settings (enable/disable OCR)
 * - Manage Your Data (backup & restore, reset app)
 *
 * Note: Backup & Restore is now accessible from Settings (moved from main menu)
 * Note: Manage Categories is now accessible from Settings > General (moved from main menu)
 *
 * PRD Reference: Section 4.17 - Settings & Preferences
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToOnboarding: () -> Unit,
    onNavigateToBackupRestore: () -> Unit,
    onNavigateToCategoryManagement: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    // Don't save scroll position - always start at top when navigating to Settings
    val scrollState = remember { ScrollState(initial = 0) }

    // Track lifecycle for debugging
    DisposableEffect(Unit) {
        Log.d(TAG, "SettingsScreen composed")
        onDispose {
            Log.d(TAG, "SettingsScreen disposed")
        }
    }

    // Dialog states
    var showTaxPeriodDialog by remember { mutableStateOf(false) }
    var showTransactionTypeDialog by remember { mutableStateOf(false) }
    var showAddButtonPositionDialog by remember { mutableStateOf(false) }
    var showReminderFrequencyDialog by remember { mutableStateOf(false) }
    var showRetentionPeriodDialog by remember { mutableStateOf(false) }
    var showUnavailableDialog by remember { mutableStateOf(false) }

    // Setup restart callback for ViewModel to use
    LaunchedEffect(Unit) {
        viewModel.setRestartCallback {
            // Restart the entire app to trigger MainActivity's onboarding check
            val activity = context as? Activity
            activity?.let { act ->
                // Create intent to restart at the root activity
                val intent = act.packageManager.getLaunchIntentForPackage(act.packageName)
                intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                
                // Start the app fresh
                act.startActivity(intent)
                
                // Kill the current process to ensure clean restart
                act.finish()
                Process.killProcess(Process.myPid())
                exitProcess(0)
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("Settings") }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
        ) {
            // App Info Section
            AppInfoSection(
                appVersion = uiState.appVersion,
                onDonateClick = {
                    // Open donation link
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://buymeacoffee.com/ossdev"))
                    context.startActivity(intent)
                }
            )

            HorizontalDivider()

            // Tax & Currency Section
            SettingsSection(title = "Tax & Currency") {
                SettingsItem(
                    icon = Icons.Default.DateRange,
                    title = "Tax Period",
                    subtitle = viewModel.formatTaxPeriodForDisplay(),
                    onClick = { showTaxPeriodDialog = true }
                )

                SettingsItem(
                    icon = Icons.Default.History,
                    title = "Retention Period",
                    subtitle = "${uiState.dataRetentionYears} years",
                    onClick = { showRetentionPeriodDialog = true }
                )
            }

            HorizontalDivider()

            // General Section
            SettingsSection(title = "General") {
                SettingsItem(
                    icon = Icons.Default.Category,
                    title = "Manage Categories",
                    subtitle = "Add, edit, or delete categories",
                    onClick = onNavigateToCategoryManagement
                )

                SettingsItem(
                    icon = Icons.Default.SwapVert,
                    title = "Default Transaction Type",
                    subtitle = uiState.defaultTransactionType.replaceFirstChar { it.uppercase() },
                    onClick = { showTransactionTypeDialog = true }
                )

                SettingsItem(
                    icon = Icons.Default.Add,
                    title = "Add Button Position",
                    subtitle = when (uiState.addButtonPosition) {
                        "top" -> "Top bar only"
                        "fab" -> "Bottom FAB only"
                        "both" -> "Both (top + FAB)"
                        else -> "Bottom FAB only"
                    },
                    onClick = { showAddButtonPositionDialog = true }
                )

                SettingsSwitchItem(
                    icon = Icons.Default.Palette,
                    title = "Dynamic Color Support",
                    subtitle = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                        "Use wallpaper colors (Material You)"
                    } else {
                        "Requires Android 12+"
                    },
                    checked = uiState.dynamicColorEnabled,
                    onCheckedChange = { viewModel.setDynamicColorEnabled(it) }
                )
            }

            HorizontalDivider()

            // Notifications Section
            SettingsSection(title = "Notifications") {
                SettingsSwitchItem(
                    icon = Icons.Default.Notifications,
                    title = "Enable Notifications",
                    subtitle = "Receive reminders and alerts",
                    checked = uiState.notificationsEnabled,
                    onCheckedChange = { viewModel.setNotificationsEnabled(it) }
                )

                if (uiState.notificationsEnabled) {
                    SettingsSwitchItem(
                        icon = Icons.Default.Alarm,
                        title = "Transaction Reminders",
                        subtitle = "Daily or weekly reminders to log transactions",
                        checked = uiState.transactionReminderEnabled,
                        onCheckedChange = { viewModel.setTransactionReminderEnabled(it) }
                    )

                    if (uiState.transactionReminderEnabled) {
                        SettingsItem(
                            icon = Icons.Default.Schedule,
                            title = "Reminder Frequency",
                            subtitle = uiState.transactionReminderFrequency.replaceFirstChar { it.uppercase() },
                            onClick = { showReminderFrequencyDialog = true }
                        )
                    }
                }
            }

            HorizontalDivider()

            // OCR Settings Section
            SettingsSection(title = "OCR Settings") {
                SettingsSwitchItem(
                    icon = Icons.Default.DocumentScanner,
                    title = "Enable OCR",
                    subtitle = "Automatically extract data from receipts",
                    checked = uiState.ocrEnabled,
                    onCheckedChange = { viewModel.setOcrEnabled(it) }
                )
            }

            HorizontalDivider()

            // Manage Your Data Section
            SettingsSection(title = "Manage Your Data") {
                SettingsItem(
                    icon = Icons.Default.Archive,
                    title = "Backup & Restore",
                    subtitle = "Download or upload your data",
                    onClick = onNavigateToBackupRestore
                )

                SettingsItem(
                    icon = Icons.Default.DeleteForever,
                    title = "Reset App",
                    subtitle = "Delete all data (dangerous)",
                    onClick = { viewModel.showResetAppDialog() },
                    isDestructive = true
                )
            }

            // Bottom spacing
            Spacer(modifier = Modifier.height(Spacing.medium))
        }
    }

    // Reset App Confirmation Dialog
    if (uiState.showResetAppDialog) {
        ResetAppDialog(
            onConfirm = { viewModel.resetApp() },
            onDismiss = { viewModel.hideResetAppDialog() }
        )
    }

    // Tax Period Selection Dialog
    if (showTaxPeriodDialog) {
        TaxPeriodPickerDialog(
            startMonth = uiState.taxPeriodStartMonth,
            startDay = uiState.taxPeriodStartDay,
            endMonth = uiState.taxPeriodEndMonth,
            endDay = uiState.taxPeriodEndDay,
            onSave = { startMonth, startDay, endMonth, endDay ->
                viewModel.setTaxPeriod(startMonth, startDay, endMonth, endDay)
                showTaxPeriodDialog = false
            },
            onDismiss = { showTaxPeriodDialog = false }
        )
    }



    // Transaction Type Selection Dialog
    if (showTransactionTypeDialog) {
        TransactionTypePickerDialog(
            selectedType = uiState.defaultTransactionType,
            onTypeSelected = { type ->
                viewModel.setDefaultTransactionType(type)
                showTransactionTypeDialog = false
            },
            onDismiss = { showTransactionTypeDialog = false }
        )
    }

    // Add Button Position Selection Dialog
    if (showAddButtonPositionDialog) {
        AddButtonPositionPickerDialog(
            selectedPosition = uiState.addButtonPosition,
            onPositionSelected = { position ->
                viewModel.setAddButtonPosition(position)
                showAddButtonPositionDialog = false
            },
            onDismiss = { showAddButtonPositionDialog = false }
        )
    }

    // Reminder Frequency Selection Dialog
    if (showReminderFrequencyDialog) {
        ReminderFrequencyPickerDialog(
            selectedFrequency = uiState.transactionReminderFrequency,
            onFrequencySelected = { frequency ->
                viewModel.setTransactionReminderFrequency(frequency)
                showReminderFrequencyDialog = false
            },
            onDismiss = { showReminderFrequencyDialog = false }
        )
    }

    // Retention Period Selection Dialog
    if (showRetentionPeriodDialog) {
        RetentionPeriodPickerDialog(
            selectedYears = uiState.dataRetentionYears,
            onYearsSelected = { years ->
                viewModel.setDataRetentionYears(years)
                showRetentionPeriodDialog = false
            },
            onDismiss = { showRetentionPeriodDialog = false }
        )
    }
}

/**
 * Settings section with title.
 */
@Composable
private fun SettingsSection(
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

/**
 * Settings item with icon, title, subtitle, and optional click action.
 */
@Composable
private fun SettingsItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)?,
    isDestructive: Boolean = false
) {
    val modifier = if (onClick != null) {
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.medium, vertical = Spacing.mediumSmall)
    } else {
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.medium, vertical = Spacing.mediumSmall)
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isDestructive) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(Spacing.large)
        )

        Spacer(modifier = Modifier.width(Spacing.medium))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isDestructive) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (onClick != null) {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(Spacing.mediumLarge)
            )
        }
    }
}

/**
 * Settings item with switch.
 */
@Composable
private fun SettingsSwitchItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.medium, vertical = Spacing.small),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(Spacing.large)
        )

        Spacer(modifier = Modifier.width(Spacing.medium))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

/**
 * Reset app confirmation dialog.
 */
@Composable
private fun ResetAppDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    var confirmationText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = { Text("Reset App") },
        text = {
            Column {
                Text(
                    "This will permanently delete ALL data and return the app to its initial state:",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(Spacing.small))
                Text("• All transactions (income + expenses)")
                Text("• All categories (seeded + custom)")
                Text("• All receipt files")
                Text("• All settings and preferences")
                Spacer(modifier = Modifier.height(Spacing.small))
                Text(
                    "The app will return to the onboarding screen as if freshly installed.",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(Spacing.medium))
                Text(
                    "Type DELETE EVERYTHING to confirm:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(Spacing.small))
                OutlinedTextField(
                    value = confirmationText,
                    onValueChange = { confirmationText = it },
                    placeholder = { Text("DELETE EVERYTHING") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = confirmationText == "DELETE EVERYTHING",
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Reset App")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Calculate exact days in a tax period (start month/day to end month/day).
 * For tax years, the end date is the LAST day of the tax year.
 *
 * Examples:
 * - 6 Apr to 5 Apr (next year) = 365 days (UK tax year)
 * - 1 Jan to 31 Dec = 365 days (US tax year)
 * - 1 Jul to 30 Jun (next year) = 365 days (AU tax year)
 * - 5 Apr to 5 Apr = 365 days (interpreted as full year cycle)
 */
private fun calculatePeriodDays(startMonth: Int, startDay: Int, endMonth: Int, endDay: Int): Int {
    val daysInMonths = listOf(31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)

    // Calculate day of year for start and end (non-leap year, 1-based)
    val startDayOfYear = daysInMonths.take(startMonth - 1).sum() + startDay
    val endDayOfYear = daysInMonths.take(endMonth - 1).sum() + endDay

    // Special case: same date means full year (365 days)
    if (startDayOfYear == endDayOfYear) {
        return 365
    }

    // Calculate period length
    val periodDays = if (endDayOfYear > startDayOfYear) {
        // Period within same year (e.g., 1 Jan to 31 Dec)
        endDayOfYear - startDayOfYear + 1
    } else {
        // Period spans year boundary (e.g., 6 Apr to 5 Apr next year)
        365 - startDayOfYear + endDayOfYear + 1
    }

    return periodDays
}

/**
 * Tax period picker dialog with month/day dropdowns.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaxPeriodPickerDialog(
    startMonth: Int,
    startDay: Int,
    endMonth: Int,
    endDay: Int,
    onSave: (Int, Int, Int, Int) -> Unit,
    onDismiss: () -> Unit
) {
    // Local state for editing
    var localStartMonth by remember { mutableStateOf(startMonth) }
    var localStartDay by remember { mutableStateOf(startDay) }
    var localEndMonth by remember { mutableStateOf(endMonth) }
    var localEndDay by remember { mutableStateOf(endDay) }

    // Month names
    val monthNames = listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December"
    )

    // Days in month
    fun daysInMonth(month: Int): Int {
        return when (month) {
            2 -> 29
            4, 6, 9, 11 -> 30
            else -> 31
        }
    }

    // Dropdown states
    var startMonthExpanded by remember { mutableStateOf(false) }
    var startDayExpanded by remember { mutableStateOf(false) }
    var endMonthExpanded by remember { mutableStateOf(false) }
    var endDayExpanded by remember { mutableStateOf(false) }

    // Validation state
    val periodDays = calculatePeriodDays(localStartMonth, localStartDay, localEndMonth, localEndDay)
    val isExactlyOneYear = periodDays == 365 || periodDays == 366
    var showPeriodWarning by remember { mutableStateOf(false) }

    // Warning dialog
    if (showPeriodWarning) {
        AlertDialog(
            onDismissRequest = { showPeriodWarning = false },
            title = { Text("Invalid Tax Period") },
            text = {
                Text(
                    "Your tax period is $periodDays days long, but a tax year must be exactly 365 days (or 366 days in a leap year).\n\n" +
                    "Please adjust the dates to create a valid tax year, or if this is intentional, you can continue anyway."
                )
            },
            confirmButton = {
                Button(onClick = {
                    showPeriodWarning = false
                    onSave(localStartMonth, localStartDay, localEndMonth, localEndDay)
                }) {
                    Text("Continue Anyway")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPeriodWarning = false }) {
                    Text("Go Back")
                }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set Tax Period") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    "Set your fiscal year start and end dates",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(Spacing.medium))

                // Start Period
                Text(
                    "Period Start",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(Spacing.small))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.small)
                ) {
                    // Start Month
                    ExposedDropdownMenuBox(
                        expanded = startMonthExpanded,
                        onExpandedChange = { startMonthExpanded = !startMonthExpanded },
                        modifier = Modifier.weight(2f)
                    ) {
                        OutlinedTextField(
                            value = monthNames[localStartMonth - 1],
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Month") },
                            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true),
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = startMonthExpanded) }
                        )
                        ExposedDropdownMenu(
                            expanded = startMonthExpanded,
                            onDismissRequest = { startMonthExpanded = false }
                        ) {
                            monthNames.forEachIndexed { index, name ->
                                DropdownMenuItem(
                                    text = { Text(name) },
                                    onClick = {
                                        val newMonth = index + 1
                                        localStartMonth = newMonth
                                        val maxDay = daysInMonth(newMonth)
                                        if (localStartDay > maxDay) localStartDay = maxDay
                                        startMonthExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Start Day
                    ExposedDropdownMenuBox(
                        expanded = startDayExpanded,
                        onExpandedChange = { startDayExpanded = !startDayExpanded },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = localStartDay.toString(),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Day") },
                            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true),
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = startDayExpanded) }
                        )
                        ExposedDropdownMenu(
                            expanded = startDayExpanded,
                            onDismissRequest = { startDayExpanded = false }
                        ) {
                            (1..daysInMonth(localStartMonth)).forEach { day ->
                                DropdownMenuItem(
                                    text = { Text(day.toString()) },
                                    onClick = {
                                        localStartDay = day
                                        startDayExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.medium))

                // End Period
                Text(
                    "Period End",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(Spacing.small))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.small)
                ) {
                    // End Month
                    ExposedDropdownMenuBox(
                        expanded = endMonthExpanded,
                        onExpandedChange = { endMonthExpanded = !endMonthExpanded },
                        modifier = Modifier.weight(2f)
                    ) {
                        OutlinedTextField(
                            value = monthNames[localEndMonth - 1],
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Month") },
                            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true),
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = endMonthExpanded) }
                        )
                        ExposedDropdownMenu(
                            expanded = endMonthExpanded,
                            onDismissRequest = { endMonthExpanded = false }
                        ) {
                            monthNames.forEachIndexed { index, name ->
                                DropdownMenuItem(
                                    text = { Text(name) },
                                    onClick = {
                                        val newMonth = index + 1
                                        localEndMonth = newMonth
                                        val maxDay = daysInMonth(newMonth)
                                        if (localEndDay > maxDay) localEndDay = maxDay
                                        endMonthExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // End Day
                    ExposedDropdownMenuBox(
                        expanded = endDayExpanded,
                        onExpandedChange = { endDayExpanded = !endDayExpanded },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = localEndDay.toString(),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Day") },
                            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true),
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = endDayExpanded) }
                        )
                        ExposedDropdownMenu(
                            expanded = endDayExpanded,
                            onDismissRequest = { endDayExpanded = false }
                        ) {
                            (1..daysInMonth(localEndMonth)).forEach { day ->
                                DropdownMenuItem(
                                    text = { Text(day.toString()) },
                                    onClick = {
                                        localEndDay = day
                                        endDayExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (!isExactlyOneYear) {
                        showPeriodWarning = true
                    } else {
                        onSave(localStartMonth, localStartDay, localEndMonth, localEndDay)
                    }
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Transaction Type Picker Dialog.
 */
@Composable
private fun TransactionTypePickerDialog(
    selectedType: String,
    onTypeSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Default Transaction Type") },
        text = {
            Column {
                Text(
                    "Select the default type when adding new transactions:",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(Spacing.medium))

                // Expense option
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onTypeSelected("expense") }
                        .padding(vertical = Spacing.mediumSmall),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selectedType == "expense",
                        onClick = { onTypeSelected("expense") }
                    )
                    Spacer(modifier = Modifier.width(Spacing.small))
                    Text("Expense", style = MaterialTheme.typography.bodyLarge)
                }

                // Income option
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onTypeSelected("income") }
                        .padding(vertical = Spacing.mediumSmall),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selectedType == "income",
                        onClick = { onTypeSelected("income") }
                    )
                    Spacer(modifier = Modifier.width(Spacing.small))
                    Text("Income", style = MaterialTheme.typography.bodyLarge)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

/**
 * Reminder Frequency Picker Dialog.
 */
@Composable
private fun ReminderFrequencyPickerDialog(
    selectedFrequency: String,
    onFrequencySelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reminder Frequency") },
        text = {
            Column {
                Text(
                    "How often should we remind you to log transactions?",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(Spacing.medium))

                // Daily option
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onFrequencySelected("daily") }
                        .padding(vertical = Spacing.mediumSmall),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selectedFrequency == "daily",
                        onClick = { onFrequencySelected("daily") }
                    )
                    Spacer(modifier = Modifier.width(Spacing.small))
                    Text("Daily", style = MaterialTheme.typography.bodyLarge)
                }

                // Weekly option
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onFrequencySelected("weekly") }
                        .padding(vertical = Spacing.mediumSmall),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selectedFrequency == "weekly",
                        onClick = { onFrequencySelected("weekly") }
                    )
                    Spacer(modifier = Modifier.width(Spacing.small))
                    Text("Weekly", style = MaterialTheme.typography.bodyLarge)
                }

                // Monthly option
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onFrequencySelected("monthly") }
                        .padding(vertical = Spacing.mediumSmall),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selectedFrequency == "monthly",
                        onClick = { onFrequencySelected("monthly") }
                    )
                    Spacer(modifier = Modifier.width(Spacing.small))
                    Text("Monthly", style = MaterialTheme.typography.bodyLarge)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

/**
 * Retention Period Picker Dialog.
 */
@Composable
private fun RetentionPeriodPickerDialog(
    selectedYears: Int,
    onYearsSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Retention Period") },
        text = {
            Column {
                Text(
                    "How many years should data be kept before archiving?",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(Spacing.medium))

                // Options: 5, 6, 7, 8, 9, 10 years
                listOf(5, 6, 7, 8, 9, 10).forEach { years ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onYearsSelected(years) }
                            .padding(vertical = Spacing.mediumSmall),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = years == selectedYears,
                            onClick = { onYearsSelected(years) }
                        )
                        Spacer(modifier = Modifier.width(Spacing.small))
                        Text("$years years", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

/**
 * Add Button Position Picker Dialog.
 */
@Composable
private fun AddButtonPositionPickerDialog(
    selectedPosition: String,
    onPositionSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Button Position") },
        text = {
            Column {
                Text(
                    "Choose where to show the button for adding new transactions:",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(Spacing.medium))

                // Top bar option
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPositionSelected("top") }
                        .padding(vertical = Spacing.mediumSmall),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selectedPosition == "top",
                        onClick = { onPositionSelected("top") }
                    )
                    Spacer(modifier = Modifier.width(Spacing.small))
                    Column {
                        Text("Top bar only", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "+ button next to title",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // FAB option (default)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPositionSelected("fab") }
                        .padding(vertical = Spacing.mediumSmall),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selectedPosition == "fab",
                        onClick = { onPositionSelected("fab") }
                    )
                    Spacer(modifier = Modifier.width(Spacing.small))
                    Column {
                        Text("Bottom FAB only (default)", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Floating button at bottom right",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Both option
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPositionSelected("both") }
                        .padding(vertical = Spacing.mediumSmall),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selectedPosition == "both",
                        onClick = { onPositionSelected("both") }
                    )
                    Spacer(modifier = Modifier.width(Spacing.small))
                    Column {
                        Text("Both (top + FAB)", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Show both buttons",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

/**
 * App info section with icon, name, version, and donate button.
 */
@Composable
private fun AppInfoSection(
    appVersion: String,
    onDonateClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = Spacing.medium,
                end = Spacing.medium,
                top = Spacing.extraExtraSmall,
                bottom = Spacing.mediumLarge
            ),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.medium)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // App icon - rounded corners
                Image(
                    painter = painterResource(id = R.drawable.app_icon),
                    contentDescription = "HappyTaxes Logo",
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CornerRadius.medium),
                    contentScale = ContentScale.Fit
                )

                Spacer(modifier = Modifier.width(Spacing.medium))

                // App name and version
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "HappyTaxes",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Version $appVersion",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Button(
                    onClick = onDonateClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = androidx.compose.ui.graphics.Color(0xFFFDD835),
                        contentColor = androidx.compose.ui.graphics.Color.Black
                    ),
                    shape = MaterialTheme.shapes.medium,
                    border = androidx.compose.foundation.BorderStroke(
                        width = 1.dp,
                        color = androidx.compose.ui.graphics.Color.Black
                    )
                ) {
                    Text(
                        text = "Donate",
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Description text inside the card
            Spacer(modifier = Modifier.height(Spacing.small))
            Text(
                text = "Proper bookkeeping. Private. Free. Consider donating to support its development if you find it useful.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

