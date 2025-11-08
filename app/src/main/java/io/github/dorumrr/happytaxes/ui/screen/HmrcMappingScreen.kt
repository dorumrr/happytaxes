package io.github.dorumrr.happytaxes.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import io.github.dorumrr.happytaxes.ui.theme.Spacing
import io.github.dorumrr.happytaxes.ui.util.rememberDebouncedNavigation

/**
 * HMRC SA103 Mapping screen.
 *
 * Shows how app categories map to HMRC Self Assessment SA103 form boxes.
 * Helps users understand which box to use when filing their tax return.
 *
 * PRD Reference: Section 4.16 - HMRC SA103 Alignment
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HmrcMappingScreen(
    onNavigateBack: () -> Unit
) {
    val scrollState = rememberScrollState()

    // Debounced navigation to prevent double-clicks
    val debouncedBack = rememberDebouncedNavigation(onNavigate = onNavigateBack)

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("HMRC SA103 Mapping") },
                navigationIcon = {
                    IconButton(onClick = debouncedBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(Spacing.medium)
        ) {
            // Introduction
            Text(
                text = "This guide shows how HappyTaxes categories map to HMRC Self Assessment SA103 form boxes.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(Spacing.large))

            // Income Categories
            Text(
                text = "INCOME CATEGORIES",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(Spacing.mediumSmall))

            MappingItem(
                category = "Sales/Turnover",
                box = "Box 15",
                description = "Sales/business income (turnover)"
            )

            MappingItem(
                category = "Other Income",
                box = "Box 16",
                description = "Other business income"
            )

            Spacer(modifier = Modifier.height(Spacing.large))

            // Expense Categories
            Text(
                text = "EXPENSE CATEGORIES",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(Spacing.mediumSmall))

            MappingItem(
                category = "Cost of Goods",
                box = "Box 17",
                description = "Cost of goods bought for resale or goods used"
            )

            MappingItem(
                category = "Construction Industry Subcontractors",
                box = "Box 18",
                description = "Construction industry - subcontractors"
            )

            MappingItem(
                category = "Wages/Salaries",
                box = "Box 19",
                description = "Wages, salaries and other staff costs"
            )

            MappingItem(
                category = "Car/Van/Travel",
                box = "Box 20",
                description = "Car, van and travel expenses"
            )

            MappingItem(
                category = "Rent/Rates/Power",
                box = "Box 21",
                description = "Rent, rates, power and insurance costs"
            )

            MappingItem(
                category = "Repairs/Renewals",
                box = "Box 22",
                description = "Repairs and renewals of property and equipment"
            )

            MappingItem(
                category = "Phone/Fax/Stationery",
                box = "Box 23",
                description = "Phone, fax, stationery and other office costs"
            )

            MappingItem(
                category = "Advertising/Marketing",
                box = "Box 24",
                description = "Advertising and business entertainment costs"
            )

            MappingItem(
                category = "Interest/Finance",
                box = "Box 25",
                description = "Interest on bank and other loans"
            )

            MappingItem(
                category = "Bank/Credit Card/Finance Charges",
                box = "Box 26",
                description = "Bank, credit card and other financial charges"
            )

            MappingItem(
                category = "Irrecoverable Debts",
                box = "Box 27",
                description = "Irrecoverable debts written off"
            )

            MappingItem(
                category = "Accountancy/Legal/Professional",
                box = "Box 28",
                description = "Accountancy, legal and other professional fees"
            )

            MappingItem(
                category = "Other Business Expenses",
                box = "Box 29",
                description = "Other allowable business expenses"
            )

            Spacer(modifier = Modifier.height(Spacing.large))

            // Footer note
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(Spacing.medium)
                ) {
                    Text(
                        text = "Note",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(modifier = Modifier.height(Spacing.small))
                    Text(
                        text = "This mapping is for guidance only. Always consult the official HMRC SA103 form and guidance notes when filing your Self Assessment tax return.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(Spacing.medium))
        }
    }
}

/**
 * Single mapping item showing category → box → description.
 */
@Composable
private fun MappingItem(
    category: String,
    box: String,
    description: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.extraSmall),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier.padding(Spacing.medium)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = category,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = box,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(Spacing.extraSmall))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

