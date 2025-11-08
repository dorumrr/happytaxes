package io.github.dorumrr.happytaxes.util

import java.math.BigDecimal

/**
 * Utility for formatting currency amounts with proper symbols.
 * 
 * Provides currency symbols for common currencies and formatting functions.
 */
object CurrencyFormatter {
    
    /**
     * Get currency symbol for a given currency code.
     *
     * @param currencyCode ISO 4217 currency code (e.g., "USD", "GBP", "EUR")
     * @return Currency symbol (e.g., "$", "£", "€") or null if no symbol available
     */
    fun getCurrencySymbol(currencyCode: String): String? {
        return when (currencyCode.uppercase()) {
            "USD" -> "$"
            "GBP" -> "£"
            "EUR" -> "€"
            "JPY" -> "¥"
            "CNY" -> "¥"
            "CAD" -> "$"
            "AUD" -> "$"
            "NZD" -> "$"
            "INR" -> "₹"
            "BRL" -> "R$"
            "ZAR" -> "R"
            "KRW" -> "₩"
            "THB" -> "฿"
            "PHP" -> "₱"
            "VND" -> "₫"
            "ILS" -> "₪"
            "RUB" -> "₽"
            "TRY" -> "₺"
            "MXN" -> "$"
            "SGD" -> "$"
            "HKD" -> "$"
            // Currencies without widely recognized symbols - return null
            else -> null
        }
    }
    
    /**
     * Format amount with currency symbol or code.
     *
     * @param amount Amount to format
     * @param currencyCode ISO 4217 currency code
     * @param showSymbol Whether to show currency symbol/code (default: true)
     * @param decimalSeparator Decimal separator ("." or ",")
     * @param thousandSeparator Thousand separator ("," or "." or " ")
     * @return Formatted string (e.g., "£123.45", "$123.45", or "123.45 RON")
     */
    fun formatAmount(
        amount: BigDecimal,
        currencyCode: String,
        showSymbol: Boolean = true,
        decimalSeparator: String = ".",
        thousandSeparator: String = ","
    ): String {
        val formattedAmount = NumberFormatter.formatAmount(
            amount = amount,
            decimalSeparator = decimalSeparator,
            thousandSeparator = thousandSeparator,
            decimalPlaces = 2
        )
        return if (showSymbol) {
            val symbol = getCurrencySymbol(currencyCode)
            if (symbol != null) {
                // Has a symbol - place before amount
                "$symbol$formattedAmount"
            } else {
                // No symbol - use currency code after amount
                "$formattedAmount $currencyCode"
            }
        } else {
            formattedAmount
        }
    }
    
    /**
     * Format amount with +/- prefix for income/expense display.
     *
     * @param amount Amount to format
     * @param currencyCode ISO 4217 currency code
     * @param isExpense Whether this is an expense (shows minus sign)
     * @param decimalSeparator Decimal separator ("." or ",")
     * @param thousandSeparator Thousand separator ("," or "." or " ")
     * @return Formatted string with prefix (e.g., "+ £123.45", "- $123.45", or "- 123.45 RON")
     */
    fun formatAmountWithSign(
        amount: BigDecimal,
        currencyCode: String,
        isExpense: Boolean,
        decimalSeparator: String = ".",
        thousandSeparator: String = ","
    ): String {
        val formattedAmount = NumberFormatter.formatAmount(
            amount = amount,
            decimalSeparator = decimalSeparator,
            thousandSeparator = thousandSeparator,
            decimalPlaces = 2
        )
        val symbol = getCurrencySymbol(currencyCode)
        val prefix = if (isExpense) "- " else "+ "

        return if (symbol != null) {
            // Has symbol: "- $50.00" or "+ $50.00"
            "$prefix$symbol$formattedAmount"
        } else {
            // No symbol: "- 50.00 RON" or "+ 50.00 RON"
            "$prefix$formattedAmount $currencyCode"
        }
    }

}

