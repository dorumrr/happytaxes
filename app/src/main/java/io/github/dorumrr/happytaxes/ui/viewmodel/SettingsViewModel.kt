package io.github.dorumrr.happytaxes.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.dorumrr.happytaxes.BuildConfig
import io.github.dorumrr.happytaxes.data.loader.CountryConfigurationLoader
import io.github.dorumrr.happytaxes.data.repository.CategoryRepository
import io.github.dorumrr.happytaxes.data.repository.PreferencesRepository
import io.github.dorumrr.happytaxes.data.repository.ProfileContext
import io.github.dorumrr.happytaxes.data.repository.ProfileRepository
import io.github.dorumrr.happytaxes.data.repository.TransactionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/**
 * ViewModel for Settings screen.
 *
 * Features:
 * - General settings (currency, date format, transaction type)
 * - Notification settings (enable/disable, reminder frequency)
 * - Appearance settings (theme, font size)
 * - About & Support (version, user manual, feedback, reset app)
 *
 * PRD Reference: Section 4.17 - Settings & Preferences
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferencesRepository: PreferencesRepository,
    private val profileContext: ProfileContext,
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val profileRepository: ProfileRepository,
    private val countryConfigLoader: CountryConfigurationLoader
) : ViewModel() {

    // UI State
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    // Restart callback - set by SettingsScreen
    private var restartCallback: (() -> Unit)? = null

    init {
        loadSettings()
    }

    /**
     * Set the restart callback to be called after reset completes.
     */
    fun setRestartCallback(callback: () -> Unit) {
        restartCallback = callback
    }

    /**
     * Load all settings from preferences.
     */
    private fun loadSettings() {
        viewModelScope.launch {
            val profileId = profileContext.getCurrentProfileIdOnce()

            // Combine tax period flows first
            val taxPeriodSettings = combine(
                preferencesRepository.getTaxPeriodStart(profileId),
                preferencesRepository.getTaxPeriodEnd(profileId)
            ) { start, end ->
                TaxPeriodSettings(start, end)
            }

            // Combine first 5 flows
            val generalSettings = combine(
                preferencesRepository.getSelectedCountry(profileId),
                preferencesRepository.getBaseCurrency(profileId),
                preferencesRepository.getDefaultTransactionType(profileId),
                preferencesRepository.getAddButtonPosition(),
                taxPeriodSettings
            ) { country, baseCurrency, defaultType, addButtonPosition, taxPeriod ->
                GeneralSettings(country, taxPeriod.start, taxPeriod.end, baseCurrency, defaultType, addButtonPosition)
            }

            // Combine next 5 flows
            val otherSettings = combine(
                preferencesRepository.getNotificationsEnabled(),
                preferencesRepository.getTransactionReminderEnabled(),
                preferencesRepository.getTransactionReminderFrequency(),
                preferencesRepository.getOcrEnabled(),
                preferencesRepository.getDataRetentionYears(profileId)
            ) { notificationsEnabled, reminderEnabled, reminderFrequency, ocrEnabled, retentionYears ->
                OtherSettings(notificationsEnabled, reminderEnabled, reminderFrequency, ocrEnabled, retentionYears)
            }

            // Get theme separately (to avoid exceeding 5 flows limit)
            val themeFlow = preferencesRepository.getTheme()

            // Combine the three groups
            combine(generalSettings, otherSettings, themeFlow) { general, other, theme ->
                // Parse tax period start/end to extract month and day
                val (startMonth, startDay) = parseTaxPeriod(general.taxPeriodStart)
                val (endMonth, endDay) = parseTaxPeriod(general.taxPeriodEnd)

                SettingsUiState(
                    selectedCountry = general.country,
                    taxPeriodStart = general.taxPeriodStart,
                    taxPeriodEnd = general.taxPeriodEnd,
                    taxPeriodStartMonth = startMonth,
                    taxPeriodStartDay = startDay,
                    taxPeriodEndMonth = endMonth,
                    taxPeriodEndDay = endDay,
                    baseCurrency = general.baseCurrency,
                    defaultTransactionType = general.defaultType,
                    addButtonPosition = general.addButtonPosition,
                    notificationsEnabled = other.notificationsEnabled,
                    transactionReminderEnabled = other.reminderEnabled,
                    transactionReminderFrequency = other.reminderFrequency,
                    ocrEnabled = other.ocrEnabled,
                    dataRetentionYears = other.retentionYears,
                    theme = theme,
                    appVersion = BuildConfig.VERSION_NAME,
                    isLoading = false
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    /**
     * Parse tax period string (MM-DD) to month and day.
     */
    private fun parseTaxPeriod(period: String): Pair<Int, Int> {
        val parts = period.split("-")
        return if (parts.size == 2) {
            Pair(parts[0].toIntOrNull() ?: 1, parts[1].toIntOrNull() ?: 1)
        } else {
            Pair(1, 1) // Default: 1 Jan
        }
    }

    // Helper data classes for combining flows
    private data class TaxPeriodSettings(
        val start: String,
        val end: String
    )

    private data class GeneralSettings(
        val country: String,
        val taxPeriodStart: String,
        val taxPeriodEnd: String,
        val baseCurrency: String,
        val defaultType: String,
        val addButtonPosition: String
    )

    private data class OtherSettings(
        val notificationsEnabled: Boolean,
        val reminderEnabled: Boolean,
        val reminderFrequency: String,
        val ocrEnabled: Boolean,
        val retentionYears: Int
    )

    /**
     * Get default tax period for country (returns Pair of start and end as month/day pairs).
     */
    private fun getDefaultTaxPeriodForCountry(countryCode: String): Pair<Pair<Int, Int>, Pair<Int, Int>> {
        return when (countryCode) {
            "US", "CA", "RO" -> Pair(Pair(1, 1), Pair(12, 31)) // 1 Jan - 31 Dec
            "GB" -> Pair(Pair(4, 6), Pair(4, 5)) // 6 Apr - 5 Apr
            "AU" -> Pair(Pair(7, 1), Pair(6, 30)) // 1 Jul - 30 Jun
            "NZ" -> Pair(Pair(4, 1), Pair(3, 31)) // 1 Apr - 31 Mar
            "IN" -> Pair(Pair(4, 1), Pair(3, 31)) // 1 Apr - 31 Mar
            "SG" -> Pair(Pair(1, 1), Pair(12, 31)) // 1 Jan - 31 Dec
            "HK" -> Pair(Pair(4, 1), Pair(3, 31)) // 1 Apr - 31 Mar
            "IE" -> Pair(Pair(1, 1), Pair(12, 31)) // 1 Jan - 31 Dec
            "ZA" -> Pair(Pair(3, 1), Pair(2, 28)) // 1 Mar - 28 Feb
            else -> Pair(Pair(1, 1), Pair(12, 31)) // Default: 1 Jan - 31 Dec
        }
    }

    /**
     * Get default currency for country (if available in our curated list).
     * Returns null if country's currency is not in our list.
     */
    private fun getDefaultCurrencyForCountry(countryCode: String): String? {
        // Our curated currency list
        val availableCurrencies = setOf(
            "AED", "AUD", "BRL", "CAD", "CHF", "CZK", "DKK", "EUR", "GBP",
            "HKD", "ILS", "INR", "JPY", "KRW", "MXN", "MYR", "NOK", "NZD",
            "PLN", "RON", "SEK", "SGD", "TRY", "USD", "ZAR"
        )

        // Map countries to their currencies
        val currency = when (countryCode) {
            "US" -> "USD"
            "GB" -> "GBP"
            "CA" -> "CAD"
            "AU" -> "AUD"
            "NZ" -> "NZD"
            "IN" -> "INR"
            "SG" -> "SGD"
            "HK" -> "HKD"
            "IE" -> "EUR"
            "ZA" -> "ZAR"
            "JP" -> "JPY"
            "KR" -> "KRW"
            "CN" -> null // CNY not in our list
            "BR" -> "BRL"
            "MX" -> "MXN"
            "CH" -> "CHF"
            "NO" -> "NOK"
            "SE" -> "SEK"
            "DK" -> "DKK"
            "PL" -> "PLN"
            "CZ" -> "CZK"
            "RO" -> "RON"
            "TR" -> "TRY"
            "IL" -> "ILS"
            "MY" -> "MYR"
            "AE" -> "AED"
            else -> null // Unknown or not in our list
        }

        // Only return if currency is in our available list
        return if (currency != null && availableCurrencies.contains(currency)) {
            currency
        } else {
            null
        }
    }

    /**
     * Set selected country and auto-update tax period, currency, and decimal separators.
     */
    fun setCountry(country: String) {
        viewModelScope.launch {
            val profileId = profileContext.getCurrentProfileIdOnce()
            preferencesRepository.setSelectedCountry(profileId, country)

            // Load country configuration to get decimal separators
            val config = countryConfigLoader.loadCountryConfig(country)

            // Auto-update tax period based on country
            val (start, end) = getDefaultTaxPeriodForCountry(country)
            val startStr = String.format("%02d-%02d", start.first, start.second)
            val endStr = String.format("%02d-%02d", end.first, end.second)
            preferencesRepository.setTaxPeriod(profileId, startStr, endStr)

            // Auto-update currency if available for this country
            val currency = getDefaultCurrencyForCountry(country)
            if (currency != null) {
                preferencesRepository.setBaseCurrency(profileId, currency)
            }

            // Auto-update decimal and thousand separators from country config
            if (config != null) {
                preferencesRepository.setDecimalSeparator(profileId, config.currency.decimalSeparator)
                preferencesRepository.setThousandSeparator(profileId, config.currency.thousandSeparator)
            }
        }
    }

    /**
     * Set tax period (start and end as month/day).
     */
    fun setTaxPeriod(startMonth: Int, startDay: Int, endMonth: Int, endDay: Int) {
        viewModelScope.launch {
            val profileId = profileContext.getCurrentProfileIdOnce()
            val start = String.format("%02d-%02d", startMonth, startDay)
            val end = String.format("%02d-%02d", endMonth, endDay)
            preferencesRepository.setTaxPeriod(profileId, start, end)
        }
    }

    /**
     * Set base currency (for tax reporting).
     * All currencies are available for transactions.
     */
    fun setBaseCurrency(currency: String) {
        viewModelScope.launch {
            val profileId = profileContext.getCurrentProfileIdOnce()
            preferencesRepository.setBaseCurrency(profileId, currency)
        }
    }

    /**
     * Format tax period for display (e.g., "1 Jan - 31 Dec").
     */
    fun formatTaxPeriodForDisplay(): String {
        val monthNames = listOf(
            "Jan", "Feb", "Mar", "Apr", "May", "Jun",
            "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
        )

        val startMonth = _uiState.value.taxPeriodStartMonth
        val startDay = _uiState.value.taxPeriodStartDay
        val endMonth = _uiState.value.taxPeriodEndMonth
        val endDay = _uiState.value.taxPeriodEndDay

        return "$startDay ${monthNames[startMonth - 1]} - $endDay ${monthNames[endMonth - 1]}"
    }

    /**
     * Set notifications enabled/disabled.
     */
    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setNotificationsEnabled(enabled)
        }
    }

    /**
     * Set transaction reminder enabled/disabled.
     */
    fun setTransactionReminderEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setTransactionReminderEnabled(enabled)
        }
    }

    /**
     * Set transaction reminder frequency.
     */
    fun setTransactionReminderFrequency(frequency: String) {
        viewModelScope.launch {
            preferencesRepository.setTransactionReminderFrequency(frequency)
        }
    }

    /**
     * Set default transaction type.
     */
    fun setDefaultTransactionType(type: String) {
        viewModelScope.launch {
            val profileId = profileContext.getCurrentProfileIdOnce()
            preferencesRepository.setDefaultTransactionType(profileId, type)
        }
    }

    /**
     * Set add button position.
     */
    fun setAddButtonPosition(position: String) {
        viewModelScope.launch {
            preferencesRepository.setAddButtonPosition(position)
        }
    }

    /**
     * Set OCR enabled/disabled.
     */
    fun setOcrEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setOcrEnabled(enabled)
        }
    }

    /**
     * Set data retention period in years.
     */
    fun setDataRetentionYears(years: Int) {
        viewModelScope.launch {
            val profileId = profileContext.getCurrentProfileIdOnce()
            preferencesRepository.setDataRetentionYears(profileId, years)
        }
    }

    /**
     * Set theme preference.
     */
    fun setTheme(theme: String) {
        viewModelScope.launch {
            preferencesRepository.setTheme(theme)
        }
    }

    /**
     * Get available currencies (curated list of 25 most likely currencies).
     * Returns list sorted with pre-selected first, then alphabetically.
     */
    fun getAvailableCurrencies(): List<String> {
        val allCurrencies = listOf(
            "AED", "AUD", "BRL", "CAD", "CHF", "CZK", "DKK", "EUR", "GBP",
            "HKD", "ILS", "INR", "JPY", "KRW", "MXN", "MYR", "NOK", "NZD",
            "PLN", "RON", "SEK", "SGD", "TRY", "USD", "ZAR"
        )

        // Sort: pre-selected first, then alphabetically
        val currentCurrency = _uiState.value.baseCurrency
        return if (currentCurrency.isNotEmpty() && allCurrencies.contains(currentCurrency)) {
            listOf(currentCurrency) + allCurrencies.filter { it != currentCurrency }.sorted()
        } else {
            allCurrencies.sorted()
        }
    }

    /**
     * Get available countries.
     */
    fun getAvailableCountries(): List<Pair<String, String>> {
        return listOf(
            "US" to "United States of America",
            "GB" to "United Kingdom",
            "CA" to "Canada",
            "AU" to "Australia",
            "NZ" to "New Zealand",
            "RO" to "Romania",
            "OTHER" to "Other"
        )
    }



    /**
     * Show reset app confirmation dialog.
     */
    fun showResetAppDialog() {
        _uiState.value = _uiState.value.copy(showResetAppDialog = true)
    }

    /**
     * Hide reset app confirmation dialog.
     */
    fun hideResetAppDialog() {
        _uiState.value = _uiState.value.copy(showResetAppDialog = false)
    }

    /**
     * Reset app (dangerous operation).
     * Wipes ALL data and returns to onboarding screen.
     *
     * Steps:
     * 1. Reset ALL data (profiles, transactions, categories, receipts, search history)
     * 2. Clear ALL preferences (including onboarding_complete flag)
     * 3. Cancel ALL WorkManager jobs (clean slate)
     * 4. Restart the app to show onboarding (like fresh install)
     *
     * After reset, app will restart and show onboarding screen immediately.
     */
    fun resetApp() {
        viewModelScope.launch {
            try {
                // Hide dialog first
                _uiState.value = _uiState.value.copy(showResetAppDialog = false)

                // 1. Reset ALL data via ProfileRepository
                //    This deletes: transactions, categories, search history, receipt files, profiles
                val result = profileRepository.resetAllData()
                if (result.isFailure) {
                    // Log error but continue with other cleanup
                    result.exceptionOrNull()?.let { error ->
                        error.printStackTrace()
                    }
                }

                // 2. Clear ALL preferences (this resets onboarding_complete to false)
                preferencesRepository.clearAllPreferences()

                // 3. Cancel ALL WorkManager jobs for clean slate
                //    Jobs will be rescheduled on next app launch
                WorkManager.getInstance(context).cancelAllWork()

                // 4. Trigger app restart via callback
                //    This ensures MainActivity rechecks onboarding_complete flag
                restartCallback?.invoke()
            } catch (e: Exception) {
                // Log error but still hide dialog
                e.printStackTrace()
                hideResetAppDialog()
            }
        }
    }
}

/**
 * UI state for Settings screen.
 *
 * Note: All currencies available by default.
 * baseCurrency is used for tax reporting (default USD).
 */
data class SettingsUiState(
    val selectedCountry: String = "US", // Country code
    val taxPeriodStart: String = "01-01", // MM-DD format
    val taxPeriodEnd: String = "12-31", // MM-DD format
    val taxPeriodStartMonth: Int = 1,
    val taxPeriodStartDay: Int = 1,
    val taxPeriodEndMonth: Int = 12,
    val taxPeriodEndDay: Int = 31,
    val baseCurrency: String = "USD", // Currency for tax reporting
    val defaultTransactionType: String = "expense",
    val addButtonPosition: String = "fab", // "top", "fab", or "both"
    val notificationsEnabled: Boolean = true,
    val transactionReminderEnabled: Boolean = false,
    val transactionReminderFrequency: String = "daily",
    val ocrEnabled: Boolean = true,
    val dataRetentionYears: Int = 6,
    val theme: String = "auto",
    val appVersion: String = "",
    val isLoading: Boolean = true,
    val showResetAppDialog: Boolean = false
)

