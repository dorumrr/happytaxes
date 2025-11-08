package io.github.dorumrr.happytaxes.util

/**
 * Merchant database for validation and fuzzy matching.
 * 
 * IMPROVEMENT (2025-10-17): Medium Priority #5
 * - Built-in merchant name database for validation
 * - Fuzzy matching for OCR errors in merchant names
 * - Expected improvement: 20-30% merchant accuracy
 * 
 * Strategy:
 * 1. Maintain a database of common merchant names
 * 2. Use Levenshtein distance for fuzzy matching
 * 3. Return best match with confidence score
 * 4. Support for franchise variations (e.g., "McDonald's #1234")
 */
object MerchantDatabase {
    
    /**
     * Global merchant names database.
     * 
     * UPDATED (2025-10-17): Trimmed to only truly global merchants
     * - Focuses on international chains present in multiple countries
     * - Regional merchants should be loaded from country-specific files
     * - User-learned merchants are added via addMerchant()
     * 
     * This hybrid approach:
     * 1. Core global merchants (hardcoded, ~25 merchants)
     * 2. Regional merchants (loaded from country JSON files - future)
     * 3. User-learned merchants (added dynamically)
     */
    private val KNOWN_MERCHANTS = mutableSetOf(
        // Fast Food (Truly Global - 100+ countries)
        "McDonald's", "KFC", "Subway", "Burger King", "Pizza Hut", "Domino's",
        
        // Coffee (Global chains)
        "Starbucks",
        
        // Retail (Global presence)
        "IKEA", "H&M", "Zara", "Uniqlo",
        
        // Supermarkets (International)
        "Aldi", "Lidl", "Costco", "Carrefour",
        
        // Gas Stations (International)
        "Shell", "BP", "Esso", "Total", "Chevron",
        
        // Hotels (International chains)
        "Marriott", "Hilton", "Holiday Inn", "Ibis", "Novotel",
        
        // Online/Tech (Global services)
        "Amazon", "Apple", "Google", "Microsoft", "Netflix", "Spotify",
        "Uber", "PayPal"
    )
    
    // Franchise indicators to strip before matching
    private val FRANCHISE_PATTERNS = listOf(
        Regex("""#\d+"""),           // #1234
        Regex("""\d{3,}"""),          // Store numbers
        Regex("""Store\s*\d+""", RegexOption.IGNORE_CASE),
        Regex("""Location\s*\d+""", RegexOption.IGNORE_CASE),
        Regex("""Branch\s*\d+""", RegexOption.IGNORE_CASE)
    )
    
    /**
     * Validate and correct merchant name using fuzzy matching.
     * 
     * @param extractedName Merchant name extracted from OCR
     * @param threshold Minimum similarity threshold (0.0-1.0, default 0.7)
     * @return Pair of (corrected name, confidence) or (original name, 0.0) if no match
     */
    fun validateMerchant(extractedName: String, threshold: Double = 0.7): Pair<String, Float> {
        if (extractedName.isBlank()) {
            return Pair(extractedName, 0f)
        }
        
        // Clean the extracted name (remove franchise indicators)
        val cleanedName = cleanMerchantName(extractedName)
        
        // Find best match in database
        var bestMatch: String? = null
        var bestSimilarity = 0.0
        
        for (knownMerchant in KNOWN_MERCHANTS) {
            val similarity = calculateSimilarity(cleanedName, knownMerchant)
            
            if (similarity > bestSimilarity) {
                bestSimilarity = similarity
                bestMatch = knownMerchant
            }
        }
        
        // Return best match if above threshold
        return if (bestMatch != null && bestSimilarity >= threshold) {
            Pair(bestMatch, bestSimilarity.toFloat())
        } else {
            // No good match found, return original
            Pair(extractedName, 0f)
        }
    }
    
    /**
     * Clean merchant name by removing franchise indicators.
     * 
     * @param name Raw merchant name
     * @return Cleaned merchant name
     */
    private fun cleanMerchantName(name: String): String {
        var cleaned = name.trim()
        
        // Remove franchise patterns
        for (pattern in FRANCHISE_PATTERNS) {
            cleaned = pattern.replace(cleaned, "").trim()
        }
        
        // Remove extra whitespace
        cleaned = cleaned.replace(Regex("""\s+"""), " ")
        
        return cleaned
    }
    
