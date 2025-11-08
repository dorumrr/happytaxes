package io.github.dorumrr.happytaxes.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.dorumrr.happytaxes.data.repository.CategoryRepository
import io.github.dorumrr.happytaxes.data.repository.PreferencesRepository
import io.github.dorumrr.happytaxes.data.repository.ProfileRepository
import io.github.dorumrr.happytaxes.domain.model.Category
import io.github.dorumrr.happytaxes.domain.model.Transaction
import io.github.dorumrr.happytaxes.domain.model.TransactionType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import java.time.ZoneId
import java.util.UUID

/**
 * ViewModel for onboarding flow.
 *
 * Features:
 * - Step navigation
 * - Country selection (UK, USA, Australia, NZ, Canada, Other)
 * - Smart defaults based on country (tax year, currency)
 * - Permission states
 * - Category setup (user must create at least 1 income + 1 expense category)
 * - Onboarding completion
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
    private val profileRepository: ProfileRepository,
    private val categoryRepository: CategoryRepository,
    private val transactionRepository: io.github.dorumrr.happytaxes.data.repository.TransactionRepository,
    private val demoReceiptGenerator: io.github.dorumrr.happytaxes.util.DemoReceiptGenerator
) : ViewModel() {

    // Current onboarding step
    private val _currentStep = MutableStateFlow(OnboardingStep.WELCOME)
    val currentStep: StateFlow<OnboardingStep> = _currentStep.asStateFlow()

    // Selected country
    private val _selectedCountry = MutableStateFlow("US") // Default: United States
    val selectedCountry: StateFlow<String> = _selectedCountry.asStateFlow()

    // Tax period start (month and day)
    private val _taxPeriodStartMonth = MutableStateFlow(1) // January
    val taxPeriodStartMonth: StateFlow<Int> = _taxPeriodStartMonth.asStateFlow()

    private val _taxPeriodStartDay = MutableStateFlow(1) // 1st
    val taxPeriodStartDay: StateFlow<Int> = _taxPeriodStartDay.asStateFlow()

    // Tax period end (month and day)
    private val _taxPeriodEndMonth = MutableStateFlow(12) // December
    val taxPeriodEndMonth: StateFlow<Int> = _taxPeriodEndMonth.asStateFlow()

    private val _taxPeriodEndDay = MutableStateFlow(31) // 31st
    val taxPeriodEndDay: StateFlow<Int> = _taxPeriodEndDay.asStateFlow()

    // Selected base currency (auto-filled based on country)
    private val _selectedBaseCurrency = MutableStateFlow("USD")
    val selectedBaseCurrency: StateFlow<String> = _selectedBaseCurrency.asStateFlow()

    // Permission states
    private val _cameraPermissionGranted = MutableStateFlow(false)
    val cameraPermissionGranted: StateFlow<Boolean> = _cameraPermissionGranted.asStateFlow()

    private val _storagePermissionGranted = MutableStateFlow(false)
    val storagePermissionGranted: StateFlow<Boolean> = _storagePermissionGranted.asStateFlow()

    private val _notificationPermissionGranted = MutableStateFlow(false)
    val notificationPermissionGranted: StateFlow<Boolean> = _notificationPermissionGranted.asStateFlow()

    // Category setup state
    private val _categories = MutableStateFlow<List<Category>>(emptyList())
    val categories: StateFlow<List<Category>> = _categories.asStateFlow()

    private val _hasMinimumCategories = MutableStateFlow(false)
    val hasMinimumCategories: StateFlow<Boolean> = _hasMinimumCategories.asStateFlow()

    private val _categoryError = MutableStateFlow<String?>(null)
    val categoryError: StateFlow<String?> = _categoryError.asStateFlow()

    // Track if profiles have been created to avoid recreating them
    private var profilesCreated = false

    // Seeding flag for demo data
    private val _seedingInProgress = MutableStateFlow(false)
    val seedingInProgress: StateFlow<Boolean> = _seedingInProgress.asStateFlow()

    // Seeding progress counter
    private val _seedingProgress = MutableStateFlow("")
    val seedingProgress: StateFlow<String> = _seedingProgress.asStateFlow()

    // Debouncing flag to prevent double-clicks on navigation buttons
    private var isNavigating = false

    /**
     * Seed demo categories and ~500 transactions over the last 8 years up to yesterday.
     * - Adds 2 income and 8 expense categories (skips existing by name)
     * - Distributes transactions naturally across months/categories with varied descriptions/amounts
     * - Never creates transactions with future dates (always stops at yesterday)
     */
    fun seedDemoData() {
        if (_seedingInProgress.value) {
            android.util.Log.d("OnboardingViewModel", "seedDemoData: Already seeding, ignoring request")
            return
        }
        viewModelScope.launch {
            android.util.Log.d("OnboardingViewModel", "seedDemoData: Starting demo data seeding")
            _seedingInProgress.value = true
            _seedingProgress.value = "Creating transactions across 7 years..."
            try {
                // Ensure categories exist (2 income + 6 expense)
                val incomeCategories = listOf(
                    "Sales/Turnover", "Other Income"
                )
                val expenseCategories = listOf(
                    "Office Supplies",
                    "Travel",
                    "Meals & Entertainment",
                    "Professional Services",
                    "Advertising & Marketing",
                    "Bank Charges"
                )

                // Create categories if missing
                val existing = categoryRepository.getAllActive().first()
                val existingNames = existing.map { it.name }.toSet()

                val newIncomeCategories = incomeCategories.filter { it !in existingNames }
                val newExpenseCategories = expenseCategories.filter { it !in existingNames }

                android.util.Log.d("OnboardingViewModel", "seedDemoData: Creating ${newIncomeCategories.size} income + ${newExpenseCategories.size} expense categories")

                newIncomeCategories.forEach {
                    categoryRepository.createCategory(it, TransactionType.INCOME)
                }
                newExpenseCategories.forEach {
                    categoryRepository.createCategory(it, TransactionType.EXPENSE)
                }

                // Refresh categories state in UI and wait for completion
                android.util.Log.d("OnboardingViewModel", "seedDemoData: Loading categories after creation")
                loadCategoriesAndWait()

                // If already heavily populated, avoid duplicating massive data
                val currentCount = transactionRepository.getAllActive().first().size
                if (currentCount >= 3000) {
                    return@launch
                }

                // Build the final category name pools (use names as stored in transactions)
                val finalCats = categoryRepository.getAllActive().first()
                val incomePool = finalCats.filter { it.type == TransactionType.INCOME }.map { it.name }
                val expensePool = finalCats.filter { it.type == TransactionType.EXPENSE }.map { it.name }

                // Date range: last 7 years up to yesterday (never seed future dates)
                val today = java.time.LocalDate.now()
                val yesterday = today.minusDays(1)
                val endYm = java.time.YearMonth.from(yesterday)
                val startYm = endYm.minusYears(7)
                val endDate = yesterday

                android.util.Log.d("HappyTaxes", "seedDemoData: today=$today, yesterday=$yesterday, endDate=$endDate")

                // Generate transactions across months with 30–42 per month (~3000 total over 7 years)
                val months = mutableListOf<java.time.YearMonth>()
                var ym = startYm
                while (!ym.isAfter(endYm)) {
                    months.add(ym)
                    ym = ym.plusMonths(1)
                }

                val rnd = kotlin.random.Random(System.currentTimeMillis())
                val zoneId = ZoneId.systemDefault()

                // Get current profile for receipt generation
                val profileId = preferencesRepository.getCurrentProfileIdOnce() ?: "default"

                // Get currency and separators for demo data formatting
                val baseCurrency = preferencesRepository.getBaseCurrency(profileId).first()
                val decimalSeparator = preferencesRepository.getDecimalSeparator(profileId).first()
                val thousandSeparator = preferencesRepository.getThousandSeparator(profileId).first()

                fun randomAmount(min: Int, max: Int): java.math.BigDecimal {
                    val cents = rnd.nextInt(min * 100, max * 100 + 1)
                    return java.math.BigDecimal(cents).divide(java.math.BigDecimal(100))
                }

                val incomeDesc = listOf(
                    { n: Int -> "Invoice #$n - Consulting services" },
                    { _: Int -> "Stripe payout" },
                    { _: Int -> "YouTube AdSense" },
                    { _: Int -> "Patreon payout" },
                    { _: Int -> "Etsy sales" },
                    { n: Int -> "Sale to client ${listOf("Acme Ltd","Globex","Initech","Umbrella").random(rnd)} #$n" }
                )
                val expenseDesc = listOf(
                    { _: Int -> "Uber trip" },
                    { _: Int -> "Office supplies at Staples" },
                    { _: Int -> "Coffee with client" },
                    { _: Int -> "Google Workspace subscription" },
                    { _: Int -> "Facebook Ads" },
                    { _: Int -> "GitHub subscription" },
                    { _: Int -> "Printer ink" },
                    { _: Int -> "Parking fee" },
                    { _: Int -> "Fuel" },
                    { _: Int -> "Website hosting" }
                )

                var invoiceSeq = 1001

                var draftsRemaining = 3
                val transactionsToInsert = mutableListOf<Transaction>()
                var totalTransactionsCreated = 0
                val estimatedTotal = months.size * 36 // Rough estimate: ~36 per month for ~3000 total

                months.forEach { month ->
                    val thisMonthCount = rnd.nextInt(30, 43) // 30–42 per month (~3000 total over 7 years)
                    val length = month.lengthOfMonth()

                    repeat(thisMonthCount) {
                        val day = rnd.nextInt(1, length + 1)
                        val date = java.time.LocalDate.of(month.year, month.month, day)

                        // Skip if date is in the future (beyond yesterday)
                        if (date.isAfter(endDate)) return@repeat

                        val isIncome = rnd.nextInt(100) < 35 // ~35% income across entire period
                        val createdInstant = date.atStartOfDay(zoneId).toInstant().plusSeconds(rnd.nextInt(0, 86_400).toLong())

                        if (isIncome && incomePool.isNotEmpty()) {
                            val cat = incomePool.random(rnd)
                            val descFun = incomeDesc.random(rnd)
                            val desc = descFun(invoiceSeq++)
                            val notes = if (rnd.nextInt(100) < 25) "Paid via Stripe" else null
                            val amount = randomAmount(50, 2500)
                            val txId = UUID.randomUUID().toString()
                            transactionsToInsert += Transaction(
                                id = txId,
                                date = date,
                                type = TransactionType.INCOME,
                                category = cat,
                                description = desc,
                                notes = notes,
                                amount = amount,
                                receiptPaths = emptyList(),
                                isDraft = false,
                                isDemoData = true, // Mark as demo data
                                isDeleted = false,
                                deletedAt = null,
                                createdAt = createdInstant,
                                updatedAt = createdInstant,
                                editHistory = emptyList()
                            )
                            totalTransactionsCreated++

                            // Update progress every 50 transactions
                            if (totalTransactionsCreated % 50 == 0) {
                                _seedingProgress.value = "Creating $totalTransactionsCreated/$estimatedTotal transactions across 7 years..."
                            }
                        } else if (!isIncome && expensePool.isNotEmpty()) {
                            val cat = expensePool.random(rnd)
                            val descFun = expenseDesc.random(rnd)
                            val desc = descFun(0)
                            val notes = if (rnd.nextInt(100) < 20) "Business expense" else null
                            val amount = when (cat) {
                                "Travel" -> randomAmount(10, 180)
                                "Meals & Entertainment" -> randomAmount(5, 80)
                                "Office Supplies" -> randomAmount(5, 120)
                                "Professional Services" -> randomAmount(50, 600)
                                "Advertising & Marketing" -> randomAmount(25, 700)
                                "Bank Charges" -> randomAmount(1, 40)
                                else -> randomAmount(5, 200)
                            }
                            val txId = UUID.randomUUID().toString()

                            // Decide draft status: leave only 2-3 very recent expenses without receipts
                            val makeDraft = draftsRemaining > 0 && date.isAfter(endDate.minusMonths(1))
                            val receiptList = if (makeDraft) {
                                draftsRemaining -= 1
                                emptyList()
                            } else {
                                // Generate real demo receipt images
                                val numReceipts = if (rnd.nextInt(100) < 15) 2 else 1 // 15% chance for 2 receipts
                                val receipts = mutableListOf<String>()
                                repeat(numReceipts) {
                                    val result = demoReceiptGenerator.generateDemoReceipt(
                                        transactionId = txId,
                                        date = date,
                                        category = cat,
                                        description = desc,
                                        amount = amount,
                                        profileId = profileId,
                                        currencyCode = baseCurrency,
                                        decimalSeparator = decimalSeparator,
                                        thousandSeparator = thousandSeparator
                                    )
                                    result.onSuccess { path ->
                                        receipts.add(path)
                                    }.onFailure { error ->
                                        android.util.Log.e("OnboardingViewModel", "Failed to generate demo receipt: ${error.message}")
                                    }
                                }
                                receipts
                            }
                            transactionsToInsert += Transaction(
                                id = txId,
                                date = date,
                                type = TransactionType.EXPENSE,
                                category = cat,
                                description = desc,
                                notes = notes,
                                amount = amount,
                                receiptPaths = receiptList,
                                isDraft = receiptList.isEmpty(),
                                isDemoData = true, // Mark as demo data
                                isDeleted = false,
                                deletedAt = null,
                                createdAt = createdInstant,
                                updatedAt = createdInstant,
                                editHistory = emptyList()
                            )
                            totalTransactionsCreated++

                            // Update progress every 50 transactions
                            if (totalTransactionsCreated % 50 == 0) {
                                _seedingProgress.value = "Creating $totalTransactionsCreated/$estimatedTotal transactions across 7 years..."
                            }
                        }
                    }
                }

                // Final progress update
                _seedingProgress.value = "Creating $totalTransactionsCreated/$totalTransactionsCreated transactions across 7 years..."

                android.util.Log.d("OnboardingViewModel", "seedDemoData: Inserting ${transactionsToInsert.size} transactions")
                transactionRepository.insertTransactionsBulk(transactionsToInsert)

                // Final refresh and wait for completion before clearing seeding flag
                android.util.Log.d("OnboardingViewModel", "seedDemoData: Final category refresh")
                loadCategoriesAndWait()

                android.util.Log.d("OnboardingViewModel", "seedDemoData: Demo data seeding completed successfully")
            } catch (e: Exception) {
                android.util.Log.e("OnboardingViewModel", "seedDemoData: Error during seeding", e)
                // Still refresh categories in case some were created
                loadCategoriesAndWait()
            } finally {
                android.util.Log.d("OnboardingViewModel", "seedDemoData: Setting seedingInProgress = false")
                _seedingInProgress.value = false
                _seedingProgress.value = ""
            }
        }
    }

    /**
     * Get default tax period for country (returns Pair of start and end as "MM-DD").
     */
    private fun getDefaultTaxPeriodForCountry(countryCode: String): Pair<Pair<Int, Int>, Pair<Int, Int>> {
        return when (countryCode) {
            "US", "CA", "RO" -> Pair(Pair(1, 1), Pair(12, 31)) // 1 Jan - 31 Dec
            "GB" -> Pair(Pair(4, 6), Pair(4, 5)) // 6 Apr - 5 Apr
            "AU" -> Pair(Pair(7, 1), Pair(6, 30)) // 1 Jul - 30 Jun
            "NZ" -> Pair(Pair(4, 1), Pair(3, 31)) // 1 Apr - 31 Mar
            else -> Pair(Pair(1, 1), Pair(12, 31)) // Default: 1 Jan - 31 Dec
        }
    }

    /**
     * Get default currency for country (if available in our curated list).
     * Returns empty string if country's currency is not in our list or for "Other".
     */
    private fun getDefaultCurrencyForCountry(countryCode: String): String {
        // Map countries to their currencies (all ISO 4217 codes)
        val currency = when (countryCode) {
            // Tier 1 - Already Supported
            "US" -> "USD"
            "GB" -> "GBP"
            "CA" -> "CAD"
            "AU" -> "AUD"
            "NZ" -> "NZD"
            "RO" -> "RON"
            // Tier 2 - High Priority English-Speaking
            "IE" -> "EUR"
            "SG" -> "SGD"
            "ZA" -> "ZAR"
            "IN" -> "INR"
            // Tier 3 - Major European Markets
            "DE" -> "EUR"
            "FR" -> "EUR"
            "NL" -> "EUR"
            "ES" -> "EUR"
            "IT" -> "EUR"
            "PL" -> "PLN"
            "SE" -> "SEK"
            "DK" -> "DKK"
            "NO" -> "NOK"
            "CH" -> "CHF"
            // Tier 4 - Emerging & Strategic Markets
            "BR" -> "BRL"
            "MX" -> "MXN"
            "AR" -> "ARS"
            "AE" -> "AED"
            "IL" -> "ILS"
            "PT" -> "EUR"
            "GR" -> "EUR"
            "CZ" -> "CZK"
            "BE" -> "EUR"
            "AT" -> "EUR"
            else -> "" // Other - user must select
        }

        return currency
    }

    /**
     * Set tax period start month and day.
     */
    fun setTaxPeriodStart(month: Int, day: Int) {
        _taxPeriodStartMonth.value = month
        _taxPeriodStartDay.value = day
    }

    /**
     * Set tax period end month and day.
     */
    fun setTaxPeriodEnd(month: Int, day: Int) {
        _taxPeriodEndMonth.value = month
        _taxPeriodEndDay.value = day
    }

    /**
     * Navigate to next step.
     * Debounced to prevent double-clicks from skipping steps.
     */
    fun nextStep() {
        // Prevent double-clicks
        if (isNavigating) return
        isNavigating = true

        _currentStep.value = when (_currentStep.value) {
            OnboardingStep.WELCOME -> OnboardingStep.PERMISSIONS
            OnboardingStep.PERMISSIONS -> OnboardingStep.COUNTRY
            OnboardingStep.COUNTRY -> OnboardingStep.TAX_YEAR
            OnboardingStep.TAX_YEAR -> OnboardingStep.CURRENCY
            OnboardingStep.CURRENCY -> {
                // When moving to category setup, create profiles (only once) and load categories
                viewModelScope.launch {
                    if (!profilesCreated) {
                        createProfilesAndLoadCategories()
                        profilesCreated = true
                    } else {
                        // Profiles already exist, just load categories
                        loadCategories()
                    }
                }
                OnboardingStep.CATEGORY_SETUP
            }
            OnboardingStep.CATEGORY_SETUP -> OnboardingStep.COMPLETE
            OnboardingStep.COMPLETE -> OnboardingStep.COMPLETE
        }

        // Reset navigation flag after a short delay
        viewModelScope.launch {
            kotlinx.coroutines.delay(500) // 500ms debounce
            isNavigating = false
        }
    }

    /**
     * Navigate to previous step.
     * Debounced to prevent double-clicks from skipping steps.
     */
    fun previousStep() {
        // Prevent double-clicks
        if (isNavigating) return
        isNavigating = true

        _currentStep.value = when (_currentStep.value) {
            OnboardingStep.WELCOME -> OnboardingStep.WELCOME
            OnboardingStep.PERMISSIONS -> OnboardingStep.WELCOME
            OnboardingStep.COUNTRY -> OnboardingStep.PERMISSIONS
            OnboardingStep.TAX_YEAR -> OnboardingStep.COUNTRY
            OnboardingStep.CURRENCY -> OnboardingStep.TAX_YEAR
            OnboardingStep.CATEGORY_SETUP -> OnboardingStep.CURRENCY
            OnboardingStep.COMPLETE -> OnboardingStep.CATEGORY_SETUP
        }

        // Reset navigation flag after a short delay
        viewModelScope.launch {
            kotlinx.coroutines.delay(500) // 500ms debounce
            isNavigating = false
        }
    }

    /**
     * Skip onboarding and use defaults.
     */
    fun skipOnboarding() {
        viewModelScope.launch {
            // Create default profiles (Business and Personal)
            val profiles = profileRepository.createDefaultProfiles()
            val businessProfile = profiles[0] // Business is first
            val personalProfile = profiles[1] // Personal is second

            // Set Business as current profile
            preferencesRepository.setCurrentProfileId(businessProfile.id)

            // Save defaults for both profiles (US: 1 Jan - 31 Dec)
            preferencesRepository.setSelectedCountry(businessProfile.id, "US")
            preferencesRepository.setTaxPeriod(businessProfile.id, "01-01", "12-31")
            preferencesRepository.setBaseCurrency(businessProfile.id, "USD")

            preferencesRepository.setSelectedCountry(personalProfile.id, "US")
            preferencesRepository.setTaxPeriod(personalProfile.id, "01-01", "12-31")
            preferencesRepository.setBaseCurrency(personalProfile.id, "USD")

            preferencesRepository.setOnboardingComplete(true)
        }
    }

    /**
     * Create profiles and load categories for category setup step.
     * Called when user moves from CURRENCY to CATEGORY_SETUP step.
     */
    private suspend fun createProfilesAndLoadCategories() {
        // Create default profiles (Business and Personal)
        val profiles = profileRepository.createDefaultProfiles()
        val businessProfile = profiles[0] // Business is first
        val personalProfile = profiles[1] // Personal is second

        // Set Business as current profile
        preferencesRepository.setCurrentProfileId(businessProfile.id)

        // Format tax period as MM-DD
        val startPeriod = String.format("%02d-%02d", _taxPeriodStartMonth.value, _taxPeriodStartDay.value)
        val endPeriod = String.format("%02d-%02d", _taxPeriodEndMonth.value, _taxPeriodEndDay.value)

        // Save user selections to both profiles
        preferencesRepository.setSelectedCountry(businessProfile.id, _selectedCountry.value)
        preferencesRepository.setTaxPeriod(businessProfile.id, startPeriod, endPeriod)
        preferencesRepository.setBaseCurrency(businessProfile.id, _selectedBaseCurrency.value)

        preferencesRepository.setSelectedCountry(personalProfile.id, _selectedCountry.value)
        preferencesRepository.setTaxPeriod(personalProfile.id, startPeriod, endPeriod)
        preferencesRepository.setBaseCurrency(personalProfile.id, _selectedBaseCurrency.value)

        // Load existing categories (should be empty on first run)
        loadCategories()
    }

    /**
     * Load categories for current profile.
     */
    fun loadCategories() {
        viewModelScope.launch {
            val allCategories = categoryRepository.getAllActive().first()
            android.util.Log.d("OnboardingViewModel", "loadCategories: Loaded ${allCategories.size} categories")
            _categories.value = allCategories
            checkMinimumCategories(allCategories)
        }
    }

    /**
     * Load categories and wait for completion (suspending version).
     * Used during demo data seeding to ensure state is updated before proceeding.
     */
    private suspend fun loadCategoriesAndWait() {
        val allCategories = categoryRepository.getAllActive().first()
        android.util.Log.d("OnboardingViewModel", "loadCategoriesAndWait: Loaded ${allCategories.size} categories")
        _categories.value = allCategories
        checkMinimumCategories(allCategories)
        android.util.Log.d("OnboardingViewModel", "loadCategoriesAndWait: hasMinimumCategories = ${_hasMinimumCategories.value}")
    }

    /**
     * Check if user has created minimum required categories (1 income + 1 expense).
     */
    private fun checkMinimumCategories(categories: List<Category>) {
        val hasIncome = categories.any { it.type == TransactionType.INCOME }
        val hasExpense = categories.any { it.type == TransactionType.EXPENSE }
        val newValue = hasIncome && hasExpense
        val oldValue = _hasMinimumCategories.value

        android.util.Log.d("OnboardingViewModel", "checkMinimumCategories: hasIncome=$hasIncome, hasExpense=$hasExpense, newValue=$newValue, oldValue=$oldValue")

        _hasMinimumCategories.value = newValue
    }

    /**
     * Create a new category during onboarding.
     */
    fun createCategory(
        name: String,
        type: TransactionType,
        icon: String?,
        color: String?
    ) {
        viewModelScope.launch {
            _categoryError.value = null // Clear previous errors
            val result = categoryRepository.createCategory(
                name = name,
                type = type,
                icon = icon,
                color = color
            )
            result.onSuccess {
                loadCategories() // Reload to update UI
            }.onFailure { error ->
                _categoryError.value = error.message ?: "Failed to create category"
            }
        }
    }

    /**
     * Update a category during onboarding.
     */
    fun updateCategory(
        id: String,
        name: String,
        type: TransactionType,
        icon: String?,
        color: String?
    ) {
        viewModelScope.launch {
            _categoryError.value = null // Clear previous errors
            val result = categoryRepository.updateCategory(
                id = id,
                name = name,
                type = type,
                icon = icon,
                color = color,
                displayOrder = 999
            )
            result.onSuccess {
                loadCategories() // Reload to update UI
            }.onFailure { error ->
                _categoryError.value = error.message ?: "Failed to update category"
            }
        }
    }

    /**
     * Clear category error message.
     */
    fun clearCategoryError() {
        _categoryError.value = null
    }

    /**
     * Delete a category during onboarding.
     * Allows deletion of categories with demo transactions.
     */
    fun deleteCategory(categoryId: String) {
        viewModelScope.launch {
            _categoryError.value = null // Clear previous errors
            val result = categoryRepository.deleteCategoryDuringOnboarding(categoryId)
            result.onSuccess {
                loadCategories() // Reload to update UI
            }.onFailure { error ->
                _categoryError.value = error.message ?: "Failed to delete category"
                android.util.Log.e("OnboardingViewModel", "Failed to delete category: ${error.message}", error)
            }
        }
    }

    /**
     * Complete onboarding and save preferences.
     * Called after user has created minimum required categories.
     */
    fun completeOnboarding() {
        android.util.Log.d("OnboardingViewModel", "completeOnboarding: Starting onboarding completion")
        android.util.Log.d("OnboardingViewModel", "completeOnboarding: hasMinimumCategories=${_hasMinimumCategories.value}, seedingInProgress=${_seedingInProgress.value}")

        viewModelScope.launch {
            preferencesRepository.setOnboardingComplete(true)
            android.util.Log.d("OnboardingViewModel", "completeOnboarding: Onboarding marked as complete")
        }
        _currentStep.value = OnboardingStep.COMPLETE
        android.util.Log.d("OnboardingViewModel", "completeOnboarding: Current step set to COMPLETE")
    }

    /**
     * Select country and auto-fill tax period and currency.
     */
    fun selectCountry(countryCode: String) {
        _selectedCountry.value = countryCode

        // Auto-fill tax period based on country
        val (start, end) = getDefaultTaxPeriodForCountry(countryCode)
        _taxPeriodStartMonth.value = start.first
        _taxPeriodStartDay.value = start.second
        _taxPeriodEndMonth.value = end.first
        _taxPeriodEndDay.value = end.second

        // Auto-fill currency based on country
        _selectedBaseCurrency.value = getDefaultCurrencyForCountry(countryCode)
    }

    /**
     * Select base currency (for tax reporting).
     */
    fun selectBaseCurrency(currency: String) {
        _selectedBaseCurrency.value = currency
    }

    /**
     * Set camera permission granted state.
     */
    fun setCameraPermissionGranted(granted: Boolean) {
        _cameraPermissionGranted.value = granted
    }

    /**
     * Set storage permission granted state.
     */
    fun setStoragePermissionGranted(granted: Boolean) {
        _storagePermissionGranted.value = granted
    }

    /**
     * Set notification permission granted state.
     */
    fun setNotificationPermissionGranted(granted: Boolean) {
        _notificationPermissionGranted.value = granted
    }

    /**
     * Get available tax years for selected country.
     */
    fun getAvailableTaxYears(): List<String> {
        return preferencesRepository.getAvailableTaxYears(_selectedCountry.value)
    }

    /**
     * Format tax year for display based on selected country.
     */
    fun formatTaxYearForDisplay(taxYear: String): String {
        return preferencesRepository.formatTaxYearForDisplay(taxYear, _selectedCountry.value)
    }
}

/**
 * Onboarding steps.
 *
 * Flow: Welcome → Permissions (with Privacy Policy) → Country → Tax Year → Currency → Category Setup → Complete
 *
 * Privacy Policy is shown on Permissions screen and must be accepted before proceeding.
 * Country selection auto-fills tax year and currency with smart defaults.
 * Category Setup requires user to create at least 1 income + 1 expense category.
 */
enum class OnboardingStep {
    WELCOME,
    PERMISSIONS,
    COUNTRY,
    TAX_YEAR,
    CURRENCY,
    CATEGORY_SETUP,
    COMPLETE
}
