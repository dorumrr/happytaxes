package io.github.dorumrr.happytaxes

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors
import io.github.dorumrr.happytaxes.data.repository.PreferencesRepository
import io.github.dorumrr.happytaxes.di.RepositoryEntryPoint
import io.github.dorumrr.happytaxes.ui.navigation.NavGraph
import io.github.dorumrr.happytaxes.ui.navigation.Screen
import io.github.dorumrr.happytaxes.ui.theme.HappyTaxesTheme
import kotlinx.coroutines.flow.first

/**
 * Main activity for HappyTaxes.
 *
 * Entry point for the app, hosts the Compose UI with navigation.
 * Checks onboarding status and sets start destination accordingly.
 * Includes Material 3 bottom navigation bar for main screens.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            val navController = rememberNavController()

            // Get repositories from Hilt
            val entryPoint = EntryPointAccessors.fromApplication(
                context.applicationContext,
                RepositoryEntryPoint::class.java
            )
            val preferencesRepository = entryPoint.preferencesRepository()
            val profileRepository = entryPoint.profileRepository()

            // Get dynamic color preference for theme
            val dynamicColorEnabled by preferencesRepository.getDynamicColorEnabled()
                .collectAsState(initial = false)

            HappyTaxesTheme(dynamicColor = dynamicColorEnabled) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {

                    // Wait for DataStore to load before determining start destination
                    val startDestination by produceState<String?>(initialValue = null) {
                        val onboardingComplete = preferencesRepository.isOnboardingComplete().first()

                        value = when {
                            // Not onboarded yet -> go to onboarding
                            !onboardingComplete -> Screen.Onboarding.route

                            // Onboarded -> go directly to ledger (last used profile will be loaded)
                            else -> Screen.Ledger.route
                        }
                    }

                    // Track current navigation destination
                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val currentDestination = navBackStackEntry?.destination

                    // Define bottom nav screens
                    val bottomNavScreens = listOf(
                        Screen.Ledger.route,
                        Screen.Reports.route,
                        Screen.Settings.route
                    )

                    // Show bottom nav only on main screens (not onboarding, detail screens, etc.)
                    val showBottomNav = currentDestination?.route in bottomNavScreens

                    // Only show NavGraph after start destination is determined
                    startDestination?.let { destination ->
                        Scaffold(
                            contentWindowInsets = WindowInsets(0, 0, 0, 0),
                            bottomBar = {
                                if (showBottomNav) {
                                    NavigationBar {
                                        // Transactions
                                        NavigationBarItem(
                                            icon = { Icon(Icons.Default.Home, contentDescription = null) },
                                            label = { Text("Transactions") },
                                            selected = currentDestination?.hierarchy?.any { it.route == Screen.Ledger.route } == true,
                                            onClick = {
                                                if (currentDestination?.route != Screen.Ledger.route) {
                                                    navController.navigate(Screen.Ledger.route) {
                                                        // Pop up to start destination to avoid building up back stack
                                                        popUpTo(Screen.Ledger.route) {
                                                            saveState = true
                                                        }
                                                        launchSingleTop = true
                                                        restoreState = true
                                                    }
                                                }
                                            }
                                        )

                                        // Reports
                                        NavigationBarItem(
                                            icon = { Icon(Icons.Default.Assessment, contentDescription = null) },
                                            label = { Text("Reports") },
                                            selected = currentDestination?.hierarchy?.any { it.route == Screen.Reports.route } == true,
                                            onClick = {
                                                if (currentDestination?.route != Screen.Reports.route) {
                                                    navController.navigate(Screen.Reports.route) {
                                                        popUpTo(Screen.Ledger.route) {
                                                            saveState = true
                                                        }
                                                        launchSingleTop = true
                                                        restoreState = true
                                                    }
                                                }
                                            }
                                        )

                                        // Settings
                                        NavigationBarItem(
                                            icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                                            label = { Text("Settings") },
                                            selected = currentDestination?.hierarchy?.any { it.route == Screen.Settings.route } == true,
                                            onClick = {
                                                if (currentDestination?.route != Screen.Settings.route) {
                                                    navController.navigate(Screen.Settings.route) {
                                                        popUpTo(Screen.Ledger.route) {
                                                            saveState = true
                                                        }
                                                        launchSingleTop = true
                                                        restoreState = true
                                                    }
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        ) { paddingValues ->
                            Box(modifier = Modifier.padding(paddingValues)) {
                                NavGraph(
                                    navController = navController,
                                    startDestination = destination
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

