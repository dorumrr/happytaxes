package io.github.dorumrr.happytaxes.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for app-wide preferences.
 *
 * Features:
 * - Onboarding completion status
 * - Profile management (current profile ID)
 * - Profile-specific preferences (tax period, country, currency, data retention, default transaction type)
 * - Global preferences (notifications, OCR, theme, add button position)
 * - Reminder tracking
 *
 * Uses DataStore for persistent storage.
 *
 * Profile-Specific Preferences:
 * - Stored with profile ID prefix: "profile_{profileId}_preference_name"
 * - Each profile has independent settings for: tax period, country, currency, data retention, default transaction type
 * - All profile-specific methods require profileId parameter (no backward compatibility)
 *
 * Global Preferences:
 * - Shared across all profiles: notifications, OCR, theme, add button position, reminder tracking
 *
 * PRD Reference: Section 4.11 - User Notifications & Reminders
 */
@Singleton
class PreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {

    companion object {
        // ========== GLOBAL PREFERENCES (shared across profiles) ==========

        // Onboarding
        private val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")

        // Profile management
        private val CURRENT_PROFILE_ID = stringPreferencesKey("current_profile_id") // Currently active profile ID

        // Notification preferences (GLOBAL)
        private val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        private val TRANSACTION_REMINDER_ENABLED = booleanPreferencesKey("transaction_reminder_enabled")
        private val TRANSACTION_REMINDER_FREQUENCY = stringPreferencesKey("transaction_reminder_frequency") // "daily" or "weekly"

        // Reminder tracking (GLOBAL - to avoid duplicate notifications)
        private val LAST_TRANSACTION_REMINDER_DATE = longPreferencesKey("last_transaction_reminder_date")
        private val LAST_TAX_YEAR_REMINDER_DATE = longPreferencesKey("last_tax_year_reminder_date")
        private val LAST_TAX_YEAR_REMINDER_TYPE = stringPreferencesKey("last_tax_year_reminder_type") // "30_days", "7_days", "end_date"
        private val LAST_RETENTION_WARNING_DATE = longPreferencesKey("last_retention_warning_date")

        // OCR settings (GLOBAL)
        private val OCR_ENABLED = booleanPreferencesKey("ocr_enabled")

        // Appearance (GLOBAL)
        private val THEME = stringPreferencesKey("theme") // "light", "dark", "auto"
        private val ADD_BUTTON_POSITION = stringPreferencesKey("add_button_position") // "top", "fab", or "both"

        // ========== PROFILE-SPECIFIC PREFERENCES ==========
        // These are stored with profile ID prefix: "profile_{profileId}_preference_name"

        // Profile-specific preference names (without profile prefix)
        private const val PREF_TAX_PERIOD_START = "tax_period_start" // Format: MM-DD (e.g., "01-01" for 1 Jan)
        private const val PREF_TAX_PERIOD_END = "tax_period_end" // Format: MM-DD (e.g., "12-31" for 31 Dec)
        private const val PREF_SELECTED_COUNTRY = "selected_country" // Country code (US, GB, CA, AU, NZ, RO, OTHER)
        private const val PREF_BASE_CURRENCY = "base_currency" // Currency for tax reporting (default USD)
        private const val PREF_DATA_RETENTION_YEARS = "data_retention_years" // 6-10 years
        private const val PREF_DEFAULT_TRANSACTION_TYPE = "default_transaction_type" // "income" or "expense"
        private const val PREF_DECIMAL_SEPARATOR = "decimal_separator" // "." or "," based on country
        private const val PREF_THOUSAND_SEPARATOR = "thousand_separator" // "," or "." based on country

        /**
         * Generate profile-specific preference key.
         */
        private fun profileKey(profileId: String, prefName: String): String {
            return "profile_${profileId}_${prefName}"
        }
    }

    /**
     * Check if onboarding is complete.
     */
    fun isOnboardingComplete(): Flow<Boolean> {
        return dataStore.data.map { preferences ->
            preferences[ONBOARDING_COMPLETE] ?: false
        }
    }

    /**
     * Mark onboarding as complete.
     */
    suspend fun setOnboardingComplete(complete: Boolean) {
        dataStore.edit { preferences ->
            preferences[ONBOARDING_COMPLETE] = complete
        }
    }

    // ========== PROFILE MANAGEMENT ==========

    /**
     * Get current profile ID.
     * Returns null if not set (should only happen on first launch).
     */
    fun getCurrentProfileId(): Flow<String?> {
        return dataStore.data.map { preferences ->
            preferences[CURRENT_PROFILE_ID]
        }
    }

