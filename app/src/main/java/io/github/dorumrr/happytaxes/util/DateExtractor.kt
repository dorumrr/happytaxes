package io.github.dorumrr.happytaxes.util

import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * Extracts transaction dates from receipt text.
 * 
 * Strategy:
 * 1. Find all date patterns (DD/MM/YYYY, DD-MM-YYYY, D MMM YYYY, etc.)
 * 2. Look for "DATE", "TIME", "TRANSACTION" keywords
 * 3. Extract date near keyword
 * 4. If no keyword, return most recent date
 * 5. Validate date is reasonable (not future, not > 1 year old)
 * 
 * IMPROVEMENTS (2025-10-17): Medium Priority #4
 * - Added DD MMM YY format (e.g., "17 Oct 25")
 * - Time extraction for better transaction matching
 * - Extended validation window (configurable, not hardcoded 1 year)
 */
object DateExtractor {
    
    // Regex patterns for different date formats
    // IMPROVEMENT (2025-10-17): Added DD MMM YY format
    private val DATE_PATTERNS = listOf(
        // DD/MM/YYYY or DD-MM-YYYY or DD.MM.YYYY
        Regex("""(\d{1,2})[/\-.](\d{1,2})[/\-.](\d{4})"""),
        // D MMM YYYY or DD MMM YYYY (e.g., "1 Oct 2025" or "01 Oct 2025")
        Regex("""(\d{1,2})\s+(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\s+(\d{4})""", RegexOption.IGNORE_CASE),
        // D MMM YY or DD MMM YY (e.g., "17 Oct 25") - NEW FORMAT
        Regex("""(\d{1,2})\s+(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\s+(\d{2})""", RegexOption.IGNORE_CASE),
        // YYYY-MM-DD (ISO format)
        Regex("""(\d{4})[/\-.](\d{1,2})[/\-.](\d{1,2})"""),
        // DD/MM/YY or DD-MM-YY (2-digit year)
        Regex("""(\d{1,2})[/\-.](\d{1,2})[/\-.](\d{2})""")
    )
    
    // IMPROVEMENT (2025-10-17): Time patterns for transaction matching
    private val TIME_PATTERNS = listOf(
        // HH:MM:SS (24-hour with seconds)
        Regex("""(\d{1,2}):(\d{2}):(\d{2})"""),
        // HH:MM (24-hour)
        Regex("""(\d{1,2}):(\d{2})"""),
        // HH:MM AM/PM (12-hour)
        Regex("""(\d{1,2}):(\d{2})\s*(AM|PM)""", RegexOption.IGNORE_CASE)
    )
    
    // Date formatters for parsing
    private val DATE_FORMATTERS = listOf(
        DateTimeFormatter.ofPattern("d/M/yyyy"),
        DateTimeFormatter.ofPattern("d-M-yyyy"),
        DateTimeFormatter.ofPattern("d.M.yyyy"),
        DateTimeFormatter.ofPattern("d MMM yyyy"),
        DateTimeFormatter.ofPattern("yyyy-M-d")
    )
    
    // Month name to number mapping
    private val MONTH_MAP = mapOf(
        "jan" to 1, "january" to 1,
        "feb" to 2, "february" to 2,
        "mar" to 3, "march" to 3,
        "apr" to 4, "april" to 4,
        "may" to 5,
        "jun" to 6, "june" to 6,
        "jul" to 7, "july" to 7,
        "aug" to 8, "august" to 8,
        "sep" to 9, "september" to 9,
        "oct" to 10, "october" to 10,
        "nov" to 11, "november" to 11,
        "dec" to 12, "december" to 12
    )
    
    // Keywords that indicate transaction date
    private val DATE_KEYWORDS = listOf(
        "DATE", "TIME", "TRANSACTION", "PURCHASE", "SALE", "RECEIPT"
    )
    
    /**
     * Extract date from receipt text.
     * 
     * @param text Full text from receipt
     * @return Pair of (date, confidence) where confidence is 0.0-1.0
     */
    fun extract(text: String): Pair<LocalDate?, Float> {
        if (text.isBlank()) {
            return Pair(null, 0f)
        }
        
        // Find all dates in text
        val dates = findAllDates(text)
        
        if (dates.isEmpty()) {
            return Pair(null, 0f)
        }
        
        // Try to find date near "DATE" keyword
        val keywordDate = findDateNearKeyword(text, dates)
        if (keywordDate != null) {
            return Pair(keywordDate, 0.9f) // High confidence
        }
        
        // Fallback: return most recent valid date
        val today = LocalDate.now()
        val oneYearAgo = today.minusYears(1)
        
        val validDates = dates.filter { date ->
            !date.isAfter(today) && !date.isBefore(oneYearAgo)
        }
        
        val mostRecentDate = validDates.maxOrNull()
        return if (mostRecentDate != null) {
            Pair(mostRecentDate, 0.6f) // Medium confidence
        } else {
            Pair(null, 0f)
        }
    }
    
