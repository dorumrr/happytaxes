package io.github.dorumrr.happytaxes.ui.util

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Centralized profile icon mapping utility.
 * 
 * Maps profile icon names to Material Design icons.
 * Used by LedgerScreen and ProfileManagementScreen to avoid code duplication.
 * 
 * DRY Principle: Single source of truth for profile icon mappings.
 */
object ProfileIconMapper {
    /**
     * Get Material Icon for a profile icon name.
     * 
     * @param iconName Icon name (e.g., "business", "person", "work")
     * @return Corresponding Material Icon, defaults to Business icon if not found
     */
    @Composable
    fun getIcon(iconName: String): ImageVector {
        return when (iconName) {
            "business" -> Icons.Default.Business
            "person" -> Icons.Default.Person
            "work" -> Icons.Default.Work
            "home" -> Icons.Default.Home
            "store" -> Icons.Default.Store
            "shopping_cart" -> Icons.Default.ShoppingCart
            "account_balance" -> Icons.Default.AccountBalance
            "savings" -> Icons.Default.Savings
            "attach_money" -> Icons.Default.AttachMoney
            "credit_card" -> Icons.Default.CreditCard
            "local_shipping" -> Icons.Default.LocalShipping
            "restaurant" -> Icons.Default.Restaurant
            "local_cafe" -> Icons.Default.LocalCafe
            "fitness_center" -> Icons.Default.FitnessCenter
            "school" -> Icons.Default.School
            "computer" -> Icons.Default.Computer
            "build" -> Icons.Default.Build
            "palette" -> Icons.Default.Palette
            "music_note" -> Icons.Default.MusicNote
            "camera_alt" -> Icons.Default.CameraAlt
            else -> Icons.Default.Business
        }
    }
}