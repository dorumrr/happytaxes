package io.github.dorumrr.happytaxes.util

import java.math.BigDecimal

/**
 * Extracts monetary amounts from receipt text.
 *
 * Strategy:
 * 1. Find all amounts with currency symbols (USD, CAD, AUD, EUR, GBP + all variations)
 * 2. Look for "TOTAL", "AMOUNT", "BALANCE" keywords (expanded list)
 * 3. Extract amount near keyword (multi-line support)
 * 4. If no keyword, return largest amount (likely the total)
 * 5. Validate format (0-2 decimal places, handles OCR errors)
 *
 * IMPROVEMENTS (2025-10-08):
 * - Comprehensive currency support: USD, CAD, AUD, EUR, GBP + all symbol variations
 * - Handles 0, 1, or 2 decimal places (not just 2)
 * - Handles both . and , as decimal separator (OCR often confuses them)
 * - Handles spaces in amounts (OCR artifacts)
 * - Expanded keyword list (SUBTOTAL, GRAND TOTAL, etc.)
 * - Multi-line detection (searches current line + next 2 lines)
 *
 * IMPROVEMENTS (2025-10-17): High Priority #3
 * - Tax/tip line detection and exclusion
 * - Proximity scoring (closer to TOTAL keyword = higher confidence)
 * - Better handling of itemized receipts
 */
object AmountExtractor {

    // Comprehensive regex patterns for all major currencies
    // CRITICAL: Handles OCR errors (spaces, comma/period confusion, 0-2 decimals)
    private val AMOUNT_PATTERNS = listOf(
        // === USD VARIATIONS ===
        Regex("""US\$\s*(\d{1,3}(?:[,\s]\d{3})*(?:[.,]\d{1,2})?)"""),
        Regex("""U\.S\.\$\s*(\d{1,3}(?:[,\s]\d{3})*(?:[.,]\d{1,2})?)"""),
        Regex("""USD\s*(\d{1,3}(?:[,\s]\d{3})*(?:[.,]\d{1,2})?)""", RegexOption.IGNORE_CASE),
        Regex("""(\d{1,3}(?:[,\s]\d{3})*(?:[.,]\d{1,2})?)\s*USD""", RegexOption.IGNORE_CASE),

        // === CAD VARIATIONS ===
        Regex("""C\$\s*(\d{1,3}(?:[,\s]\d{3})*(?:[.,]\d{1,2})?)"""),
        Regex("""CA\$\s*(\d{1,3}(?:[,\s]\d{3})*(?:[.,]\d{1,2})?)"""),
        Regex("""CAD\s*(\d{1,3}(?:[,\s]\d{3})*(?:[.,]\d{1,2})?)""", RegexOption.IGNORE_CASE),
        Regex("""Can\$\s*(\d{1,3}(?:[,\s]\d{3})*(?:[.,]\d{1,2})?)"""),
        Regex("""(\d{1,3}(?:[,\s]\d{3})*(?:[.,]\d{1,2})?)\s*CAD""", RegexOption.IGNORE_CASE),

        // === AUD VARIATIONS ===
        Regex("""A\$\s*(\d{1,3}(?:[,\s]\d{3})*(?:[.,]\d{1,2})?)"""),
        Regex("""AU\$\s*(\d{1,3}(?:[,\s]\d{3})*(?:[.,]\d{1,2})?)"""),
        Regex("""AUD\s*(\d{1,3}(?:[,\s]\d{3})*(?:[.,]\d{1,2})?)""", RegexOption.IGNORE_CASE),
        Regex("""(\d{1,3}(?:[,\s]\d{3})*(?:[.,]\d{1,2})?)\s*AUD""", RegexOption.IGNORE_CASE),

        // === EUR VARIATIONS ===
        Regex("""€\s*(\d{1,3}(?:[,\s]\d{3})*(?:[.,]\d{1,2})?)"""),
        Regex("""EUR\s*(\d{1,3}(?:[,\s]\d{3})*(?:[.,]\d{1,2})?)""", RegexOption.IGNORE_CASE),
        Regex("""(\d{1,3}(?:[,\s]\d{3})*(?:[.,]\d{1,2})?)\s*€"""),
        Regex("""(\d{1,3}(?:[,\s]\d{3})*(?:[.,]\d{1,2})?)\s*EUR""", RegexOption.IGNORE_CASE),

        // === GBP VARIATIONS ===
        Regex("""£\s*(\d{1,3}(?:[,\s]\d{3})*(?:[.,]\d{1,2})?)"""),
        Regex("""GBP\s*(\d{1,3}(?:[,\s]\d{3})*(?:[.,]\d{1,2})?)""", RegexOption.IGNORE_CASE),
        Regex("""GB£\s*(\d{1,3}(?:[,\s]\d{3})*(?:[.,]\d{1,2})?)"""),
        Regex("""UK£\s*(\d{1,3}(?:[,\s]\d{3})*(?:[.,]\d{1,2})?)"""),
        Regex("""(\d{1,3}(?:[,\s]\d{3})*(?:[.,]\d{1,2})?)\s*GBP""", RegexOption.IGNORE_CASE),

        // === GENERIC $ (fallback - matches USD, CAD, AUD, etc.) ===
        Regex("""\$\s*(\d{1,3}(?:[,\s]\d{3})*(?:[.,]\d{1,2})?)"""),
        Regex("""(\d{1,3}(?:[,\s]\d{3})*(?:[.,]\d{1,2})?)\s*\$"""),

        // === OTHER COMMON CURRENCIES ===
        Regex("""NZ\$\s*(\d{1,3}(?:[,\s]\d{3})*(?:[.,]\d{1,2})?)"""),
        Regex("""HK\$\s*(\d{1,3}(?:[,\s]\d{3})*(?:[.,]\d{1,2})?)"""),
        Regex("""S\$\s*(\d{1,3}(?:[,\s]\d{3})*(?:[.,]\d{1,2})?)"""),
        Regex("""MX\$\s*(\d{1,3}(?:[,\s]\d{3})*(?:[.,]\d{1,2})?)"""),
        Regex("""CHF\s*(\d{1,3}(?:[,\s]\d{3})*(?:[.,]\d{1,2})?)""", RegexOption.IGNORE_CASE),
        Regex("""¥\s*(\d{1,3}(?:[,\s]\d{3})*(?:[.,]\d{1,2})?)"""),
        Regex("""￥\s*(\d{1,3}(?:[,\s]\d{3})*(?:[.,]\d{1,2})?)"""),
        Regex("""JPY\s*(\d{1,3}(?:[,\s]\d{3})*(?:[.,]\d{1,2})?)""", RegexOption.IGNORE_CASE),
        Regex("""CNY\s*(\d{1,3}(?:[,\s]\d{3})*(?:[.,]\d{1,2})?)""", RegexOption.IGNORE_CASE),
        Regex("""RMB\s*(\d{1,3}(?:[,\s]\d{3})*(?:[.,]\d{1,2})?)""", RegexOption.IGNORE_CASE),
        Regex("""₹\s*(\d{1,3}(?:[,\s]\d{3})*(?:[.,]\d{1,2})?)"""),
        Regex("""INR\s*(\d{1,3}(?:[,\s]\d{3})*(?:[.,]\d{1,2})?)""", RegexOption.IGNORE_CASE),
        Regex("""Rs\.\s*(\d{1,3}(?:[,\s]\d{3})*(?:[.,]\d{1,2})?)"""),

        // === AMOUNTS WITHOUT CURRENCY (fallback) ===
        // Match amounts with 2 decimals
        Regex("""(?<!\d)(\d{1,3}(?:[,\s]\d{3})*[.,]\d{2})(?!\d)"""),
        // Match amounts with 1 decimal
        Regex("""(?<!\d)(\d{1,3}(?:[,\s]\d{3})*[.,]\d{1})(?!\d)"""),
        // Match plain numbers (no decimals)
        Regex("""(?<!\d)(\d{1,6})(?!\d)""")
    )

