package io.github.dorumrr.happytaxes.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Dimension constants for UI components.
 * 
 * Organized by component type for easy discovery.
 * All values follow Material 3 design guidelines.
 * 
 * Usage:
 * - .size(Dimensions.iconMedium)
 * - .size(Dimensions.receiptThumbnail)
 */
object Dimensions {
    // Icon sizes
    /** 16dp - Small icons (close buttons, badges) */
    val iconSmall: Dp = 16.dp

    /** 20dp - Medium-small icons */
    val iconMediumSmall: Dp = 20.dp
    
    /** 24dp - Standard icons */
    val iconMedium: Dp = 24.dp
    
    /** 32dp - Large icons */
    val iconLarge: Dp = 32.dp
    
    /** 48dp - Extra large icons (category icons without receipt) */
    val iconExtraLarge: Dp = 48.dp
    
    // Component sizes
    /** 56dp - Receipt thumbnail size */
    val receiptThumbnail: Dp = 56.dp
    
    /** 64dp - Medium component size */
    val componentMedium: Dp = 64.dp
    
    /** 80dp - Large component size */
    val componentLarge: Dp = 80.dp
    
    /** 88dp - FAB bottom padding to prevent overlap */
    val fabBottomPadding: Dp = 88.dp

    /** 80dp - Bottom navigation bar height */
    val bottomNavHeight: Dp = 80.dp

    /** 168dp - Combined FAB + bottom nav padding (88dp FAB + 80dp nav bar) */
    val fabWithBottomNavPadding: Dp = 168.dp
    
    /** 90dp - Extra large component */
    val componentExtraLarge: Dp = 90.dp
    
    /** 100dp - Receipt thumbnail in grid/gallery */
    val receiptThumbnailGrid: Dp = 100.dp
    
    /** 110dp - Large thumbnail */
    val thumbnailLarge: Dp = 110.dp
    
    /** 120dp - Extra large thumbnail */
    val thumbnailExtraLarge: Dp = 120.dp

    // Stroke widths
    /** 1dp - Thin stroke */
    val strokeThin: Dp = 1.dp

    /** 2dp - Standard stroke width */
    val strokeStandard: Dp = 2.dp

    // Specific UI measurements
    /** 72dp - List item height */
    val listItemHeight: Dp = 72.dp

    /** 48dp - Onboarding icon spacing */
    val onboardingIconSpacing: Dp = 48.dp

    /** 80dp - FAB clearance height */
    val fabClearance: Dp = 80.dp

    /** 110dp - Input field width */
    val inputFieldWidth: Dp = 110.dp

    /** 18dp - Small icon size */
    val iconExtraSmall: Dp = 18.dp
}
