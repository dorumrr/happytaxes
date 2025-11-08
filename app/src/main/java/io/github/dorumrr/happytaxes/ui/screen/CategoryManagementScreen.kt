package io.github.dorumrr.happytaxes.ui.screen

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.dorumrr.happytaxes.ui.theme.Spacing
import kotlin.math.abs
import io.github.dorumrr.happytaxes.ui.theme.Dimensions
import io.github.dorumrr.happytaxes.ui.theme.Alpha
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.dorumrr.happytaxes.domain.model.Category
import io.github.dorumrr.happytaxes.domain.model.TransactionType
import io.github.dorumrr.happytaxes.ui.util.rememberDebouncedNavigation
import io.github.dorumrr.happytaxes.ui.viewmodel.CategoryViewModel
import io.github.dorumrr.happytaxes.util.CurrencyFormatter
import kotlinx.coroutines.launch
import java.math.BigDecimal

/**
 * Category Management screen.
 *
 * Features:
 * - List all categories (income and expense)
 * - Show usage statistics (transaction count, total amount)
 * - Add new categories
 * - Edit categories
 * - Delete categories (only if empty, permanent)
 *
 * PRD Reference: Section 4.14 - Category Management
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryManagementScreen(
    onNavigateBack: () -> Unit,
    viewModel: CategoryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val allCategories by viewModel.allCategories.collectAsState()
    val incomeCategories = allCategories.filter { it.type == TransactionType.INCOME }.sortedBy { it.name }
    val expenseCategories = allCategories.filter { it.type == TransactionType.EXPENSE }.sortedBy { it.name }
    val decimalSeparator by viewModel.decimalSeparator.collectAsState()
    val thousandSeparator by viewModel.thousandSeparator.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val deleteScope = rememberCoroutineScope()

    // Debounced navigation to prevent double-clicks
    val debouncedBack = rememberDebouncedNavigation(onNavigate = onNavigateBack)

    // Show success message
    LaunchedEffect(uiState.moveSuccess) {
        uiState.moveSuccess?.let { message ->
            scope.launch {
                snackbarHostState.showSnackbar(
                    message = message,
                    duration = SnackbarDuration.Short
                )
                viewModel.clearMoveSuccess()
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Manage Categories") },
                navigationIcon = {
                    IconButton(onClick = debouncedBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.showAddDialog() },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, "Add Category")
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(Spacing.large)
            ) {
                // Category list
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(Spacing.small),
                    contentPadding = PaddingValues(bottom = Dimensions.fabBottomPadding)
                ) {
                    // Income Categories Section
                    item {
                        Text(
                            text = "INCOME",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.padding(vertical = Spacing.small)
                        )
                    }

                    items(
                        items = incomeCategories,
                        key = { category -> category.id }
                    ) { category ->
                        CategoryItem(
                            category = category,
                            viewModel = viewModel,
                            baseCurrency = uiState.baseCurrency,
                            decimalSeparator = decimalSeparator,
                            thousandSeparator = thousandSeparator,
                            onEdit = { viewModel.showEditDialog(category) },
                            onDelete = { viewModel.showDeleteDialog(category) },
                            onMove = { viewModel.showMoveDialog(category) },
                            allCategories = allCategories
                        )
                    }

                    // Expense Categories Section
                    item {
                        Spacer(modifier = Modifier.height(Spacing.small))
                        Text(
                            text = "EXPENSES",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(vertical = Spacing.small)
                        )
                    }

                    items(
                        items = expenseCategories,
                        key = { category -> category.id }
                    ) { category ->
                        CategoryItem(
                            category = category,
                            viewModel = viewModel,
                            baseCurrency = uiState.baseCurrency,
                            decimalSeparator = decimalSeparator,
                            thousandSeparator = thousandSeparator,
                            onEdit = { viewModel.showEditDialog(category) },
                            onDelete = { viewModel.showDeleteDialog(category) },
                            onMove = { viewModel.showMoveDialog(category) },
                            allCategories = allCategories
                        )
                    }
                }
            }
        }
    }

    // Add category dialog
    if (uiState.showAddDialog) {
        AddCategoryDialog(
            viewModel = viewModel,
            onDismiss = { viewModel.hideAddDialog() },
            error = uiState.error
        )
    }

    // Edit category dialog
    if (uiState.showEditDialog && uiState.selectedCategory != null) {
        val selectedCategory = uiState.selectedCategory!! // Capture before closing dialog
        EditCategoryDialog(
            category = selectedCategory,
            viewModel = viewModel,
            onDismiss = { viewModel.hideEditDialog() },
            error = uiState.error,
            transactionCount = uiState.selectedCategoryTransactionCount,
            allCategories = allCategories,
            onDelete = { 
                viewModel.hideEditDialog()
                // Add small delay to prevent modal flashing
                deleteScope.launch {
                    kotlinx.coroutines.delay(150)
                    viewModel.showDeleteDialog(selectedCategory)
                }
            }
        )
    }

    // Delete confirmation dialog
    if (uiState.showDeleteDialog && uiState.categoryToDelete != null) {
        DeleteCategoryDialog(
            category = uiState.categoryToDelete!!,
            viewModel = viewModel,
            onDismiss = { viewModel.hideDeleteDialog() },
            onConfirm = { viewModel.deleteCategory(uiState.categoryToDelete!!.id) },
            error = uiState.deleteError
        )
    }

    // Move transactions dialog
    if (uiState.showMoveDialog && uiState.categoryToMove != null) {
        MoveTransactionsDialog(
            category = uiState.categoryToMove!!,
            allCategories = allCategories,
            viewModel = viewModel,
            onDismiss = { viewModel.hideMoveDialog() },
            error = uiState.error,
            isLoading = uiState.moveInProgress
        )
    }
}

/**
 * Category item with usage statistics (card-based design matching CategorySetupScreen).
 * Edit only when clicking edit icon. Delete only shown when 2+ categories of same type exist.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CategoryItem(
    category: Category,
    viewModel: CategoryViewModel,
    baseCurrency: String,
    decimalSeparator: String,
    thousandSeparator: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMove: () -> Unit = {},
    allCategories: List<Category>
) {
    val scope = rememberCoroutineScope()
    var transactionCount by remember { mutableStateOf(0) }
    var totalAmount by remember { mutableStateOf(BigDecimal.ZERO) }

    // Load statistics
    LaunchedEffect(category.id) {
        transactionCount = viewModel.getCategoryTransactionCount(category.id)
        totalAmount = viewModel.getCategoryTotalAmount(category.id)
    }

    // Count categories of the same type to determine if delete should be shown
    val categoriesOfSameType = allCategories.filter { it.type == category.type }
    val showDeleteButton = categoriesOfSameType.size >= 2 && transactionCount == 0

    // Highlight unused categories (PRD Section 4.14)
    val isUnused = transactionCount == 0

    // Card-based design (matching CategorySetupScreen and ProfileManagementScreen)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.medium)
        ) {
            // Category name and amount
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = category.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = CurrencyFormatter.formatAmount(
                        amount = totalAmount,
                        currencyCode = baseCurrency,
                        decimalSeparator = decimalSeparator,
                        thousandSeparator = thousandSeparator
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (category.type == TransactionType.INCOME) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                )
            }

            Spacer(modifier = Modifier.height(Spacing.extraSmall))

            // Statistics and metadata
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left side: transaction count and badges
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.small),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$transactionCount transaction${if (transactionCount != 1) "s" else ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (isUnused) {
                        Text(
                            text = "• Unused",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Right side: action buttons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.extraSmall)
                ) {
                    // Edit button
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Edit",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Delete category confirmation dialog.
 */
