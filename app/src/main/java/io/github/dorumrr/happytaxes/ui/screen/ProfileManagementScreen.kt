package io.github.dorumrr.happytaxes.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.dorumrr.happytaxes.domain.model.Profile
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import io.github.dorumrr.happytaxes.ui.theme.Dimensions
import io.github.dorumrr.happytaxes.ui.theme.Spacing
import io.github.dorumrr.happytaxes.ui.util.ProfileIconMapper
import io.github.dorumrr.happytaxes.ui.util.rememberDebouncedNavigation
import io.github.dorumrr.happytaxes.ui.viewmodel.ProfileManagementViewModel

/**
 * Profile Management Screen.
 *
 * Features:
 * - Display all profiles
 * - Create new profile (unlimited)
 * - Delete profile (with double confirmation)
 * - Switch between profiles
 * - Show current active profile
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileManagementScreen(
    onNavigateBack: () -> Unit,
    viewModel: ProfileManagementViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()

    // Debounced navigation to prevent double-clicks
    val debouncedBack = rememberDebouncedNavigation(onNavigate = onNavigateBack)

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("Manage Profiles") },
                navigationIcon = {
                    IconButton(onClick = debouncedBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.showCreateProfileDialog() },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, "Add Profile")
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(Spacing.large)
            ) {
                // Profile list
                LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(Spacing.small),
                        contentPadding = PaddingValues(bottom = Dimensions.fabBottomPadding)
                    ) {
                        items(uiState.profiles) { profile ->
                            ProfileItem(
                                profile = profile,
                                isActive = profile.id == uiState.currentProfileId,
                                canDelete = uiState.canDeleteProfile,
                                onDelete = { viewModel.showDeleteConfirmation(profile.id) },
                                onEdit = { viewModel.showEditProfileDialog(profile) }
                            )
                        }
                    }
                }
            }
        }

        // Create Profile Dialog
        if (uiState.showCreateDialog) {
            CreateProfileDialog(
                profileName = uiState.profileNameToCreate,
                selectedIcon = uiState.profileIconToCreate,
                selectedColor = uiState.profileColorToCreate,
                isCreating = uiState.isCreating,
                error = uiState.createError,
                onNameChange = { viewModel.updateProfileName(it) },
                onIconChange = { viewModel.updateProfileIcon(it) },
                onColorChange = { viewModel.updateProfileColor(it) },
                onConfirm = { viewModel.createProfile() },
                onDismiss = { viewModel.hideCreateProfileDialog() }
            )
        }

        // Edit Profile Dialog
        if (uiState.showEditDialog && uiState.profileToEdit != null) {
            val profileToEdit = uiState.profiles.find { it.id == uiState.profileToEdit }
            if (profileToEdit != null) {
                EditProfileDialog(
                    profileName = uiState.profileNameToEdit,
                    selectedIcon = uiState.profileIconToEdit,
                    selectedColor = uiState.profileColorToEdit,
                    isEditing = uiState.isEditing,
                    error = uiState.editError,
                    canDelete = uiState.canDeleteProfile,
                    onNameChange = { viewModel.updateEditProfileName(it) },
                    onIconChange = { viewModel.updateEditProfileIcon(it) },
                    onColorChange = { viewModel.updateEditProfileColor(it) },
                    onConfirm = { viewModel.updateProfile() },
                    onDismiss = { viewModel.hideEditProfileDialog() },
                    onDelete = { 
                        viewModel.hideEditProfileDialog()
                        // Add small delay to prevent modal flashing
                        scope.launch {
                            delay(150)
                            viewModel.showDeleteConfirmation(profileToEdit.id)
                        }
                    }
                )
            }
        }

        // Delete Confirmation Dialog (First step)
        if (uiState.showDeleteConfirmation) {
            val profile = uiState.profiles.find { it.id == uiState.profileToDelete }
            if (profile != null) {
                DeleteConfirmationDialog(
                    profileName = profile.name,
                    onConfirm = { viewModel.showFinalDeleteConfirmation() },
                    onDismiss = { viewModel.hideDeleteConfirmation() }
                )
            }
        }

        // Final Delete Confirmation Dialog (Second step)
        if (uiState.showFinalDeleteConfirmation) {
            val profile = uiState.profiles.find { it.id == uiState.profileToDelete }
            if (profile != null) {
                FinalDeleteConfirmationDialog(
                    profileName = profile.name,
                    isDeleting = uiState.isDeleting,
                    error = uiState.deleteError,
                    onConfirm = { viewModel.deleteProfile() },
                    onDismiss = { viewModel.hideDeleteConfirmation() }
                )
            }
        }
    }
}

/**
 * Info header showing profile count (matching CategorySetupScreen style).
 */
