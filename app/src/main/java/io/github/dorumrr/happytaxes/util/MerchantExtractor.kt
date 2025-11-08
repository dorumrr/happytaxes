package io.github.dorumrr.happytaxes.util

/**
 * Extracts merchant names from receipt text.
 * 
 * Strategy:
 * 1. Get first 5 lines of text (merchant name usually at top)
 * 2. Filter out common headers ("RECEIPT", "INVOICE", "TAX INVOICE")
 * 3. Filter out very short lines (< 3 characters)
 * 4. Look for company indicators (LTD, LIMITED, INC, LLC, PLC)
 * 5. Return first valid line as merchant name
 * 
 * IMPROVEMENTS (2025-10-17): Medium Priority #5
 * - Merchant database validation with fuzzy matching
 * - Expected improvement: 20-30% merchant accuracy
 */
object MerchantExtractor {
    
    // Common receipt headers to filter out
    private val HEADER_KEYWORDS = listOf(
        "RECEIPT", "INVOICE", "TAX INVOICE", "SALES RECEIPT", "PURCHASE RECEIPT",
        "VAT RECEIPT", "TILL RECEIPT", "CUSTOMER RECEIPT", "COPY", "DUPLICATE"
    )
    
    // Company indicators that suggest a business name
    private val COMPANY_INDICATORS = listOf(
        "LTD", "LIMITED", "INC", "LLC", "PLC", "CO", "CORP", "CORPORATION",
        "& CO", "AND CO", "COMPANY", "ENTERPRISES", "GROUP", "HOLDINGS"
    )
    
    /**
     * Extract merchant name from receipt text.
     * 
     * @param text Full text from receipt
     * @return Pair of (merchant name, confidence) where confidence is 0.0-1.0
     */
    fun extract(text: String): Pair<String?, Float> {
        if (text.isBlank()) {
            return Pair(null, 0f)
        }
        
        // Get first 10 lines (merchant name usually in first few lines)
        val lines = text.lines().take(10)
        
        // Filter and score each line
        val candidates = lines.mapNotNull { line ->
            val trimmed = line.trim()
            
            // Skip empty or very short lines
            if (trimmed.length < 3) {
                return@mapNotNull null
            }
            
            // Skip lines that are just numbers or symbols
            if (trimmed.all { it.isDigit() || !it.isLetterOrDigit() }) {
                return@mapNotNull null
            }
            
            // Calculate score for this line
            val score = scoreLine(trimmed)
            
            if (score > 0f) {
                Pair(trimmed, score)
            } else {
                null
            }
        }
        
        // Return highest scoring candidate
        val best = candidates.maxByOrNull { it.second }
        
        return if (best != null) {
            Pair(best.first, best.second)
        } else {
            Pair(null, 0f)
        }
    }
    
    /**
     * Score a line based on likelihood of being a merchant name.
     * 
     * @param line Line of text to score
     * @return Score from 0.0 (unlikely) to 1.0 (very likely)
     */
    private fun scoreLine(line: String): Float {
        val upperLine = line.uppercase()
        var score = 0.5f // Base score
        
        // Penalty: Contains header keywords
        if (HEADER_KEYWORDS.any { keyword -> upperLine.contains(keyword) }) {
            score -= 0.6f
        }
        
        // Bonus: Contains company indicators
        if (COMPANY_INDICATORS.any { indicator -> upperLine.contains(indicator) }) {
            score += 0.3f
        }
        
        // Bonus: Reasonable length (5-50 characters)
        if (line.length in 5..50) {
            score += 0.1f
        }
        
        // Penalty: Too long (likely address or description)
        if (line.length > 50) {
            score -= 0.2f
        }
        
        // Bonus: Contains letters and spaces (typical business name)
        val hasLetters = line.any { it.isLetter() }
        val hasSpaces = line.contains(' ')
        if (hasLetters && hasSpaces) {
            score += 0.1f
        }
        
        // Penalty: Contains too many numbers (likely address or phone)
        val digitCount = line.count { it.isDigit() }
        val digitRatio = digitCount.toFloat() / line.length
        if (digitRatio > 0.3f) {
            score -= 0.3f
        }
        
        // Penalty: Contains common address keywords
        val addressKeywords = listOf("STREET", "ROAD", "AVENUE", "LANE", "DRIVE", "ST", "RD", "AVE")
        if (addressKeywords.any { keyword -> upperLine.contains(keyword) }) {
            score -= 0.4f
        }
        
        // Penalty: Contains phone/email indicators
        val contactIndicators = listOf("TEL", "PHONE", "EMAIL", "@", "WWW", "HTTP")
        if (contactIndicators.any { indicator -> upperLine.contains(indicator) }) {
            score -= 0.5f
        }
        
        // Ensure score is in valid range
        return score.coerceIn(0f, 1f)
    }
    
    /**
     * Extract merchant name with database validation and fuzzy matching.
     * 
     * IMPROVEMENT (2025-10-17): Medium Priority #5
     * - Uses MerchantDatabase for validation
     * - Fuzzy matching for OCR errors
     * - Expected improvement: 20-30% merchant accuracy
     * 
     * @param text Full text from receipt
     * @return Pair of (merchant name, confidence) where confidence is 0.0-1.0
     */
    fun extractEnhanced(text: String): Pair<String?, Float> {
        // First, extract merchant name using standard method
        val (extractedName, extractionConfidence) = extract(text)
        
        if (extractedName == null) {
            return Pair(null, 0f)
        }
        
        // Try to validate/correct using merchant database
        val (validatedName, validationConfidence) = MerchantDatabase.validateMerchant(extractedName)
        
        // If validation found a match, use it with combined confidence
        return if (validationConfidence > 0f) {
            // Combine extraction and validation confidence
            val combinedConfidence = (extractionConfidence + validationConfidence) / 2
            Pair(validatedName, combinedConfidence)
        } else {
            // No database match, return original extraction
            Pair(extractedName, extractionConfidence)
        }
    }
    
    /**
     * Get multiple merchant name suggestions with confidence scores.
     * 
     * IMPROVEMENT (2025-10-17): Medium Priority #5
     * - Provides multiple suggestions for user selection
     * - Useful when OCR is uncertain
     * 
     * @param text Full text from receipt
     * @param maxSuggestions Maximum number of suggestions (default 3)
     * @return List of (merchant name, confidence) pairs sorted by confidence
     */
    fun getSuggestions(text: String, maxSuggestions: Int = 3): List<Pair<String, Float>> {
        // Extract merchant name
        val (extractedName, _) = extract(text)
        
        if (extractedName == null) {
            return emptyList()
        }
        
        // Get matches from database
        val matches = MerchantDatabase.findMatches(extractedName, threshold = 0.5, maxResults = maxSuggestions)
        
        // If we have matches, return them
        if (matches.isNotEmpty()) {
            return matches
        }
        
        // No matches, return original extraction
        return listOf(Pair(extractedName, 0.5f))
    }
}
