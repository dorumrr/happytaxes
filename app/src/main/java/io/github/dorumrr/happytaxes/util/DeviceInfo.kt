package io.github.dorumrr.happytaxes.util

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Utility class for generating and caching device information.
 *
 * Provides unique device ID and human-readable device name for device lock mechanism.
 *
 * Device ID is generated from ANDROID_ID + hash for uniqueness and cached in SharedPreferences.
 * Device name is generated from manufacturer + model (e.g., "Google Pixel 7 Pro").
 *
 * Note: Uses standard SharedPreferences (not encrypted) as device info is not sensitive.
 * Device-level security (PIN/biometric) provides adequate protection.
 *
 * PRD Reference: Section 4.13 (Multi-Device Strategy)
 */
@Singleton
class DeviceInfo @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val PREFS_NAME = "device_info_prefs"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_DEVICE_NAME = "device_name"
    }
    
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    /**
     * Get unique device ID.
     * Generated from ANDROID_ID + hash, cached in SharedPreferences.
     *
     * Format: "pixel7_abc123def456"
     */
    val deviceId: String by lazy {
        prefs.getString(KEY_DEVICE_ID, null) ?: generateDeviceId().also { id ->
            prefs.edit().putString(KEY_DEVICE_ID, id).apply()
        }
    }
    
    /**
     * Get human-readable device name.
     * Generated from manufacturer + model, cached in SharedPreferences.
     *
     * Format: "Google Pixel 7 Pro"
     */
    val deviceName: String by lazy {
        prefs.getString(KEY_DEVICE_NAME, null) ?: generateDeviceName().also { name ->
            prefs.edit().putString(KEY_DEVICE_NAME, name).apply()
        }
    }
    
    /**
     * Generate unique device ID from ANDROID_ID + hash.
     * 
     * Uses Settings.Secure.ANDROID_ID which is:
     * - Unique per device
     * - Persists across app reinstalls
     * - Unique per app on Android 8.0+
     * 
     * Adds SHA-256 hash for additional uniqueness.
     */
    private fun generateDeviceId(): String {
        val androidId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        ) ?: "unknown"
        
        // Create hash from ANDROID_ID + package name for uniqueness
        val input = "$androidId-${context.packageName}"
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
            .take(12) // Take first 12 characters
        
        // Format: model_hash (e.g., "pixel7_abc123def456")
        val modelName = Build.MODEL.lowercase()
            .replace(" ", "")
            .replace("-", "")
            .take(10)
        
        return "${modelName}_${hash}"
    }
    
    /**
     * Generate human-readable device name from manufacturer + model.
     * 
     * Examples:
     * - "Google Pixel 7 Pro"
     * - "Samsung Galaxy S23"
     * - "OnePlus 11"
     */
    private fun generateDeviceName(): String {
        val manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
        val model = Build.MODEL
        
        // If model already contains manufacturer, don't duplicate
        return if (model.startsWith(manufacturer, ignoreCase = true)) {
            model
        } else {
            "$manufacturer $model"
        }
    }
}

