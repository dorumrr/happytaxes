package io.github.dorumrr.happytaxes.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Generator for minimal demo receipt images.
 *
 * Creates simple colored images with transaction information for demo data.
 * These are small, lightweight images that look like receipts but are generated programmatically.
 *
 * Image Specifications:
 * - Size: 400x500px (portrait, receipt-like, optimized for speed)
 * - Format: JPEG, 50% quality (fast generation)
 * - Background: Random pastel colors
 * - Text: Transaction details (date, category, amount, description)
 * - Watermark: "DEMO DATA" text
 */
@Singleton
class DemoReceiptGenerator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fileManager: FileManager
) {
    
    companion object {
        private const val IMAGE_WIDTH = 400
        private const val IMAGE_HEIGHT = 500
        private const val JPEG_QUALITY = 50
        
        // Pastel colors for backgrounds
        private val PASTEL_COLORS = listOf(
            Color.rgb(255, 223, 223), // Light pink
            Color.rgb(223, 255, 223), // Light green
            Color.rgb(223, 223, 255), // Light blue
            Color.rgb(255, 255, 223), // Light yellow
            Color.rgb(255, 223, 255), // Light magenta
            Color.rgb(223, 255, 255), // Light cyan
            Color.rgb(255, 239, 223), // Light peach
            Color.rgb(239, 223, 255)  // Light lavender
        )
    }
    
    /**
     * Generate a demo receipt image for a transaction.
     *
     * @param transactionId Transaction ID
     * @param date Transaction date
     * @param category Category name
     * @param description Transaction description
     * @param amount Transaction amount
     * @param profileId Profile ID
     * @param currencyCode Currency code (e.g., "GBP", "USD")
     * @param decimalSeparator Decimal separator for formatting
     * @param thousandSeparator Thousand separator for formatting
     * @return Result with absolute path to generated image or error
     */
    suspend fun generateDemoReceipt(
        transactionId: String,
        date: LocalDate,
        category: String,
        description: String?,
        amount: BigDecimal,
        profileId: String,
        currencyCode: String = "GBP",
        decimalSeparator: String = ".",
        thousandSeparator: String = ","
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            // Create bitmap (RGB_565 for faster creation and smaller memory footprint)
            val bitmap = Bitmap.createBitmap(IMAGE_WIDTH, IMAGE_HEIGHT, Bitmap.Config.RGB_565)
            val canvas = Canvas(bitmap)
            
            // Choose random pastel background
            val backgroundColor = PASTEL_COLORS.random()
            canvas.drawColor(backgroundColor)
            
            // Setup paint for text (scaled down for smaller image)
            val textPaint = Paint().apply {
                color = Color.BLACK
                textSize = 20f
                isAntiAlias = false // Disable for speed
            }

            val titlePaint = Paint().apply {
                color = Color.BLACK
                textSize = 30f
                isAntiAlias = false // Disable for speed
                isFakeBoldText = true
            }

            val watermarkPaint = Paint().apply {
                color = Color.argb(50, 0, 0, 0) // Semi-transparent
                textSize = 40f
                isAntiAlias = false // Disable for speed
                isFakeBoldText = true
                textAlign = Paint.Align.CENTER
            }
            
            // Draw content (scaled down for smaller image)
            var y = 50f

            // Title
            canvas.drawText("RECEIPT", 25f, y, titlePaint)
            y += 50f

            // Date
            val dateFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy")
            canvas.drawText("Date: ${date.format(dateFormatter)}", 25f, y, textPaint)
            y += 30f

            // Category
            canvas.drawText("Category: $category", 25f, y, textPaint)
            y += 30f

            // Description (wrap if too long)
            if (!description.isNullOrBlank()) {
                val maxWidth = IMAGE_WIDTH - 50f
                val lines = wrapText(description, maxWidth, textPaint)
                canvas.drawText("Description:", 25f, y, textPaint)
                y += 30f
                lines.forEach { line ->
                    canvas.drawText("  $line", 25f, y, textPaint)
                    y += 25f
                }
                y += 5f
            }

            // Amount (larger, bold) - formatted with currency and separators
            val amountPaint = Paint().apply {
                color = Color.BLACK
                textSize = 35f
                isAntiAlias = false // Disable for speed
                isFakeBoldText = true
            }
            val formattedAmount = CurrencyFormatter.formatAmount(
                amount = amount,
                currencyCode = currencyCode,
                decimalSeparator = decimalSeparator,
                thousandSeparator = thousandSeparator
            )
            canvas.drawText(formattedAmount, 25f, y, amountPaint)
            y += 50f
            
            // Watermark
            canvas.save()
            canvas.rotate(-45f, IMAGE_WIDTH / 2f, IMAGE_HEIGHT / 2f)
            canvas.drawText("DEMO DATA", IMAGE_WIDTH / 2f, IMAGE_HEIGHT / 2f, watermarkPaint)
            canvas.restore()
            
            // Transaction ID at bottom (small)
            val idPaint = Paint().apply {
                color = Color.GRAY
                textSize = 12f
                isAntiAlias = false // Disable for speed
            }
            canvas.drawText("ID: ${transactionId.take(8)}", 25f, IMAGE_HEIGHT - 25f, idPaint)
            
            // Save to file
            val filename = fileManager.generateReceiptFilename(transactionId)
            val destDir = fileManager.getReceiptsDir(profileId, date)
            val destFile = File(destDir, filename)
            
            FileOutputStream(destFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            }
            
            bitmap.recycle()
            
            Result.success(destFile.absolutePath)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Wrap text to fit within max width.
     */
    private fun wrapText(text: String, maxWidth: Float, paint: Paint): List<String> {
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var currentLine = ""
        
        words.forEach { word ->
            val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
            val bounds = Rect()
            paint.getTextBounds(testLine, 0, testLine.length, bounds)
            
            if (bounds.width() <= maxWidth) {
                currentLine = testLine
            } else {
                if (currentLine.isNotEmpty()) {
                    lines.add(currentLine)
                }
                currentLine = word
            }
        }
        
        if (currentLine.isNotEmpty()) {
            lines.add(currentLine)
        }
        
        return lines
    }
}

