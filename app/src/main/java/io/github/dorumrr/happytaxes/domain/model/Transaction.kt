package io.github.dorumrr.happytaxes.domain.model

import io.github.dorumrr.happytaxes.util.CurrencyFormatter
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/**
 * Domain model for a transaction.
 *
 * This is the business logic representation, separate from database entity.
 * Used by ViewModels and UI layer.
 *
 * Single Currency Model:
 * For cash-basis accounting, transactions are recorded in the user's base currency.
 * This is the amount that actually hit the bank account (already converted).
 */
data class Transaction(
    val id: String,
    val date: LocalDate,
    val type: TransactionType,
    val category: String,
    val description: String?,
    val notes: String?,
    val amount: BigDecimal, // Amount in user's base currency
    val receiptPaths: List<String>,  // Multiple receipts supported
    val isDraft: Boolean,
    val isDemoData: Boolean = false, // Flag for demo/sample transactions
    val isDeleted: Boolean,
    val deletedAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val editHistory: List<EditHistoryEntry>
) {
    /**
     * Check if transaction requires a receipt.
     * Business rule: Expenses MUST have receipts.
     */
    fun requiresReceipt(): Boolean = type == TransactionType.EXPENSE

    /**
     * Check if transaction has at least one receipt.
     */
    fun hasReceipts(): Boolean = receiptPaths.isNotEmpty()

    /**
     * Check if transaction is valid for saving.
     * Business rule: Expenses without receipts must be drafts.
     */
    fun isValidForSave(): Boolean {
        return if (requiresReceipt()) {
            hasReceipts() || isDraft
        } else {
            true
        }
    }

    /**
     * Get display amount with currency symbol and +/- prefix.
     * Expenses show with minus (-), Income shows with plus (+).
     *
     * @param baseCurrency The user's selected base currency (e.g., "GBP", "USD", "RON")
     * @param decimalSeparator Decimal separator ("." or ",")
     * @param thousandSeparator Thousand separator ("," or "." or " ")
     * @return Formatted amount string with currency symbol
     *
     * Examples:
     * - "- $50.00" (USD with symbol, US format)
     * - "- 50,00 RON" (RON without widely recognized symbol, EU format)
     * - "+ £1,250.00" (GBP income, UK format)
     */
    fun getDisplayAmount(
        baseCurrency: String,
        decimalSeparator: String = ".",
        thousandSeparator: String = ","
    ): String {
        return CurrencyFormatter.formatAmountWithSign(
            amount = amount,
            currencyCode = baseCurrency,
            isExpense = type == TransactionType.EXPENSE,
            decimalSeparator = decimalSeparator,
            thousandSeparator = thousandSeparator
        )
    }
}

enum class TransactionType {
    INCOME,
    EXPENSE
}

/**
 * Edit history entry tracking field changes.
 */
data class EditHistoryEntry(
    val editedAt: Instant,
    val changes: List<FieldChange>
)

/**
 * Individual field change with from/to values.
 */
data class FieldChange(
    val field: String,
    val from: String,
    val to: String
)

