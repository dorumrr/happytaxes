package io.github.dorumrr.happytaxes.ui.screen

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.dorumrr.happytaxes.data.repository.PreferencesRepository
import io.github.dorumrr.happytaxes.ui.theme.Spacing
import io.github.dorumrr.happytaxes.util.BiometricAuthManager
import io.github.dorumrr.happytaxes.util.BiometricAvailability
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Access PIN screen for configuring biometric authentication.
 *
 * Features:
 * - Enable/disable biometric authentication on app launch
 * - Test authentication
 * - Show biometric availability status
 * - Explain what authentication methods are supported
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccessPinScreen(
    onNavigateBack: () -> Unit,
    viewModel: AccessPinViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val activity = context as? FragmentActivity

    LaunchedEffect(Unit) {
        activity?.let {
            viewModel.checkBiometricAvailability(it)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Access Security") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(Spacing.medium)
        ) {
            // Header icon and description
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = null,
                modifier = Modifier
                    .size(64.dp)
                    .align(Alignment.CenterHorizontally),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(Spacing.medium))

            Text(
                text = "Secure Your Data",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            Spacer(modifier = Modifier.height(Spacing.small))

            Text(
                text = "Require authentication when opening HappyTaxes. Uses your device's security methods:",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(Spacing.mediumSmall))

            // Supported methods
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                )
            ) {
                Column(modifier = Modifier.padding(Spacing.medium)) {
                    Text(
                        text = "Supported Methods:",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(Spacing.small))
                    Text("• Fingerprint", style = MaterialTheme.typography.bodyMedium)
                    Text("• Face unlock", style = MaterialTheme.typography.bodyMedium)
                    Text("• Device PIN/Pattern/Password", style = MaterialTheme.typography.bodyMedium)
                }
            }

            Spacer(modifier = Modifier.height(Spacing.large))

            // Availability status
            when (uiState.availability) {
                BiometricAvailability.AVAILABLE -> {
                    // Enable/disable switch
                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(Spacing.medium),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Require Authentication",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "Authenticate when opening app",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = uiState.isEnabled,
                                onCheckedChange = { enabled ->
                                    if (enabled && activity != null) {
                                        // Test authentication before enabling
                                        viewModel.testAuthentication(activity)
                                    } else {
                                        viewModel.setBiometricEnabled(false)
                                    }
                                }
                            )
                        }
                    }
                }

                BiometricAvailability.NONE_ENROLLED -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(Spacing.medium)) {
                            Text(
                                text = "No Security Method Set Up",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.height(Spacing.small))
                            Text(
                                text = "Please set up a fingerprint, face unlock, or device PIN/pattern in your device settings first.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )

                            Spacer(modifier = Modifier.height(Spacing.medium))

                            Button(
                                onClick = {
                                    // Open Android Security Settings
                                    val intent = Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS)
                                    context.startActivity(intent)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError
                                )
                            ) {
                                Icon(Icons.Default.Settings, contentDescription = null)
                                Spacer(modifier = Modifier.width(Spacing.small))
                                Text("Open Security Settings")
                            }
                        }
                    }
                }

                BiometricAvailability.NO_HARDWARE -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(Spacing.medium)) {
                            Text(
                                text = "Not Supported",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.height(Spacing.small))
                            Text(
                                text = "Your device doesn't support biometric authentication. However, you can still use a device PIN/pattern/password for security.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )

                            Spacer(modifier = Modifier.height(Spacing.medium))

                            Button(
                                onClick = {
                                    // Open Android Security Settings
                                    val intent = Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS)
                                    context.startActivity(intent)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError
                                )
                            ) {
                                Icon(Icons.Default.Settings, contentDescription = null)
                                Spacer(modifier = Modifier.width(Spacing.small))
                                Text("Open Security Settings")
                            }
                        }
                    }
                }

                else -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(Spacing.medium)) {
                            Text(
                                text = "Unavailable",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.height(Spacing.small))
                            Text(
                                text = "Biometric authentication is currently unavailable on this device. Please check your device security settings.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )

                            Spacer(modifier = Modifier.height(Spacing.medium))

                            Button(
                                onClick = {
                                    // Open Android Security Settings
                                    val intent = Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS)
                                    context.startActivity(intent)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError
                                )
                            ) {
                                Icon(Icons.Default.Settings, contentDescription = null)
                                Spacer(modifier = Modifier.width(Spacing.small))
                                Text("Open Security Settings")
                            }
                        }
                    }
                }
            }

            // Auto-lock timeout (only show if enabled)
            if (uiState.isEnabled) {
                Spacer(modifier = Modifier.height(Spacing.large))

                Text(
                    text = "Auto-Lock Timeout",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(Spacing.small))

                Text(
                    text = "Lock the app after switching to another app",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(Spacing.medium))

                // Timeout options
                val timeoutOptions = listOf(
                    0 to "Immediately",
                    60 to "1 minute",
                    300 to "5 minutes",
                    600 to "10 minutes"
                )

                timeoutOptions.forEach { (seconds, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.setAutoLockTimeout(seconds) }
                            .padding(vertical = Spacing.extraSmall),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = uiState.autoLockTimeout == seconds,
                            onClick = null // Click handled by Row
                        )
                        Spacer(modifier = Modifier.width(Spacing.small))
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Test button (only show if available and enabled)
            if (uiState.availability == BiometricAvailability.AVAILABLE && uiState.isEnabled) {
                Spacer(modifier = Modifier.height(Spacing.medium))

                OutlinedButton(
                    onClick = {
                        activity?.let {
                            viewModel.testAuthentication(it)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Fingerprint, contentDescription = null)
                    Spacer(modifier = Modifier.width(Spacing.small))
                    Text("Test Authentication")
                }
            }

            // Error message
            if (uiState.errorMessage != null) {
                Spacer(modifier = Modifier.height(Spacing.medium))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = uiState.errorMessage!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(Spacing.medium)
                    )
                }
            }

            // Success message
            if (uiState.successMessage != null) {
                Spacer(modifier = Modifier.height(Spacing.medium))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Text(
                        text = uiState.successMessage!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(Spacing.medium)
                    )
                }
            }
        }
    }
}

