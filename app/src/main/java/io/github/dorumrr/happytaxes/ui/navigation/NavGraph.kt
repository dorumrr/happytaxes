package io.github.dorumrr.happytaxes.ui.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import io.github.dorumrr.happytaxes.ui.screen.ArchiveScreen
import io.github.dorumrr.happytaxes.ui.screen.BackupRestoreScreen
import io.github.dorumrr.happytaxes.ui.screen.CameraScreen
import io.github.dorumrr.happytaxes.ui.screen.CategoryManagementScreen
import io.github.dorumrr.happytaxes.ui.screen.CategorySetupScreen
import io.github.dorumrr.happytaxes.ui.screen.HmrcMappingScreen
import io.github.dorumrr.happytaxes.ui.screen.LedgerScreen
import io.github.dorumrr.happytaxes.ui.screen.OnboardingScreen
import io.github.dorumrr.happytaxes.ui.screen.ProfileManagementScreen
import io.github.dorumrr.happytaxes.ui.screen.ProfileSelectionScreen
import io.github.dorumrr.happytaxes.ui.screen.ReceiptViewerScreen
import io.github.dorumrr.happytaxes.ui.screen.RecentlyDeletedScreen
import io.github.dorumrr.happytaxes.ui.screen.ReportsScreen
import io.github.dorumrr.happytaxes.ui.screen.SettingsScreen
import io.github.dorumrr.happytaxes.ui.screen.TransactionDetailScreen

/**
 * Navigation graph for HappyTaxes app.
 *
 * Routes:
 * - onboarding: First-time user experience (shown once)
 * - profile_selection: Profile selection screen (shown at startup if 2 profiles exist)
 * - ledger: Main transaction list (displayed as "Transactions")
 * - transaction_detail/{transactionId}: Add/edit transaction
 * - receipt_viewer: Full-screen receipt viewer with zoom/pan
 * - recently_deleted: Soft-deleted transactions
 * - profile_management: Manage profiles (create/delete/switch)
 * - category_setup: Standalone category setup (when profile has no categories)
 * - camera: Camera for capturing receipts
 * - settings: App settings and preferences
 * - backup_restore: Backup and restore data
 * - category_management: Manage categories
 * - hmrc_mapping: HMRC SA103 category mapping
 */
