package io.github.dorumrr.happytaxes.ui.screen

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.dorumrr.happytaxes.R
import io.github.dorumrr.happytaxes.domain.model.Category
import io.github.dorumrr.happytaxes.domain.model.TransactionType
import io.github.dorumrr.happytaxes.ui.theme.Spacing
import io.github.dorumrr.happytaxes.ui.theme.Dimensions
import io.github.dorumrr.happytaxes.ui.viewmodel.OnboardingStep
import io.github.dorumrr.happytaxes.ui.viewmodel.OnboardingViewModel
import io.github.dorumrr.happytaxes.data.currency.CurrencyData

/**
 * Onboarding screen - first-time user experience.
 *
 * Flow:
 * 1. Welcome
 * 2. Permissions (includes Privacy Policy acceptance)
 * 3. Country Selection (UK, USA, Australia, NZ, Canada, Other)
 * 4. Tax Year Selection (pre-filled based on country)
 * 5. Base Currency Selection (pre-filled based on country)
 * 6. Category Setup (user must create at least 1 income + 1 expense category)
 * 7. Complete → Navigate to Ledger
 *
 * Note: All currencies are available for transactions.
 * Base currency is used for tax reporting and calculations.
 */
@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val currentStep by viewModel.currentStep.collectAsState()
    val context = LocalContext.current

    // Handle back button - navigate to previous step or do nothing on first step
    BackHandler(enabled = currentStep != OnboardingStep.WELCOME) {
        viewModel.previousStep()
    }

    // Listen for completion
    LaunchedEffect(currentStep) {
        if (currentStep == OnboardingStep.COMPLETE) {
            onComplete()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (currentStep) {
                OnboardingStep.WELCOME -> WelcomeStep(
                    onGetStarted = { viewModel.nextStep() }
                )
                OnboardingStep.PERMISSIONS -> PermissionsStep(
                    cameraPermissionGranted = viewModel.cameraPermissionGranted.collectAsState().value,
                    storagePermissionGranted = viewModel.storagePermissionGranted.collectAsState().value,
                    notificationPermissionGranted = viewModel.notificationPermissionGranted.collectAsState().value,
                    onCameraPermissionResult = { viewModel.setCameraPermissionGranted(it) },
                    onStoragePermissionResult = { viewModel.setStoragePermissionGranted(it) },
                    onNotificationPermissionResult = { viewModel.setNotificationPermissionGranted(it) },
                    onNext = { viewModel.nextStep() },
                    onBack = { viewModel.previousStep() }
                )
                OnboardingStep.COUNTRY -> CountryStep(
                    selectedCountry = viewModel.selectedCountry.collectAsState().value,
                    onCountrySelected = { viewModel.selectCountry(it) },
                    onNext = { viewModel.nextStep() },
                    onBack = { viewModel.previousStep() }
                )
                OnboardingStep.TAX_YEAR -> TaxPeriodStep(
                    startMonth = viewModel.taxPeriodStartMonth.collectAsState().value,
                    startDay = viewModel.taxPeriodStartDay.collectAsState().value,
                    endMonth = viewModel.taxPeriodEndMonth.collectAsState().value,
                    endDay = viewModel.taxPeriodEndDay.collectAsState().value,
                    onStartChanged = { month, day -> viewModel.setTaxPeriodStart(month, day) },
                    onEndChanged = { month, day -> viewModel.setTaxPeriodEnd(month, day) },
                    onNext = { viewModel.nextStep() },
                    onBack = { viewModel.previousStep() }
                )
                OnboardingStep.CURRENCY -> BaseCurrencyStep(
                    selectedCurrency = viewModel.selectedBaseCurrency.collectAsState().value,
                    onCurrencySelected = { viewModel.selectBaseCurrency(it) },
                    onNext = { viewModel.nextStep() },
                    onBack = { viewModel.previousStep() }
                )
                OnboardingStep.CATEGORY_SETUP -> CategorySetupStep(
                    categories = viewModel.categories.collectAsState().value,
                    hasMinimumCategories = viewModel.hasMinimumCategories.collectAsState().value,
                    categoryError = viewModel.categoryError.collectAsState().value,
                    seedingInProgress = viewModel.seedingInProgress.collectAsState().value,
                    seedingProgress = viewModel.seedingProgress.collectAsState().value,
                    onCreateCategory = { name, type, icon, color ->
                        viewModel.createCategory(name, type, icon, color)
                    },
                    onUpdateCategory = { id, name, type, icon, color ->
                        viewModel.updateCategory(id, name, type, icon, color)
                    },
                    onDeleteCategory = { categoryId ->
                        viewModel.deleteCategory(categoryId)
                    },
                    onClearError = { viewModel.clearCategoryError() },
                    onSeedDemoData = { viewModel.seedDemoData() },
                    onComplete = { viewModel.completeOnboarding() },
                    onBack = { viewModel.previousStep() }
                )
                OnboardingStep.COMPLETE -> {
                    // Will navigate via LaunchedEffect
                }
            }
        }
    }
}