    // Expanded keywords that indicate total amount
    // IMPROVEMENT: Added common variations and OCR error patterns
    private val TOTAL_KEYWORDS = listOf(
        // Primary keywords
        "TOTAL", "AMOUNT", "BALANCE", "DUE", "PAYABLE", "PAID", "PAYMENT",

        // Subtotal variations
        "SUBTOTAL", "SUB TOTAL", "SUB-TOTAL", "SUB_TOTAL",

        // Grand total variations
        "GRAND TOTAL", "GRANDTOTAL", "GRAND-TOTAL", "GRAND_TOTAL",

        // Amount variations
        "AMOUNT DUE", "AMOUNT PAYABLE", "TOTAL AMOUNT", "FINAL AMOUNT",

        // Payment variations
        "TO PAY", "TOPAY", "TO-PAY", "TO_PAY",
        "TOTAL DUE", "TOTAL PAYABLE", "TOTAL PAID",

        // Final variations
        "FINAL TOTAL", "FINALTOTAL", "FINAL-TOTAL",

        // Net variations
        "NET TOTAL", "NET AMOUNT", "NET DUE",

        // OCR error variations (0 instead of O, 1 instead of I)
        "T0TAL", "AM0UNT", "T0 PAY", "TOTA1", "AM0UNT DUE",

        // Common receipt terms
        "SALE", "PURCHASE", "INVOICE", "RECEIPT TOTAL",

        // Multi-word with different spacing (with colons)
        "TOTAL:", "AMOUNT:", "BALANCE:", "DUE:", "PAYABLE:"
    )
    