    /**
     * Get current profile ID as suspend function.
     * Returns null if not set.
     */
    suspend fun getCurrentProfileIdOnce(): String? {
        return dataStore.data.map { preferences ->
            preferences[CURRENT_PROFILE_ID]
        }.first()
    }

    /**
     * Set current profile ID.
     * This switches the active profile for the entire app.
     */
    suspend fun setCurrentProfileId(profileId: String) {
        dataStore.edit { preferences ->
            preferences[CURRENT_PROFILE_ID] = profileId
        }
    }

    // ========== TAX YEAR & PERIOD (PROFILE-SPECIFIC) ==========

    /**
     * Get tax period start (MM-DD format) for a specific profile.
     *
     * @param profileId Profile ID
     * @return Tax period start date (MM-DD format), defaults to "01-01"
     */
    fun getTaxPeriodStart(profileId: String): Flow<String> {
        return dataStore.data.map { preferences ->
            val key = stringPreferencesKey(profileKey(profileId, PREF_TAX_PERIOD_START))
            preferences[key] ?: "01-01"
        }
    }

    /**
     * Get tax period end (MM-DD format) for a specific profile.
     *
     * @param profileId Profile ID
     * @return Tax period end date (MM-DD format), defaults to "12-31"
     */
    fun getTaxPeriodEnd(profileId: String): Flow<String> {
        return dataStore.data.map { preferences ->
            val key = stringPreferencesKey(profileKey(profileId, PREF_TAX_PERIOD_END))
            preferences[key] ?: "12-31"
        }
    }

    /**
     * Set tax period (start and end as MM-DD format) for a specific profile.
     *
     * @param profileId Profile ID
     * @param start Tax period start (MM-DD format)
     * @param end Tax period end (MM-DD format)
     */
    suspend fun setTaxPeriod(profileId: String, start: String, end: String) {
        dataStore.edit { preferences ->
            val startKey = stringPreferencesKey(profileKey(profileId, PREF_TAX_PERIOD_START))
            val endKey = stringPreferencesKey(profileKey(profileId, PREF_TAX_PERIOD_END))
            preferences[startKey] = start
            preferences[endKey] = end
        }
    }



    /**
     * Get current UK tax year.
     *
     * UK tax year runs from 6 April to 5 April.
     * Format: "2025-26" (for tax year 2025-2026)
     *
     * Examples:
     * - 2025-04-05 → "2024-25"
     * - 2025-04-06 → "2025-26"
     * - 2025-10-09 → "2025-26"
     */
    private fun getCurrentTaxYear(): String {
        val today = LocalDate.now()
        val taxYearStart = LocalDate.of(today.year, 4, 6) // 6 April

        return if (today.isBefore(taxYearStart)) {
            // Before 6 April → Previous tax year
            "${today.year - 1}-${today.year.toString().takeLast(2)}"
        } else {
            // On or after 6 April → Current tax year
            "${today.year}-${(today.year + 1).toString().takeLast(2)}"
        }
    }

    /**
     * Get selected country for a specific profile.
     *
     * @param profileId Profile ID
     * @return Country code (US, GB, CA, AU, NZ, RO, OTHER), defaults to "US"
     */
    fun getSelectedCountry(profileId: String): Flow<String> {
        return dataStore.data.map { preferences ->
            val key = stringPreferencesKey(profileKey(profileId, PREF_SELECTED_COUNTRY))
            preferences[key] ?: "US"
        }
    }

    /**
     * Set selected country for a specific profile.
     *
     * @param profileId Profile ID
     * @param country Country code (US, GB, CA, AU, NZ, RO, OTHER)
     */
    suspend fun setSelectedCountry(profileId: String, country: String) {
        dataStore.edit { preferences ->
            val key = stringPreferencesKey(profileKey(profileId, PREF_SELECTED_COUNTRY))
            preferences[key] = country
        }
    }