/**
 * Welcome step - intro to HappyTaxes.
 */
@Composable
private fun WelcomeStep(
    onGetStarted: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.large),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top spacer - 30% of screen height (flexible)
        Spacer(modifier = Modifier.weight(0.3f))

        // App icon with circular background
        Box(
            modifier = Modifier
                .size(Dimensions.thumbnailExtraLarge)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(color = MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.app_icon),
                contentDescription = "HappyTaxes Logo",
                modifier = Modifier
                    .size(Dimensions.thumbnailLarge)
                    .clip(androidx.compose.foundation.shape.CircleShape),
                contentScale = androidx.compose.ui.layout.ContentScale.Fit
            )
        }

        Spacer(modifier = Modifier.height(Spacing.extraLarge))

        // Welcome message
        Text(
            text = "Welcome to HappyTaxes",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(Spacing.medium))

        Text(
            text = "Proper bookkeeping. Free.",
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Bottom spacer - remaining space (flexible)
        Spacer(modifier = Modifier.weight(0.7f))

        // Get Started button
        Button(
            onClick = onGetStarted,
            modifier = Modifier
                .fillMaxWidth()
                .height(Spacing.buttonHeight)
        ) {
            Text("Get Started")
        }
    }
}

/**
 * Permissions step - request camera, storage, and notification permissions.
 * Also includes privacy policy acceptance.
 */
