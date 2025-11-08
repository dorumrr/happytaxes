package io.github.dorumrr.happytaxes.data.config

import io.github.dorumrr.happytaxes.data.local.ProfileConstants
import io.github.dorumrr.happytaxes.data.local.entity.CategoryEntity
import io.github.dorumrr.happytaxes.data.local.entity.TransactionType
import kotlinx.serialization.Serializable
import java.time.Instant

/**
 * Country-specific configuration loaded from JSON files.
 *
 * Each country has its own JSON configuration file containing:
 * - Country metadata (code, name, fiscal year name)
 * - Tax period defaults (start/end dates)
 * - Currency information (code, symbol, name)
 * - Tax authority information
 * - Default categories (income and expense)
 *
 * JSON files are located in: app/src/main/assets/countries/
 * - gb.json - United Kingdom
 * - us.json - United States
 * - ca.json - Canada
 * - au.json - Australia
 * - nz.json - New Zealand
 * - ro.json - Romania
 * - default.json - Universal categories for OTHER countries
 */
@Serializable
data class CountryConfiguration(
    val country: CountryMetadata,
    val taxPeriod: TaxPeriodConfig,
    val currency: CurrencyConfig,
    val taxAuthority: TaxAuthorityConfig,
    val categories: CategoriesConfig,
    val metadata: ConfigMetadata
)

/**
 * Country metadata.
 */
@Serializable
data class CountryMetadata(
    val code: String,
    val name: String,
    val fiscalYearName: String
)

/**
 * Tax period configuration (fiscal year start/end dates).
 */
@Serializable
data class TaxPeriodConfig(
    val startMonth: Int,
    val startDay: Int,
    val endMonth: Int,
    val endDay: Int,
    val description: String
)

/**
 * Currency configuration.
 */
@Serializable
data class CurrencyConfig(
    val code: String,
    val symbol: String,
    val name: String,
    val decimalSeparator: String = ".",  // Default to period for backward compatibility
    val thousandSeparator: String = ","  // Default to comma for backward compatibility
)

/**
 * Tax authority configuration.
 */
@Serializable
data class TaxAuthorityConfig(
    val name: String,
    val fullName: String,
    val selfAssessmentForm: String,
    val formDescription: String,
    val website: String
)

/**
 * Categories configuration (income and expense categories).
 */
@Serializable
data class CategoriesConfig(
    val income: List<CategoryDefinition>,
    val expense: List<CategoryDefinition>
)

/**
 * Category definition from JSON.
 */
@Serializable
data class CategoryDefinition(
    val id: String,
    val name: String,
    val icon: String,
    val color: String,
    val taxFormReference: String,
    val taxFormDescription: String,
    val displayOrder: Int,
    val description: String
)

/**
 * Configuration metadata.
 */
@Serializable
data class ConfigMetadata(
    val version: String,
    val lastUpdated: String,
    val source: String,
    val notes: String
)

/**
 * Extension function to convert CategoryDefinition to CategoryEntity.
 *
 * TEMPORARY: Uses default profile ID. Will be replaced with proper profile context in Phase 2-3.
 */
fun CategoryDefinition.toEntity(
    countryCode: String,
    type: TransactionType,
    profileId: String = ProfileConstants.DEFAULT_PROFILE_ID
): CategoryEntity {
    val now = Instant.now()
    return CategoryEntity(
        id = id,
        profileId = profileId,
        name = name,
        type = type,
        icon = icon,
        color = color,
        taxFormReference = taxFormReference,
        taxFormDescription = taxFormDescription,
        countryCode = countryCode,
        isCustom = false,
        isArchived = false,
        displayOrder = displayOrder,
        createdAt = now,
        updatedAt = now
    )
}

/**
 * Extension function to convert CategoriesConfig to list of CategoryEntity.
 *
 * TEMPORARY: Uses default profile ID. Will be replaced with proper profile context in Phase 2-3.
 */
fun CategoriesConfig.toEntities(
    countryCode: String,
    profileId: String = ProfileConstants.DEFAULT_PROFILE_ID
): List<CategoryEntity> {
    val incomeEntities = income.map { it.toEntity(countryCode, TransactionType.INCOME, profileId) }
    val expenseEntities = expense.map { it.toEntity(countryCode, TransactionType.EXPENSE, profileId) }
    return incomeEntities + expenseEntities
}

