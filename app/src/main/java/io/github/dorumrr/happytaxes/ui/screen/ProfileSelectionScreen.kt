package io.github.dorumrr.happytaxes.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.dorumrr.happytaxes.domain.model.Profile
import io.github.dorumrr.happytaxes.ui.theme.Spacing
import io.github.dorumrr.happytaxes.ui.viewmodel.ProfileSelectionViewModel

/**
 * Profile Selection Screen.
 *
 * Shown at app startup when 2 profiles exist.
 * User selects which profile to use.
 *
 * Features:
 * - Display available profiles (Business and Personal)
 * - Color-coded profile cards
 * - Icon for each profile type
 * - Tap to select and navigate to main screen
 */
@Composable
fun ProfileSelectionScreen(
    onProfileSelected: () -> Unit,
    viewModel: ProfileSelectionViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // Navigate when profile is selected
    LaunchedEffect(uiState.navigationComplete) {
        if (uiState.navigationComplete) {
            onProfileSelected()
            viewModel.resetNavigation()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.Center
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator()
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Spacing.large),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Spacing.large)
                ) {
                    // Title
                    Text(
                        text = "Select Profile",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "Choose which profile to use",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(Spacing.medium))

                    // Profile cards
                    uiState.profiles.forEach { profile ->
                        ProfileCard(
                            profile = profile,
                            onClick = { viewModel.selectProfile(profile.id) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Profile card component.
 */
@Composable
private fun ProfileCard(
    profile: Profile,
    onClick: () -> Unit
) {
    val profileColor = Color(android.graphics.Color.parseColor(profile.color))
    val icon = when (profile.icon) {
        "business" -> Icons.Default.Business
        "person" -> Icons.Default.Person
        else -> Icons.Default.Business
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(profileColor.copy(alpha = 0.1f))
                .padding(Spacing.large),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.medium)
        ) {
            // Icon
            Icon(
                imageVector = icon,
                contentDescription = profile.name,
                tint = profileColor,
                modifier = Modifier.size(48.dp)
            )

            // Profile name
            Text(
                text = profile.name,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = profileColor
            )
        }
    }
}