@Composable
private fun PermissionsStep(
    cameraPermissionGranted: Boolean,
    storagePermissionGranted: Boolean,
    notificationPermissionGranted: Boolean,
    onCameraPermissionResult: (Boolean) -> Unit,
    onStoragePermissionResult: (Boolean) -> Unit,
    onNotificationPermissionResult: (Boolean) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    // Determine which storage permission to request based on Android version
    val storagePermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_IMAGES
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        // Android 10-12: No storage permission needed for app-specific storage
        // But we can request READ_EXTERNAL_STORAGE for accessing existing images
        Manifest.permission.READ_EXTERNAL_STORAGE
    } else {
        // Android 9 and below
        Manifest.permission.WRITE_EXTERNAL_STORAGE
    }

    // Check if storage permission is already granted
    val isStoragePermissionGranted = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // For Android 10+, app-specific storage doesn't need permission
            // We only need permission to read media images
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_MEDIA_IMAGES
                ) == PermissionChecker.PERMISSION_GRANTED
            } else {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PermissionChecker.PERMISSION_GRANTED
            }
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PermissionChecker.PERMISSION_GRANTED
        }
    }

    // Update storage permission state if already granted
    LaunchedEffect(isStoragePermissionGranted) {
        if (isStoragePermissionGranted) {
            onStoragePermissionResult(true)
        }
    }

    // Track whether each permission button has been clicked
    var cameraButtonClicked by rememberSaveable {
        mutableStateOf(cameraPermissionGranted)
    }
    var storageButtonClicked by rememberSaveable {
        mutableStateOf(storagePermissionGranted || isStoragePermissionGranted)
    }
    // Keep interaction flags in sync when permissions are granted outside this screen
    LaunchedEffect(cameraPermissionGranted) {
        if (cameraPermissionGranted) {
            cameraButtonClicked = true
        }
    }
    LaunchedEffect(storagePermissionGranted, isStoragePermissionGranted) {
        if (storagePermissionGranted || isStoragePermissionGranted) {
            storageButtonClicked = true
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        onCameraPermissionResult(isGranted)
        cameraButtonClicked = true
    }

    val storagePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        onStoragePermissionResult(isGranted)
        storageButtonClicked = true
    }

    // Notification permission (Android 13+)
    val notificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.POST_NOTIFICATIONS
    } else {
        null // Not needed on Android 12 and below
    }

    // Check if notification permission is already granted
    val isNotificationPermissionGranted = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PermissionChecker.PERMISSION_GRANTED
        } else {
            true // Not required on older Android versions
        }
    }

    // Update notification permission state if already granted
    LaunchedEffect(isNotificationPermissionGranted) {
        if (isNotificationPermissionGranted) {
            onNotificationPermissionResult(true)
        }
    }

    var notificationButtonClicked by rememberSaveable {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                notificationPermissionGranted || isNotificationPermissionGranted
        )
    }

    LaunchedEffect(notificationPermissionGranted, isNotificationPermissionGranted) {
        if (
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            notificationPermissionGranted ||
            isNotificationPermissionGranted
        ) {
            notificationButtonClicked = true
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        onNotificationPermissionResult(isGranted)
        notificationButtonClicked = true
    }

    // Determine if all permission buttons have been clicked
    val allButtonsClicked = remember {
        derivedStateOf {
            cameraButtonClicked && storageButtonClicked &&
            (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || notificationButtonClicked)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.large)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(Dimensions.onboardingIconSpacing))

            Text(
                text = "Permissions",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(Spacing.medium))

            

            Spacer(modifier = Modifier.height(Spacing.extraLarge))

            // Notification permission (Android 13+ only) - FIRST
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                PermissionCard(
                    title = "Notifications",
                    description = "Receive reminders and alerts for transactions",
                    isGranted = notificationPermissionGranted || isNotificationPermissionGranted,
                    onRequestPermission = {
                        notificationPermission?.let { permission ->
                            notificationPermissionLauncher.launch(permission)
                        }
                    }
                )

                Spacer(modifier = Modifier.height(Spacing.medium))
            }

            // Camera permission - SECOND
            PermissionCard(
                title = "Camera",
                description = "Capture receipts instantly with your camera",
                isGranted = cameraPermissionGranted,
                onRequestPermission = {
                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                }
            )

            Spacer(modifier = Modifier.height(Spacing.medium))

            // Storage permission (version-specific) - THIRD
            PermissionCard(
                title = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    "Photos & Media"
                } else {
                    "Storage"
                },
                description = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    "Access photos for receipt capture"
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    "Access storage for receipts"
                } else {
                    "Store receipts securely on your device"
                },
                isGranted = storagePermissionGranted || isStoragePermissionGranted,
                onRequestPermission = {
                    storagePermissionLauncher.launch(storagePermission)
                }
            )

            Spacer(modifier = Modifier.height(Spacing.medium))

            // Privacy Policy section
            HorizontalDivider(
                modifier = Modifier.padding(vertical = Spacing.medium),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            // Privacy policy text with clickable link
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "By continuing, you agree to the ",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Privacy Policy",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) {
                        val intent = android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse("https://dorumrr.github.io/happytaxes/privacy-policy.html")
                        )
                        context.startActivity(intent)
                    }
                )
            }

            Spacer(modifier = Modifier.height(Spacing.medium))
        }

        // Action buttons: Disagree (exit app) and Agree (continue)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.medium)
        ) {
            OutlinedButton(
                onClick = {
                    // Close the app
                    (context as? android.app.Activity)?.finishAffinity()
                },
                modifier = Modifier
                    .weight(1f)
                    .height(Spacing.buttonHeight)
            ) {
                Text("Disagree")
            }

            Button(
                onClick = onNext,
                enabled = allButtonsClicked.value,
                modifier = Modifier
                    .weight(1f)
                    .height(Spacing.buttonHeight)
            ) {
                Text("Agree")
            }
        }
    }

}

/**
 * Permission card component.
 */
@Composable
private fun PermissionCard(
    title: String,
    description: String,
    isGranted: Boolean,
    onRequestPermission: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.medium),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(Spacing.extraSmall))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(Spacing.medium))

            if (isGranted) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Granted",
                    tint = MaterialTheme.colorScheme.primary
                )
            } else {
                Button(onClick = onRequestPermission) {
                    Text("Allow")
                }
            }
        }
    }
}

/**
 * Country step - select country for smart defaults.
 */
