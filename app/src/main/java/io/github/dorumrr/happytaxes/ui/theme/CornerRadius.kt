package io.github.dorumrr.happytaxes.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * Corner radius constants for consistent rounded corners.
 * 
 * Provides both Dp values and pre-built RoundedCornerShape objects.
 * 
 * Usage:
 * - .clip(CornerRadius.small)
 * - .background(shape = CornerRadius.medium)
 */
object CornerRadius {
    // Dp values
    /** 8dp corner radius */
    val smallDp = 8.dp
    
    /** 12dp corner radius */
    val mediumDp = 12.dp
    
    /** 16dp corner radius */
    val largeDp = 16.dp
    
    // Pre-built shapes
    /** 8dp rounded corner shape */
    val small = RoundedCornerShape(smallDp)
    
    /** 12dp rounded corner shape */
    val medium = RoundedCornerShape(mediumDp)
    
    /** 16dp rounded corner shape */
    val large = RoundedCornerShape(largeDp)
}

