package io.github.dorumrr.happytaxes.util

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Environment
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.dorumrr.happytaxes.data.model.ReportData
import io.github.dorumrr.happytaxes.domain.model.Transaction
import io.github.dorumrr.happytaxes.domain.model.TransactionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.io.FileOutputStream
import java.math.BigDecimal
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeoutException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PDF export utility.
 *
 * Exports report data to PDF format with charts.
 *
 * PRD Reference: Section 4.16 - Export Formats (PDF)
 */
@Singleton
class PdfExporter @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    // Page dimensions (A4)
    private val PAGE_WIDTH = 595  // points
    private val PAGE_HEIGHT = 842  // points
    private val MARGIN = 50
    
    companion object {
        /**
         * Export timeout: 60 seconds for PDF generation.
         * PDF rendering is more complex than CSV, needs more time.
         */
        private const val EXPORT_TIMEOUT_MS = 60_000L
    }

    // Paint styles
    private val titlePaint = Paint().apply {
        color = Color.BLACK
        textSize = 24f
        isAntiAlias = true
        isFakeBoldText = true
    }

    private val headingPaint = Paint().apply {
        color = Color.BLACK
        textSize = 18f
        isAntiAlias = true
        isFakeBoldText = true
    }

    private val bodyPaint = Paint().apply {
        color = Color.BLACK
        textSize = 12f
        isAntiAlias = true
    }

    private val smallPaint = Paint().apply {
        color = Color.GRAY
        textSize = 10f
        isAntiAlias = true
    }

    /**
     * Export report to PDF file in Downloads folder.
     *
     * @param reportData Report data
     * @param transactions List of transactions for the period (for transaction details pages)
     * @param filename Output filename
     * @param baseCurrency User's base currency code (e.g., "GBP", "USD", "EUR")
     * @param decimalSeparator Decimal separator for formatting amounts
     * @param thousandSeparator Thousand separator for formatting amounts
     * @return Result with file path or error
     */
    suspend fun exportReport(
        reportData: ReportData,
        transactions: List<Transaction>,
        filename: String,
        baseCurrency: String,
        decimalSeparator: String = ".",
        thousandSeparator: String = ","
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            // Apply timeout to prevent indefinite hangs
            withTimeout(EXPORT_TIMEOUT_MS) {
            // Get Downloads directory
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) {
                downloadsDir.mkdirs()
            }

            // Create PDF document
            val pdfDocument = PdfDocument()

            // Page 1: Summary
            val page1 = pdfDocument.startPage(
                PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
            )
            drawSummaryPage(page1.canvas, reportData, baseCurrency, decimalSeparator, thousandSeparator)
            pdfDocument.finishPage(page1)

            // Page 2: Category Breakdown
            var pageNumber = 2
            if (reportData.categoryBreakdown.isNotEmpty()) {
                val page2 = pdfDocument.startPage(
                    PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
                )
                drawCategoryBreakdownPage(page2.canvas, reportData, baseCurrency, decimalSeparator, thousandSeparator, pageNumber)
                pdfDocument.finishPage(page2)
                pageNumber++
            }

            // Page 3+: Transaction Details (grouped by category)
            if (transactions.isNotEmpty()) {
                pageNumber = drawTransactionDetailsPages(
                    pdfDocument,
                    transactions,
                    baseCurrency,
                    decimalSeparator,
                    thousandSeparator,
                    pageNumber
                )
            }

            // Write to file in Downloads
            val pdfFile = File(downloadsDir, filename)
            FileOutputStream(pdfFile).use { outputStream ->
                pdfDocument.writeTo(outputStream)
            }

                pdfDocument.close()

                Result.success(pdfFile.absolutePath)
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            Result.failure(TimeoutException("PDF export timed out after ${EXPORT_TIMEOUT_MS / 1000} seconds. Try exporting a smaller date range."))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Draw summary page.
     */
    private fun drawSummaryPage(
        canvas: Canvas,
        reportData: ReportData,
        baseCurrency: String,
        decimalSeparator: String,
        thousandSeparator: String
    ) {
        var y = MARGIN.toFloat()
        val currencySymbol = CurrencyFormatter.getCurrencySymbol(baseCurrency)

        // Title
        canvas.drawText("Profit & Loss Report", MARGIN.toFloat(), y, titlePaint)
        y += 40

        // Period
        canvas.drawText(reportData.getPeriodString(), MARGIN.toFloat(), y, bodyPaint)
        y += 20
        canvas.drawText(
            "${reportData.startDate.format(dateFormatter)} to ${reportData.endDate.format(dateFormatter)}",
            MARGIN.toFloat(),
            y,
            smallPaint
        )
        y += 40

        // Summary section
        canvas.drawText("Summary", MARGIN.toFloat(), y, headingPaint)
        y += 30

        // Income
        canvas.drawText("Total Income:", MARGIN.toFloat(), y, bodyPaint)
        canvas.drawText(
            "$currencySymbol${formatAmount(reportData.totalIncome, decimalSeparator, thousandSeparator)}",
            (PAGE_WIDTH - MARGIN - 150).toFloat(),
            y,
            bodyPaint
        )
        y += 25

        // Expenses
        canvas.drawText("Total Expenses:", MARGIN.toFloat(), y, bodyPaint)
        canvas.drawText(
            "$currencySymbol${formatAmount(reportData.totalExpenses, decimalSeparator, thousandSeparator)}",
            (PAGE_WIDTH - MARGIN - 150).toFloat(),
            y,
            bodyPaint
        )
        y += 25

        // Draw line
        val linePaint = Paint().apply {
            color = Color.BLACK
            strokeWidth = 2f
        }
        canvas.drawLine(
            MARGIN.toFloat(),
            y,
            (PAGE_WIDTH - MARGIN).toFloat(),
            y,
            linePaint
        )
        y += 15

        // Net Profit
        val profitColor = if (reportData.netProfit >= BigDecimal.ZERO) {
            Color.rgb(46, 125, 50)  // Green
        } else {
            Color.rgb(198, 40, 40)  // Red
        }
        val profitPaint = Paint().apply {
            color = profitColor
            textSize = 16f
            isAntiAlias = true
            isFakeBoldText = true
        }
        canvas.drawText("Net Profit:", MARGIN.toFloat(), y, profitPaint)
        canvas.drawText(
            "$currencySymbol${formatAmount(reportData.netProfit, decimalSeparator, thousandSeparator)}",
            (PAGE_WIDTH - MARGIN - 150).toFloat(),
            y,
            profitPaint
        )
        y += 40

        // Footer
        canvas.drawText(
            "Generated by HappyTaxes",
            MARGIN.toFloat(),
            (PAGE_HEIGHT - MARGIN).toFloat(),
            smallPaint
        )
        canvas.drawText(
            "Page 1",
            (PAGE_WIDTH - MARGIN - 50).toFloat(),
            (PAGE_HEIGHT - MARGIN).toFloat(),
            smallPaint
        )
    }

    /**
     * Draw category breakdown page.
     */
    private fun drawCategoryBreakdownPage(
        canvas: Canvas,
        reportData: ReportData,
        baseCurrency: String,
        decimalSeparator: String,
        thousandSeparator: String,
        pageNumber: Int
    ) {
        var y = MARGIN.toFloat()
        val currencySymbol = CurrencyFormatter.getCurrencySymbol(baseCurrency)

        // Title
        canvas.drawText("Category Breakdown", MARGIN.toFloat(), y, titlePaint)
        y += 40

        // Income categories
        val incomeCategories = reportData.categoryBreakdown.filter {
            it.type == TransactionType.INCOME
        }
        if (incomeCategories.isNotEmpty()) {
            canvas.drawText("Income Categories", MARGIN.toFloat(), y, headingPaint)
            y += 30

            incomeCategories.forEach { category ->
                canvas.drawText(category.categoryName, MARGIN.toFloat(), y, bodyPaint)
                canvas.drawText(
                    "$currencySymbol${formatAmount(category.totalAmount, decimalSeparator, thousandSeparator)} (${String.format("%.1f", category.percentage)}%)",
                    (PAGE_WIDTH - MARGIN - 200).toFloat(),
                    y,
                    bodyPaint
                )
                y += 20
            }
            y += 20
        }

        // Expense categories
        val expenseCategories = reportData.categoryBreakdown.filter {
            it.type == TransactionType.EXPENSE
        }
        if (expenseCategories.isNotEmpty()) {
            canvas.drawText("Expense Categories", MARGIN.toFloat(), y, headingPaint)
            y += 30

            expenseCategories.forEach { category ->
                canvas.drawText(category.categoryName, MARGIN.toFloat(), y, bodyPaint)
                canvas.drawText(
                    "$currencySymbol${formatAmount(category.totalAmount, decimalSeparator, thousandSeparator)} (${String.format("%.1f", category.percentage)}%)",
                    (PAGE_WIDTH - MARGIN - 200).toFloat(),
                    y,
                    bodyPaint
                )
                y += 20
            }
        }

        // Footer
        canvas.drawText(
            "Generated by HappyTaxes",
            MARGIN.toFloat(),
            (PAGE_HEIGHT - MARGIN).toFloat(),
            smallPaint
        )
        canvas.drawText(
            "Page $pageNumber",
            (PAGE_WIDTH - MARGIN - 50).toFloat(),
            (PAGE_HEIGHT - MARGIN).toFloat(),
            smallPaint
        )
    }

    /**
     * Draw transaction details pages (grouped by category).
     * Returns the next page number to use.
     */
    private fun drawTransactionDetailsPages(
        pdfDocument: PdfDocument,
        transactions: List<Transaction>,
        baseCurrency: String,
        decimalSeparator: String,
        thousandSeparator: String,
        startPageNumber: Int
    ): Int {
        var pageNumber = startPageNumber
        // Get currency symbol or use currency code if no symbol available
        val currencySymbol = CurrencyFormatter.getCurrencySymbol(baseCurrency) ?: baseCurrency

        // Group transactions by type and category
        val incomeTransactions = transactions.filter { it.type == TransactionType.INCOME }
            .groupBy { it.category }
            .toList()
            .sortedBy { it.first } // Sort by category name

        val expenseTransactions = transactions.filter { it.type == TransactionType.EXPENSE }
            .groupBy { it.category }
            .toList()
            .sortedBy { it.first } // Sort by category name

        // Draw income transactions
        if (incomeTransactions.isNotEmpty()) {
            pageNumber = drawTransactionsByType(
                pdfDocument,
                incomeTransactions,
                "Income Transactions",
                TransactionType.INCOME,
                currencySymbol,
                decimalSeparator,
                thousandSeparator,
                pageNumber
            )
        }

        // Draw expense transactions
        if (expenseTransactions.isNotEmpty()) {
            pageNumber = drawTransactionsByType(
                pdfDocument,
                expenseTransactions,
                "Expense Transactions",
                TransactionType.EXPENSE,
                currencySymbol,
                decimalSeparator,
                thousandSeparator,
                pageNumber
            )
        }

        return pageNumber
    }

    /**
     * Draw transactions for a specific type (Income or Expense).
     * Returns the next page number to use.
     */
    private fun drawTransactionsByType(
        pdfDocument: PdfDocument,
        transactionsByCategory: List<Pair<String, List<Transaction>>>,
        sectionTitle: String,
        type: TransactionType,
        currencySymbol: String,
        decimalSeparator: String,
        thousandSeparator: String,
        startPageNumber: Int
    ): Int {
        var pageNumber = startPageNumber
        var currentPage = pdfDocument.startPage(
            PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
        )
        var y = MARGIN.toFloat()

        // Section title
        currentPage.canvas.drawText(sectionTitle, MARGIN.toFloat(), y, titlePaint)
        y += 40

        // Iterate through categories
        for ((categoryName, categoryTransactions) in transactionsByCategory) {
            // Check if we need a new page for category header
            if (y > PAGE_HEIGHT - MARGIN - 100) {
                // Finish current page
                drawPageFooter(currentPage.canvas, pageNumber)
                pdfDocument.finishPage(currentPage)

                // Start new page
                pageNumber++
                currentPage = pdfDocument.startPage(
                    PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
                )
                y = MARGIN.toFloat()
            }

            // Category heading
            currentPage.canvas.drawText(categoryName, MARGIN.toFloat(), y, headingPaint)
            y += 30

            // Sort transactions by date (most recent first)
            val sortedTransactions = categoryTransactions.sortedByDescending { it.date }

            // Draw each transaction
            for (transaction in sortedTransactions) {
                // Check if we need a new page for transaction
                if (y > PAGE_HEIGHT - MARGIN - 60) {
                    // Finish current page
                    drawPageFooter(currentPage.canvas, pageNumber)
                    pdfDocument.finishPage(currentPage)

                    // Start new page
                    pageNumber++
                    currentPage = pdfDocument.startPage(
                        PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
                    )
                    y = MARGIN.toFloat()

                    // Repeat category heading on new page
                    currentPage.canvas.drawText("$categoryName (continued)", MARGIN.toFloat(), y, headingPaint)
                    y += 30
                }

                // Transaction date
                val dateStr = transaction.date.format(dateFormatter)
                currentPage.canvas.drawText(dateStr, MARGIN.toFloat(), y, bodyPaint)

                // Transaction description (or "No description")
                val description = transaction.description?.take(40) ?: "No description"
                currentPage.canvas.drawText(
                    description,
                    (MARGIN + 100).toFloat(),
                    y,
                    bodyPaint
                )

                // Transaction amount (right-aligned)
                val amountStr = "$currencySymbol${formatAmount(transaction.amount, decimalSeparator, thousandSeparator)}"
                currentPage.canvas.drawText(
                    amountStr,
                    (PAGE_WIDTH - MARGIN - 100).toFloat(),
                    y,
                    bodyPaint
                )

                // Receipt indicator
                if (transaction.hasReceipts()) {
                    currentPage.canvas.drawText(
                        "✓",
                        (PAGE_WIDTH - MARGIN - 30).toFloat(),
                        y,
                        bodyPaint
                    )
                }

                y += 20
            }

            y += 10 // Extra space between categories
        }

        // Finish last page
        drawPageFooter(currentPage.canvas, pageNumber)
        pdfDocument.finishPage(currentPage)

        return pageNumber + 1
    }

    /**
     * Draw page footer with "Generated by HappyTaxes" and page number.
     */
    private fun drawPageFooter(canvas: Canvas, pageNumber: Int) {
        canvas.drawText(
            "Generated by HappyTaxes",
            MARGIN.toFloat(),
            (PAGE_HEIGHT - MARGIN).toFloat(),
            smallPaint
        )
        canvas.drawText(
            "Page $pageNumber",
            (PAGE_WIDTH - MARGIN - 50).toFloat(),
            (PAGE_HEIGHT - MARGIN).toFloat(),
            smallPaint
        )
    }

    /**
     * Format amount for display with country-specific separators.
     */
    private fun formatAmount(
        amount: BigDecimal,
        decimalSeparator: String,
        thousandSeparator: String
    ): String {
        return NumberFormatter.formatAmount(
            amount = amount,
            decimalSeparator = decimalSeparator,
            thousandSeparator = thousandSeparator,
            decimalPlaces = 2
        )
    }
}