/**
 * ViewModel for Access PIN screen.
 */
@HiltViewModel
class AccessPinViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
    private val biometricAuthManager: BiometricAuthManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(AccessPinUiState())
    val uiState: StateFlow<AccessPinUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            preferencesRepository.getBiometricAuthEnabled().collect { enabled ->
                _uiState.value = _uiState.value.copy(isEnabled = enabled)
            }
        }
        viewModelScope.launch {
            preferencesRepository.getAutoLockTimeout().collect { timeout ->
                _uiState.value = _uiState.value.copy(autoLockTimeout = timeout)
            }
        }
    }

    fun checkBiometricAvailability(activity: FragmentActivity) {
        val availability = biometricAuthManager.checkBiometricAvailability(activity)
        _uiState.value = _uiState.value.copy(availability = availability)
    }

    fun setBiometricEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setBiometricAuthEnabled(enabled)
            if (enabled) {
                _uiState.value = _uiState.value.copy(
                    successMessage = "Authentication enabled successfully",
                    errorMessage = null
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    successMessage = "Authentication disabled",
                    errorMessage = null
                )
            }
        }
    }

    fun testAuthentication(activity: FragmentActivity) {
        biometricAuthManager.authenticate(
            activity = activity,
            title = "Test Authentication",
            subtitle = "Verify your identity",
            description = "Use your device's security method to authenticate",
            onSuccess = {
                setBiometricEnabled(true)
            },
            onError = { error ->
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Authentication failed: $error",
                    successMessage = null
                )
            },
            onCancel = {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Authentication cancelled",
                    successMessage = null
                )
            }
        )
    }

    fun setAutoLockTimeout(timeoutSeconds: Int) {
        viewModelScope.launch {
            preferencesRepository.setAutoLockTimeout(timeoutSeconds)
        }
    }
}

/**
 * UI state for Access PIN screen.
 */
data class AccessPinUiState(
    val isEnabled: Boolean = false,
    val availability: BiometricAvailability = BiometricAvailability.UNKNOWN,
    val autoLockTimeout: Int = 60, // Default: 1 minute
    val errorMessage: String? = null,
    val successMessage: String? = null
)