    /**
     * Calculate similarity between two strings using Levenshtein distance.
     * 
     * Returns a value between 0.0 (completely different) and 1.0 (identical).
     * 
     * @param s1 First string
     * @param s2 Second string
     * @return Similarity score (0.0-1.0)
     */
    private fun calculateSimilarity(s1: String, s2: String): Double {
        val str1 = s1.lowercase()
        val str2 = s2.lowercase()
        
        // Handle edge cases
        if (str1 == str2) return 1.0
        if (str1.isEmpty() || str2.isEmpty()) return 0.0
        
        // Calculate Levenshtein distance
        val distance = levenshteinDistance(str1, str2)
        val maxLength = maxOf(str1.length, str2.length)
        
        // Convert distance to similarity (0.0-1.0)
        return 1.0 - (distance.toDouble() / maxLength)
    }
    
    /**
     * Calculate Levenshtein distance between two strings.
     * 
     * The Levenshtein distance is the minimum number of single-character edits
     * (insertions, deletions, or substitutions) required to change one string into another.
     * 
     * @param s1 First string
     * @param s2 Second string
     * @return Levenshtein distance
     */
    private fun levenshteinDistance(s1: String, s2: String): Int {
        val len1 = s1.length
        val len2 = s2.length
        
        // Create a matrix to store distances
        val matrix = Array(len1 + 1) { IntArray(len2 + 1) }
        
        // Initialize first row and column
        for (i in 0..len1) {
            matrix[i][0] = i
        }
        for (j in 0..len2) {
            matrix[0][j] = j
        }
        
        // Fill in the rest of the matrix
        for (i in 1..len1) {
            for (j in 1..len2) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                
                matrix[i][j] = minOf(
                    matrix[i - 1][j] + 1,      // Deletion
                    matrix[i][j - 1] + 1,      // Insertion
                    matrix[i - 1][j - 1] + cost // Substitution
                )
            }
        }
        
        return matrix[len1][len2]
    }
    
    /**
     * Find all possible matches for a merchant name.
     * 
     * @param extractedName Merchant name extracted from OCR
     * @param threshold Minimum similarity threshold (0.0-1.0, default 0.6)
     * @param maxResults Maximum number of results to return (default 5)
     * @return List of (merchant name, confidence) pairs sorted by confidence
     */
    fun findMatches(
        extractedName: String,
        threshold: Double = 0.6,
        maxResults: Int = 5
    ): List<Pair<String, Float>> {
        if (extractedName.isBlank()) {
            return emptyList()
        }
        
        // Clean the extracted name
        val cleanedName = cleanMerchantName(extractedName)
        
        // Calculate similarity for all merchants
        val matches = KNOWN_MERCHANTS.map { merchant ->
            val similarity = calculateSimilarity(cleanedName, merchant)
            Pair(merchant, similarity.toFloat())
        }
        
        // Filter by threshold and sort by confidence
        return matches
            .filter { it.second >= threshold }
            .sortedByDescending { it.second }
            .take(maxResults)
    }
    
    /**
     * Add a merchant to the database.
     * 
     * This can be used to expand the database with user-specific merchants.
     * 
     * @param merchantName Merchant name to add
     */
    fun addMerchant(merchantName: String) {
        if (merchantName.isNotBlank()) {
            (KNOWN_MERCHANTS as MutableSet).add(merchantName.trim())
        }
    }
    
    /**
     * Check if a merchant exists in the database (exact match).
     * 
     * @param merchantName Merchant name to check
     * @return True if merchant exists in database
     */
    fun exists(merchantName: String): Boolean {
        return KNOWN_MERCHANTS.contains(merchantName.trim())
    }
    
    /**
     * Get all merchants in the database.
     * 
     * @return Set of all known merchant names
     */
    fun getAllMerchants(): Set<String> {
        return KNOWN_MERCHANTS.toSet()
    }
}
