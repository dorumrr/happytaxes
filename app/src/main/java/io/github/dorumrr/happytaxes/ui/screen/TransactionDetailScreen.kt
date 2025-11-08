package io.github.dorumrr.happytaxes.ui.screen

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import io.github.dorumrr.happytaxes.ui.theme.Spacing
import io.github.dorumrr.happytaxes.ui.theme.Dimensions
import io.github.dorumrr.happytaxes.ui.theme.Alpha
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.rememberNavController
import io.github.dorumrr.happytaxes.domain.model.Category
import io.github.dorumrr.happytaxes.domain.model.TransactionType
import io.github.dorumrr.happytaxes.ui.component.ReceiptThumbnailList
import io.github.dorumrr.happytaxes.ui.util.rememberDebouncedNavigation
import io.github.dorumrr.happytaxes.ui.viewmodel.CategoryViewModel
import io.github.dorumrr.happytaxes.ui.viewmodel.OcrState
import io.github.dorumrr.happytaxes.ui.viewmodel.TransactionDetailViewModel
import io.github.dorumrr.happytaxes.util.CurrencyFormatter
import io.github.dorumrr.happytaxes.util.DateFormats
import java.time.LocalDate
import androidx.compose.foundation.layout.imePadding
import androidx.compose.ui.Alignment

