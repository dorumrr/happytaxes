package io.github.dorumrr.happytaxes.ui.theme

import androidx.compose.ui.graphics.Color

// Brand colors - Primary (Blue)
// Material 3 requires different shades for light and dark modes
val PrimaryLight = Color(0xFF0065C2) // Brand blue for light mode (darker shade)
val PrimaryDark = Color(0xFF6DB6FF) // Lighter blue for dark mode (Material 3 Blue 200)

// Secondary color (Green) - Used for "Custom" labels
val Secondary = Color(0xFF4CAF50) // Green - Used for "Custom" labels in CategoryManagementScreen

// Light mode colors (income/expense)
val TertiaryLight = Color(0xFF2E7D32) // Dark green for income (readable on light background)
val ErrorLight = Color(0xFFC62828) // Dark red for expenses (readable on light background)

// Dark mode colors (income/expense)
val TertiaryDark = Color(0xFF81C784) // Light green for income (readable on dark background)
val ErrorDark = Color(0xFFEF9A9A) // Light red for expenses (readable on dark background)

// Note: Background, surface, and text colors now use Material 3 defaults
// Material 3 provides:
// - Light mode: background/surface = #FFFBFE (warm off-white), text = #1C1B1F (very dark gray)
// - Dark mode: background/surface = #1C1B1F (warm dark gray), text = #E6E1E5 (soft off-white)

