package io.github.dorumrr.happytaxes.util

import java.math.BigDecimal
import java.time.LocalDate

/**
 * Utility for validating user inputs across the application.
 *
 * Provides comprehensive validation for:
 * - Transaction amounts (range, precision, format)
 * - Dates (range, business rules)
 * - Text fields (length, characters, format)
 * - Names (profiles, categories)
 * - File paths and sizes
 *
 * All validation methods return Result<Unit> for consistent error handling.
 */
object InputValidator {
    
    // ========== AMOUNT VALIDATION ==========
    
    /**
     * Validate transaction amount.
     *
     * Rules:
     * - Must be positive (> 0)
     * - Maximum: 999,999,999.99 (1 billion - 1 cent)
     * - Maximum 2 decimal places
     * - No scientific notation
     *
     * @param amount Amount to validate
     * @return Result with success or validation error
     */
    fun validateAmount(amount: BigDecimal?): Result<Unit> {
        if (amount == null) {
            return Result.failure(ValidationException("Amount cannot be null"))
        }
        
        // Check positive
        if (amount <= BigDecimal.ZERO) {
            return Result.failure(ValidationException("Amount must be greater than zero"))
        }
        
        // Check maximum (999,999,999.99)
        val maxAmount = BigDecimal("999999999.99")
        if (amount > maxAmount) {
            return Result.failure(ValidationException("Amount cannot exceed £999,999,999.99"))
        }
        
        // Check decimal places (max 2)
        if (amount.scale() > 2) {
            return Result.failure(ValidationException("Amount cannot have more than 2 decimal places"))
        }
        
        return Result.success(Unit)
    }
    
    /**
     * Validate amount string before parsing.
     *
     * Rules:
     * - Not blank
     * - Valid number format
     * - No letters or special characters (except . and ,)
     *
     * @param amountStr Amount string to validate
     * @return Result with success or validation error
     */
    fun validateAmountString(amountStr: String?): Result<Unit> {
        if (amountStr.isNullOrBlank()) {
            return Result.failure(ValidationException("Amount cannot be empty"))
        }
        
        // Remove common formatting (spaces, commas)
        val cleaned = amountStr.trim().replace(",", "").replace(" ", "")
        
        // Check if valid number format
        try {
            BigDecimal(cleaned)
        } catch (e: NumberFormatException) {
            return Result.failure(ValidationException("Invalid amount format"))
        }
        
        return Result.success(Unit)
    }
    
    // ========== DATE VALIDATION ==========
    
    /**
     * Validate transaction date.
     *
     * Rules:
     * - Not null
     * - Not in future (max: today)
     * - Not too old (min: 100 years ago)
     * - Reasonable business date
     *
     * @param date Date to validate
     * @return Result with success or validation error
     */
    fun validateDate(date: LocalDate?): Result<Unit> {
        if (date == null) {
            return Result.failure(ValidationException("Date cannot be null"))
        }
        
        val today = LocalDate.now()
        
        // Check not in future
        if (date.isAfter(today)) {
            return Result.failure(ValidationException("Date cannot be in the future"))
        }
        
        // Check not too old (100 years)
        val minDate = today.minusYears(100)
        if (date.isBefore(minDate)) {
            return Result.failure(ValidationException("Date cannot be more than 100 years ago"))
        }
        
        return Result.success(Unit)
    }
    
    // ========== TEXT FIELD VALIDATION ==========
    
    /**
     * Validate description field.
     *
     * Rules:
     * - Optional (can be null/blank)
     * - Max length: 500 characters
     * - No control characters
     *
     * @param description Description to validate
     * @return Result with success or validation error
     */
    fun validateDescription(description: String?): Result<Unit> {
        if (description == null) {
            return Result.success(Unit) // Optional field
        }
        
        // Check max length
        if (description.length > 500) {
            return Result.failure(ValidationException("Description cannot exceed 500 characters"))
        }
        
        // Check for control characters
        if (description.any { it.isISOControl() && it != '\n' && it != '\r' }) {
            return Result.failure(ValidationException("Description contains invalid characters"))
        }
        
        return Result.success(Unit)
    }
    
    /**
     * Validate notes field.
     *
     * Rules:
     * - Optional (can be null/blank)
     * - Max length: 2000 characters
     * - No control characters
     *
     * @param notes Notes to validate
     * @return Result with success or validation error
     */
    fun validateNotes(notes: String?): Result<Unit> {
        if (notes == null) {
            return Result.success(Unit) // Optional field
        }
        
        // Check max length
        if (notes.length > 2000) {
            return Result.failure(ValidationException("Notes cannot exceed 2000 characters"))
        }
        
        // Check for control characters
        if (notes.any { it.isISOControl() && it != '\n' && it != '\r' }) {
            return Result.failure(ValidationException("Notes contain invalid characters"))
        }
        
        return Result.success(Unit)
    }
    
    // ========== NAME VALIDATION ==========
    
