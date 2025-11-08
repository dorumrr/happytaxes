package io.github.dorumrr.happytaxes.data.repository

import io.github.dorumrr.happytaxes.data.local.dao.TransactionDao
import io.github.dorumrr.happytaxes.data.mapper.TransactionMapper
import io.github.dorumrr.happytaxes.data.model.*
import io.github.dorumrr.happytaxes.domain.model.Transaction
import io.github.dorumrr.happytaxes.domain.model.TransactionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.Month
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for report generation.
 *
 * Handles:
 * - Profit & Loss calculations
 * - Category breakdowns
 * - Monthly trends
 * - Report data aggregation
 * - Dynamic tax year calculation based on user's tax period
 * - Multi-profile support (filters by current profile)
 *
 * PRD Reference: Section 4.16 - Reporting Enhancements
 */
@Singleton
class ReportRepository @Inject constructor(
    private val transactionDao: TransactionDao,
    private val preferencesRepository: PreferencesRepository,
    private val profileContext: ProfileContext
) {

    /**
     * Generate report data for a date range.
     *
     * @param startDate Start date (inclusive)
     * @param endDate End date (inclusive)
     * @param period Report period type
     * @param filters Report filters (categories, type, receipts)
     * @return Report data
     */
    suspend fun generateReport(
        startDate: LocalDate,
        endDate: LocalDate,
        period: ReportPeriod,
        filters: ReportFilters = ReportFilters()
    ): Result<ReportData> = withContext(Dispatchers.IO) {
        try {
            val profileId = profileContext.getCurrentProfileIdOnce()

            // Get all confirmed transactions in range (exclude drafts)
            val transactionEntities = transactionDao.getByDateRange(profileId, startDate, endDate)

            // Collect first value from Flow and transform
            val allTransactions = transactionEntities.first()
                .map { entity -> TransactionMapper.toDomain(entity) }
                .filter { transaction -> !transaction.isDraft }  // Exclude drafts from reports

            // Apply filters
            val transactions = allTransactions.filter { transaction ->
                filters.passes(
                    category = transaction.category,
                    type = transaction.type,
                    hasReceipts = transaction.receiptPaths.isNotEmpty()
                )
            }

            // Calculate totals
            val incomeTransactions = transactions.filter { it.type == TransactionType.INCOME }
            val expenseTransactions = transactions.filter { it.type == TransactionType.EXPENSE }

            val totalIncome = incomeTransactions.sumOf { it.amount }
            val totalExpenses = expenseTransactions.sumOf { it.amount }
            val netProfit = totalIncome - totalExpenses

            // Category breakdown
            val categoryBreakdown = calculateCategoryBreakdown(
                transactions,
                totalIncome,
                totalExpenses
            )

            // Monthly trends
            val monthlyTrends = calculateMonthlyTrends(transactions, startDate, endDate)

            val reportData = ReportData(
                period = period,
                startDate = startDate,
                endDate = endDate,
                totalIncome = totalIncome,
                totalExpenses = totalExpenses,
                netProfit = netProfit,
                transactionCount = transactions.size,
                incomeTransactionCount = incomeTransactions.size,
                expenseTransactionCount = expenseTransactions.size,
                categoryBreakdown = categoryBreakdown,
                monthlyTrends = monthlyTrends
            )

            Result.success(reportData)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Calculate category breakdown.
     */
    private fun calculateCategoryBreakdown(
        transactions: List<Transaction>,
        totalIncome: BigDecimal,
        totalExpenses: BigDecimal
    ): List<CategorySummary> {
        return transactions
            .groupBy { it.category }
            .map { (category, txns) ->
                val type = txns.first().type
                val totalAmount = txns.sumOf { it.amount }
                val percentage = if (type == TransactionType.INCOME && totalIncome > BigDecimal.ZERO) {
                    (totalAmount.divide(totalIncome, 4, RoundingMode.HALF_UP) * BigDecimal(100)).toDouble()
                } else if (type == TransactionType.EXPENSE && totalExpenses > BigDecimal.ZERO) {
                    (totalAmount.divide(totalExpenses, 4, RoundingMode.HALF_UP) * BigDecimal(100)).toDouble()
                } else {
                    0.0
                }

                CategorySummary(
                    categoryId = category,
                    categoryName = category,  // Category name (transactions store names, not IDs)
                    type = type,
                    totalAmount = totalAmount,
                    transactionCount = txns.size,
                    percentage = percentage
                )
            }
            .sortedByDescending { it.totalAmount }
    }

    /**
     * Calculate monthly trends.
     */
    private fun calculateMonthlyTrends(
        transactions: List<Transaction>,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<MonthlyTrend> {
        val trends = mutableListOf<MonthlyTrend>()
        var currentMonth = startDate.withDayOfMonth(1)
        val lastMonth = endDate.withDayOfMonth(1)

        while (!currentMonth.isAfter(lastMonth)) {
            val monthStart = currentMonth
            val monthEnd = currentMonth.plusMonths(1).minusDays(1)

            val monthTransactions = transactions.filter {
                !it.date.isBefore(monthStart) && !it.date.isAfter(monthEnd)
            }

            val income = monthTransactions
                .filter { it.type == TransactionType.INCOME }
                .sumOf { it.amount }

            val expenses = monthTransactions
                .filter { it.type == TransactionType.EXPENSE }
                .sumOf { it.amount }

            trends.add(
                MonthlyTrend(
                    month = currentMonth,
                    income = income,
                    expenses = expenses,
                    netProfit = income - expenses
                )
            )

            currentMonth = currentMonth.plusMonths(1)
        }

        return trends
    }

    /**
     * Get tax year date range based on user's tax period.
     *
     * Tax period is stored as MM-DD format (e.g., "04-06" to "04-05" for UK).
     * This method dynamically builds the tax year dates based on the stored period.
     *
     * @param year Start year (e.g., 2025 for 2025-26 tax year)
     * @return Pair of start and end dates
     */
    suspend fun getTaxYearRange(year: Int): Pair<LocalDate, LocalDate> {
        // Get current profile ID
        val profileId = profileContext.getCurrentProfileIdOnce()

        // Get tax period from preferences
        val taxPeriodStart = preferencesRepository.getTaxPeriodStart(profileId).first()
        val taxPeriodEnd = preferencesRepository.getTaxPeriodEnd(profileId).first()

        // Parse MM-DD format
        val (startMonth, startDay) = parseTaxPeriod(taxPeriodStart)
        val (endMonth, endDay) = parseTaxPeriod(taxPeriodEnd)

        // Build start date
        val startDate = LocalDate.of(year, startMonth, startDay)

        // Build end date (might be in next year if period spans year boundary)
        val endDate = if (endMonth < startMonth || (endMonth == startMonth && endDay < startDay)) {
            // Period spans year boundary (e.g., 6 Apr 2025 to 5 Apr 2026)
            LocalDate.of(year + 1, endMonth, endDay)
        } else {
            // Period within same year (e.g., 1 Jan 2025 to 31 Dec 2025)
            LocalDate.of(year, endMonth, endDay)
        }

        return Pair(startDate, endDate)
    }

    /**
     * Parse tax period string (MM-DD) to month and day.
     */
    private fun parseTaxPeriod(period: String): Pair<Int, Int> {
        val parts = period.split("-")
        return if (parts.size == 2) {
            Pair(parts[0].toIntOrNull() ?: 1, parts[1].toIntOrNull() ?: 1)
        } else {
            Pair(1, 1) // Default: 1 Jan
        }
    }

    /**
     * Get current tax year based on user's tax period.
     *
     * Determines which tax year the current date falls into based on the
     * user's configured tax period.
     *
     * @return Start year of current tax year
     */
    suspend fun getCurrentTaxYear(): Int {
        val today = LocalDate.now()

        // Get current profile ID
        val profileId = profileContext.getCurrentProfileIdOnce()

        // Get tax period from preferences
        val taxPeriodStart = preferencesRepository.getTaxPeriodStart(profileId).first()
        val (startMonth, startDay) = parseTaxPeriod(taxPeriodStart)

        // If today is before the tax period start date, we're in the previous tax year
        return if (today.monthValue < startMonth ||
                   (today.monthValue == startMonth && today.dayOfMonth < startDay)) {
            today.year - 1
        } else {
            today.year
        }
    }

    /**
     * Get quarterly date range.
     *
     * @param year Year
     * @param quarter Quarter (1-4)
     * @return Pair of start and end dates
     */
    fun getQuarterlyRange(year: Int, quarter: Int): Pair<LocalDate, LocalDate> {
        require(quarter in 1..4) { "Quarter must be between 1 and 4" }

        val startMonth = when (quarter) {
            1 -> Month.JANUARY
            2 -> Month.APRIL
            3 -> Month.JULY
            else -> Month.OCTOBER
        }

        val startDate = LocalDate.of(year, startMonth, 1)
        val endDate = startDate.plusMonths(3).minusDays(1)

        return Pair(startDate, endDate)
    }

    /**
     * Get monthly date range.
     *
     * @param year Year
     * @param month Month (1-12)
     * @return Pair of start and end dates
     */
    fun getMonthlyRange(year: Int, month: Int): Pair<LocalDate, LocalDate> {
        require(month in 1..12) { "Month must be between 1 and 12" }

        val startDate = LocalDate.of(year, month, 1)
        val endDate = startDate.plusMonths(1).minusDays(1)

        return Pair(startDate, endDate)
    }

    /**
     * Get available tax years that have transactions.
     *
     * Returns a list of tax year start years based on the user's tax period,
     * but only includes years that have at least one transaction.
     * Always includes the current tax year even if empty.
     */
    suspend fun getAvailableTaxYears(): List<Int> {
        val currentYear = getCurrentTaxYear()
        val potentialYears = (currentYear downTo currentYear - 9).toList()

        // Filter to only years with transactions, but always include current year
        val profileId = profileContext.getCurrentProfileIdOnce()
        val yearsWithTransactions = mutableListOf<Int>()
        for (year in potentialYears) {
            if (year == currentYear) {
                yearsWithTransactions.add(year) // Always include current year
            } else {
                // Check if this tax year has any transactions
                val (startDate, endDate) = getTaxYearRange(year)
                val transactions = transactionDao.getByDateRange(profileId, startDate, endDate).first()
                if (transactions.isNotEmpty()) {
                    yearsWithTransactions.add(year)
                }
            }
        }
        return yearsWithTransactions
    }

    /**
     * Get available months that have transactions.
     * Returns list of (year, month) pairs in reverse chronological order.
     * Only includes months with at least one transaction.
     * Always includes current month even if empty.
     *
     * @return List of Pair<Year, Month> sorted newest first
     */
    suspend fun getAvailableMonths(): List<Pair<Int, Int>> = withContext(Dispatchers.IO) {
        val profileId = profileContext.getCurrentProfileIdOnce()
        val now = LocalDate.now()
        val currentYear = now.year
        val currentMonth = now.monthValue

        // Get all transactions to find distinct months
        val allTransactions = transactionDao.getAllActiveNonDraft(profileId).first()

        // Extract distinct year-month combinations
        val monthsWithTransactions = allTransactions
            .map { entity -> TransactionMapper.toDomain(entity) }
            .filter { !it.isDraft }  // Exclude drafts
            .map { transaction ->
                Pair(transaction.date.year, transaction.date.monthValue)
            }
            .distinct()
            .toMutableSet()

        // Always include current month
        monthsWithTransactions.add(Pair(currentYear, currentMonth))

        // Sort in reverse chronological order (newest first)
        return@withContext monthsWithTransactions
            .sortedWith(compareByDescending<Pair<Int, Int>> { it.first }.thenByDescending { it.second })
    }
}