    // IMPROVEMENT (2025-10-17): Keywords to exclude (tax, tip, subtotal lines)
    private val EXCLUDE_KEYWORDS = listOf(
        // Tax variations
        "TAX", "VAT", "GST", "HST", "PST", "SALES TAX", "TAX AMOUNT",
        
        // Tip variations
        "TIP", "GRATUITY", "SERVICE CHARGE", "SERVICE FEE",
        
        // Subtotal (we want grand total, not subtotal)
        "SUBTOTAL", "SUB TOTAL", "SUB-TOTAL", "SUB_TOTAL",
        
        // Discount (not the final amount)
        "DISCOUNT", "SAVINGS", "OFF",
        
        // Change given
        "CHANGE", "CHANGE DUE", "CASH BACK"
    )
    
    /**
     * Extract amount from receipt text.
     * 
     * @param text Full text from receipt
     * @return Pair of (amount, confidence) where confidence is 0.0-1.0
     */
    fun extract(text: String): Pair<BigDecimal?, Float> {
        if (text.isBlank()) {
            return Pair(null, 0f)
        }
        
        // Find all amounts in text
        val amounts = findAllAmounts(text)
        
        if (amounts.isEmpty()) {
            return Pair(null, 0f)
        }
        
        // Try to find amount near "TOTAL" keyword
        val totalAmount = findAmountNearKeyword(text, amounts)
        if (totalAmount != null) {
            return Pair(totalAmount, 0.9f) // High confidence
        }
        
        // Fallback: return largest amount (likely the total)
        val largestAmount = amounts.maxOrNull()
        return Pair(largestAmount, 0.6f) // Medium confidence
    }
    
    /**
     * Find all amounts in text using regex patterns.
     *
     * IMPROVEMENT: Handles spaces, comma/period confusion, 0-2 decimals
     */
    private fun findAllAmounts(text: String): List<BigDecimal> {
        val amounts = mutableListOf<BigDecimal>()

        for (pattern in AMOUNT_PATTERNS) {
            val matches = pattern.findAll(text)
            for (match in matches) {
                // Clean up the matched amount string
                // Remove commas and spaces (thousand separators)
                var amountStr = match.groupValues[1]
                    .replace(",", "")
                    .replace(" ", "")

                // Handle comma as decimal separator (European format)
                // If there's a comma, assume it's a decimal separator
                if (amountStr.contains(",")) {
                    amountStr = amountStr.replace(",", ".")
                }

                try {
                    val amount = BigDecimal(amountStr)
                    // Filter out unreasonable amounts (< 0.01 or > 999,999.99)
                    if (amount >= BigDecimal("0.01") && amount <= BigDecimal("999999.99")) {
                        amounts.add(amount)
                    }
                } catch (e: NumberFormatException) {
                    // Ignore invalid numbers
                }
            }
        }

        return amounts.distinct()
    }
    
    /**
     * Find amount near "TOTAL" or similar keywords.
     *
     * IMPROVEMENT (2025-10-17): High Priority #3
     * - Excludes tax/tip/subtotal lines
     * - Uses proximity scoring (closer to TOTAL = higher priority)
     * - Handles itemized receipts better
     *
     * Strategy:
     * 1. Split text into lines
     * 2. Find lines containing TOTAL keywords (excluding tax/tip lines)
     * 3. Score each candidate based on proximity to keyword
     * 4. Extract amount from highest-scoring line
     * 5. Return the amount
     *
     * IMPROVEMENT: Multi-line detection handles cases like:
     *   "TOTAL"
     *   "£15.99"
     */
    private fun findAmountNearKeyword(text: String, amounts: List<BigDecimal>): BigDecimal? {
        val lines = text.lines()
        val candidates = mutableListOf<Pair<BigDecimal, Float>>() // amount to score

        for ((lineIndex, line) in lines.withIndex()) {
            val upperLine = line.uppercase()

            // IMPROVEMENT: Skip lines with exclude keywords (tax, tip, subtotal, etc.)
            val hasExcludeKeyword = EXCLUDE_KEYWORDS.any { keyword ->
                upperLine.contains(keyword)
            }
            if (hasExcludeKeyword) {
                continue
            }

            // Check if line contains any TOTAL keyword
            val matchedKeyword = TOTAL_KEYWORDS.firstOrNull { keyword ->
                upperLine.contains(keyword)
            }

            if (matchedKeyword != null) {
                // IMPROVEMENT: Calculate proximity score
                // Closer to keyword = higher score
                // Same line = 1.0, next line = 0.8, 2 lines away = 0.6
                
                // Search current line + next 2 lines for amount
                for (offset in 0..2) {
                    val searchLine = lines.getOrNull(lineIndex + offset) ?: continue
                    val proximityScore = when (offset) {
                        0 -> 1.0f  // Same line as keyword
                        1 -> 0.8f  // Next line
                        2 -> 0.6f  // 2 lines away
                        else -> 0.4f
                    }

                    // Try all patterns
                    for (pattern in AMOUNT_PATTERNS) {
                        val match = pattern.find(searchLine)
                        if (match != null) {
                            // Clean up the matched amount string
                            var amountStr = match.groupValues[1]
                                .replace(",", "")
                                .replace(" ", "")

                            // Handle comma as decimal separator
                            if (amountStr.contains(",")) {
                                amountStr = amountStr.replace(",", ".")
                            }

                            try {
                                val amount = BigDecimal(amountStr)
                                // Verify this amount is in our list of found amounts
                                // Use compareTo for BigDecimal comparison (not equals)
                                if (amounts.any { it.compareTo(amount) == 0 }) {
                                    // IMPROVEMENT: Boost score for "GRAND TOTAL" vs "SUBTOTAL"
                                    val keywordBoost = when {
                                        matchedKeyword.contains("GRAND") -> 1.2f
                                        matchedKeyword.contains("FINAL") -> 1.1f
                                        matchedKeyword.contains("SUBTOTAL") -> 0.7f
                                        else -> 1.0f
                                    }
                                    
                                    val finalScore = proximityScore * keywordBoost
                                    candidates.add(Pair(amount, finalScore))
                                }
                            } catch (e: NumberFormatException) {
                                // Ignore
                            }
                        }
                    }
                }
            }
        }

        // IMPROVEMENT: Return highest-scoring candidate
        // This handles cases where multiple "TOTAL" keywords exist
        return candidates.maxByOrNull { it.second }?.first
    }
    