    /**
     * Validate profile name.
     *
     * Rules:
     * - Not blank
     * - Length: 1-50 characters
     * - No leading/trailing whitespace
     * - No control characters
     * - No special characters except: - _ ( ) & '
     *
     * @param name Profile name to validate
     * @return Result with success or validation error
     */
    fun validateProfileName(name: String?): Result<Unit> {
        if (name.isNullOrBlank()) {
            return Result.failure(ValidationException("Profile name cannot be empty"))
        }
        
        val trimmed = name.trim()
        
        // Check length
        if (trimmed.length < 1 || trimmed.length > 50) {
            return Result.failure(ValidationException("Profile name must be 1-50 characters"))
        }
        
        // Check for leading/trailing whitespace
        if (trimmed != name) {
            return Result.failure(ValidationException("Profile name cannot have leading or trailing spaces"))
        }
        
        // Check for control characters
        if (trimmed.any { it.isISOControl() }) {
            return Result.failure(ValidationException("Profile name contains invalid characters"))
        }
        
        // Check for allowed characters (letters, numbers, spaces, and: - _ ( ) & ')
        val allowedPattern = Regex("^[a-zA-Z0-9 \\-_()&']+$")
        if (!allowedPattern.matches(trimmed)) {
            return Result.failure(ValidationException("Profile name contains invalid characters. Allowed: letters, numbers, spaces, - _ ( ) & '"))
        }
        
        return Result.success(Unit)
    }
    
    /**
     * Validate category name.
     *
     * Rules:
     * - Not blank
     * - Length: 1-100 characters
     * - No leading/trailing whitespace
     * - No control characters
     * - No special characters except: - _ ( ) & ' / ,
     *
     * @param name Category name to validate
     * @return Result with success or validation error
     */
    fun validateCategoryName(name: String?): Result<Unit> {
        if (name.isNullOrBlank()) {
            return Result.failure(ValidationException("Category name cannot be empty"))
        }
        
        val trimmed = name.trim()
        
        // Check length
        if (trimmed.length < 1 || trimmed.length > 100) {
            return Result.failure(ValidationException("Category name must be 1-100 characters"))
        }
        
        // Check for leading/trailing whitespace
        if (trimmed != name) {
            return Result.failure(ValidationException("Category name cannot have leading or trailing spaces"))
        }
        
        // Check for control characters
        if (trimmed.any { it.isISOControl() }) {
            return Result.failure(ValidationException("Category name contains invalid characters"))
        }
        
        // Check for allowed characters (letters, numbers, spaces, and: - _ ( ) & ' / ,)
        val allowedPattern = Regex("^[a-zA-Z0-9 \\-_()&'/,]+$")
        if (!allowedPattern.matches(trimmed)) {
            return Result.failure(ValidationException("Category name contains invalid characters. Allowed: letters, numbers, spaces, - _ ( ) & ' / ,"))
        }
        
        return Result.success(Unit)
    }
    
    // ========== FILE VALIDATION ==========
    
    /**
     * Validate file path.
     *
     * Rules:
     * - Not blank
     * - No path traversal attempts (../)
     * - No null bytes
     * - Reasonable length (< 500 chars)
     *
     * @param path File path to validate
     * @return Result with success or validation error
     */
    fun validateFilePath(path: String?): Result<Unit> {
        if (path.isNullOrBlank()) {
            return Result.failure(ValidationException("File path cannot be empty"))
        }
        
        // Check length
        if (path.length > 500) {
            return Result.failure(ValidationException("File path too long"))
        }
        
        // Check for path traversal
        if (path.contains("..")) {
            return Result.failure(ValidationException("Invalid file path (path traversal detected)"))
        }
        
        // Check for null bytes
        if (path.contains('\u0000')) {
            return Result.failure(ValidationException("Invalid file path (null byte detected)"))
        }
        
        return Result.success(Unit)
    }
    
    /**
     * Validate file size.
     *
     * Rules:
     * - Positive
     * - Max: 100MB (configurable)
     *
     * @param sizeBytes File size in bytes
     * @param maxSizeBytes Maximum allowed size (default: 100MB)
     * @return Result with success or validation error
     */
    fun validateFileSize(sizeBytes: Long, maxSizeBytes: Long = 100L * 1024 * 1024): Result<Unit> {
        if (sizeBytes < 0) {
            return Result.failure(ValidationException("Invalid file size"))
        }
        
        if (sizeBytes > maxSizeBytes) {
            val maxMB = maxSizeBytes / (1024 * 1024)
            return Result.failure(ValidationException("File size exceeds maximum of ${maxMB}MB"))
        }
        
        return Result.success(Unit)
    }
    
    // ========== SEARCH QUERY VALIDATION ==========
    
    /**
     * Validate search query.
     *
     * Rules:
     * - Max length: 200 characters
     * - No control characters
     * - No SQL injection patterns
     *
     * @param query Search query to validate
     * @return Result with success or validation error
     */
    fun validateSearchQuery(query: String?): Result<Unit> {
        if (query.isNullOrBlank()) {
            return Result.success(Unit) // Empty search is valid
        }
        
        // Check max length
        if (query.length > 200) {
            return Result.failure(ValidationException("Search query too long (max 200 characters)"))
        }
        
        // Check for control characters
        if (query.any { it.isISOControl() }) {
            return Result.failure(ValidationException("Search query contains invalid characters"))
        }
        
        // Check for SQL injection patterns (basic check)
        val dangerousPatterns = listOf("--", "/*", "*/", "xp_", "sp_", "DROP", "DELETE", "INSERT", "UPDATE")
        val upperQuery = query.uppercase()
        if (dangerousPatterns.any { upperQuery.contains(it) }) {
            return Result.failure(ValidationException("Search query contains invalid patterns"))
        }
        
        return Result.success(Unit)
    }
}

/**
 * Exception thrown when input validation fails.
 */
class ValidationException(message: String) : Exception(message)