    /**
     * Get available tax years (last 3 years + current + next 2 years).
     *
     * @param countryCode Country code (US, GB, CA, AU, NZ, OTHER)
     * @return List of tax year strings in format based on country
     */
    fun getAvailableTaxYears(countryCode: String = "GB"): List<String> {
        val today = LocalDate.now()

        return when (countryCode) {
            "US", "CA" -> {
                // Calendar year (1 Jan - 31 Dec)
                val currentYear = today.year
                (-3..2).map { offset ->
                    (currentYear + offset).toString()
                }
            }
            "GB" -> {
                // UK tax year (6 Apr - 5 Apr)
                val taxYearStart = LocalDate.of(today.year, 4, 6)
                val baseYear = if (today.isBefore(taxYearStart)) {
                    today.year - 1
                } else {
                    today.year
                }
                (-3..2).map { offset ->
                    val year = baseYear + offset
                    "$year-${(year + 1).toString().takeLast(2)}"
                }
            }
            "AU" -> {
                // Australian tax year (1 Jul - 30 Jun)
                val taxYearStart = LocalDate.of(today.year, 7, 1)
                val baseYear = if (today.isBefore(taxYearStart)) {
                    today.year - 1
                } else {
                    today.year
                }
                (-3..2).map { offset ->
                    val year = baseYear + offset
                    "$year-${(year + 1).toString().takeLast(2)}"
                }
            }
            "NZ" -> {
                // New Zealand tax year (1 Apr - 31 Mar)
                val taxYearStart = LocalDate.of(today.year, 4, 1)
                val baseYear = if (today.isBefore(taxYearStart)) {
                    today.year - 1
                } else {
                    today.year
                }
                (-3..2).map { offset ->
                    val year = baseYear + offset
                    "$year-${(year + 1).toString().takeLast(2)}"
                }
            }
            else -> {
                // OTHER - return empty list (user will set custom dates)
                emptyList()
            }
        }
    }

    /**
     * Format tax year for display based on country.
     *
     * @param taxYear Tax year string
     * @param countryCode Country code (US, GB, CA, AU, NZ, OTHER)
     * @return Formatted tax year string
     */
    fun formatTaxYearForDisplay(taxYear: String, countryCode: String = "GB"): String {
        // Handle custom format for OTHER (YYYY-MM-DD_YYYY-MM-DD)
        if (taxYear.contains("_")) {
            val parts = taxYear.split("_")
            if (parts.size == 2) {
                return "${parts[0]} to ${parts[1]}"
            }
        }

        return when (countryCode) {
            "US", "CA" -> {
                // Calendar year: "2024" → "2024 (1 Jan - 31 Dec)"
                "$taxYear (1 Jan - 31 Dec)"
            }
            "GB" -> {
                // UK tax year: "2024-25" → "2024-25 (6 Apr 2024 - 5 Apr 2025)"
                val startYear = taxYear.substringBefore("-").toIntOrNull() ?: return taxYear
                val endYear = startYear + 1
                "$taxYear (6 Apr $startYear - 5 Apr $endYear)"
            }
            "AU" -> {
                // Australian tax year: "2024-25" → "2024-25 (1 Jul 2024 - 30 Jun 2025)"
                val startYear = taxYear.substringBefore("-").toIntOrNull() ?: return taxYear
                val endYear = startYear + 1
                "$taxYear (1 Jul $startYear - 30 Jun $endYear)"
            }
            "NZ" -> {
                // NZ tax year: "2024-25" → "2024-25 (1 Apr 2024 - 31 Mar 2025)"
                val startYear = taxYear.substringBefore("-").toIntOrNull() ?: return taxYear
                val endYear = startYear + 1
                "$taxYear (1 Apr $startYear - 31 Mar $endYear)"
            }
            else -> taxYear
        }
    }

    // ========== NOTIFICATION PREFERENCES ==========

    /**
     * Check if notifications are enabled.
     * Default: true
     */
    fun getNotificationsEnabled(): Flow<Boolean> {
        return dataStore.data.map { preferences ->
            preferences[NOTIFICATIONS_ENABLED] ?: true
        }
    }

