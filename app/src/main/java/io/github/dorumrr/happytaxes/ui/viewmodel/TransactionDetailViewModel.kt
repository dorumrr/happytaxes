package io.github.dorumrr.happytaxes.ui.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.dorumrr.happytaxes.data.repository.PreferencesRepository
import io.github.dorumrr.happytaxes.data.repository.ProfileContext
import io.github.dorumrr.happytaxes.data.repository.ReceiptRepository
import io.github.dorumrr.happytaxes.data.repository.TransactionRepository
import io.github.dorumrr.happytaxes.domain.model.Transaction
import io.github.dorumrr.happytaxes.domain.model.TransactionType
import io.github.dorumrr.happytaxes.util.NumberFormatter
import io.github.dorumrr.happytaxes.util.OcrProcessor
import io.github.dorumrr.happytaxes.util.OcrResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import javax.inject.Inject

/**
 * ViewModel for transaction detail/edit screen.
 *
 * Manages:
 * - Transaction creation and editing
 * - Form validation
 * - Receipt attachment
 * - OCR processing (Tesseract OCR - FLOSS, on-device)
 * - Duplicate detection warnings
 * - Discard confirmation with photo cleanup
 */
@HiltViewModel
class TransactionDetailViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val receiptRepository: ReceiptRepository,
    private val preferencesRepository: PreferencesRepository,
    private val profileContext: ProfileContext,
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val transactionId: String? = savedStateHandle["transactionId"]

    // Track original state for discard detection
    private var originalState: TransactionDetailUiState? = null

    // Temporary transaction ID for new transactions (used for receipt filenames)
    private val tempTransactionId: String = transactionId ?: java.util.UUID.randomUUID().toString()

    // Public getter for transaction ID (used for scroll positioning after save)
    fun getTransactionId(): String = tempTransactionId

    // OCR processor
    private val ocrProcessor = OcrProcessor(context)

    // ========== UI STATE ==========

    private val _uiState = MutableStateFlow(TransactionDetailUiState())
    val uiState: StateFlow<TransactionDetailUiState> = _uiState.asStateFlow()

    init {
        // Load existing transaction if editing
        transactionId?.let { id ->
            viewModelScope.launch {
                val profileId = profileContext.getCurrentProfileIdOnce()
                val baseCurrency = preferencesRepository.getBaseCurrency(profileId).first()
                val decimalSeparator = preferencesRepository.getDecimalSeparator(profileId).first()
                val transaction = transactionRepository.getById(id)
                transaction?.let {
                    // Format amount input with user's preferred decimal separator
                    val formattedInput = NumberFormatter.formatForInput(it.amount, decimalSeparator)

                    val loadedState = _uiState.value.copy(
                        isEditing = true,
                        date = it.date,
                        type = it.type,
                        category = it.category,
                        description = it.description ?: "",
                        notes = it.notes ?: "",
                        amount = it.amount,
                        amountInput = formattedInput,  // Use formatted input with preferred separator
                        baseCurrency = baseCurrency,
                        decimalSeparator = decimalSeparator,  // Store user's preferred separator
                        receiptPaths = it.receiptPaths,
                        originalReceiptPaths = it.receiptPaths,  // Store original for tracking
                        isDraft = it.isDraft
                    )
                    _uiState.value = loadedState
                    // Store original state for discard detection
                    originalState = loadedState
                }
            }
        } ?: run {
            // New transaction - load defaults from preferences
            viewModelScope.launch {
                val profileId = profileContext.getCurrentProfileIdOnce()
                val defaultType = preferencesRepository.getDefaultTransactionType(profileId).first()
                val baseCurrency = preferencesRepository.getBaseCurrency(profileId).first()
                val decimalSeparator = preferencesRepository.getDecimalSeparator(profileId).first()

                _uiState.value = _uiState.value.copy(
                    type = if (defaultType == "income") TransactionType.INCOME else TransactionType.EXPENSE,
                    baseCurrency = baseCurrency,
                    decimalSeparator = decimalSeparator  // Store user's preferred separator
                )
            }
        }
    }

    // ========== USER ACTIONS ==========

    fun onDateChanged(date: LocalDate) {
        _uiState.value = _uiState.value.copy(date = date)
        validateForm()
    }

    fun onTypeChanged(type: TransactionType) {
        _uiState.value = _uiState.value.copy(type = type)
        validateForm()
    }

    fun onCategoryChanged(category: String) {
        _uiState.value = _uiState.value.copy(category = category)
        validateForm()
    }

    fun onDescriptionChanged(description: String) {
        _uiState.value = _uiState.value.copy(description = description)
        validateForm()
    }

    fun onNotesChanged(notes: String) {
        _uiState.value = _uiState.value.copy(notes = notes)
        validateForm()
    }

    fun onAmountChanged(amountInput: String) {
        // Handle decimal separator input (both comma and period)
        // This allows users to type either , or . as decimal separator
        // The input is normalized to use period internally for BigDecimal parsing

        val normalizedAmount = normalizeDecimalInput(amountInput)
        val parsedAmount = normalizedAmount.toBigDecimalOrNull()

        _uiState.value = _uiState.value.copy(
            amountInput = amountInput,  // Store raw input to preserve user's decimal separator
            amount = parsedAmount ?: BigDecimal.ZERO
        )
        validateForm()
    }

    /**
     * Format amount with proper decimal places when user leaves the field.
     * Adds .00 if no decimals, or formats to 2 decimal places (e.g., 100.9 -> 100.90).
     */
    fun formatAmountOnFocusLoss() {
        val currentAmount = _uiState.value.amount
        if (currentAmount > BigDecimal.ZERO) {
            // Use separator from input if present, otherwise use user's preferred separator from state
            val decimalSeparator = _uiState.value.amountInput.firstOrNull { it == ',' || it == '.' }?.toString()
                ?: _uiState.value.decimalSeparator

            // Round to 2 decimal places and format
            val rounded = currentAmount.setScale(2, java.math.RoundingMode.HALF_UP)
            val formattedAmount = NumberFormatter.formatForInput(rounded, decimalSeparator)
            _uiState.value = _uiState.value.copy(amountInput = formattedAmount)
        }
    }

    /**
     * Normalize decimal input to handle both comma and period as decimal separators.
     *
     * Rules:
     * - Accept both comma (,) and period (.) as decimal separator
     * - Remove thousand separators (spaces)
     * - Normalize to period (.) for BigDecimal parsing
     * - Handle edge cases (multiple separators, trailing separators)
     *
     * Examples:
     * - "123,45" -> "123.45"
     * - "123.45" -> "123.45"
     * - "1 234,56" -> "1234.56"
     * - "1,234.56" -> "1234.56" (US format with thousand separator)
     * - "1.234,56" -> "1234.56" (EU format with thousand separator)
     */
    private fun normalizeDecimalInput(input: String): String {
        if (input.isBlank()) return ""

        // Remove spaces (thousand separators)
        var normalized = input.replace(" ", "")

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
            commaCount == 0 && periodCount > 0 -> {
                // If only one period, treat as decimal separator
                // If multiple periods, treat as thousand separators and remove all but last
                if (periodCount == 1) {
                    // Single period - decimal separator (e.g., "123.45")
                    // Keep as is
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

        return normalized
    }



    /**
     * Handle receipt captured from camera or imported from gallery.
     *
     * Process:
     * 1. Show loading state
     * 2. Compress image (max 1600px, 65% JPEG quality)
     * 3. Preserve EXIF data
     * 4. Save to /receipts/YYYY-MM/ directory
     * 5. Add path to transaction state
     *
     * @param sourceUri URI from camera or gallery
     */
    fun onReceiptCaptured(sourceUri: android.net.Uri) {
        viewModelScope.launch {
            // Set loading state
            _uiState.value = _uiState.value.copy(
                isCompressingReceipt = true,
                error = null
            )

            try {
                // Save and compress receipt
                val result = receiptRepository.saveReceipt(
                    sourceUri = sourceUri,
                    transactionId = tempTransactionId,
                    date = _uiState.value.date
                )

                result.onSuccess { path ->
                    // Add compressed receipt path to state
                    onReceiptAttached(path)
                    _uiState.value = _uiState.value.copy(
                        isCompressingReceipt = false
                    )
                }.onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isCompressingReceipt = false,
                        error = "Failed to save receipt: ${error.message}"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isCompressingReceipt = false,
                    error = "Failed to save receipt: ${e.message}"
                )
            }
        }
    }

    /**
     * Add receipt path directly (for internal use).
     * Use onReceiptCaptured() for new receipts from camera/gallery.
     */
    private fun onReceiptAttached(path: String) {
        val currentPaths = _uiState.value.receiptPaths.toMutableList()
        currentPaths.add(path)

        // Track as added receipt (for cleanup on discard)
        val addedPaths = _uiState.value.addedReceiptPaths.toMutableList()
        addedPaths.add(path)

        _uiState.value = _uiState.value.copy(
            receiptPaths = currentPaths,
            addedReceiptPaths = addedPaths
        )
        validateForm()
    }

    fun onReceiptRemoved(path: String) {
        // Remove from UI state
        val currentPaths = _uiState.value.receiptPaths.toMutableList()
        currentPaths.remove(path)

        // Track removal based on whether it's an original or newly added receipt
        val isOriginal = _uiState.value.originalReceiptPaths.contains(path)

        val removedPaths = _uiState.value.removedReceiptPaths.toMutableList()

        if (isOriginal) {
            // Original receipt - track for deletion on save
            // Prevent duplicates
            if (!removedPaths.contains(path)) {
                removedPaths.add(path)
            }
        }

        // Don't remove from addedReceiptPaths!
        // Even if removed from UI, the file still needs to be deleted on discard.
        // This handles the case: Add receipt B, remove receipt B, discard → B should be deleted

        _uiState.value = _uiState.value.copy(
            receiptPaths = currentPaths,
            removedReceiptPaths = removedPaths
            // Don't modify addedReceiptPaths
        )
        validateForm()
    }

    // ========== OCR PROCESSING ==========

    /**
     * Process receipt with OCR and pre-fill form fields.
     *
     * PRD Section 4.2: "run on-device OCR; detect amount, date, merchant; pre-fill form"
     * PRD Section 14.5: "CameraX → OCR (on-device) → regex parse → prefill form. ≤ 2000 ms"
     * PRD Section 15.2: "Logging: Log.w('OCR', 'Extraction failed for receipt ${receiptId}')"
     *
     * Uses Tesseract4Android (FLOSS) for F-Droid compatibility.
     *
     * @param imageUri URI of the receipt image to process
     */
    fun processReceiptOcr(imageUri: Uri) {
        viewModelScope.launch {
            // Check if OCR is enabled in settings
            val ocrEnabled = preferencesRepository.getOcrEnabled().first()

            if (!ocrEnabled) {
                Log.i("OCR", "OCR disabled in settings, skipping processing")
                // Keep OCR state as Idle - no processing needed
                return@launch
            }

            // OCR is enabled - proceed with processing
            _uiState.value = _uiState.value.copy(
                ocrState = OcrState.Processing
            )

            try {
                val result = ocrProcessor.processReceipt(imageUri)

                if (result.success) {
                    // Log success
                    Log.i("OCR", "Extraction succeeded for receipt $tempTransactionId: " +
                            "amount=${result.amount}, date=${result.date}, merchant=${result.merchant}, " +
                            "confidence=${result.confidence.overall}")

                    // Pre-fill form with extracted data
                    val currentState = _uiState.value

                    val newAmount = if (currentState.amount == BigDecimal.ZERO && result.amount != null) {
                        result.amount
                    } else {
                        currentState.amount
                    }

                    // Format amount input with user's preferred decimal separator (from state)
                    val newAmountInput = if (currentState.amount == BigDecimal.ZERO && result.amount != null) {
                        NumberFormatter.formatForInput(result.amount, currentState.decimalSeparator)
                    } else {
                        currentState.amountInput
                    }

                    _uiState.value = currentState.copy(
                        // Only update if field is empty (don't overwrite user input)
                        amount = newAmount,
                        amountInput = newAmountInput,
                        date = if (currentState.date == LocalDate.now() && result.date != null) {
                            result.date
                        } else {
                            currentState.date
                        },
                        description = if (currentState.description.isBlank() && result.merchant != null) {
                            result.merchant
                        } else {
                            currentState.description
                        },
                        ocrState = OcrState.Success(result)
                    )

                    // Validate form after OCR updates
                    validateForm()
                } else {
                    // OCR failed - log warning
                    Log.w("OCR", "Extraction failed for receipt $tempTransactionId: ${result.error}")

                    _uiState.value = _uiState.value.copy(
                        ocrState = OcrState.Error(result.error ?: "Could not extract data from receipt")
                    )
                }
            } catch (e: Exception) {
                // OCR exception - log error
                Log.e("OCR", "OCR processing failed for receipt $tempTransactionId", e)

                _uiState.value = _uiState.value.copy(
                    ocrState = OcrState.Error(e.message ?: "OCR processing failed")
                )
            }
        }
    }

    /**
     * Retry OCR by returning to camera to retake photo.
     * PRD Section 15.2: "[Retake Photo]" button in OCR failure dialog
     * PRD Section 15.2: "Discard current photo, open camera for new capture"
     */
    fun retakePhoto() {
        viewModelScope.launch {
            // Delete the last added receipt (the one that failed OCR)
            val lastReceipt = _uiState.value.receiptPaths.lastOrNull()
            if (lastReceipt != null) {
                // Delete physical file
                receiptRepository.deleteReceipt(lastReceipt)

                // Remove from UI state
                val updatedPaths = _uiState.value.receiptPaths.toMutableList()
                updatedPaths.remove(lastReceipt)

                val updatedAddedPaths = _uiState.value.addedReceiptPaths.toMutableList()
                updatedAddedPaths.remove(lastReceipt)

                _uiState.value = _uiState.value.copy(
                    receiptPaths = updatedPaths,
                    addedReceiptPaths = updatedAddedPaths,
                    ocrState = OcrState.Idle,
                    ocrRetakeRequested = true
                )
            } else {
                // No receipt to delete, just reset state
                _uiState.value = _uiState.value.copy(
                    ocrState = OcrState.Idle,
                    ocrRetakeRequested = true
                )
            }
        }
    }

    /**
     * Cancel OCR and enter data manually.
     * PRD Section 15.2: "[Enter Manually]" button in OCR failure dialog
     * PRD Section 15.2: "Keep photo, user fills form manually"
     */
    fun enterManually() {
        _uiState.value = _uiState.value.copy(
            ocrState = OcrState.Idle
        )
    }

    /**
     * Cancel OCR and dismiss dialog.
     * PRD Section 15.2: "[Cancel]" button in OCR failure dialog
     * PRD Section 15.2: "Discard photo, return to previous screen"
     */
    fun cancelOcr() {
        viewModelScope.launch {
            // Delete the last added receipt (the one that failed OCR)
            val lastReceipt = _uiState.value.receiptPaths.lastOrNull()
            if (lastReceipt != null) {
                // Delete physical file
                receiptRepository.deleteReceipt(lastReceipt)

                // Remove from UI state
                val updatedPaths = _uiState.value.receiptPaths.toMutableList()
                updatedPaths.remove(lastReceipt)

                val updatedAddedPaths = _uiState.value.addedReceiptPaths.toMutableList()
                updatedAddedPaths.remove(lastReceipt)

                _uiState.value = _uiState.value.copy(
                    receiptPaths = updatedPaths,
                    addedReceiptPaths = updatedAddedPaths,
                    ocrState = OcrState.Idle
                )
            } else {
                // No receipt to delete, just reset state
                _uiState.value = _uiState.value.copy(
                    ocrState = OcrState.Idle
                )
            }
        }
    }

    /**
     * Reset OCR retake flag after navigation.
     */
    fun resetOcrRetakeFlag() {
        _uiState.value = _uiState.value.copy(
            ocrRetakeRequested = false
        )
    }

    fun saveTransaction() {
        val state = _uiState.value

        if (!state.isValid) {
            _uiState.value = state.copy(error = "Please fix validation errors")
            return
        }

        _uiState.value = state.copy(isSaving = true, error = null)

        viewModelScope.launch {
            val result = if (state.isEditing && transactionId != null) {
                // Update existing transaction
                transactionRepository.updateTransaction(
                    id = transactionId,
                    date = state.date,
                    category = state.category,
                    description = state.description.ifBlank { null },
                    notes = state.notes.ifBlank { null },
                    amount = state.amount,
                    receiptPaths = state.receiptPaths
                )
            } else {
                // Create new transaction with tempTransactionId
                // This ensures receipts saved with tempTransactionId are associated correctly
                transactionRepository.createTransaction(
                    id = tempTransactionId,  // Use temp ID so receipts match
                    date = state.date,
                    type = state.type,
                    category = state.category,
                    description = state.description.ifBlank { null },
                    notes = state.notes.ifBlank { null },
                    amount = state.amount,
                    receiptPaths = state.receiptPaths
                )
            }
            
            result.onSuccess {
                // Delete removed receipts after successful save
                state.removedReceiptPaths.forEach { path ->
                    receiptRepository.deleteReceipt(path)
                }

                _uiState.value = state.copy(
                    isSaving = false,
                    isSaved = true
                )
            }.onFailure { error ->
                _uiState.value = state.copy(
                    isSaving = false,
                    error = error.message ?: "Failed to save transaction"
                )
            }
        }
    }
    
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    /**
     * Delete transaction (soft delete by setting deletedAt timestamp).
     * Uses existing softDelete method from TransactionRepository.
     * Only available when editing an existing transaction.
     */
    fun deleteTransaction() {
        val state = _uiState.value

        if (!state.isEditing || transactionId == null) {
            _uiState.value = state.copy(error = "Cannot delete unsaved transaction")
            return
        }

        _uiState.value = state.copy(isDeleting = true, error = null)

        viewModelScope.launch {
            val result = transactionRepository.softDelete(transactionId)

            result.onSuccess {
                _uiState.value = state.copy(
                    isDeleting = false,
                    isDeleted = true
                )
            }.onFailure { error ->
                _uiState.value = state.copy(
                    isDeleting = false,
                    error = error.message ?: "Failed to delete transaction"
                )
            }
        }
    }

    /**
     * Confirm a draft transaction (convert to regular transaction by adding receipt).
     * Drafts are expenses saved without receipts (PRD Section 4.1).
     */
    fun confirmDraft() {
        val state = _uiState.value

        if (!state.isDraft) {
            _uiState.value = state.copy(error = "Transaction is not a draft")
            return
        }

        if (!state.isValid) {
            _uiState.value = state.copy(error = "Please fix validation errors")
            return
        }

        _uiState.value = state.copy(isSaving = true, error = null)

        viewModelScope.launch {
            transactionId?.let { id ->
                android.util.Log.d("HappyTaxes", "confirmDraft: id=$id, receiptPaths=${state.receiptPaths.size}, isDraft=false")
                val result = transactionRepository.updateTransaction(
                    id = id,
                    date = state.date,
                    category = state.category,
                    description = state.description.ifBlank { null },
                    notes = state.notes.ifBlank { null },
                    amount = state.amount,
                    receiptPaths = state.receiptPaths,
                    isDraft = false  // Convert to regular transaction
                )

                result.onSuccess {
                    android.util.Log.d("HappyTaxes", "confirmDraft: success, transaction updated")
                    _uiState.value = state.copy(
                        isSaving = false,
                        isSaved = true,
                        isDraft = false
                    )
                }.onFailure { error ->
                    android.util.Log.e("HappyTaxes", "confirmDraft: failed - ${error.message}")
                    _uiState.value = state.copy(
                        isSaving = false,
                        error = error.message ?: "Failed to confirm draft"
                    )
                }
            }
        }
    }

    // ========== DISCARD LOGIC ==========

    /**
     * Check if form has unsaved changes.
     *
     * @return true if changes were made, false if form is pristine
     */
    fun hasUnsavedChanges(): Boolean {
        val current = _uiState.value

        // If editing, compare with original state
        if (current.isEditing && originalState != null) {
            val original = originalState!!
            return current.date != original.date ||
                   current.type != original.type ||
                   current.category != original.category ||
                   current.description != original.description ||
                   current.notes != original.notes ||
                   current.amount != original.amount ||
                   current.receiptPaths != original.receiptPaths
        }

        // If creating new, check if any field has been modified
        return current.category.isNotBlank() ||
               current.description.isNotBlank() ||
               current.notes.isNotBlank() ||
               current.amount > BigDecimal.ZERO ||
               current.receiptPaths.isNotEmpty()
    }



    /**
     * Discard changes and clean up photos.
     *
     * Business Rules:
     * - New transaction: Delete ALL attached photos
     * - Edit transaction: Delete ONLY newly added photos (keep original photos)
     * - Removed receipts: NOT deleted (restored to original state)
     */
    fun discardChanges() {
        viewModelScope.launch {
            // Delete newly added photos
            _uiState.value.addedReceiptPaths.forEach { path ->
                receiptRepository.deleteReceipt(path)
                // Ignore errors - photos might already be deleted
            }

            // Mark as discarded (UI will handle navigation)
            _uiState.value = _uiState.value.copy(isDiscarded = true)
        }
    }

    /**
     * Get discard confirmation message.
     *
     * @return Message to show in confirmation dialog
     */
    fun getDiscardMessage(): String {
        val hasAddedPhotos = _uiState.value.addedReceiptPaths.isNotEmpty()
        val hasRemovedPhotos = _uiState.value.removedReceiptPaths.isNotEmpty()

        return if (_uiState.value.isEditing) {
            // Edit mode
            when {
                hasAddedPhotos && hasRemovedPhotos ->
                    "Your changes will be lost. New photos will be deleted. Transaction will revert to original state."
                hasAddedPhotos ->
                    "Your changes will be lost. New photos will be deleted."
                hasRemovedPhotos ->
                    "Your changes will be lost. Transaction will revert to original state."
                else ->
                    "Your changes will be lost."
            }
        } else {
            // Create mode
            if (hasAddedPhotos) {
                "You have unsaved changes. Attached photos will also be deleted."
            } else {
                "You have unsaved changes."
            }
        }
    }

    // ========== PRIVATE HELPERS ==========
    
    private fun validateForm() {
        val state = _uiState.value

        val errors = mutableListOf<String>()

        // Validate date (warn if future date)
        if (state.date.isAfter(LocalDate.now())) {
            errors.add("Future date - are you sure?")
        }

        // Validate amount
        if (state.amount <= BigDecimal.ZERO) {
            errors.add("Amount must be greater than zero")
        }

        // Validate category
        if (state.category.isBlank()) {
            errors.add("Category is required")
        }

        // Validate receipt requirement
        if (state.type == TransactionType.EXPENSE && state.receiptPaths.isEmpty()) {
            // This is allowed (will be saved as draft), but show warning
            errors.add("Expense without receipt will be saved as draft")
        }

        _uiState.value = state.copy(
            validationErrors = errors,
            isValid = errors.none { it.contains("required") || it.contains("must be") }
        )
    }

    override fun onCleared() {
        super.onCleared()
        // Release OCR processor resources
        ocrProcessor.close()
    }
}