@Composable
private fun CountryStep(
    selectedCountry: String,
    onCountrySelected: (String) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit
) {
    // Top 30 countries for tax/bookkeeping apps (alphabetically sorted)
    val countries = listOf(
        "AR" to "Argentina",
        "AU" to "Australia",
        "AT" to "Austria",
        "BE" to "Belgium",
        "BR" to "Brazil",
        "CA" to "Canada",
        "CZ" to "Czech Republic",
        "DK" to "Denmark",
        "FR" to "France",
        "DE" to "Germany",
        "GR" to "Greece",
        "IN" to "India",
        "IE" to "Ireland",
        "IL" to "Israel",
        "IT" to "Italy",
        "MX" to "Mexico",
        "NL" to "Netherlands",
        "NZ" to "New Zealand",
        "NO" to "Norway",
        "PL" to "Poland",
        "PT" to "Portugal",
        "RO" to "Romania",
        "SG" to "Singapore",
        "ZA" to "South Africa",
        "ES" to "Spain",
        "SE" to "Sweden",
        "CH" to "Switzerland",
        "AE" to "United Arab Emirates",
        "GB" to "United Kingdom",
        "US" to "United States",
        "OTHER" to "Other"
    )

    // Determine the selected country's position and prepare scroll state
    val selectedIndex = if (selectedCountry.isNotEmpty()) {
        countries.indexOfFirst { it.first == selectedCountry }.takeIf { it >= 0 } ?: 0
    } else {
        0
    }
    val listState = rememberLazyListState()

    // Scroll only on first composition so the default selection is visible
    LaunchedEffect(Unit) {
        if (selectedCountry.isNotEmpty()) {
            listState.scrollToItem(selectedIndex)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.large)
    ) {
        Spacer(modifier = Modifier.height(Dimensions.onboardingIconSpacing))

        Text(
            text = "Select Your Country",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(Spacing.medium))

        Text(
            text = "We'll pre-fill tax year and currency based on your country",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(Spacing.large))

        // Country list with radio buttons
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f)
        ) {
            items(countries) { (code, name) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = Spacing.small)
                        .clickable { onCountrySelected(code) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selectedCountry == code,
                        onClick = { onCountrySelected(code) }
                    )
                    Spacer(modifier = Modifier.width(Spacing.mediumSmall))
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(Spacing.medium))

        // Next button - enabled once the user picks a country
        Button(
            onClick = onNext,
            enabled = selectedCountry.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .height(Spacing.buttonHeight)
        ) {
            Text("Next")
        }

        Spacer(modifier = Modifier.height(Spacing.mediumSmall))

        // Back button
        TextButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Back")
        }
    }
}

/**
 * Calculate exact days in a tax period (start month/day to end month/day).
 * For tax years, the end date is the LAST day of the tax year.
 *
 * Examples:
 * - 6 Apr to 5 Apr (next year) = 365 days (UK tax year)
 * - 1 Jan to 31 Dec = 365 days (US tax year)
 * - 1 Jul to 30 Jun (next year) = 365 days (AU tax year)
 * - 5 Apr to 5 Apr = 365 days (interpreted as full year cycle)
 */
private fun calculatePeriodDays(startMonth: Int, startDay: Int, endMonth: Int, endDay: Int): Int {
    val daysInMonths = listOf(31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)

    // Calculate day of year for start and end (non-leap year, 1-based)
    val startDayOfYear = daysInMonths.take(startMonth - 1).sum() + startDay
    val endDayOfYear = daysInMonths.take(endMonth - 1).sum() + endDay

    // Special case: same date means full year (365 days)
    if (startDayOfYear == endDayOfYear) {
        return 365
    }

    // Calculate period length
    val periodDays = if (endDayOfYear > startDayOfYear) {
        // Period within same year (e.g., 1 Jan to 31 Dec)
        endDayOfYear - startDayOfYear + 1
    } else {
        // Period spans year boundary (e.g., 6 Apr to 5 Apr next year)
        365 - startDayOfYear + endDayOfYear + 1
    }

    return periodDays
}

