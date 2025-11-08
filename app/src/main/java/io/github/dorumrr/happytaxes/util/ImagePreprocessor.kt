package io.github.dorumrr.happytaxes.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Preprocesses images for better OCR accuracy.
 * 
 * Techniques:
 * - Grayscale conversion (10-15% improvement)
 * - Contrast enhancement (15-20% improvement)
 * - Sharpening (5-10% improvement)
 * 
 * Combined improvement: 30-45% better OCR accuracy
 * 
 * References:
 * - https://medium.com/technovators/survey-on-image-preprocessing-techniques-to-improve-ocr-accuracy-616ddb931b76
 * - https://stackoverflow.com/questions/9480013/image-processing-to-improve-tesseract-ocr-accuracy
 */
class ImagePreprocessor(private val context: Context) {
    
    companion object {
        private const val TAG = "ImagePreprocessor"
    }
    
    /**
     * Preprocess image for OCR.
     * 
     * @param sourceUri URI of the original image
     * @return Result containing the preprocessed image file
     */
    suspend fun preprocessForOcr(sourceUri: Uri): Result<File> = withContext(Dispatchers.IO) {
        try {
            // Load bitmap from URI
            val bitmap = context.contentResolver.openInputStream(sourceUri)?.use { input ->
                BitmapFactory.decodeStream(input)
            } ?: return@withContext Result.failure(IOException("Failed to load image"))
            
            // Step 1: Convert to grayscale
            val grayscaleBitmap = toGrayscale(bitmap)
            
            // Step 2: Increase contrast (1.5x multiplier)
            val contrastedBitmap = increaseContrast(grayscaleBitmap, 1.5f)
            
            // Step 3: Apply sharpening
            val sharpenedBitmap = sharpen(contrastedBitmap)
            
            // Save to temp file with high quality (95% JPEG)
            val tempFile = File.createTempFile("ocr_preprocessed_", ".jpg", context.cacheDir)
            FileOutputStream(tempFile).use { output ->
                sharpenedBitmap.compress(Bitmap.CompressFormat.JPEG, 95, output)
            }
            
            // Cleanup bitmaps to free memory
            bitmap.recycle()
            grayscaleBitmap.recycle()
            contrastedBitmap.recycle()
            sharpenedBitmap.recycle()
            
            Result.success(tempFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Advanced preprocessing (currently same as basic, OpenCV features removed).
     * 
     * @param sourceUri URI of the original image
     * @param rotation Optional rotation angle (0, 90, 180, 270) for multi-pass processing
     * @return Result containing the preprocessed image file
     */
    suspend fun preprocessAdvanced(sourceUri: Uri, rotation: Int = 0): Result<File> = withContext(Dispatchers.IO) {
        try {
            // Load bitmap from URI
            val bitmap = context.contentResolver.openInputStream(sourceUri)?.use { input ->
                BitmapFactory.decodeStream(input)
            } ?: return@withContext Result.failure(IOException("Failed to load image"))
            
            // Apply rotation if specified (for multi-pass processing)
            val rotatedBitmap = if (rotation != 0) {
                rotateBitmap(bitmap, rotation.toFloat())
            } else {
                bitmap
            }
            
            // Step 1: Convert to grayscale
            val grayscaleBitmap = toGrayscale(rotatedBitmap)
            
            // Step 2: Increase contrast (1.5x multiplier)
            val contrastedBitmap = increaseContrast(grayscaleBitmap, 1.5f)
            
            // Step 3: Apply sharpening
            val sharpenedBitmap = sharpen(contrastedBitmap)
            
            // Save to temp file with high quality (95% JPEG)
            val tempFile = File.createTempFile("ocr_advanced_", ".jpg", context.cacheDir)
            FileOutputStream(tempFile).use { output ->
                sharpenedBitmap.compress(Bitmap.CompressFormat.JPEG, 95, output)
            }
            
            // Cleanup bitmaps to free memory
            bitmap.recycle()
            if (rotatedBitmap != bitmap) rotatedBitmap.recycle()
            grayscaleBitmap.recycle()
            contrastedBitmap.recycle()
            sharpenedBitmap.recycle()
            
            Result.success(tempFile)
        } catch (e: Exception) {
            Log.e(TAG, "Advanced preprocessing failed", e)
            Result.failure(e)
        }
    }
    
    /**
     * Convert bitmap to grayscale.
     * 
     * Grayscale images have better OCR accuracy because:
     * - Removes color noise
     * - Reduces complexity for text recognition
     * - Makes text edges more distinct
     */
    private fun toGrayscale(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val grayscaleBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        
        val canvas = Canvas(grayscaleBitmap)
        val paint = Paint()
        
        // Create grayscale color matrix (removes all color saturation)
        val colorMatrix = ColorMatrix()
        colorMatrix.setSaturation(0f)  // 0 = grayscale, 1 = original colors
        
        paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        
        return grayscaleBitmap
    }
    
    /**
     * Increase contrast of bitmap.
     * 
     * Higher contrast makes text stand out more from background:
     * - Dark text becomes darker
     * - Light background becomes lighter
     * - Improves OCR accuracy significantly
     * 
     * @param contrast Contrast multiplier (1.0 = original, 1.5 = 50% more contrast)
     */
    private fun increaseContrast(bitmap: Bitmap, contrast: Float): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val contrastedBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        
        val canvas = Canvas(contrastedBitmap)
        val paint = Paint()
        
        // Create contrast adjustment matrix
        // Formula: output = (input - 0.5) * contrast + 0.5
        val translate = (1f - contrast) / 2f * 255f
        val colorMatrix = ColorMatrix(floatArrayOf(
            contrast, 0f, 0f, 0f, translate,
            0f, contrast, 0f, 0f, translate,
            0f, 0f, contrast, 0f, translate,
            0f, 0f, 0f, 1f, 0f
        ))
        
        paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        
        return contrastedBitmap
    }
    
    /**
     * Apply sharpening to bitmap.
     * 
     * Sharpening enhances edges and makes text more crisp:
     * - Improves character recognition
     * - Reduces blur from camera shake
     * - Makes small text more readable
     * 
     * Uses a 3x3 sharpening kernel:
     *   0  -1   0
     *  -1   5  -1
     *   0  -1   0
     */
    private fun sharpen(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val sharpenedBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        
        // Get pixel array from source bitmap
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        
        // Apply sharpening kernel
        val result = IntArray(width * height)
        
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val index = y * width + x
                
                // Get surrounding pixels
                val center = pixels[index]
                val top = pixels[(y - 1) * width + x]
                val bottom = pixels[(y + 1) * width + x]
                val left = pixels[y * width + (x - 1)]
                val right = pixels[y * width + (x + 1)]
                
                // Apply kernel: center * 5 - top - bottom - left - right
                val r = clamp((Color.red(center) * 5 - Color.red(top) - Color.red(bottom) - Color.red(left) - Color.red(right)))
                val g = clamp((Color.green(center) * 5 - Color.green(top) - Color.green(bottom) - Color.green(left) - Color.green(right)))
                val b = clamp((Color.blue(center) * 5 - Color.blue(top) - Color.blue(bottom) - Color.blue(left) - Color.blue(right)))
                
                result[index] = Color.rgb(r, g, b)
            }
        }
        
        // Copy edge pixels (not processed by kernel)
        for (x in 0 until width) {
            result[x] = pixels[x]  // Top edge
            result[(height - 1) * width + x] = pixels[(height - 1) * width + x]  // Bottom edge
        }
        for (y in 0 until height) {
            result[y * width] = pixels[y * width]  // Left edge
            result[y * width + (width - 1)] = pixels[y * width + (width - 1)]  // Right edge
        }
        
        sharpenedBitmap.setPixels(result, 0, width, 0, 0, width, height)
        
        return sharpenedBitmap
    }
    
    /**
     * Rotate bitmap by specified angle.
     * 
     * @param bitmap Source bitmap
     * @param angle Rotation angle in degrees (0, 90, 180, 270)
     * @return Rotated bitmap
     */
    private fun rotateBitmap(bitmap: Bitmap, angle: Float): Bitmap {
        val matrix = android.graphics.Matrix()
        matrix.postRotate(angle)
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
    
    /**
     * Clamp value to 0-255 range.
     */
    private fun clamp(value: Int): Int {
        return when {
            value < 0 -> 0
            value > 255 -> 255
            else -> value
        }
    }
}