@Composable
fun NavGraph(
    navController: NavHostController,
    startDestination: String = Screen.Ledger.route
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        // Onboarding screen (first launch only)
        composable(Screen.Onboarding.route) {
            OnboardingScreen(
                onComplete = {
                    navController.navigate(Screen.Ledger.route) {
                        // Remove onboarding from back stack
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                }
            )
        }

        // Ledger screen (main)
        composable(Screen.Ledger.route) { backStackEntry ->
            // Get transaction ID to scroll to (if returning from detail or receipt viewer)
            val scrollToTransactionId = backStackEntry.savedStateHandle
                .get<String>("scroll_to_transaction_id")

            LedgerScreen(
                onNavigateToDetail = { transactionId ->
                    if (transactionId != null) {
                        navController.navigate(Screen.TransactionDetail.createRoute(transactionId))
                    } else {
                        navController.navigate(Screen.TransactionDetail.route)
                    }
                },
                onNavigateToDeleted = {
                    navController.navigate(Screen.RecentlyDeleted.route)
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                },
                onNavigateToReports = {
                    navController.navigate(Screen.Reports.route)
                },
                onNavigateToCategoryManagement = {
                    navController.navigate(Screen.CategoryManagement.route)
                },
                onNavigateToCategorySetup = {
                    navController.navigate(Screen.CategorySetup.route)
                },
                onNavigateToProfiles = {
                    navController.navigate(Screen.ProfileManagement.route)
                },
                onNavigateToReceiptViewer = { receiptPaths, initialIndex, transactionId ->
                    navController.navigate(
                        Screen.ReceiptViewer.createRoute(
                            receiptPaths = receiptPaths,
                            initialIndex = initialIndex,
                            transactionId = transactionId // Pass transaction ID for scrolling back
                        )
                    )
                },
                scrollToTransactionId = scrollToTransactionId,
                onScrollHandled = {
                    // Clear the saved state after handling scroll
                    backStackEntry.savedStateHandle.remove<String>("scroll_to_transaction_id")
                }
            )
        }
        
        // Transaction detail screen (add/edit)
        composable(
            route = Screen.TransactionDetail.routeWithArgs,
            arguments = listOf(
                navArgument("transactionId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            // Get the transaction ID from route arguments
            val transactionId = backStackEntry.arguments?.getString("transactionId")

            // Get receipt URI from saved state (if returning from camera)
            val receiptUriString = backStackEntry.savedStateHandle
                .get<String>("receipt_uri")

            // Get delete receipt index from saved state (if returning from receipt viewer)
            val deleteReceiptIndex = backStackEntry.savedStateHandle
                .get<Int>("delete_receipt_index")

            TransactionDetailScreen(
                receiptUri = receiptUriString?.let { Uri.parse(it) },
                deleteReceiptIndex = deleteReceiptIndex,
                onNavigateBack = { savedTransactionId ->
                    // Pass the transaction ID back to ledger for scrolling
                    // savedTransactionId is null if deleted/discarded, otherwise it's the saved transaction ID
                    if (savedTransactionId != null) {
                        navController.previousBackStackEntry
                            ?.savedStateHandle
                            ?.set("scroll_to_transaction_id", savedTransactionId)
                    }
                    navController.popBackStack()
                },
                onNavigateToCamera = {
                    navController.navigate(Screen.Camera.route)
                },
                onNavigateToReceiptViewer = { receiptPaths, initialIndex, txId ->
                    navController.navigate(
                        Screen.ReceiptViewer.createRoute(
                            receiptPaths = receiptPaths,
                            initialIndex = initialIndex,
                            transactionId = txId
                        )
                    )
                },
                onReceiptHandled = {
                    // Clear the saved state after handling
                    backStackEntry.savedStateHandle.remove<String>("receipt_uri")
                },
                onDeleteReceiptHandled = {
                    // Clear the saved state after handling
                    backStackEntry.savedStateHandle.remove<Int>("delete_receipt_index")
                }
            )
        }
        
        // Recently deleted screen
        composable(Screen.RecentlyDeleted.route) {
            RecentlyDeletedScreen(
                navController = navController,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        // Camera screen
        composable(Screen.Camera.route) {
            CameraScreen(
                onImageCaptured = { uri ->
                    // Pass the URI back to TransactionDetailScreen
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("receipt_uri", uri.toString())
                    navController.popBackStack()
                },
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        // Settings screen
        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToOnboarding = {
                    // Navigate to onboarding and clear entire back stack
                    navController.navigate(Screen.Onboarding.route) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onNavigateToBackupRestore = {
                    navController.navigate(Screen.BackupRestore.route)
                },
                onNavigateToCategoryManagement = {
                    navController.navigate(Screen.CategoryManagement.route)
                }
            )
        }

        // Category Management screen
        composable(Screen.CategoryManagement.route) {
            CategoryManagementScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        // Reports screen
        composable(Screen.Reports.route) {
            ReportsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }



        // Receipt viewer screen
        composable(
            route = Screen.ReceiptViewer.routeWithArgs,
            arguments = listOf(
                navArgument("receiptPaths") {
                    type = NavType.StringType
                },
                navArgument("initialIndex") {
                    type = NavType.IntType
                    defaultValue = 0
                },
                navArgument("transactionId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val receiptPathsString = backStackEntry.arguments?.getString("receiptPaths") ?: ""
            val receiptPaths = if (receiptPathsString.isNotEmpty()) {
                receiptPathsString.split("|")
            } else {
                emptyList()
            }
            val initialIndex = backStackEntry.arguments?.getInt("initialIndex") ?: 0
            val transactionId = backStackEntry.arguments?.getString("transactionId")

            // Determine the previous screen to decide behavior
            val previousRoute = navController.previousBackStackEntry?.destination?.route
            val isFromTransactionDetail = previousRoute == Screen.TransactionDetail.routeWithArgs
            val isFromLedger = previousRoute == Screen.Ledger.route

            ReceiptViewerScreen(
                receiptPaths = receiptPaths,
                initialIndex = initialIndex,
                onNavigateBack = {
                    // If viewing from ledger, pass transaction ID back for scroll positioning
                    if (isFromLedger && transactionId != null && transactionId.isNotEmpty()) {
                        navController.previousBackStackEntry
                            ?.savedStateHandle
                            ?.set("scroll_to_transaction_id", transactionId)
                    }
                    navController.popBackStack()
                },
                // Only allow deletion when in edit mode (from TransactionDetailScreen)
                // When viewing from ledger, no delete button (view-only mode)
                onDeleteReceipt = if (
                    isFromTransactionDetail &&
                    transactionId != null &&
                    transactionId.isNotEmpty()
                ) {
                    { index ->
                        // Pass delete request back to TransactionDetailScreen
                        navController.previousBackStackEntry
                            ?.savedStateHandle
                            ?.set("delete_receipt_index", index)
                        navController.popBackStack()
                    }
                } else {
                    null
                }
            )
        }

        // HMRC SA103 Mapping screen
        composable(Screen.HmrcMapping.route) {
            HmrcMappingScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        // Archive screen
        composable(Screen.Archive.route) {
            ArchiveScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        // Backup and Restore screen
        composable(Screen.BackupRestore.route) {
            BackupRestoreScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        // Profile Selection screen
        composable(Screen.ProfileSelection.route) {
            ProfileSelectionScreen(
                onProfileSelected = {
                    navController.navigate(Screen.Ledger.route) {
                        // Remove profile selection from back stack
                        popUpTo(Screen.ProfileSelection.route) { inclusive = true }
                    }
                }
            )
        }

        // Profile Management screen
        composable(Screen.ProfileManagement.route) {
            ProfileManagementScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        // Category Setup screen (standalone, for profiles without categories)
        composable(Screen.CategorySetup.route) {
            CategorySetupScreen(
                onComplete = {
                    navController.navigate(Screen.Ledger.route) {
                        // Clear the back stack to prevent navigation loops
                        popUpTo(Screen.CategorySetup.route) { inclusive = true }
                    }
                }
            )
        }


    }
}

/**
 * Screen destinations.
 */
sealed class Screen(val route: String) {
    object Onboarding : Screen("onboarding")
    object Ledger : Screen("ledger")

    object TransactionDetail : Screen("transaction_detail") {
        const val routeWithArgs = "transaction_detail?transactionId={transactionId}"
        fun createRoute(transactionId: String) = "transaction_detail?transactionId=$transactionId"
    }

    object RecentlyDeleted : Screen("recently_deleted")

    object Camera : Screen("camera")

    object Settings : Screen("settings")

    object CategoryManagement : Screen("category_management")

    object Reports : Screen("reports")

    object HmrcMapping : Screen("hmrc_mapping")

    object ReceiptViewer : Screen("receipt_viewer") {
        const val routeWithArgs = "receipt_viewer?receiptPaths={receiptPaths}&initialIndex={initialIndex}&transactionId={transactionId}"

        /**
         * Create route for receipt viewer.
         *
         * @param receiptPaths List of receipt file paths
         * @param initialIndex Index of receipt to display first (default: 0)
         * @param transactionId Transaction ID (for scroll positioning when viewing from ledger,
         *                      or for delete functionality when editing from TransactionDetailScreen)
         */
        fun createRoute(
            receiptPaths: List<String>,
            initialIndex: Int = 0,
            transactionId: String? = null
        ): String {
            val pathsParam = receiptPaths.joinToString("|")
            val txIdParam = transactionId ?: ""
            return "receipt_viewer?receiptPaths=$pathsParam&initialIndex=$initialIndex&transactionId=$txIdParam"
        }
    }

    object Archive : Screen("archive")

    object BackupRestore : Screen("backup_restore")

    object ProfileSelection : Screen("profile_selection")

    object ProfileManagement : Screen("profile_management")

    object CategorySetup : Screen("category_setup")
}