/**
 * Tax period step - set fiscal year start/end dates using month/day dropdowns.
 *
 * This defines the reporting period only. Actual tax years in reports are built
 * dynamically from transaction dates.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaxPeriodStep(
    startMonth: Int,
    startDay: Int,
    endMonth: Int,
    endDay: Int,
    onStartChanged: (Int, Int) -> Unit,
    onEndChanged: (Int, Int) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit
) {
    val monthNames = listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December"
    )

    fun daysInMonth(month: Int): Int = when (month) {
        2 -> 29
        4, 6, 9, 11 -> 30
        else -> 31
    }

    var startMonthExpanded by remember { mutableStateOf(false) }
    var startDayExpanded by remember { mutableStateOf(false) }
    var endMonthExpanded by remember { mutableStateOf(false) }
    var endDayExpanded by remember { mutableStateOf(false) }

    val periodDays = calculatePeriodDays(startMonth, startDay, endMonth, endDay)
    val isPeriodValid = periodDays > 0
    val isExactlyOneYear = periodDays == 365 || periodDays == 366

    var showPeriodWarning by remember { mutableStateOf(false) }

    if (showPeriodWarning) {
        AlertDialog(
            onDismissRequest = { showPeriodWarning = false },
            title = { Text("Invalid Tax Period") },
            text = {
                Text(
                    "Your tax period is $periodDays days long, but a tax year must be exactly 365 days (or 366 days in a leap year).\n\n" +
                            "Please adjust the dates to create a valid tax year, or if this is intentional, you can continue anyway."
                )
            },
            confirmButton = {
                Button(onClick = {
                    showPeriodWarning = false
                    onNext()
                }) {
                    Text("Continue Anyway")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPeriodWarning = false }) {
                    Text("Go Back")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.large)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(Spacing.screenTop))

            Text(
                text = "Tax Period",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(Spacing.medium))

            Text(
                text = "Set your fiscal year start and end dates",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(Spacing.large))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.medium)
            ) {
                Text(
                    text = "Tax Period Start",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(Spacing.mediumSmall))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.small)
                ) {
                    ExposedDropdownMenuBox(
                        expanded = startMonthExpanded,
                        onExpandedChange = { startMonthExpanded = !startMonthExpanded },
                        modifier = Modifier.weight(2f)
                    ) {
                        OutlinedTextField(
                            value = monthNames[startMonth - 1],
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Month") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true),
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = startMonthExpanded) }
                        )
                        ExposedDropdownMenu(
                            expanded = startMonthExpanded,
                            onDismissRequest = { startMonthExpanded = false }
                        ) {
                            monthNames.forEachIndexed { index, name ->
                                DropdownMenuItem(
                                    text = { Text(name) },
                                    onClick = {
                                        val newMonth = index + 1
                                        val maxDay = daysInMonth(newMonth)
                                        val newDay = if (startDay > maxDay) maxDay else startDay
                                        onStartChanged(newMonth, newDay)
                                        startMonthExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    ExposedDropdownMenuBox(
                        expanded = startDayExpanded,
                        onExpandedChange = { startDayExpanded = !startDayExpanded },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = startDay.toString(),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Day") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true),
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = startDayExpanded) }
                        )
                        ExposedDropdownMenu(
                            expanded = startDayExpanded,
                            onDismissRequest = { startDayExpanded = false }
                        ) {
                            (1..daysInMonth(startMonth)).forEach { day ->
                                DropdownMenuItem(
                                    text = { Text(day.toString()) },
                                    onClick = {
                                        onStartChanged(startMonth, day)
                                        startDayExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(Spacing.medium))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.medium)
            ) {
                Text(
                    text = "Tax Period End",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(Spacing.mediumSmall))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.small)
                ) {
                    ExposedDropdownMenuBox(
                        expanded = endMonthExpanded,
                        onExpandedChange = { endMonthExpanded = !endMonthExpanded },
                        modifier = Modifier.weight(2f)
                    ) {
                        OutlinedTextField(
                            value = monthNames[endMonth - 1],
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Month") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true),
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = endMonthExpanded) }
                        )
                        ExposedDropdownMenu(
                            expanded = endMonthExpanded,
                            onDismissRequest = { endMonthExpanded = false }
                        ) {
                            monthNames.forEachIndexed { index, name ->
                                DropdownMenuItem(
                                    text = { Text(name) },
                                    onClick = {
                                        val newMonth = index + 1
                                        val maxDay = daysInMonth(newMonth)
                                        val newDay = if (endDay > maxDay) maxDay else endDay
                                        onEndChanged(newMonth, newDay)
                                        endMonthExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    ExposedDropdownMenuBox(
                        expanded = endDayExpanded,
                        onExpandedChange = { endDayExpanded = !endDayExpanded },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = endDay.toString(),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Day") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true),
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = endDayExpanded) }
                        )
                        ExposedDropdownMenu(
                            expanded = endDayExpanded,
                            onDismissRequest = { endDayExpanded = false }
                        ) {
                            (1..daysInMonth(endMonth)).forEach { day ->
                                DropdownMenuItem(
                                    text = { Text(day.toString()) },
                                    onClick = {
                                        onEndChanged(endMonth, day)
                                        endDayExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(Spacing.large))
        }

        Button(
            onClick = {
                if (isPeriodValid && !isExactlyOneYear) {
                    showPeriodWarning = true
                } else {
                    onNext()
                }
            },
            enabled = isPeriodValid,
            modifier = Modifier
                .fillMaxWidth()
                .height(Spacing.buttonHeight)
        ) {
            Text("Next")
        }

        Spacer(modifier = Modifier.height(Spacing.mediumSmall))

        TextButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Back")
        }
    }
}

/**
 * Base currency step - select currency for tax reporting.
 *
 * Note: All currencies are available for transactions.
 * This selection is only for tax reporting and calculations.
 */