@Composable
private fun InfoHeader(
    profileCount: Int
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "Profiles ($profileCount)",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(Spacing.extraSmall))
        Text(
            text = "Each profile has independent transactions, categories, and settings. Create as many profiles as you need.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Profile item (Card style, matching CategorySetupScreen).
 * Shows edit icon, switch icon (if not active), and delete (if 2+ profiles).
 */
@Composable
private fun ProfileItem(
    profile: Profile,
    isActive: Boolean,
    canDelete: Boolean,
    onDelete: () -> Unit,
    onEdit: () -> Unit
) {
    val profileColor = Color(android.graphics.Color.parseColor(profile.color))
    val icon = ProfileIconMapper.getIcon(profile.icon)

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
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Icon and name
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.mediumSmall),
                modifier = Modifier.weight(1f)
            ) {
                // Profile icon with colored background
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(profileColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = profile.name,
                        tint = profileColor,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Column {
                    Text(
                        text = profile.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
                    )
                    if (isActive) {
                        Text(
                            text = "Currently in use",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Edit button
            IconButton(
                onClick = onEdit,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Default.Edit,
                    "Edit",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/**
 * Create Profile Dialog with name, icon, and color selection.
 */
@Composable
private fun CreateProfileDialog(
    profileName: String,
    selectedIcon: String,
    selectedColor: String,
    isCreating: Boolean,
    error: String?,
    onNameChange: (String) -> Unit,
    onIconChange: (String) -> Unit,
    onColorChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!isCreating) onDismiss() },
        title = {
            Text("Create New Profile")
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Spacing.medium)
            ) {
                // Profile name input
                OutlinedTextField(
                    value = profileName,
                    onValueChange = onNameChange,
                    label = { Text("Profile Name") },
                    placeholder = { Text("e.g., Business, Personal, Freelance") },
                    singleLine = true,
                    enabled = !isCreating,
                    modifier = Modifier.fillMaxWidth()
                )

                // Icon selection
                Text(
                    text = "Select Icon",
                    style = MaterialTheme.typography.labelLarge
                )
                IconPicker(
                    selectedIcon = selectedIcon,
                    onIconSelected = onIconChange,
                    enabled = !isCreating
                )

                // Color selection
                Text(
                    text = "Select Color",
                    style = MaterialTheme.typography.labelLarge
                )
                ColorPicker(
                    selectedColor = selectedColor,
                    onColorSelected = onColorChange,
                    enabled = !isCreating
                )

                // Error message
                if (error != null) {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !isCreating && profileName.isNotBlank()
            ) {
                if (isCreating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isCreating
            ) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Edit Profile Dialog with name, icon, and color selection.
 */
@Composable
private fun EditProfileDialog(
    profileName: String,
    selectedIcon: String,
    selectedColor: String,
    isEditing: Boolean,
    error: String?,
    canDelete: Boolean,
    onNameChange: (String) -> Unit,
    onIconChange: (String) -> Unit,
    onColorChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    onDelete: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!isEditing) onDismiss() },
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Edit Profile")
                if (canDelete) {
                    IconButton(
                        onClick = onDelete,
                        enabled = !isEditing
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            "Delete Profile",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Spacing.medium)
            ) {
                // Profile name input
                OutlinedTextField(
                    value = profileName,
                    onValueChange = onNameChange,
                    label = { Text("Profile Name") },
                    placeholder = { Text("e.g., Business, Personal, Freelance") },
                    singleLine = true,
                    enabled = !isEditing,
                    modifier = Modifier.fillMaxWidth()
                )

                // Icon selection
                Text(
                    text = "Select Icon",
                    style = MaterialTheme.typography.labelLarge
                )
                IconPicker(
                    selectedIcon = selectedIcon,
                    onIconSelected = onIconChange,
                    enabled = !isEditing
                )

                // Color selection
                Text(
                    text = "Select Color",
                    style = MaterialTheme.typography.labelLarge
                )
                ColorPicker(
                    selectedColor = selectedColor,
                    onColorSelected = onColorChange,
                    enabled = !isEditing
                )

                // Error message
                if (error != null) {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !isEditing && profileName.isNotBlank()
            ) {
                if (isEditing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isEditing
            ) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Delete Confirmation Dialog (First step).
 */
@Composable
private fun DeleteConfirmationDialog(
    profileName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.Delete,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(48.dp)
            )
        },
        title = {
            Text("Delete $profileName Profile?")
        },
        text = {
            Text(
                "This will permanently delete ALL data associated with this profile:\n\n" +
                "• All transactions (active, deleted, drafts)\n" +
                "• All categories (seeded + custom)\n" +
                "• All receipt photos\n" +
                "• All search history\n\n" +
                "This action cannot be undone."
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Continue")
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
 * Final Delete Confirmation Dialog (Second step).
 */
@Composable
private fun FinalDeleteConfirmationDialog(
    profileName: String,
    isDeleting: Boolean,
    error: String?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!isDeleting) onDismiss() },
        icon = {
            Icon(
                Icons.Default.Delete,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(48.dp)
            )
        },
        title = {
            Text("Are you absolutely sure?")
        },
        text = {
            Column {
                Text(
                    "You are about to permanently delete the $profileName profile and ALL its data.",
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(Spacing.medium))
                Text("This action is irreversible. All transactions, categories, and receipts will be lost forever.")

                if (error != null) {
                    Spacer(modifier = Modifier.height(Spacing.medium))
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !isDeleting,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                if (isDeleting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onError
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text("Delete Forever")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isDeleting
            ) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Icon picker grid.
 */
@Composable
private fun IconPicker(
    selectedIcon: String,
    onIconSelected: (String) -> Unit,
    enabled: Boolean
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(5),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 200.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(Profile.AVAILABLE_ICONS) { iconName ->
            val icon = ProfileIconMapper.getIcon(iconName)
            val isSelected = iconName == selectedIcon

            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .border(
                        width = if (isSelected) 2.dp else 0.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                        shape = CircleShape
                    )
                    .clickable(enabled = enabled) { onIconSelected(iconName) },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = iconName,
                    tint = if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

/**
 * Color picker row.
 */
@Composable
private fun ColorPicker(
    selectedColor: String,
    onColorSelected: (String) -> Unit,
    enabled: Boolean
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(Profile.AVAILABLE_COLORS) { colorHex ->
            val color = Color(android.graphics.Color.parseColor(colorHex))
            val isSelected = colorHex == selectedColor

            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(
                        width = if (isSelected) 3.dp else 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline,
                        shape = CircleShape
                    )
                    .clickable(enabled = enabled) { onColorSelected(colorHex) },
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

