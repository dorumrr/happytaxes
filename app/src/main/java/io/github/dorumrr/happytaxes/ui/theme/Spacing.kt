package io.github.dorumrr.happytaxes.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Spacing constants for consistent UI across the app.
 * 
 * Usage:
 * - Spacer(modifier = Modifier.height(Spacing.large))
 * - .padding(Spacing.medium)
 */
object Spacing {
    /** 2dp - Extra minimal spacing */
    val extraExtraSmall: Dp = 2.dp

    /** 4dp - Minimal spacing for tight layouts */
    val extraSmall: Dp = 4.dp

    /** 6dp - Very small spacing */
    val verySmall: Dp = 6.dp

    /** 8dp - Small spacing between related elements */
    val small: Dp = 8.dp

    /** 12dp - Medium-small spacing */
    val mediumSmall: Dp = 12.dp

    /** 16dp - Standard spacing between elements */
    val medium: Dp = 16.dp

    /** 20dp - Medium-large spacing */
    val mediumLarge: Dp = 20.dp

    /** 24dp - Large spacing between sections */
    val large: Dp = 24.dp

    /** 32dp - Extra large spacing for major sections */
    val extraLarge: Dp = 32.dp

    /** 48dp - Top padding for screens */
    val screenTop: Dp = 48.dp

    /** 56dp - Standard button height */
    val buttonHeight: Dp = 56.dp

    /** 64dp - Large component spacing */
    val componentLarge: Dp = 64.dp
}

