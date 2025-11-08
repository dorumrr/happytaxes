package io.github.dorumrr.happytaxes.data.model

import io.github.dorumrr.happytaxes.domain.model.TransactionType
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Report period types.
 *
 * PRD Reference: Section 4.16 - Report Periods
 */
enum class ReportPeriod {
    TAX_YEAR,      // 6 Apr → 5 Apr
    MONTHLY,       // Calendar month
    QUARTERLY,     // Q1, Q2, Q3, Q4
    CUSTOM         // User-defined range
}

/**
 * Report data for Profit & Loss.
 *
 * PRD Reference: Section 4.16 - Reporting Enhancements
 */
data class ReportData(
    val period: ReportPeriod,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val totalIncome: BigDecimal,
    val totalExpenses: BigDecimal,
    val netProfit: BigDecimal,
    val transactionCount: Int,
    val incomeTransactionCount: Int,
    val expenseTransactionCount: Int,
    val categoryBreakdown: List<CategorySummary>,
    val monthlyTrends: List<MonthlyTrend>
) {
    /**
     * Get formatted period string.
     */
    fun getPeriodString(): String {
        return when (period) {
            ReportPeriod.TAX_YEAR -> {
                val startYear = startDate.year
                val endYear = endDate.year
                "$startYear-${endYear.toString().takeLast(2)} Tax Year"
            }
            ReportPeriod.MONTHLY -> {
                "${startDate.month.name.lowercase().replaceFirstChar { it.uppercase() }} ${startDate.year}"
            }
            ReportPeriod.QUARTERLY -> {
                val quarter = when (startDate.monthValue) {
                    1, 2, 3 -> "Q1"
                    4, 5, 6 -> "Q2"
                    7, 8, 9 -> "Q3"
                    else -> "Q4"
                }
                "$quarter ${startDate.year}"
            }
            ReportPeriod.CUSTOM -> {
                "${startDate} to ${endDate}"
            }
        }
    }
}

/**
 * Category summary for reports.
 */
data class CategorySummary(
    val categoryId: String,
    val categoryName: String,
    val type: TransactionType,
    val totalAmount: BigDecimal,
    val transactionCount: Int,
    val percentage: Double  // Percentage of total income or expenses
)

/**
 * Monthly trend data for charts.
 */
data class MonthlyTrend(
    val month: LocalDate,  // First day of month
    val income: BigDecimal,
    val expenses: BigDecimal,
    val netProfit: BigDecimal
)

/**
 * Export format options.
 *
 * PRD Reference: Section 4.16 - Export Formats
 */
enum class ExportFormat {
    CSV,   // Raw transaction data
    PDF,   // Formatted summary with charts
    ZIP    // CSV + PDF + all receipts
}

/**
 * Export result.
 */
data class ExportResult(
    val format: ExportFormat,
    val filePath: String,
    val fileSize: Long
)

/**
 * Report filters.
 *
 * PRD Reference: Section 4.16 - Report Filters
 */
data class ReportFilters(
    val includeCategories: Set<String> = emptySet(),  // Empty = all categories
    val excludeCategories: Set<String> = emptySet(),
    val transactionType: TransactionTypeFilter = TransactionTypeFilter.ALL,
    val receiptFilter: ReceiptFilter = ReceiptFilter.ALL
) {
    /**
     * Check if a transaction passes all filters.
     */
    fun passes(
        category: String,
        type: TransactionType,
        hasReceipts: Boolean
    ): Boolean {
        // Category filters
        if (includeCategories.isNotEmpty() && category !in includeCategories) {
            return false
        }
        if (category in excludeCategories) {
            return false
        }

        // Transaction type filter
        when (transactionType) {
            TransactionTypeFilter.INCOME_ONLY -> if (type != TransactionType.INCOME) return false
            TransactionTypeFilter.EXPENSES_ONLY -> if (type != TransactionType.EXPENSE) return false
            TransactionTypeFilter.ALL -> { /* no filter */ }
        }

        // Receipt filter
        when (receiptFilter) {
            ReceiptFilter.WITH_RECEIPTS -> if (!hasReceipts) return false
            ReceiptFilter.WITHOUT_RECEIPTS -> if (hasReceipts) return false
            ReceiptFilter.ALL -> { /* no filter */ }
        }

        return true
    }
}

/**
 * Transaction type filter options.
 */
enum class TransactionTypeFilter {
    ALL,
    INCOME_ONLY,
    EXPENSES_ONLY
}

/**
 * Receipt filter options.
 */
enum class ReceiptFilter {
    ALL,
    WITH_RECEIPTS,
    WITHOUT_RECEIPTS
}