    /**
     * Set notifications enabled/disabled.
     */
    suspend fun setNotificationsEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[NOTIFICATIONS_ENABLED] = enabled
        }
    }

    /**
     * Check if transaction reminders are enabled.
     * Default: true
     */
    fun getTransactionReminderEnabled(): Flow<Boolean> {
        return dataStore.data.map { preferences ->
            preferences[TRANSACTION_REMINDER_ENABLED] ?: true
        }
    }

    /**
     * Set transaction reminder enabled/disabled.
     */
    suspend fun setTransactionReminderEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[TRANSACTION_REMINDER_ENABLED] = enabled
        }
    }

    /**
     * Get transaction reminder frequency.
     * Default: "weekly"
     */
    fun getTransactionReminderFrequency(): Flow<String> {
        return dataStore.data.map { preferences ->
            preferences[TRANSACTION_REMINDER_FREQUENCY] ?: "weekly"
        }
    }

    /**
     * Set transaction reminder frequency.
     * @param frequency "daily" or "weekly"
     */
    suspend fun setTransactionReminderFrequency(frequency: String) {
        dataStore.edit { preferences ->
            preferences[TRANSACTION_REMINDER_FREQUENCY] = frequency
        }
    }

    // ========== REMINDER TRACKING ==========

    /**
     * Get last transaction reminder date.
     */
    fun getLastTransactionReminderDate(): Flow<LocalDate?> {
        return dataStore.data.map { preferences ->
            preferences[LAST_TRANSACTION_REMINDER_DATE]?.let { epochDay ->
                LocalDate.ofEpochDay(epochDay)
            }
        }
    }

    /**
     * Set last transaction reminder date.
     */
    suspend fun setLastTransactionReminderDate(date: LocalDate) {
        dataStore.edit { preferences ->
            preferences[LAST_TRANSACTION_REMINDER_DATE] = date.toEpochDay()
        }
    }

    /**
     * Get last tax year reminder date.
     */
    fun getLastTaxYearReminderDate(): Flow<LocalDate?> {
        return dataStore.data.map { preferences ->
            preferences[LAST_TAX_YEAR_REMINDER_DATE]?.let { epochDay ->
                LocalDate.ofEpochDay(epochDay)
            }
        }
    }

    /**
     * Set last tax year reminder date.
     */
    suspend fun setLastTaxYearReminderDate(date: LocalDate) {
        dataStore.edit { preferences ->
            preferences[LAST_TAX_YEAR_REMINDER_DATE] = date.toEpochDay()
        }
    }

    /**
     * Get last tax year reminder type.
     */
    fun getLastTaxYearReminderType(): Flow<String?> {
        return dataStore.data.map { preferences ->
            preferences[LAST_TAX_YEAR_REMINDER_TYPE]
        }
    }

    /**
     * Set last tax year reminder type.
     * @param type "30_days", "7_days", or "end_date"
     */
    suspend fun setLastTaxYearReminderType(type: String) {
        dataStore.edit { preferences ->
            preferences[LAST_TAX_YEAR_REMINDER_TYPE] = type
        }
    }

    // ========== GENERAL SETTINGS ==========

    /**
     * Get base currency (for tax reporting) for a specific profile.
     * Default: USD
     *
     * Note: All currencies are available for transactions.
     * Base currency is used for tax calculations and reports.
     *
     * @param profileId Profile ID
     * @return Currency code (USD, GBP, EUR, etc.)
     */
    fun getBaseCurrency(profileId: String): Flow<String> {
        return dataStore.data.map { preferences ->
            val key = stringPreferencesKey(profileKey(profileId, PREF_BASE_CURRENCY))
            preferences[key] ?: "USD"
        }
    }

    /**
     * Set base currency (for tax reporting) for a specific profile.
     *
     * @param profileId Profile ID
     * @param currency Currency code (USD, GBP, EUR, etc.)
     */
    suspend fun setBaseCurrency(profileId: String, currency: String) {
        dataStore.edit { preferences ->
            val key = stringPreferencesKey(profileKey(profileId, PREF_BASE_CURRENCY))
            preferences[key] = currency
        }
    }

    /**
     * Get default transaction type for a specific profile.
     * Default: expense
     *
     * @param profileId Profile ID
     * @return "income" or "expense"
     */
    fun getDefaultTransactionType(profileId: String): Flow<String> {
        return dataStore.data.map { preferences ->
            val key = stringPreferencesKey(profileKey(profileId, PREF_DEFAULT_TRANSACTION_TYPE))
            preferences[key] ?: "expense"
        }
    }

    /**
     * Set default transaction type for a specific profile.
     *
     * @param profileId Profile ID
     * @param type "income" or "expense"
     */
    suspend fun setDefaultTransactionType(profileId: String, type: String) {
        dataStore.edit { preferences ->
            val key = stringPreferencesKey(profileKey(profileId, PREF_DEFAULT_TRANSACTION_TYPE))
            preferences[key] = type
        }
    }

    /**
     * Get decimal separator for a specific profile.
     * Default: "." (period)
     *
     * @param profileId Profile ID
     * @return "." or ","
     */
    fun getDecimalSeparator(profileId: String): Flow<String> {
        return dataStore.data.map { preferences ->
            val key = stringPreferencesKey(profileKey(profileId, PREF_DECIMAL_SEPARATOR))
            preferences[key] ?: "."
        }
    }

    /**
     * Set decimal separator for a specific profile.
     *
     * @param profileId Profile ID
     * @param separator "." or ","
     */
    suspend fun setDecimalSeparator(profileId: String, separator: String) {
        dataStore.edit { preferences ->
            val key = stringPreferencesKey(profileKey(profileId, PREF_DECIMAL_SEPARATOR))
            preferences[key] = separator
        }
    }

    /**
     * Get thousand separator for a specific profile.
     * Default: "," (comma)
     *
     * @param profileId Profile ID
     * @return "," or "." or " " (space)
     */
    fun getThousandSeparator(profileId: String): Flow<String> {
        return dataStore.data.map { preferences ->
            val key = stringPreferencesKey(profileKey(profileId, PREF_THOUSAND_SEPARATOR))
            preferences[key] ?: ","
        }
    }

    /**
     * Set thousand separator for a specific profile.
     *
     * @param profileId Profile ID
     * @param separator "," or "." or " " (space)
     */
    suspend fun setThousandSeparator(profileId: String, separator: String) {
        dataStore.edit { preferences ->
            val key = stringPreferencesKey(profileKey(profileId, PREF_THOUSAND_SEPARATOR))
            preferences[key] = separator
        }
    }

    /**
     * Get add button position preference (GLOBAL).
     * Default: "fab" (bottom right FAB only)
     * Options: "top" (+ in top bar), "fab" (bottom FAB), "both" (both buttons)
     */
    fun getAddButtonPosition(): Flow<String> {
        return dataStore.data.map { preferences ->
            preferences[ADD_BUTTON_POSITION] ?: "fab"
        }
    }

    /**
     * Set add button position preference (GLOBAL).
     * @param position "top", "fab", or "both"
     */
    suspend fun setAddButtonPosition(position: String) {
        dataStore.edit { preferences ->
            preferences[ADD_BUTTON_POSITION] = position
        }
    }

    // ========== OCR SETTINGS ==========

    /**
     * Check if OCR is enabled.
     * Default: false (user must opt-in)
     */
    fun getOcrEnabled(): Flow<Boolean> {
        return dataStore.data.map { preferences ->
            preferences[OCR_ENABLED] ?: false
        }
    }

    /**
     * Set OCR enabled/disabled.
     */
    suspend fun setOcrEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[OCR_ENABLED] = enabled
        }
    }

    // ========== DATA RETENTION (PROFILE-SPECIFIC) ==========

    /**
     * Get data retention period in years for a specific profile.
     * Default: 6 years
     *
     * @param profileId Profile ID
     * @return Retention period in years (6-10)
     */
    fun getDataRetentionYears(profileId: String): Flow<Int> {
        return dataStore.data.map { preferences ->
            val key = intPreferencesKey(profileKey(profileId, PREF_DATA_RETENTION_YEARS))
            preferences[key] ?: 6
        }
    }

    /**
     * Set data retention period in years for a specific profile.
     *
     * @param profileId Profile ID
     * @param years 6, 7, 8, 9, or 10
     */
    suspend fun setDataRetentionYears(profileId: String, years: Int) {
        dataStore.edit { preferences ->
            val key = intPreferencesKey(profileKey(profileId, PREF_DATA_RETENTION_YEARS))
            preferences[key] = years
        }
    }

    /**
     * Get last retention warning date.
     */
    fun getLastRetentionWarningDate(): Flow<LocalDate?> {
        return dataStore.data.map { preferences ->
            preferences[LAST_RETENTION_WARNING_DATE]?.let { epochDay ->
                LocalDate.ofEpochDay(epochDay)
            }
        }
    }

    /**
     * Set last retention warning date.
     */
    suspend fun setLastRetentionWarningDate(date: LocalDate) {
        dataStore.edit { preferences ->
            preferences[LAST_RETENTION_WARNING_DATE] = date.toEpochDay()
        }
    }

    // ========== APPEARANCE ==========

    /**
     * Get theme preference.
     * Default: auto (follow system)
     */
    fun getTheme(): Flow<String> {
        return dataStore.data.map { preferences ->
            preferences[THEME] ?: "auto"
        }
    }

    /**
     * Set theme preference.
     * @param theme "light", "dark", or "auto"
     */
    suspend fun setTheme(theme: String) {
        dataStore.edit { preferences ->
            preferences[THEME] = theme
        }
    }

    // ========== APP RESET ==========

    /**
     * Clear all preferences (used for app reset).
     * This will reset the app to factory defaults and require onboarding again.
     */
    suspend fun clearAllPreferences() {
        dataStore.edit { preferences ->
            preferences.clear()
        }
    }
}

