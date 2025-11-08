package io.github.dorumrr.happytaxes.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryDark, // Lighter blue for dark mode (#6DB6FF)
    secondary = Secondary,
    tertiary = TertiaryDark, // Light green for income (readable on dark background)
    tertiaryContainer = Color(0xFF1B5E20), // Dark green container for income chips (dark mode)
    onTertiaryContainer = Color(0xFFA5D6A7), // Light green text on dark green container
    error = ErrorDark, // Light red for expenses (readable on dark background)
    errorContainer = Color(0xFF93000A), // Dark red container for expense chips (dark mode)
    onErrorContainer = Color(0xFFFFDAD6), // Light red text on dark red container
    onError = Color(0xFF000000) // Dark text on light red background
    // Material 3 defaults for dark mode:
    // - background = #1C1B1F (warm dark gray, not pure black)
    // - surface = #1C1B1F (same as background)
    // - onBackground = #E6E1E5 (soft off-white, not pure white)
    // - onSurface = #E6E1E5 (same as onBackground)
    // - onPrimary, onSecondary = auto-calculated for optimal contrast
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryLight, // Brand blue for light mode (#0065C2)
    secondary = Secondary,
    tertiary = TertiaryLight, // Dark green for income (readable on light background)
    tertiaryContainer = Color(0xFFC8E6C9), // Light green container for income chips (light mode)
    onTertiaryContainer = Color(0xFF1B5E20), // Dark green text on light green container
    error = ErrorLight, // Dark red for expenses (readable on light background)
    errorContainer = Color(0xFFFFDAD6), // Light red container for expense chips (light mode)
    onErrorContainer = Color(0xFF93000A) // Dark red text on light red container
    // Material 3 defaults for light mode:
    // - background = #FFFBFE (warm off-white, not cool gray)
    // - surface = #FFFBFE (same as background)
    // - onBackground = #1C1B1F (very dark gray, not pure black)
    // - onSurface = #1C1B1F (same as onBackground)
    // - onPrimary, onSecondary, onError = auto-calculated for optimal contrast
)

@Composable
fun HappyTaxesTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color disabled to ensure consistent brand color (#0065C2)
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

