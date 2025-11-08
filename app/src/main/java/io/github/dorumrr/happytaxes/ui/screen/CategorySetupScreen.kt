package io.github.dorumrr.happytaxes.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.dorumrr.happytaxes.domain.model.Category
import io.github.dorumrr.happytaxes.domain.model.TransactionType
import io.github.dorumrr.happytaxes.ui.theme.Dimensions
import io.github.dorumrr.happytaxes.ui.theme.Spacing
import io.github.dorumrr.happytaxes.ui.viewmodel.CategorySetupViewModel

/**
 * Standalone Category Setup Screen.
 * 
 * Used when:
 * - User switches to a profile that has no categories
 * - User needs to set up categories for a new profile
 * 
 * Requires at least 1 income + 1 expense category before allowing to proceed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategorySetupScreen(
    onComplete: () -> Unit,
    viewModel: CategorySetupViewModel = hiltViewModel()
) {
    val categories by viewModel.categories.collectAsState()
    val hasMinimumCategories by viewModel.hasMinimumCategories.collectAsState()
    val categoryError by viewModel.categoryError.collectAsState()
    val currentProfileName by viewModel.currentProfileName.collectAsState()

    var showCreateDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var createCategoryType by remember { mutableStateOf(TransactionType.INCOME) }
    var categoryToEdit by remember { mutableStateOf<Category?>(null) }
    var categoryToDelete by remember { mutableStateOf<Category?>(null) }
    val scope = rememberCoroutineScope()

    // Prevent back navigation until categories are set up
    BackHandler(enabled = !hasMinimumCategories) {
        // Do nothing - user must create categories
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (currentProfileName != null)
                            "Set Up Categories: $currentProfileName"
                        else
                            "Set Up Categories"
                    )
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(Spacing.large)
        ) {
            Text(
                text = "Create at least one income category and one expense category to get started.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(Spacing.medium))

            // Add category buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.small)
            ) {
                OutlinedButton(
                    onClick = {
                        createCategoryType = TransactionType.INCOME
                        showCreateDialog = true
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(Spacing.small))
                    Text("Income")
                }
                OutlinedButton(
                    onClick = {
                        createCategoryType = TransactionType.EXPENSE
                        showCreateDialog = true
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(Spacing.small))
                    Text("Expense")
                }
            }

            Spacer(modifier = Modifier.height(Spacing.medium))

            // Category list
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.small)
            ) {
                // Income categories
                val incomeCategories = categories.filter { it.type == TransactionType.INCOME }.sortedBy { it.name }
                if (incomeCategories.isNotEmpty()) {
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
                        CategoryItemSimple(
                            category = category,
                            onClick = {
                                categoryToEdit = category
                                showEditDialog = true
                            }
                        )
                    }
                }

                // Expense categories
                val expenseCategories = categories.filter { it.type == TransactionType.EXPENSE }.sortedBy { it.name }
                if (expenseCategories.isNotEmpty()) {
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
                        CategoryItemSimple(
                            category = category,
                            onClick = {
                                categoryToEdit = category
                                showEditDialog = true
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(Spacing.medium))

            // Complete button
            Button(
                onClick = onComplete,
                enabled = hasMinimumCategories,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(Spacing.buttonHeight)
            ) {
                Text(
                    if (hasMinimumCategories)
                        "Complete Setup"
                    else
                        "Create Categories to Continue"
                )
            }
        }
    }

    // Create category dialog
    if (showCreateDialog) {
        SimpleCategoryCreateDialog(
            type = createCategoryType,
            existingCategories = categories,
            error = categoryError,
            onDismiss = {
                showCreateDialog = false
                viewModel.clearCategoryError()
            },
            onCreate = { name, icon, color ->
                viewModel.createCategory(name, createCategoryType, icon, color)
            },
            onSuccess = {
                showCreateDialog = false
                viewModel.clearCategoryError()
            }
        )
    }

    // Edit category dialog
    if (showEditDialog && categoryToEdit != null) {
        val categoryId = categoryToEdit!!.id // Capture the ID before closing dialog
        SimpleCategoryEditDialog(
            category = categoryToEdit!!,
            allCategories = categories,
            error = categoryError,
            onDismiss = {
                showEditDialog = false
                categoryToEdit = null
                viewModel.clearCategoryError()
            },
            onUpdate = { name, icon, color ->
                viewModel.updateCategory(categoryToEdit!!.id, name, categoryToEdit!!.type, icon, color)
            },
            onDelete = {
                showEditDialog = false
                categoryToEdit = null
                viewModel.clearCategoryError()
                // Add small delay to prevent modal flashing, then show delete confirmation
                scope.launch {
                    kotlinx.coroutines.delay(150)
                    categoryToDelete = categories.find { it.id == categoryId }
                    showDeleteDialog = true
                }
            },
            onSuccess = {
                showEditDialog = false
                categoryToEdit = null
                viewModel.clearCategoryError()
            }
        )
    }

    // Delete confirmation dialog
    if (showDeleteDialog && categoryToDelete != null) {
        AlertDialog(
            onDismissRequest = {
                showDeleteDialog = false
                categoryToDelete = null
            },
            icon = {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("Delete Category") },
            text = {
                Text("Are you sure you want to delete \"${categoryToDelete!!.name}\"? This action cannot be undone.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteCategory(categoryToDelete!!.id)
                        showDeleteDialog = false
                        categoryToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        categoryToDelete = null
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * Simple category list item with edit icon only.
 * Edit when clicking the edit icon, not the entire card.
 */
