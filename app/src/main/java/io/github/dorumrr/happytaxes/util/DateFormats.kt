package io.github.dorumrr.happytaxes.util

import java.time.format.DateTimeFormatter

/**
 * Centralized date formatting patterns for consistent date display across the app.
 * 
 * DRY Principle: Single source of truth for all date format patterns.
 * 
 * Usage:
 * ```kotlin
 * val formatted = transaction.date.format(DateFormats.DISPLAY_DATE)
 * val filename = "backup_${LocalDate.now().format(DateFormats.COMPACT_DATE)}.zip"
 * ```
 */
object DateFormats {
    /**
     * Display format: "15 Jan 2025"
     * Used in: Transaction lists, transaction details, recently deleted screen
     */
    val DISPLAY_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy")
    
    /**
     * Short format: "15/01/2025"
     * Used in: Compact displays, archive screen
     */
    val SHORT_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    
    /**
     * Timestamp format: "20250115_143022"
     * Used in: Receipt filenames, backup filenames
     */
    val TIMESTAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
    
    /**
     * Year-month format: "2025-01"
     * Used in: Receipt directory organization (FileManager)
     */
    val YEAR_MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM")
    
    /**
     * Compact date: "20250115"
     * Used in: Export filenames, report filenames
     */
    val COMPACT_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")
}