    /**
     * Find all dates in text using regex patterns.
     * 
     * IMPROVEMENT (2025-10-17): Added DD MMM YY and DD/MM/YY formats
     */
    private fun findAllDates(text: String): List<LocalDate> {
        val dates = mutableListOf<LocalDate>()
        val currentYear = LocalDate.now().year
        
        // Pattern 1: DD/MM/YYYY or DD-MM-YYYY or DD.MM.YYYY
        DATE_PATTERNS[0].findAll(text).forEach { match ->
            val day = match.groupValues[1].toIntOrNull()
            val month = match.groupValues[2].toIntOrNull()
            val year = match.groupValues[3].toIntOrNull()
            
            if (day != null && month != null && year != null) {
                try {
                    val date = LocalDate.of(year, month, day)
                    dates.add(date)
                } catch (e: Exception) {
                    // Invalid date, ignore
                }
            }
        }
        
        // Pattern 2: D MMM YYYY (e.g., "1 Oct 2025")
        DATE_PATTERNS[1].findAll(text).forEach { match ->
            val day = match.groupValues[1].toIntOrNull()
            val monthStr = match.groupValues[2].lowercase()
            val year = match.groupValues[3].toIntOrNull()
            
            val month = MONTH_MAP[monthStr]
            
            if (day != null && month != null && year != null) {
                try {
                    val date = LocalDate.of(year, month, day)
                    dates.add(date)
                } catch (e: Exception) {
                    // Invalid date, ignore
                }
            }
        }
        
        // Pattern 3: D MMM YY (e.g., "17 Oct 25") - NEW FORMAT
        DATE_PATTERNS[2].findAll(text).forEach { match ->
            val day = match.groupValues[1].toIntOrNull()
            val monthStr = match.groupValues[2].lowercase()
            var year = match.groupValues[3].toIntOrNull()
            
            val month = MONTH_MAP[monthStr]
            
            if (day != null && month != null && year != null) {
                // Convert 2-digit year to 4-digit year
                // Assume years 00-49 are 2000-2049, 50-99 are 1950-1999
                year = if (year < 50) 2000 + year else 1900 + year
                
                try {
                    val date = LocalDate.of(year, month, day)
                    dates.add(date)
                } catch (e: Exception) {
                    // Invalid date, ignore
                }
            }
        }
        
        // Pattern 4: YYYY-MM-DD (ISO format)
        DATE_PATTERNS[3].findAll(text).forEach { match ->
            val year = match.groupValues[1].toIntOrNull()
            val month = match.groupValues[2].toIntOrNull()
            val day = match.groupValues[3].toIntOrNull()
            
            if (year != null && month != null && day != null) {
                try {
                    val date = LocalDate.of(year, month, day)
                    dates.add(date)
                } catch (e: Exception) {
                    // Invalid date, ignore
                }
            }
        }
        
        // Pattern 5: DD/MM/YY or DD-MM-YY (2-digit year) - NEW FORMAT
        DATE_PATTERNS[4].findAll(text).forEach { match ->
            val day = match.groupValues[1].toIntOrNull()
            val month = match.groupValues[2].toIntOrNull()
            var year = match.groupValues[3].toIntOrNull()
            
            if (day != null && month != null && year != null) {
                // Convert 2-digit year to 4-digit year
                year = if (year < 50) 2000 + year else 1900 + year
                
                try {
                    val date = LocalDate.of(year, month, day)
                    dates.add(date)
                } catch (e: Exception) {
                    // Invalid date, ignore
                }
            }
        }
        
        return dates.distinct()
    }
    
    /**
     * Find date near "DATE" or similar keywords.
     */
    private fun findDateNearKeyword(text: String, dates: List<LocalDate>): LocalDate? {
        val lines = text.lines()
        
        for (line in lines) {
            val upperLine = line.uppercase()
            
            // Check if line contains any DATE keyword
            val hasKeyword = DATE_KEYWORDS.any { keyword ->
                upperLine.contains(keyword)
            }
            
            if (hasKeyword) {
                // Find date in this line
                for (pattern in DATE_PATTERNS) {
                    val match = pattern.find(line)
                    if (match != null) {
                        // Try to parse the date
                        val dateStr = match.value
                        for (formatter in DATE_FORMATTERS) {
                            try {
                                val date = LocalDate.parse(dateStr, formatter)
                                // Verify this date is in our list of found dates
                                if (dates.contains(date)) {
                                    return date
                                }
                            } catch (e: DateTimeParseException) {
                                // Try next formatter
                            }
                        }
                        
                        // If formatter parsing failed, try manual parsing
                        val parsedDate = parseDate(match)
                        if (parsedDate != null && dates.contains(parsedDate)) {
                            return parsedDate
                        }
                    }
                }
            }
        }
        
        return null
    }
    
