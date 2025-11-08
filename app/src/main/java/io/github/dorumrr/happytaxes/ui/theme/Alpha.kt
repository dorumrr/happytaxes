package io.github.dorumrr.happytaxes.ui.theme

/**
 * Alpha (transparency) constants for consistent opacity across the app.
 * 
 * Values follow Material Design guidelines for opacity levels.
 * 
 * Usage:
 * - MaterialTheme.colorScheme.error.copy(alpha = Alpha.LIGHT)
 * - Color.Black.copy(alpha = Alpha.MEDIUM)
 */
object Alpha {
    /** 0.1f - Very light tint (10% opacity) - for subtle backgrounds */
    const val LIGHT = 0.1f
    
    /** 0.38f - Disabled state (38% opacity) - Material Design standard */
    const val DISABLED = 0.38f
    
    /** 0.7f - Medium-high opacity (70%) - for overlays */
    const val MEDIUM_HIGH = 0.7f
    
    /** 0.95f - Almost opaque (95%) - for strong overlays */
    const val ALMOST_OPAQUE = 0.95f
}

