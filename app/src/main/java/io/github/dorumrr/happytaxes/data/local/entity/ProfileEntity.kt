package io.github.dorumrr.happytaxes.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * Profile entity for multi-business/multi-profile support.
 *
 * Business Rules:
 * - Unlimited profiles: Users can create as many profiles as needed
 * - Each profile has complete data isolation (transactions, categories, receipts)
 * - Default profiles "Business" and "Personal" created during onboarding
 * - Cannot delete last remaining profile
 * - Deleting profile removes ALL associated data (transactions, categories, receipts)
 *
 * Default Profile Types:
 * - Business: icon="business", color="#1976D2" (Blue)
 * - Personal: icon="person", color="#388E3C" (Green)
 */
@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey
    val id: String, // UUID

    /**
     * Profile name: "Business" or "Personal"
     */
    val name: String,

    /**
     * Material 3 icon name: "business" or "person"
     */
    val icon: String,

    /**
     * Hex color code for visual distinction
     * Business: #1976D2 (Blue)
     * Personal: #388E3C (Green)
     */
    val color: String,

    val createdAt: Instant,
    val updatedAt: Instant
)

