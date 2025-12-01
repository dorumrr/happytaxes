package io.github.dorumrr.happytaxes.util

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import androidx.core.content.ContextCompat
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manager for biometric authentication.
 *
 * Handles:
 * - Checking biometric availability
 * - Showing biometric authentication prompts
 * - Using device credentials (PIN, pattern, password) as fallback
 *
 * Uses Android's BiometricPrompt API which supports:
 * - Fingerprint
 * - Face unlock
 * - Iris scan
 * - Device PIN/pattern/password (as fallback)
 *
 * Note: This is a singleton to ensure consistent state across the app.
 */
@Singleton
class BiometricAuthManager @Inject constructor() {

    /**
     * Check if biometric authentication is available on this device.
     *
     * @param activity The activity context
     * @return BiometricAvailability status
     */
    fun checkBiometricAvailability(activity: FragmentActivity): BiometricAvailability {
        val biometricManager = BiometricManager.from(activity)
        
        return when (biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or 
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        )) {
            BiometricManager.BIOMETRIC_SUCCESS -> 
                BiometricAvailability.AVAILABLE
            
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> 
                BiometricAvailability.NO_HARDWARE
            
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> 
                BiometricAvailability.HARDWARE_UNAVAILABLE
            
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> 
                BiometricAvailability.NONE_ENROLLED
            
            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED ->
                BiometricAvailability.SECURITY_UPDATE_REQUIRED
            
            BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED ->
                BiometricAvailability.UNSUPPORTED
            
            BiometricManager.BIOMETRIC_STATUS_UNKNOWN ->
                BiometricAvailability.UNKNOWN
            
            else -> BiometricAvailability.UNKNOWN
        }
    }

    /**
     * Show biometric authentication prompt.
     *
     * @param activity The activity context
     * @param title Title for the prompt
     * @param subtitle Subtitle for the prompt (optional)
     * @param description Description for the prompt (optional)
     * @param onSuccess Callback when authentication succeeds
     * @param onError Callback when authentication fails with error message
     * @param onCancel Callback when user cancels authentication
     */
    fun authenticate(
        activity: FragmentActivity,
        title: String = "Authenticate",
        subtitle: String? = null,
        description: String? = null,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
        onCancel: () -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        
        val biometricPrompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    
                    // User cancelled
                    if (errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                        errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                        onCancel()
                    } else {
                        onError(errString.toString())
                    }
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    // Don't call onError here - this is called for each failed attempt
                    // The prompt will stay open for retry
                }
            }
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .apply {
                subtitle?.let { setSubtitle(it) }
                description?.let { setDescription(it) }
            }
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or 
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()

        biometricPrompt.authenticate(promptInfo)
    }
}

/**
 * Biometric availability status.
 */
enum class BiometricAvailability {
    /** Biometric authentication is available and ready to use */
    AVAILABLE,
    
    /** No biometric hardware available on this device */
    NO_HARDWARE,
    
    /** Biometric hardware is unavailable (e.g., disabled by admin) */
    HARDWARE_UNAVAILABLE,
    
    /** No biometric credentials enrolled (user needs to set up fingerprint/face) */
    NONE_ENROLLED,
    
    /** Security update required */
    SECURITY_UPDATE_REQUIRED,
    
    /** Biometric authentication is not supported */
    UNSUPPORTED,
    
    /** Status unknown */
    UNKNOWN
}

