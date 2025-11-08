package io.github.dorumrr.happytaxes.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * Category entity for organizing transactions.
 *
 * Business Rules:
 * - Pre-populated with country-specific tax categories (loaded from JSON configs)
 * - User can add custom categories
 * - Cannot delete categories with transactions (soft delete)
 * - Display order for UI sorting
 * - Multi-profile: Each category belongs to exactly one profile
 * - Unique constraint: (profileId, name, type) must be unique
 *
 * Version History:
 * - Version 8: Initial schema with hmrcBox/hmrcDescription
 * - Version 9: Added countryCode, renamed hmrcBox→taxFormReference, hmrcDescription→taxFormDescription
 * - Version 13: Added profileId for multi-profile support
 * - Version 15: Added unique index on (profileId, name, type) to prevent duplicates
 */
@Entity(
    tableName = "categories",
    indices = [
        androidx.room.Index(value = ["profileId", "name", "type"], unique = true)
    ]
)
data class CategoryEntity(
    @PrimaryKey
    val id: String,

    // Profile association (multi-profile support)
    val profileId: String,

    val name: String,
    val type: TransactionType,
    val icon: String?, // Material icon name
    val color: String?, // Hex color code

    // Tax form mapping (country-agnostic)
    val taxFormReference: String?, // e.g., "Box 17" (UK), "Line 8" (US), "Deductible" (RO)
    val taxFormDescription: String?, // Description of the tax form field

    // Country association
    val countryCode: String? = null, // ISO 3166-1 alpha-2 code (e.g., "GB", "US", "RO")

    // User management
    val isCustom: Boolean = false,
    val isArchived: Boolean = false,
    val displayOrder: Int = 0,

    val createdAt: Instant,
    val updatedAt: Instant
)

