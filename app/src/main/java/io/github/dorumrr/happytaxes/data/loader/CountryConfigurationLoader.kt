package io.github.dorumrr.happytaxes.data.loader

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.dorumrr.happytaxes.data.config.CountryConfiguration
import io.github.dorumrr.happytaxes.data.config.CountryMetadata
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Loader for country-specific configuration files.
 *
 * Loads JSON configuration files from assets/countries/ directory.
 * Provides caching to avoid repeated file I/O operations.
 *
 * Available country configurations (31 total):
 * - Original: GB, US, CA, AU, NZ, RO
 * - Tier 2 (English-speaking): IE, SG, ZA, IN
 * - Tier 3 (European): DE, FR, NL, ES, IT, PL, SE, DK, NO, CH, AT, BE, CZ, GR, PT
 * - Tier 4 (Emerging): BR, MX, AR, AE, IL
 * - default.json - Universal categories for OTHER countries
 *
 * Usage:
 * ```
 * val config = loader.loadCountryConfig("GB")
 * val categories = config?.categories?.toEntities("GB")
 * ```
 */
@Singleton
class CountryConfigurationLoader @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = false
    }

    // Cache loaded configurations to avoid repeated file I/O
    private val configCache = mutableMapOf<String, CountryConfiguration?>()

    companion object {
        private const val TAG = "CountryConfigLoader"
    }

    /**
     * Load country configuration from JSON file.
     *
     * @param countryCode ISO 3166-1 alpha-2 country code (e.g., "GB", "US", "RO")
     *                    or "OTHER" for default configuration
     * @return CountryConfiguration or null if file not found or parsing failed
     */
    fun loadCountryConfig(countryCode: String): CountryConfiguration? {
        // Check cache first
        if (configCache.containsKey(countryCode)) {
            return configCache[countryCode]
        }

        // Map country code to filename (lowercase)
        val filename = when (countryCode.uppercase()) {
            // Original countries
            "GB" -> "gb.json"
            "US" -> "us.json"
            "CA" -> "ca.json"
            "AU" -> "au.json"
            "NZ" -> "nz.json"
            "RO" -> "ro.json"
            // New countries (Tier 2-4)
            "AR" -> "ar.json"
            "AT" -> "at.json"
            "BE" -> "be.json"
            "BR" -> "br.json"
            "CZ" -> "cz.json"
            "DK" -> "dk.json"
            "FR" -> "fr.json"
            "DE" -> "de.json"
            "GR" -> "gr.json"
            "IN" -> "in.json"
            "IE" -> "ie.json"
            "IL" -> "il.json"
            "IT" -> "it.json"
            "MX" -> "mx.json"
            "NL" -> "nl.json"
            "NO" -> "no.json"
            "PL" -> "pl.json"
            "PT" -> "pt.json"
            "SG" -> "sg.json"
            "ZA" -> "za.json"
            "ES" -> "es.json"
            "SE" -> "se.json"
            "CH" -> "ch.json"
            "AE" -> "ae.json"
            // Default/Other
            "OTHER" -> "default.json"
            else -> {
                Log.w(TAG, "Unknown country code: $countryCode, falling back to default.json")
                "default.json"
            }
        }

        return try {
            val jsonString = context.assets.open("countries/$filename").bufferedReader().use {
                it.readText()
            }
            val config = json.decodeFromString<CountryConfiguration>(jsonString)
            configCache[countryCode] = config
            Log.d(TAG, "Loaded country configuration for $countryCode from $filename")
            config
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load country configuration for $countryCode from $filename", e)
            configCache[countryCode] = null
            null
        }
    }

    /**
     * Get list of all available countries.
     *
     * @return List of CountryMetadata for all available country configurations
     */
    fun getAllAvailableCountries(): List<CountryMetadata> {
        val countryCodes = listOf(
            "GB", "US", "CA", "AU", "NZ", "RO",  // Original
            "IE", "SG", "ZA", "IN",  // Tier 2
            "DE", "FR", "NL", "ES", "IT", "PL", "SE", "DK", "NO", "CH", "AT", "BE", "CZ", "GR", "PT",  // Tier 3
            "BR", "MX", "AR", "AE", "IL",  // Tier 4
            "OTHER"  // Default
        )
        return countryCodes.mapNotNull { code ->
            loadCountryConfig(code)?.country
        }
    }

    /**
     * Clear the configuration cache.
     * Useful for testing or when configurations need to be reloaded.
     */
    fun clearCache() {
        configCache.clear()
        Log.d(TAG, "Country configuration cache cleared")
    }

    /**
     * Preload all country configurations into cache.
     * Can be called during app initialization to improve performance.
     */
    fun preloadAllConfigurations() {
        val countryCodes = listOf(
            "GB", "US", "CA", "AU", "NZ", "RO",  // Original
            "IE", "SG", "ZA", "IN",  // Tier 2
            "DE", "FR", "NL", "ES", "IT", "PL", "SE", "DK", "NO", "CH", "AT", "BE", "CZ", "GR", "PT",  // Tier 3
            "BR", "MX", "AR", "AE", "IL",  // Tier 4
            "OTHER"  // Default
        )
        countryCodes.forEach { code ->
            loadCountryConfig(code)
        }
        Log.d(TAG, "Preloaded ${configCache.size} country configurations")
    }
}