@Composable
private fun BaseCurrencyStep(
    selectedCurrency: String,
    onCurrencySelected: (String) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit
) {
    // Get all currencies with descriptions from CurrencyData
    val availableCurrencies = CurrencyData.allCurrencies
    
    // Find the index of the selected currency for scrolling
    val selectedIndex = if (selectedCurrency.isNotEmpty()) {
        availableCurrencies.indexOfFirst { it.code == selectedCurrency }.takeIf { it >= 0 } ?: 0
    } else {
        0
    }
    
    // LazyListState for scrolling to selected item
    val listState = rememberLazyListState()
    
    // Scroll to selected currency ONLY when screen first appears (not during selection)
    LaunchedEffect(Unit) {
        if (selectedCurrency.isNotEmpty() && selectedIndex >= 0) {
            listState.scrollToItem(selectedIndex)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.large)
    ) {
        Spacer(modifier = Modifier.height(Dimensions.onboardingIconSpacing))

        Text(
            text = "Base Currency",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(Spacing.medium))

        Text(
            text = if (selectedCurrency.isEmpty()) {
                "Select your main currency for tax reporting"
            } else {
                "Confirm or change your base currency"
            },
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(Spacing.large))

        // Currency list with radio buttons
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f)
        ) {
            items(availableCurrencies) { currencyInfo ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = Spacing.small)
                        .clickable { onCurrencySelected(currencyInfo.code) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selectedCurrency == currencyInfo.code,
                        onClick = { onCurrencySelected(currencyInfo.code) }
                    )
                    Spacer(modifier = Modifier.width(Spacing.mediumSmall))
                    Column {
                        Text(
                            text = currencyInfo.displayText,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = if (selectedCurrency == currencyInfo.code) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(Spacing.medium))

        // Next button (disabled if no currency selected)
        Button(
            onClick = onNext,
            enabled = selectedCurrency.isNotEmpty(),
            modifier = Modifier
                .fillMaxWidth()
                .height(Spacing.buttonHeight)
        ) {
            Text("Next")
        }

        Spacer(modifier = Modifier.height(Spacing.mediumSmall))

        // Back button
        TextButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Back")
        }
    }
}

/**
 * Category Setup step - user must create at least 1 income + 1 expense category.
 */