/**
 * UI state for transaction detail screen.
 */
data class TransactionDetailUiState(
    val isEditing: Boolean = false,
    val date: LocalDate = LocalDate.now(),
    val type: TransactionType = TransactionType.EXPENSE,
    val category: String = "",
    val description: String = "",
    val notes: String = "",
    val amount: BigDecimal = BigDecimal.ZERO,
    val amountInput: String = "",  // Raw user input for amount field (preserves decimal separator)
    val baseCurrency: String = "GBP",  // User's base currency (loaded from preferences)
    val decimalSeparator: String = ".",  // User's preferred decimal separator (loaded from preferences)

    val receiptPaths: List<String> = emptyList(),  // Current receipts shown in UI
    val originalReceiptPaths: List<String> = emptyList(),  // Original receipts (for edit mode)
    val addedReceiptPaths: List<String> = emptyList(),  // Newly added receipts (for cleanup on discard)
    val removedReceiptPaths: List<String> = emptyList(),  // Removed receipts (for cleanup on save)
    val isDraft: Boolean = false,
    val validationErrors: List<String> = emptyList(),
    val isValid: Boolean = false,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val isDeleting: Boolean = false,  // Set to true while deleting transaction
    val isDeleted: Boolean = false,  // Set to true when transaction is deleted
    val isDiscarded: Boolean = false,  // Set to true when user discards changes
    val isCompressingReceipt: Boolean = false,  // Set to true while compressing receipt
    val ocrState: OcrState = OcrState.Idle,  // OCR processing state
    val ocrRetakeRequested: Boolean = false,  // Set to true when user requests retake photo
    val error: String? = null
)

/**
 * OCR processing state.
 * PRD Section 15.2: OCR failure handling
 */
sealed class OcrState {
    /** No OCR processing in progress */
    object Idle : OcrState()

    /** OCR processing in progress */
    object Processing : OcrState()

    /** OCR succeeded, data extracted */
    data class Success(val result: OcrResult) : OcrState()

    /** OCR failed, show error dialog */
    data class Error(val message: String) : OcrState()
}