@Composable
private fun DeleteCategoryDialog(
    category: Category,
    viewModel: CategoryViewModel,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    error: String?
) {
    val scope = rememberCoroutineScope()
    var canDelete by remember { mutableStateOf(false) }
    var transactionCount by remember { mutableStateOf(0) }

    LaunchedEffect(category.id) {
        canDelete = viewModel.canDeleteCategory(category.id)
        transactionCount = viewModel.getCategoryTransactionCount(category.id)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = { Text("Delete Category") },
        text = {
            Column {
                if (canDelete) {
                    Text("Are you sure you want to delete \"${category.name}\"?")
                    Text(
                        "This action cannot be undone.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    Text("Cannot delete \"${category.name}\"")
                    Text(
                        "This category has $transactionCount transaction(s). " +
                                "You must move or delete all transactions before deleting this category.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                if (error != null) {
                    Spacer(modifier = Modifier.height(Spacing.small))
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Delete button on the left (only if can delete)
                if (canDelete) {
                    Button(
                        onClick = onConfirm,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Delete")
                    }
                } else {
                    Spacer(modifier = Modifier.width(1.dp)) // Placeholder for layout
                }

                // Cancel/OK button on the right
                TextButton(onClick = onDismiss) {
                    Text(if (canDelete) "Cancel" else "OK")
                }
            }
        }
    )
}

/**
 * Add category dialog.
 */
@Composable
private fun AddCategoryDialog(
    viewModel: CategoryViewModel,
    onDismiss: () -> Unit,
    error: String?
) {
    var name by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(TransactionType.EXPENSE) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = { Text("Add Category") },
        text = {
            Column {
                // Name input
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Category Name") },
                    placeholder = { Text("e.g., Office Supplies") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(Spacing.medium))

                // Type selector
                Text(
                    text = "Type",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(Spacing.small))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.small)
                ) {
                    FilterChip(
                        selected = selectedType == TransactionType.INCOME,
                        onClick = { selectedType = TransactionType.INCOME },
                        label = { Text("Income") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onTertiaryContainer
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = selectedType == TransactionType.EXPENSE,
                        onClick = { selectedType = TransactionType.EXPENSE },
                        label = { Text("Expense") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.errorContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onErrorContainer
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }

                if (error != null) {
                    Spacer(modifier = Modifier.height(Spacing.small))
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    scope.launch {
                        viewModel.createCategory(
                            name = name.trim(),
                            type = selectedType
                        )
                        // Wait a bit for the operation to complete
                        kotlinx.coroutines.delay(100)
                        if (viewModel.uiState.value.isCreated) {
                            viewModel.resetCreatedFlag()
                            onDismiss()
                        }
                    }
                },
                enabled = name.trim().isNotBlank()
            ) {
                Text("Add")
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
 * Edit category dialog.
 */
@Composable
private fun EditCategoryDialog(
    category: Category,
    viewModel: CategoryViewModel,
    onDismiss: () -> Unit,
    error: String?,
    transactionCount: Int = 0,
    allCategories: List<Category>,
    onDelete: () -> Unit
) {
    var name by remember { mutableStateOf(category.name) }
    var type by remember { mutableStateOf(category.type) }
    var showChangeConfirmation by remember { mutableStateOf(false) }
    val uiState by viewModel.uiState.collectAsState()

    // Auto-close dialog when update succeeds
    LaunchedEffect(uiState.isUpdated) {
        if (uiState.isUpdated) {
            viewModel.resetUpdatedFlag()
            onDismiss()
        }
    }

    // Check if delete button should be shown (2+ categories of same type AND empty)
    val categoriesOfSameType = allCategories.filter { it.type == category.type }
    val canShowDelete = categoriesOfSameType.size >= 2 && transactionCount == 0

    // Detect what changed
    val nameChanged = name.trim() != category.name
    val typeChanged = type != category.type
    val hasChanges = nameChanged || typeChanged

    if (showChangeConfirmation && hasChanges) {
        AlertDialog(
            onDismissRequest = { showChangeConfirmation = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("Confirm Changes") },
            text = {
                val changes = mutableListOf<String>()
                if (nameChanged) changes.add("rename this category")
                if (typeChanged) changes.add("change its type")
                
                Text(
                    "This will ${changes.joinToString(" and ")} " +
                    "and update all historical transactions. " +
                    "This action cannot be undone. Are you sure?"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateCategory(
                            id = category.id,
                            name = name.trim(),
                            type = type,
                            icon = category.icon,
                            color = category.color
                        )
                        showChangeConfirmation = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Confirm")
                }
            },
            dismissButton = {
                TextButton(onClick = { showChangeConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Edit Category")
                if (canShowDelete) {
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Default.Delete,
                            "Delete Category",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        },
        text = {
            Column {
                // Name input
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Category Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(Spacing.medium))

                // Type selector (with colored buttons like transaction entry)
                Text(
                    text = "Type",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(Spacing.small))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.small)
                ) {
                    FilterChip(
                        selected = type == TransactionType.INCOME,
                        onClick = { type = TransactionType.INCOME },
                        label = { Text("Income") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onTertiaryContainer
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = type == TransactionType.EXPENSE,
                        onClick = { type = TransactionType.EXPENSE },
                        label = { Text("Expense") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.errorContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onErrorContainer
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(Spacing.small))

                // Move transactions button (only if category has transactions)
                if (transactionCount > 0) {
                    OutlinedButton(
                        onClick = {
                            viewModel.showMoveDialog(category)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Default.SwapHoriz,
                            contentDescription = null,
                            modifier = Modifier.size(Dimensions.iconSmall)
                        )
                        Spacer(modifier = Modifier.width(Spacing.small))
                        Text("Move $transactionCount Transaction${if (transactionCount != 1) "s" else ""}")
                    }
                    Spacer(modifier = Modifier.height(Spacing.small))
                }

                if (error != null) {
                    Spacer(modifier = Modifier.height(Spacing.small))
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { showChangeConfirmation = true },
                enabled = name.trim().isNotBlank() && hasChanges
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
 * Move transactions dialog.
 *
 * PRD Reference: Section 4.14 - Move transactions between categories (bulk action)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoveTransactionsDialog(
    category: Category,
    allCategories: List<Category>,
    viewModel: CategoryViewModel,
    onDismiss: () -> Unit,
    error: String?,
    isLoading: Boolean
) {
    val scope = rememberCoroutineScope()
    var transactionCount by remember { mutableStateOf(0) }
    var selectedDestinationCategory by remember { mutableStateOf<Category?>(null) }
    var expanded by remember { mutableStateOf(false) }

    // Load transaction count
    LaunchedEffect(category.id) {
        transactionCount = viewModel.getCategoryTransactionCount(category.id)
    }

    // Close dialog when move completes successfully
    val uiState = viewModel.uiState.collectAsState()
    LaunchedEffect(uiState.value.moveSuccess) {
        if (uiState.value.moveSuccess != null) {
            onDismiss()
        }
    }

    // Filter destination categories (same type, not source, not archived)
    val destinationCategories = allCategories.filter {
        it.type == category.type && it.id != category.id && !it.isArchived
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move Transactions") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Spacing.medium)
            ) {
                // Info text
                Text(
                    text = "Move all $transactionCount transaction(s) from \"${category.name}\" to another category.",
                    style = MaterialTheme.typography.bodyMedium
                )

                // No destination categories available
                if (destinationCategories.isEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(Spacing.mediumSmall),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.small)
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                text = "No destination categories available. You need at least one other ${if (category.type == TransactionType.INCOME) "income" else "expense"} category to move transactions.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }

                // Destination category dropdown
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedDestinationCategory?.name ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Destination Category") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true),
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
                    )

                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        destinationCategories.forEach { destCategory ->
                            DropdownMenuItem(
                                text = { Text(destCategory.name) },
                                onClick = {
                                    selectedDestinationCategory = destCategory
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                // Warning text
                if (selectedDestinationCategory != null) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(Spacing.mediumSmall),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.small)
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                text = "This action will update all $transactionCount transaction(s) and add an edit history entry to each.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }

                // Error message
                error?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                // Loading indicator
                if (isLoading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    selectedDestinationCategory?.let { dest ->
                        viewModel.moveTransactions(
                            fromCategoryName = category.name,
                            toCategoryName = dest.name
                        )
                    }
                },
                enabled = selectedDestinationCategory != null && !isLoading && destinationCategories.isNotEmpty()
            ) {
                Text("Move")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isLoading
            ) {
                Text("Cancel")
            }
        }
    )
}
