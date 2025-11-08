package io.github.dorumrr.happytaxes.util

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Utility for formatting numbers with country-specific decimal and thousand separators.
 *
 * This formatter respects the user's selected country preferences for:
 * - Decimal separator (. or ,)
 * - Thousand separator (, or . or space)
 *
 * Examples:
 * - US/UK format: 1,234.56
 * - EU format: 1.234,56
 * - Some countries: 1 234,56
 */
object NumberFormatter {

    /**
     * Format a BigDecimal amount with country-specific separators.
     *
     * @param amount Amount to format
     * @param decimalSeparator Decimal separator ("." or ",")
     * @param thousandSeparator Thousand separator ("," or "." or " ")
     * @param decimalPlaces Number of decimal places (default: 2)
     * @return Formatted string (e.g., "1,234.56" or "1.234,56")
     */
    fun formatAmount(
        amount: BigDecimal,
        decimalSeparator: String = ".",
        thousandSeparator: String = ",",
        decimalPlaces: Int = 2
    ): String {
        // Round to specified decimal places
        val rounded = amount.setScale(decimalPlaces, RoundingMode.HALF_UP)
        
        // Split into integer and decimal parts
        val parts = rounded.toPlainString().split('.')
        val integerPart = parts[0]
        val decimalPart = if (parts.size > 1) parts[1] else "0".repeat(decimalPlaces)
        
        // Format integer part with thousand separators
        val formattedInteger = formatIntegerWithSeparators(integerPart, thousandSeparator)
        
        // Combine with decimal separator
        return if (decimalPlaces > 0) {
            "$formattedInteger$decimalSeparator${decimalPart.padEnd(decimalPlaces, '0')}"
        } else {
            formattedInteger
        }
    }

    /**
     * Format integer part with thousand separators.
     *
     * @param integerStr Integer part as string (may include negative sign)
     * @param separator Thousand separator
     * @return Formatted string with thousand separators
     */
    private fun formatIntegerWithSeparators(integerStr: String, separator: String): String {
        // Handle negative numbers
        val isNegative = integerStr.startsWith("-")
        val digits = if (isNegative) integerStr.substring(1) else integerStr
        
        // Add thousand separators from right to left
        val result = StringBuilder()
        var count = 0
        
        for (i in digits.length - 1 downTo 0) {
            if (count > 0 && count % 3 == 0) {
                result.insert(0, separator)
            }
            result.insert(0, digits[i])
            count++
        }
        
        return if (isNegative) "-$result" else result.toString()
    }

    /**
     * Parse a string with country-specific separators to BigDecimal.
     *
     * This function intelligently handles both comma and period as decimal separators.
     * It uses the same logic as TransactionDetailViewModel.normalizeDecimalInput().
     *
     * @param input Input string (e.g., "1,234.56" or "1.234,56")
     * @return BigDecimal or null if parsing fails
     */
    fun parseAmount(input: String): BigDecimal? {
        if (input.isBlank()) return null
        
        // Remove spaces and normalize
        var normalized = input.trim().replace(" ", "")
        
        // Count separators to determine format
        val commaCount = normalized.count { it == ',' }
        val periodCount = normalized.count { it == '.' }
        
        // Determine which separator is the decimal separator
        when {
            // No separators - just digits
            commaCount == 0 && periodCount == 0 -> {
                // Keep as is
            }
            
            // Only commas - could be decimal or thousand separator
            commaCount > 0 && periodCount == 0 -> {
                // If only one comma, treat as decimal separator
                // If multiple commas, treat as thousand separators and remove them
                if (commaCount == 1) {
                    // Single comma - decimal separator (e.g., "123,45")
                    normalized = normalized.replace(',', '.')
                } else {
                    // Multiple commas - thousand separators (e.g., "1,234,567")
                    normalized = normalized.replace(",", "")
                }
            }
            
            // Only periods - could be decimal or thousand separator
            periodCount > 0 && commaCount == 0 -> {
                // If only one period, treat as decimal separator
                // If multiple periods, treat as thousand separators and remove them
                if (periodCount == 1) {
                    // Single period - decimal separator (e.g., "123.45")
                    // Already in correct format
                } else {
                    // Multiple periods - thousand separators (e.g., "1.234.567")
                    normalized = normalized.replace(".", "")
                }
            }
            
            // Both commas and periods - determine which is decimal
            else -> {
                // Find last separator - that's the decimal separator
                val lastCommaIndex = normalized.lastIndexOf(',')
                val lastPeriodIndex = normalized.lastIndexOf('.')
                
                if (lastCommaIndex > lastPeriodIndex) {
                    // Comma is decimal separator (EU format: "1.234,56")
                    normalized = normalized.replace(".", "").replace(',', '.')
                } else {
                    // Period is decimal separator (US format: "1,234.56")
                    normalized = normalized.replace(",", "")
                }
            }
        }
        
        return normalized.toBigDecimalOrNull()
    }

    /**
     * Format amount for display in input field (preserves user's preferred separator).
     *
     * @param amount Amount to format
     * @param decimalSeparator User's preferred decimal separator
     * @return Formatted string without thousand separators (e.g., "1234.56" or "1234,56")
     */
    fun formatForInput(
        amount: BigDecimal,
        decimalSeparator: String = "."
    ): String {
        val plainString = amount.toPlainString()
        return if (decimalSeparator == ",") {
            plainString.replace('.', ',')
        } else {
            plainString
        }
    }
}