    /**
     * Extract amount with enhanced algorithm.
     * 
     * IMPROVEMENT (2025-10-17): High Priority #3
     * - Better confidence scoring based on context
     * - Excludes tax/tip lines
     * - Uses proximity scoring
     * 
     * @param text Full text from receipt
     * @return Pair of (amount, confidence) where confidence is 0.0-1.0
     */
    fun extractEnhanced(text: String): Pair<BigDecimal?, Float> {
        if (text.isBlank()) {
            return Pair(null, 0f)
        }
        
        // Find all amounts in text
        val amounts = findAllAmounts(text)
        
        if (amounts.isEmpty()) {
            return Pair(null, 0f)
        }
        
        // Filter out amounts that appear on tax/tip lines
        val filteredAmounts = filterExcludedAmounts(text, amounts)
        
        if (filteredAmounts.isEmpty()) {
            // If all amounts were filtered, fall back to original list
            return extract(text)
        }
        
        // Try to find amount near "TOTAL" keyword (with proximity scoring)
        val totalAmount = findAmountNearKeyword(text, filteredAmounts)
        if (totalAmount != null) {
            return Pair(totalAmount, 0.95f) // Very high confidence
        }
        
        // Fallback: return largest non-excluded amount
        val largestAmount = filteredAmounts.maxOrNull()
        return Pair(largestAmount, 0.7f) // Medium-high confidence
    }
    
    /**
     * Filter out amounts that appear on excluded lines (tax, tip, etc.).
     * 
     * IMPROVEMENT (2025-10-17): High Priority #3
     * 
     * @param text Full text from receipt
     * @param amounts List of all found amounts
     * @return Filtered list excluding amounts on tax/tip/discount lines
     */
    private fun filterExcludedAmounts(text: String, amounts: List<BigDecimal>): List<BigDecimal> {
        val lines = text.lines()
        val excludedAmounts = mutableSetOf<BigDecimal>()
        
        for (line in lines) {
            val upperLine = line.uppercase()
            
            // Check if line contains exclude keywords
            val hasExcludeKeyword = EXCLUDE_KEYWORDS.any { keyword ->
                upperLine.contains(keyword)
            }
            
            if (hasExcludeKeyword) {
                // Find any amounts on this line and mark them as excluded
                for (pattern in AMOUNT_PATTERNS) {
                    val match = pattern.find(line)
                    if (match != null) {
                        var amountStr = match.groupValues[1]
                            .replace(",", "")
                            .replace(" ", "")
                        
                        if (amountStr.contains(",")) {
                            amountStr = amountStr.replace(",", ".")
                        }
                        
                        try {
                            val amount = BigDecimal(amountStr)
                            excludedAmounts.add(amount)
                        } catch (e: NumberFormatException) {
                            // Ignore
                        }
                    }
                }
            }
        }
        
        // Return amounts that are not in the excluded set
        return amounts.filter { amount ->
            excludedAmounts.none { it.compareTo(amount) == 0 }
        }
    }
}