    /**
     * Parse date from regex match.
     */
    private fun parseDate(match: MatchResult): LocalDate? {
        return try {
            when (match.groupValues.size) {
                4 -> {
                    // DD/MM/YYYY format
                    val day = match.groupValues[1].toInt()
                    val month = match.groupValues[2].toIntOrNull()
                        ?: MONTH_MAP[match.groupValues[2].lowercase()]
                        ?: return null
                    val year = match.groupValues[3].toInt()
                    LocalDate.of(year, month, day)
                }
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Extract date with enhanced algorithm and configurable validation window.
     * 
     * IMPROVEMENT (2025-10-17): Medium Priority #4
     * - Configurable validation window (not hardcoded 1 year)
     * - Better confidence scoring
     * 
     * @param text Full text from receipt
     * @param validationYears Number of years back to consider valid (default 3)
     * @return Pair of (date, confidence) where confidence is 0.0-1.0
     */
    fun extractEnhanced(text: String, validationYears: Long = 3): Pair<LocalDate?, Float> {
        if (text.isBlank()) {
            return Pair(null, 0f)
        }
        
        // Find all dates in text
        val dates = findAllDates(text)
        
        if (dates.isEmpty()) {
            return Pair(null, 0f)
        }
        
        // Try to find date near "DATE" keyword
        val keywordDate = findDateNearKeyword(text, dates)
        if (keywordDate != null) {
            return Pair(keywordDate, 0.95f) // Very high confidence
        }
        
        // Fallback: return most recent valid date
        val today = LocalDate.now()
        val validFrom = today.minusYears(validationYears)
        
        val validDates = dates.filter { date ->
            !date.isAfter(today) && !date.isBefore(validFrom)
        }
        
        val mostRecentDate = validDates.maxOrNull()
        return if (mostRecentDate != null) {
            Pair(mostRecentDate, 0.7f) // Medium-high confidence
        } else {
            Pair(null, 0f)
        }
    }
    
    /**
     * Extract time from receipt text.
     * 
     * IMPROVEMENT (2025-10-17): Medium Priority #4
     * - Time extraction for better transaction matching
     * - Supports 24-hour and 12-hour formats
     * 
     * @param text Full text from receipt
     * @return Pair of (time, confidence) where confidence is 0.0-1.0
     */
    fun extractTime(text: String): Pair<LocalTime?, Float> {
        if (text.isBlank()) {
            return Pair(null, 0f)
        }
        
        val times = mutableListOf<LocalTime>()
        
        // Try all time patterns
        for (pattern in TIME_PATTERNS) {
            pattern.findAll(text).forEach { match ->
                try {
                    val hour = match.groupValues[1].toInt()
                    val minute = match.groupValues[2].toInt()
                    
                    // Check if it's 12-hour format with AM/PM
                    val amPm = match.groupValues.getOrNull(3)?.uppercase()
                    
                    val adjustedHour = when {
                        amPm == "PM" && hour != 12 -> hour + 12
                        amPm == "AM" && hour == 12 -> 0
                        else -> hour
                    }
                    
                    // Get seconds if available
                    val second = match.groupValues.getOrNull(3)?.toIntOrNull() ?: 0
                    
                    if (adjustedHour in 0..23 && minute in 0..59) {
                        val time = LocalTime.of(adjustedHour, minute, second)
                        times.add(time)
                    }
                } catch (e: Exception) {
                    // Invalid time, ignore
                }
            }
        }
        
        if (times.isEmpty()) {
            return Pair(null, 0f)
        }
        
        // Return the first valid time found (usually near the top of receipt)
        return Pair(times.first(), 0.8f) // High confidence
    }
    
    /**
     * Extract both date and time from receipt text.
     * 
     * IMPROVEMENT (2025-10-17): Medium Priority #4
     * - Combined date and time extraction
     * - Better for transaction matching
     * 
     * @param text Full text from receipt
     * @param validationYears Number of years back to consider valid (default 3)
     * @return Triple of (date, time, confidence) where confidence is 0.0-1.0
     */
    fun extractDateTime(text: String, validationYears: Long = 3): Triple<LocalDate?, LocalTime?, Float> {
        val (date, dateConfidence) = extractEnhanced(text, validationYears)
        val (time, timeConfidence) = extractTime(text)
        
        // Combined confidence is average of both
        val combinedConfidence = if (date != null && time != null) {
            (dateConfidence + timeConfidence) / 2
        } else if (date != null) {
            dateConfidence * 0.8f // Slightly lower if no time
        } else if (time != null) {
            timeConfidence * 0.5f // Much lower if no date
        } else {
            0f
        }
        
        return Triple(date, time, combinedConfidence)
    }
}