/**
 * Transaction detail screen for creating/editing transactions.
 * 
 * Features:
 * - Form with all transaction fields
 * - Date picker
 * - Category picker
 * - Amount input with validation
 * - Receipt attachment (Phase 2)
 * - Form validation
 * - Save/Cancel actions
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDetailScreen(
    receiptUri: Uri? = null,
    deleteReceiptIndex: Int? = null,
    onNavigateBack: (String?) -> Unit, // Pass transaction ID for scrolling (null if deleted/discarded)
    onNavigateToCamera: () -> Unit = {},
    onNavigateToReceiptViewer: (List<String>, Int, String?) -> Unit = { _, _, _ -> },
    onReceiptHandled: () -> Unit = {},
    onDeleteReceiptHandled: () -> Unit = {},
    viewModel: TransactionDetailViewModel = hiltViewModel(),
    categoryViewModel: CategoryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()
    val focusManager = LocalFocusManager.current

    var showDatePicker by remember { mutableStateOf(false) }
    var showCategoryPicker by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showOcrErrorDialog by remember { mutableStateOf(false) }
    var ocrErrorMessage by remember { mutableStateOf("") }
    var showRemoveReceiptDialog by remember { mutableStateOf(false) }
    var receiptIndexToRemove by remember { mutableStateOf<Int?>(null) }

    // Interaction source for date field clickable overlay (hoisted to prevent recreation)
    val dateFieldInteractionSource = remember { MutableInteractionSource() }

    // Track last receipt URI to trigger OCR only once
    var lastReceiptUri by remember { mutableStateOf<Uri?>(null) }

    // Handle receipt URI from camera/gallery
    LaunchedEffect(receiptUri) {
        if (receiptUri != null && receiptUri != lastReceiptUri) {
            lastReceiptUri = receiptUri
            // CRITICAL FIX: OCR FIRST on original high-quality image, THEN compress for storage
            // This ensures OCR runs on the best quality image before compression artifacts are introduced
            viewModel.processReceiptOcr(receiptUri)
            // Compress and save receipt (after OCR completes)
            viewModel.onReceiptCaptured(receiptUri)
            onReceiptHandled()
        }
    }

    // Handle receipt deletion from viewer
    LaunchedEffect(deleteReceiptIndex) {
        if (deleteReceiptIndex != null) {
            val pathToRemove = uiState.receiptPaths.getOrNull(deleteReceiptIndex)
            if (pathToRemove != null) {
                // Remove from UI state (tracked for cleanup on save/discard)
                viewModel.onReceiptRemoved(pathToRemove)
            }
            onDeleteReceiptHandled()
        }
    }

    // Navigate back on save success - pass transaction ID for scrolling
    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) {
            onNavigateBack(viewModel.getTransactionId())
        }
    }

    // Navigate back on delete success - don't scroll to deleted transaction
    LaunchedEffect(uiState.isDeleted) {
        if (uiState.isDeleted) {
            onNavigateBack(null)
        }
    }

    // Navigate back on discard - don't scroll if discarded
    LaunchedEffect(uiState.isDiscarded) {
        if (uiState.isDiscarded) {
            onNavigateBack(null)
        }
    }

    // Handle OCR state changes
    LaunchedEffect(uiState.ocrState) {
        when (val state = uiState.ocrState) {
            is OcrState.Error -> {
                ocrErrorMessage = state.message
                showOcrErrorDialog = true
            }
            else -> {
                // Success or Processing - no dialog needed
            }
        }
    }

    // Handle OCR retake request
    LaunchedEffect(uiState.ocrRetakeRequested) {
        if (uiState.ocrRetakeRequested) {
            viewModel.resetOcrRetakeFlag()
            onNavigateToCamera()
        }
    }

    // Handle back button press with unsaved changes check
    val handleBackPress = {
        if (viewModel.hasUnsavedChanges()) {
            showDiscardDialog = true
        } else {
            // Manual back navigation - don't scroll
            onNavigateBack(null)
        }
    }

    // Debounced navigation to prevent double-clicks
    val debouncedBack = rememberDebouncedNavigation(onNavigate = handleBackPress)

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(if (uiState.isEditing) "Edit Transaction" else "Add Transaction") },
                navigationIcon = {
                    IconButton(onClick = debouncedBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding() // Add padding when keyboard appears
                .verticalScroll(scrollState)
                .padding(Spacing.medium),
            verticalArrangement = Arrangement.spacedBy(Spacing.medium)
        ) {
            // Transaction type (with custom colors: dark green for Income, dark red for Expense)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.small)
            ) {
                FilterChip(
                    selected = uiState.type == TransactionType.INCOME,
                    onClick = { viewModel.onTypeChanged(TransactionType.INCOME) },
                    label = { Text("Income", style = MaterialTheme.typography.titleMedium) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onTertiaryContainer
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = uiState.type == TransactionType.INCOME,
                        borderColor = MaterialTheme.colorScheme.outline,
                        selectedBorderColor = MaterialTheme.colorScheme.tertiary,
                        borderWidth = Dimensions.strokeThin,
                        selectedBorderWidth = Dimensions.strokeThin
                    ),
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = uiState.type == TransactionType.EXPENSE,
                    onClick = { viewModel.onTypeChanged(TransactionType.EXPENSE) },
                    label = { Text("Expense", style = MaterialTheme.typography.titleMedium) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.errorContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onErrorContainer
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = uiState.type == TransactionType.EXPENSE,
                        borderColor = MaterialTheme.colorScheme.outline,
                        selectedBorderColor = MaterialTheme.colorScheme.error,
                        borderWidth = Dimensions.strokeThin,
                        selectedBorderWidth = Dimensions.strokeThin
                    ),
                    modifier = Modifier.weight(1f)
                )
            }
            
            // Date - wrapped in Box to intercept clicks before TextField
            Box(
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = uiState.date.format(DateFormats.DISPLAY_DATE),
                    onValueChange = {},
                    label = { Text("Date") },
                    readOnly = true,
                    trailingIcon = {
                        Icon(Icons.Default.DateRange, "Pick date")
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                // Invisible clickable overlay on top
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clickable(
                            onClick = { showDatePicker = true },
                            indication = null,
                            interactionSource = dateFieldInteractionSource
                        )
                )
            }

            // Receipt/Attachment section (Phase 2)
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (uiState.type == TransactionType.EXPENSE && uiState.receiptPaths.isEmpty()) {
                        MaterialTheme.colorScheme.errorContainer
                    } else if (uiState.receiptPaths.isNotEmpty()) {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(Spacing.medium),
                    verticalArrangement = Arrangement.spacedBy(Spacing.mediumSmall)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (uiState.type == TransactionType.EXPENSE) {
                                if (uiState.receiptPaths.isEmpty()) "Receipt Required"
                                else "${uiState.receiptPaths.size} Receipt(s) Attached"
                            } else {
                                if (uiState.receiptPaths.isEmpty()) "Attachments (Optional)"
                                else "${uiState.receiptPaths.size} Attachment(s)"
                            },
                            style = MaterialTheme.typography.titleSmall
                        )
                        Icon(
                            if (uiState.type == TransactionType.EXPENSE && uiState.receiptPaths.isEmpty()) Icons.Default.Warning
                            else Icons.Default.AttachFile,
                            contentDescription = null
                        )
                    }

                    if (uiState.type == TransactionType.EXPENSE && uiState.receiptPaths.isEmpty()) {
                        Text(
                            text = "Expenses must have at least one receipt. Will be saved as draft.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else if (uiState.type == TransactionType.INCOME && uiState.receiptPaths.isEmpty()) {
                        Text(
                            text = "Attach supporting documents (invoices, contracts, etc.)",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    if (uiState.receiptPaths.isNotEmpty()) {
                            // Receipt thumbnails
                            ReceiptThumbnailList(
                                receiptPaths = uiState.receiptPaths,
                                onReceiptClick = { index ->
                                    // Navigate to receipt viewer with transaction ID for edit mode
                                    // (allows delete button)
                                    onNavigateToReceiptViewer(
                                        uiState.receiptPaths,
                                        index,
                                        viewModel.getTransactionId()
                                    )
                                },
                                onReceiptRemove = { index ->
                                    // Show confirmation dialog
                                    receiptIndexToRemove = index
                                    showRemoveReceiptDialog = true
                                }
                            )
                        }

                        // Show loading indicator while compressing
                        if (uiState.isCompressingReceipt) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = Spacing.small),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(Spacing.mediumLarge),
                                    strokeWidth = Dimensions.strokeStandard
                                )
                                Spacer(Modifier.width(Spacing.small))
                                Text(
                                    text = "Compressing image...",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }

                    Button(
                        onClick = onNavigateToCamera,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !uiState.isCompressingReceipt
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null)
                        Spacer(Modifier.width(Spacing.small))
                        Text(
                            if (uiState.type == TransactionType.EXPENSE) {
                                if (uiState.receiptPaths.isEmpty()) "Take Receipt Photo" else "Add Another Receipt"
                            } else {
                                if (uiState.receiptPaths.isEmpty()) "Attach Document" else "Add Another Attachment"
                            }
                        )
                    }
                }
            }

            // Category (grouped dropdown with Income/Expense sections)
            ExposedDropdownMenuBox(
                expanded = showCategoryPicker,
                onExpandedChange = { showCategoryPicker = it }
            ) {
                OutlinedTextField(
                    value = uiState.category,
                    onValueChange = {},
                    label = { Text("Category") },
                    readOnly = true,
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = showCategoryPicker)
                    },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                    isError = uiState.validationErrors.any { it.contains("Category") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                )

                ExposedDropdownMenu(
                    expanded = showCategoryPicker,
                    onDismissRequest = { showCategoryPicker = false }
                ) {
                    // Get all categories
                    val allCategories by categoryViewModel.allCategories.collectAsState()

                    // Filter by transaction type and sort alphabetically
                    val incomeCategories = allCategories.filter { it.type == TransactionType.INCOME }.sortedBy { it.name }
                    val expenseCategories = allCategories.filter { it.type == TransactionType.EXPENSE }.sortedBy { it.name }

                    // Show only relevant categories based on transaction type
                    if (uiState.type == TransactionType.INCOME && incomeCategories.isNotEmpty()) {
                        // Income header
                        DropdownMenuItem(
                            text = {
                                Text(
                                    "INCOME",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                            },
                            onClick = {},
                            enabled = false
                        )
                        // Income categories
                        incomeCategories.forEach { category ->
                            DropdownMenuItem(
                                text = { Text("  ${category.name}") },
                                onClick = {
                                    viewModel.onCategoryChanged(category.name)
                                    showCategoryPicker = false
                                }
                            )
                        }
                    } else if (uiState.type == TransactionType.EXPENSE && expenseCategories.isNotEmpty()) {
                        // Expense header
                        DropdownMenuItem(
                            text = {
                                Text(
                                    "EXPENSES",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            },
                            onClick = {},
                            enabled = false
                        )
                        // Expense categories
                        expenseCategories.forEach { category ->
                            DropdownMenuItem(
                                text = { Text("  ${category.name}") },
                                onClick = {
                                    viewModel.onCategoryChanged(category.name)
                                    showCategoryPicker = false
                                }
                            )
                        }
                    }
                }
            }

            // Amount
            val amountFocusRequester = remember { FocusRequester() }
            OutlinedTextField(
                value = uiState.amountInput,
                onValueChange = viewModel::onAmountChanged,
                label = { Text("Amount (${uiState.baseCurrency})") },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        focusManager.clearFocus()  // This will trigger onFocusChanged
                    }
                ),
                isError = uiState.validationErrors.any { it.contains("Amount") },
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { focusState ->
                        if (!focusState.isFocused) {
                            viewModel.formatAmountOnFocusLoss()
                        }
                    }
            )

            // Description (multi-line textarea)
            OutlinedTextField(
                value = uiState.description,
                onValueChange = viewModel::onDescriptionChanged,
                label = { Text("Transaction Description (optional)") },
                minLines = 3,
                maxLines = 5,
                modifier = Modifier.fillMaxWidth()
            )

            // ACTION BUTTONS - Delete (if editing) and Save
            if (uiState.isEditing) {
                // Two buttons: Delete (left) and Save (right)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = Spacing.small),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.medium)
                ) {
                    // Delete Button
                    OutlinedButton(
                        onClick = { showDeleteDialog = true },
                        enabled = !uiState.isSaving && !uiState.isDeleting,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        if (uiState.isDeleting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(Spacing.mediumLarge),
                                color = MaterialTheme.colorScheme.error,
                                strokeWidth = Dimensions.strokeStandard
                            )
                        } else {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = null,
                                modifier = Modifier.size(Spacing.mediumLarge)
                            )
                            Spacer(Modifier.width(Spacing.extraSmall))
                            Text("Delete")
                        }
                    }

                    // Save Button
                    Button(
                        onClick = viewModel::saveTransaction,
                        enabled = uiState.isValid && !uiState.isSaving && !uiState.isDeleting,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (uiState.isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(Spacing.mediumLarge),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = Dimensions.strokeStandard
                            )
                        } else {
                            Icon(
                                Icons.Default.Save,
                                contentDescription = null,
                                modifier = Modifier.size(Spacing.mediumLarge)
                            )
                            Spacer(Modifier.width(Spacing.extraSmall))
                            Text("Save")
                        }
                    }
                }
            } else {
                // Single Save button (full width) when adding new transaction
                Button(
                    onClick = viewModel::saveTransaction,
                    enabled = uiState.isValid && !uiState.isSaving,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (uiState.isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(Spacing.mediumLarge),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = Dimensions.strokeStandard
                        )
                    } else {
                        Icon(
                            Icons.Default.Save,
                            contentDescription = null,
                            modifier = Modifier.size(Spacing.mediumLarge)
                        )
                        Spacer(Modifier.width(Spacing.small))
                        Text("Save")
                    }
                }
            }

            // Notes (moved after action buttons)
            OutlinedTextField(
                value = uiState.notes,
                onValueChange = viewModel::onNotesChanged,
                label = { Text("Additional Notes (optional)") },
                minLines = 3,
                maxLines = 5,
                modifier = Modifier.fillMaxWidth()
            )

            // Error message
            uiState.error?.let { error ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(Spacing.medium)
                    )
                }
            }
        }
    }

    // Date picker dialog
    if (showDatePicker) {
        DatePickerDialog(
            currentDate = uiState.date,
            onDateSelected = { date ->
                viewModel.onDateChanged(date)
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false }
        )
    }

    // Discard confirmation dialog
    if (showDiscardDialog) {
        DiscardConfirmationDialog(
            isEditing = uiState.isEditing,
            message = viewModel.getDiscardMessage(),
            onConfirm = {
                showDiscardDialog = false
                viewModel.discardChanges()
            },
            onDismiss = {
                showDiscardDialog = false
            }
        )
    }

    // Delete transaction confirmation dialog
    if (showDeleteDialog) {
        DeleteTransactionDialog(
            onConfirm = {
                showDeleteDialog = false
                viewModel.deleteTransaction()
            },
            onDismiss = {
                showDeleteDialog = false
            }
        )
    }

    // OCR error dialog
    if (showOcrErrorDialog) {
        OcrErrorDialog(
            message = ocrErrorMessage,
            onRetakePhoto = {
                showOcrErrorDialog = false
                viewModel.retakePhoto()
            },
            onEnterManually = {
                showOcrErrorDialog = false
                viewModel.enterManually()
            },
            onCancel = {
                showOcrErrorDialog = false
                viewModel.cancelOcr()
            }
        )
    }

    // Remove receipt confirmation dialog
    if (showRemoveReceiptDialog && receiptIndexToRemove != null) {
        RemoveReceiptDialog(
            onConfirm = {
                val pathToRemove = uiState.receiptPaths[receiptIndexToRemove!!]
                viewModel.onReceiptRemoved(pathToRemove)
                showRemoveReceiptDialog = false
                receiptIndexToRemove = null
            },
            onDismiss = {
                showRemoveReceiptDialog = false
                receiptIndexToRemove = null
            }
        )
    }

    // OCR loading indicator
    if (uiState.ocrState is OcrState.Processing) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = Alpha.ALMOST_OPAQUE)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = Spacing.small)
            ) {
                Column(
                    modifier = Modifier.padding(Spacing.large),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Spacing.medium)
                ) {
                    CircularProgressIndicator()
                    Text(
                        text = "Extracting receipt data...",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "This may take a few seconds",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerDialog(
    currentDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = currentDate.toEpochDay() * 86400000
    )
    
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val date = LocalDate.ofEpochDay(millis / 86400000)
                        onDateSelected(date)
                    }
                }
            ) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    ) {
        DatePicker(state = datePickerState)
    }
}

/**
 * Delete transaction confirmation dialog.
 *
 * Shows when user attempts to delete a transaction.
 * Informs user about soft delete and 30-day recovery period.
 */