@Composable
private fun CategorySetupStep(
    categories: List<Category>,
    hasMinimumCategories: Boolean,
    categoryError: String?,
    seedingInProgress: Boolean,
    seedingProgress: String,
    onCreateCategory: (String, TransactionType, String?, String?) -> Unit,
    onUpdateCategory: (String, String, TransactionType, String?, String?) -> Unit,
    onDeleteCategory: (String) -> Unit,
    onClearError: () -> Unit,
    onSeedDemoData: () -> Unit,
    onComplete: () -> Unit,
    onBack: () -> Unit
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var createCategoryType by remember { mutableStateOf(TransactionType.INCOME) }
    var categoryToEdit by remember { mutableStateOf<Category?>(null) }
    var categoryToDelete by remember { mutableStateOf<Category?>(null) }
    val scope = rememberCoroutineScope()

    // Show error snackbar if there's an error
    LaunchedEffect(categoryError) {
        if (categoryError != null) {
            // Error will be shown in the dialog
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.large)
    ) {
        Spacer(modifier = Modifier.height(Dimensions.onboardingIconSpacing))

        // Track if demo data has been seeded
        var demoDataSeeded by rememberSaveable { mutableStateOf(false) }

        Text(
            text = "Set Up Categories",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(Spacing.small))

        Text(
            text = "Create at least one income category and one expense category to get started.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Demo data seeding progress indicator
        if (seedingInProgress) {
            Spacer(modifier = Modifier.height(Spacing.medium))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Spacing.medium),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(Spacing.medium))
                    Column {
                        Text(
                            text = "Seeding Demo Data...",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (seedingProgress.isNotEmpty()) seedingProgress else "Creating transactions across 7 years...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(Spacing.medium))

        // Show Income/Expense buttons and "or" divider only if demo data not seeded
        if (!demoDataSeeded) {
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
                    Text("+ Income")
                }
                OutlinedButton(
                    onClick = {
                        createCategoryType = TransactionType.EXPENSE
                        showCreateDialog = true
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("+ Expense")
                }
            }

            Spacer(modifier = Modifier.height(Spacing.medium))

            // "or" divider
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                HorizontalDivider(
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                Text(
                    text = "or",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.medium)
                )
                HorizontalDivider(
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
            }

            Spacer(modifier = Modifier.height(Spacing.medium))
        }

        // Add Demo Data button
        OutlinedButton(
            onClick = {
                if (!demoDataSeeded && !seedingInProgress) {
                    demoDataSeeded = true
                    onSeedDemoData()
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !demoDataSeeded && !seedingInProgress
        ) {
            Icon(
                imageVector = Icons.Default.Science,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(Spacing.small))
            Text(
                text = if (demoDataSeeded) "Demo Data Added" else "Add Demo Data",
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Spacer(modifier = Modifier.height(Spacing.medium))

        // Category list
        if (categories.isNotEmpty()) {
            Text(
                text = "Your Categories",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(Spacing.small))

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
                            allCategories = categories,
                            onClick = {
                                categoryToEdit = category
                                showEditDialog = true
                            },
                            onDelete = { onDeleteCategory(category.id) }
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
                            allCategories = categories,
                            onClick = {
                                categoryToEdit = category
                                showEditDialog = true
                            },
                            onDelete = { onDeleteCategory(category.id) }
                        )
                    }
                }
            }
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(Spacing.medium))

        // Complete button (enabled only when minimum categories created and not seeding)
        Button(
            onClick = onComplete,
            enabled = hasMinimumCategories && !seedingInProgress,
            modifier = Modifier
                .fillMaxWidth()
                .height(Spacing.buttonHeight)
        ) {
            Text(
                when {
                    seedingInProgress -> "Seeding Demo Data..."
                    hasMinimumCategories -> "Complete Setup"
                    else -> "Create Categories to Continue"
                }
            )
        }

        Spacer(modifier = Modifier.height(Spacing.mediumSmall))

        // Back button (disabled during seeding)
        TextButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth(),
            enabled = !seedingInProgress
        ) {
            Text("Back")
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
                onClearError()
            },
            onCreate = { name, icon, color ->
                onCreateCategory(name, createCategoryType, icon, color)
            },
            onSuccess = {
                showCreateDialog = false
                onClearError()
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
                onClearError()
            },
            onUpdate = { name, icon, color ->
                onUpdateCategory(categoryToEdit!!.id, name, categoryToEdit!!.type, icon, color)
            },
            onDelete = { 
                showEditDialog = false
                categoryToEdit = null
                onClearError()
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
                onClearError()
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
                        onDeleteCategory(categoryToDelete!!.id)
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
 * Simple category item for onboarding with edit icon only.
 * Edit when clicking the edit icon, not the entire card.
 */
@Composable
private fun CategoryItemSimple(
    category: Category,
    allCategories: List<Category>,
    onClick: () -> Unit,
    onDelete: () -> Unit
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = category.name,
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.width(Spacing.small))
                Text(
                    text = if (category.type == TransactionType.INCOME) "Income" else "Expense",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // Edit button
            IconButton(onClick = onClick) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edit",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/**
 * Simple dialog for creating a category during onboarding.
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
            Text(text = "Create ${if (type == TransactionType.INCOME) "Income" else "Expense"} Category")
        },
        text = {
            Column {
                Text(
                    text = "Enter a name for your category",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(Spacing.small))
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
            Button(
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
 * Simple dialog for editing a category during onboarding with conditional delete button.
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
                Text(
                    text = "Update the category name",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(Spacing.small))
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
            Button(
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