@Composable
private fun CategoryItemSimple(
    category: Category,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.medium),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = category.name,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            // Edit button
            IconButton(onClick = onClick) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "Edit",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/**
 * Simple dialog for creating a category (name only).
 */
@Composable
private fun SimpleCategoryCreateDialog(
    type: TransactionType,
    existingCategories: List<Category>,
    error: String?,
    onDismiss: () -> Unit,
    onCreate: (String, String?, String?) -> Unit,
    onSuccess: () -> Unit
) {
    var categoryName by remember { mutableStateOf("") }
    var createAttempted by remember { mutableStateOf(false) }

    // Real-time duplicate check (case-insensitive)
    val isDuplicate = remember(categoryName, existingCategories) {
        categoryName.isNotBlank() && existingCategories.any {
            it.name.equals(categoryName.trim(), ignoreCase = true)
        }
    }

    val validationError = when {
        isDuplicate -> "Category '${categoryName.trim()}' already exists in this profile"
        else -> null
    }

    // Show either real-time validation error or server error
    val displayError = validationError ?: error

    // Close dialog on success (when error is null after a create attempt)
    LaunchedEffect(error, createAttempted) {
        if (createAttempted && error == null) {
            // Success - category was created
            onSuccess()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Create ${type.name.lowercase().replaceFirstChar { it.uppercase() }} Category")
        },
        text = {
            Column {
                OutlinedTextField(
                    value = categoryName,
                    onValueChange = { categoryName = it },
                    label = { Text("Category Name") },
                    singleLine = true,
                    isError = displayError != null,
                    modifier = Modifier.fillMaxWidth()
                )
                if (displayError != null) {
                    Spacer(modifier = Modifier.height(Spacing.extraSmall))
                    Text(
                        text = displayError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (categoryName.isNotBlank() && !isDuplicate) {
                        createAttempted = true
                        onCreate(categoryName.trim(), null, null)
                    }
                },
                enabled = categoryName.isNotBlank() && !isDuplicate
            ) {
                Text("Create")
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
 * Simple dialog for editing a category (name only) with conditional delete button.
 */
@Composable
private fun SimpleCategoryEditDialog(
    category: Category,
    allCategories: List<Category>,
    error: String?,
    onDismiss: () -> Unit,
    onUpdate: (String, String?, String?) -> Unit,
    onDelete: () -> Unit,
    onSuccess: () -> Unit
) {
    var categoryName by remember { mutableStateOf(category.name) }
    var updateAttempted by remember { mutableStateOf(false) }

    // Check if delete button should be shown (2+ categories of same type)
    val categoriesOfSameType = allCategories.filter { it.type == category.type }
    val canShowDelete = categoriesOfSameType.size >= 2

    // Close dialog on success (when error is null after an update attempt)
    LaunchedEffect(error, updateAttempted) {
        if (updateAttempted && error == null) {
            // Success - category was updated
            onSuccess()
        }
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
                OutlinedTextField(
                    value = categoryName,
                    onValueChange = { categoryName = it },
                    label = { Text("Category Name") },
                    singleLine = true,
                    isError = error != null,
                    modifier = Modifier.fillMaxWidth()
                )
                if (error != null) {
                    Spacer(modifier = Modifier.height(Spacing.extraSmall))
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (categoryName.isNotBlank()) {
                        updateAttempted = true
                        onUpdate(categoryName.trim(), category.icon, category.color)
                    }
                },
                enabled = categoryName.isNotBlank()
            ) {
                Text("Update")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