@Composable
private fun DeleteTransactionDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.Delete,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = {
            Text("Delete Transaction?")
        },
        text = {
            Text("This transaction will be moved to Recently Deleted. You can restore it within 30 days.")
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Delete")
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
 * Discard confirmation dialog.
 *
 * Shows when user tries to leave form with unsaved changes.
 * Warns about photo deletion if applicable.
 */
@Composable
private fun DiscardConfirmationDialog(
    isEditing: Boolean,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = {
            Text(if (isEditing) "Discard Changes?" else "Discard Transaction?")
        },
        text = {
            Text(message)
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Discard")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Keep Editing")
            }
        }
    )
}

/**
 * OCR error dialog.
 *
 * PRD Section 15.2: "Show dialog: '⚠️ OCR Failed - Could not extract data from receipt.
 * [Retake Photo] [Enter Manually] [Cancel]'"
 */
@Composable
private fun OcrErrorDialog(
    message: String,
    onRetakePhoto: () -> Unit,
    onEnterManually: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        icon = {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = {
            Text("⚠️ OCR Failed")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
                Text("Could not extract data from receipt.")
                if (message.isNotBlank()) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Spacing.small)
            ) {
                Button(
                    onClick = onRetakePhoto,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null)
                    Spacer(Modifier.width(Spacing.small))
                    Text("Retake Photo")
                }
                OutlinedButton(
                    onClick = onEnterManually,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null)
                    Spacer(Modifier.width(Spacing.small))
                    Text("Enter Manually")
                }
                TextButton(
                    onClick = onCancel,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cancel")
                }
            }
        }
    )
}


/**
 * Remove attachment confirmation dialog.
 *
 * PRD Section 4.15: Confirm before removing attachment from transaction
 * Generic terminology to work for both receipts (expenses) and attachments (income)
 */
@Composable
private fun RemoveReceiptDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.Delete,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = {
            Text("Remove Attachment?")
        },
        text = {
            Text("This attachment will be removed from the transaction. The file will be permanently deleted when you save.")
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Remove")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

