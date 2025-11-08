package io.github.dorumrr.happytaxes.domain.model

import java.time.Instant

/**
 * Domain model for a profile.
 *
 * Represents a profile with complete data isolation.
 * Each profile has its own transactions, categories, and receipts.
 *
 * Business Rules:
 * - Unlimited profiles allowed
 * - Cannot delete last remaining profile
 * - Deleting profile removes ALL associated data
 * - Users can customize icon and color
 */
data class Profile(
    val id: String,
    val name: String,
    val icon: String,
    val color: String,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    companion object {
        /**
         * Default profile names.
         */
        const val NAME_BUSINESS = "Business"
        const val NAME_PERSONAL = "Personal"

        /**
         * Available profile icons (Material 3 icon names).
         */
        val AVAILABLE_ICONS = listOf(
            "business",
            "person",
            "work",
            "home",
            "store",
            "shopping_cart",
            "account_balance",
            "savings",
            "attach_money",
            "credit_card",
            "local_shipping",
            "restaurant",
            "local_cafe",
            "fitness_center",
            "school",
            "computer",
            "build",
            "palette",
            "music_note",
            "camera_alt"
        )

        /**
         * Available profile colors (hex codes).
         */
        val AVAILABLE_COLORS = listOf(
            "#1976D2", // Blue
            "#388E3C", // Green
            "#D32F2F", // Red
            "#7B1FA2", // Purple
            "#F57C00", // Orange
            "#0097A7", // Cyan
            "#C2185B", // Pink
            "#5D4037", // Brown
            "#455A64", // Blue Grey
            "#FBC02D", // Yellow
            "#00796B", // Teal
            "#512DA8", // Deep Purple
            "#E64A19", // Deep Orange
            "#303F9F", // Indigo
            "#689F38"  // Light Green
        )

        /**
         * Default icon for Business profile.
         */
        const val ICON_BUSINESS = "business"

        /**
         * Default icon for Personal profile.
         */
        const val ICON_PERSONAL = "person"

        /**
         * Default color for Business profile.
         */
        const val COLOR_BUSINESS = "#1976D2" // Blue

        /**
         * Default color for Personal profile.
         */
        const val COLOR_PERSONAL = "#388E3C" // Green
    }
}

