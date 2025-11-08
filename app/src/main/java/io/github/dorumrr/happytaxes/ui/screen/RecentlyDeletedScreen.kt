package io.github.dorumrr.happytaxes.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.navigation.NavController
import io.github.dorumrr.happytaxes.ui.theme.Spacing
import io.github.dorumrr.happytaxes.ui.theme.Dimensions
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.dorumrr.happytaxes.domain.model.Transaction
import io.github.dorumrr.happytaxes.domain.model.TransactionType
import io.github.dorumrr.happytaxes.ui.component.ReceiptThumbnailGrid
import io.github.dorumrr.happytaxes.ui.navigation.Screen
import io.github.dorumrr.happytaxes.ui.util.rememberDebouncedNavigation
import io.github.dorumrr.happytaxes.ui.viewmodel.TransactionListViewModel
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Recently deleted screen showing soft-deleted transactions.
 * 
 * Features:
 * - List of deleted transactions
 * - Receipt viewing (thumbnails + fullscreen viewer)
 * - Restore action
 * - Permanent delete action
 * - Days remaining indicator
 * - Empty state
 * - Auto-cleanup after 30 days
 * 
 * PRD Section 4.10: Recently Deleted
 * PRD Section 15.3: Receipt Management - receipts viewable in all contexts
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecentlyDeletedScreen(
    navController: NavController,
    onNavigateBack: () -> Unit,
    viewModel: TransactionListViewModel = hiltViewModel()
) {
    val deletedTransactions by viewModel.deleted.collectAsState()
    val baseCurrency by viewModel.baseCurrency.collectAsState()
    val decimalSeparator by viewModel.decimalSeparator.collectAsState()
    val thousandSeparator by viewModel.thousandSeparator.collectAsState()

    // Debounced navigation to prevent double-clicks
    val debouncedBack = rememberDebouncedNavigation(onNavigate = onNavigateBack)

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("Recently Deleted") },
                navigationIcon = {
                    IconButton(onClick = debouncedBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (deletedTransactions.isEmpty()) {
            EmptyDeletedState(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // Info banner
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Spacing.medium)
                ) {
                    Row(
                        modifier = Modifier.padding(Spacing.medium),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.mediumSmall)
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = "Deleted transactions are kept for 30 days. " +
                                   "After that, they're permanently removed.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
                
                // Deleted transactions list
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = Spacing.small)
                ) {
                    items(deletedTransactions, key = { it.id }) { transaction ->
                        DeletedTransactionItem(
                            transaction = transaction,
                            baseCurrency = baseCurrency,
                            decimalSeparator = decimalSeparator,
                            thousandSeparator = thousandSeparator,
                            onRestore = { viewModel.restoreTransaction(transaction.id) },
                            onReceiptClick = { index ->
                                navController.navigate(
                                    Screen.ReceiptViewer.createRoute(
                                        receiptPaths = transaction.receiptPaths,
                                        initialIndex = index,
                                        transactionId = null  // Read-only mode - no delete button
                                    )
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DeletedTransactionItem(
    transaction: Transaction,
    baseCurrency: String,
    decimalSeparator: String,
    thousandSeparator: String,
    onRestore: () -> Unit,
    onReceiptClick: (Int) -> Unit
) {
    val dateFormatter = remember { DateTimeFormatter.ofPattern("dd MMM yyyy") }
    
    // Calculate days remaining
    val daysRemaining = transaction.deletedAt?.let { deletedAt ->
        val daysSinceDeleted = ChronoUnit.DAYS.between(
            deletedAt.atZone(java.time.ZoneId.systemDefault()).toLocalDate(),
            java.time.LocalDate.now()
        )
        30 - daysSinceDeleted.toInt()
    } ?: 30
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.medium, vertical = Spacing.extraSmall)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.medium)
        ) {
            // Main content row: receipt thumbnail + transaction details + amount
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Receipt thumbnail on the LEFT (matches Ledger screen)
                if (transaction.receiptPaths.isNotEmpty()) {
                    ReceiptThumbnailGrid(
                        receiptPaths = transaction.receiptPaths,
                        modifier = Modifier.padding(end = Spacing.mediumSmall),
                        onReceiptClick = onReceiptClick
                    )
                } else {
                    // Show icon when no receipt (same size as receipt thumbnail)
                    Box(
                        modifier = Modifier
                            .size(Dimensions.receiptThumbnail)
                            .background(
                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                shape = MaterialTheme.shapes.small
                            )
                            .padding(end = Spacing.mediumSmall),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Receipt,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(Dimensions.iconMedium)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.width(Spacing.mediumSmall))
                
                // Transaction details in the middle
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = transaction.category,
                        style = MaterialTheme.typography.titleMedium
                    )
                    transaction.description?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = transaction.date.format(dateFormatter),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Spacer(modifier = Modifier.width(Spacing.small))
                
                // Amount and days remaining on the RIGHT
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = transaction.getDisplayAmount(baseCurrency, decimalSeparator, thousandSeparator),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (transaction.type == TransactionType.INCOME) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        }
                    )
                    Text(
                        text = "$daysRemaining days left",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(Spacing.mediumSmall))

            // Action button
            Button(
                onClick = onRestore,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Restore, contentDescription = null, Modifier.size(Dimensions.iconExtraSmall))
                Spacer(Modifier.width(Spacing.small))
                Text("Restore Transaction")
            }
        }
    }
}

@Composable
private fun EmptyDeletedState(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(Spacing.extraLarge),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.DeleteOutline,
            contentDescription = null,
            modifier = Modifier.size(Dimensions.componentMedium),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(Spacing.medium))
        Text(
            text = "No deleted transactions",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(Spacing.small))
        Text(
            text = "Deleted transactions appear here for 30 days",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

