package io.github.dorumrr.happytaxes.domain.model

import java.time.Instant

/**
 * Domain model for a category.
 */
data class Category(
    val id: String,
    val name: String,
    val type: TransactionType,
    val icon: String?,
    val color: String?,
    val taxFormReference: String?, // e.g., "Box 17" (UK), "Line 8" (US), "Deductible" (RO)
    val taxFormDescription: String?, // Description of the tax form field
    val countryCode: String?, // ISO 3166-1 alpha-2 code (e.g., "GB", "US", "RO")
    val isCustom: Boolean,
    val isArchived: Boolean,
    val displayOrder: Int,
    val createdAt: Instant,
    val updatedAt: Instant
)

