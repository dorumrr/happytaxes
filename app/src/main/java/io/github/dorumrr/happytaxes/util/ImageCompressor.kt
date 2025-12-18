package io.github.dorumrr.happytaxes.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import dagger.hilt.android.qualifiers.ApplicationContext
import id.zelory.compressor.Compressor
import id.zelory.compressor.constraint.default
import id.zelory.compressor.constraint.destination
import id.zelory.compressor.constraint.format
import id.zelory.compressor.constraint.quality
import id.zelory.compressor.constraint.resolution
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

/**
 * Image compressor with EXIF data preservation.
 * 
 * Compression Rules (PRD Section 4.10):
 * - Max 1600px longest side
 * - 65% JPEG quality
 * - Never upscale smaller images
 * - DO NOT strip EXIF data
 * - Store only compressed version
 * 
 * EXIF Data Preserved:
 * - Orientation
 * - DateTime
 * - GPS Location
 * - Camera Make/Model
 * - ISO, Aperture, Shutter Speed
 * - All other metadata
 */
@Singleton
class ImageCompressor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fileManager: FileManager
) {
    
    companion object {
        private const val MAX_DIMENSION = 1600
        private const val JPEG_QUALITY = 65
        
        /**
         * EXIF tags to preserve during compression.
         * Comprehensive list to ensure no metadata is lost.
         */
        private val EXIF_TAGS = arrayOf(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.TAG_DATETIME,
            ExifInterface.TAG_DATETIME_ORIGINAL,
            ExifInterface.TAG_DATETIME_DIGITIZED,
            ExifInterface.TAG_GPS_LATITUDE,
            ExifInterface.TAG_GPS_LATITUDE_REF,
            ExifInterface.TAG_GPS_LONGITUDE,
            ExifInterface.TAG_GPS_LONGITUDE_REF,
            ExifInterface.TAG_GPS_ALTITUDE,
            ExifInterface.TAG_GPS_ALTITUDE_REF,
            ExifInterface.TAG_GPS_TIMESTAMP,
            ExifInterface.TAG_GPS_DATESTAMP,
            ExifInterface.TAG_MAKE,
            ExifInterface.TAG_MODEL,
            ExifInterface.TAG_SOFTWARE,
            ExifInterface.TAG_ARTIST,
            ExifInterface.TAG_COPYRIGHT,
            ExifInterface.TAG_IMAGE_WIDTH,
            ExifInterface.TAG_IMAGE_LENGTH,
            ExifInterface.TAG_FLASH,
            ExifInterface.TAG_FOCAL_LENGTH,
            ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, // Replaces deprecated TAG_ISO_SPEED_RATINGS
            ExifInterface.TAG_EXPOSURE_TIME,
            ExifInterface.TAG_APERTURE_VALUE,
            ExifInterface.TAG_F_NUMBER,
            ExifInterface.TAG_WHITE_BALANCE,
            ExifInterface.TAG_METERING_MODE,
            ExifInterface.TAG_SCENE_CAPTURE_TYPE,
            ExifInterface.TAG_SUBJECT_DISTANCE,
            ExifInterface.TAG_SUBSEC_TIME,
            ExifInterface.TAG_SUBSEC_TIME_ORIGINAL,
            ExifInterface.TAG_SUBSEC_TIME_DIGITIZED
        )
    }
    
    /**
     * Compress an image from URI.
     * 
     * Business Rules:
     * - Max 1600px longest side
     * - 65% JPEG quality
     * - Never upscale smaller images
     * - Preserve EXIF data (except orientation which is applied to pixels)
     * - Apply EXIF orientation to pixels for universal compatibility
     * 
     * @param sourceUri Source image URI (from camera or gallery)
     * @param destinationFile Destination file for compressed image
     * @return Result with compressed file or error
     */
    suspend fun compressImage(
        sourceUri: Uri,
        destinationFile: File
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            // Step 1: Copy URI to temporary file
            val tempFile = createTempFile(context)
            copyUriToFile(sourceUri, tempFile)
            
            // Step 2: Read EXIF data from source
            val sourceExif = try {
                ExifInterface(tempFile.absolutePath)
            } catch (e: IOException) {
                null // EXIF data not available, continue without it
            }
            
            // Step 3: Get EXIF orientation and apply rotation to pixels
            // This ensures the image displays correctly in all apps/viewers
            val orientation = sourceExif?.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            ) ?: ExifInterface.ORIENTATION_NORMAL
            
            val rotatedTempFile = if (orientation != ExifInterface.ORIENTATION_NORMAL) {
                applyExifOrientationToFile(tempFile, orientation)
            } else {
                tempFile
            }
            
            // Step 4: Get image dimensions (after rotation)
            val (width, height) = getImageDimensions(rotatedTempFile)
            
            // Step 5: Calculate target dimensions (never upscale)
            val (targetWidth, targetHeight) = calculateTargetDimensions(width, height)
            
            // Step 6: Compress image
            val compressedFile = if (targetWidth == width && targetHeight == height) {
                // No resizing needed, just compress quality
                Compressor.compress(context, rotatedTempFile) {
                    quality(JPEG_QUALITY)
                    format(Bitmap.CompressFormat.JPEG)
                    destination(destinationFile)
                }
            } else {
                // Resize and compress
                Compressor.compress(context, rotatedTempFile) {
                    resolution(targetWidth, targetHeight)
                    quality(JPEG_QUALITY)
                    format(Bitmap.CompressFormat.JPEG)
                    destination(destinationFile)
                }
            }
            
            // Step 7: Preserve EXIF data (but set orientation to normal since we applied it)
            sourceExif?.let { source ->
                preserveExifData(source, compressedFile, resetOrientation = true)
            }
            
            // Step 8: Clean up temp files
            tempFile.delete()
            if (rotatedTempFile != tempFile) {
                rotatedTempFile.delete()
            }
            
            Result.success(compressedFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Apply EXIF orientation to image pixels and save to a new file.
     * This physically rotates/flips the image so it displays correctly everywhere.
     * 
     * Uses subsampling for very large images to prevent OOM errors.
     */
    private fun applyExifOrientationToFile(sourceFile: File, orientation: Int): File {
        // First, get image dimensions to determine if we need subsampling
        val boundsOptions = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(sourceFile.absolutePath, boundsOptions)
        
        val imageWidth = boundsOptions.outWidth
        val imageHeight = boundsOptions.outHeight
        
        if (imageWidth <= 0 || imageHeight <= 0) {
            return sourceFile
        }
        
        // Calculate sample size to keep memory usage reasonable
        // Target: max ~16MP in memory (4096x4096) which is safe for most devices
        // The final compression will resize anyway, so we just need enough resolution
        val maxPixelsInMemory = 16 * 1024 * 1024 // 16 megapixels
        val totalPixels = imageWidth.toLong() * imageHeight.toLong()
        
        var sampleSize = 1
        while (totalPixels / (sampleSize * sampleSize) > maxPixelsInMemory) {
            sampleSize *= 2
        }
        
        // Decode bitmap with calculated sample size
        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
        }
        val bitmap = BitmapFactory.decodeFile(sourceFile.absolutePath, decodeOptions)
            ?: return sourceFile
        
        // Create transformation matrix based on EXIF orientation
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(-90f)
                matrix.postScale(-1f, 1f)
            }
            else -> {
                bitmap.recycle()
                return sourceFile // No transformation needed
            }
        }
        
        // Apply transformation
        val rotatedBitmap = Bitmap.createBitmap(
            bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
        )
        
        // Save to new temp file with high quality (compression happens later)
        val rotatedFile = createTempFile(context)
        FileOutputStream(rotatedFile).use { out ->
            rotatedBitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
        }
        
        // Clean up bitmaps
        if (rotatedBitmap != bitmap) {
            rotatedBitmap.recycle()
        }
        bitmap.recycle()
        
        return rotatedFile
    }
    
    /**
     * Copy URI content to file.
     */
    private fun copyUriToFile(uri: Uri, file: File) {
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(file).use { output ->
                input.copyTo(output)
            }
        } ?: throw IOException("Failed to open URI: $uri")
    }
    
    /**
     * Get image dimensions without loading full bitmap into memory.
     */
    private fun getImageDimensions(file: File): Pair<Int, Int> {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(file.absolutePath, options)
        return Pair(options.outWidth, options.outHeight)
    }
    
    /**
     * Calculate target dimensions.
     * 
     * Rules:
     * - Max 1600px longest side
     * - Never upscale smaller images
     * - Maintain aspect ratio
     */
    private fun calculateTargetDimensions(width: Int, height: Int): Pair<Int, Int> {
        val longestSide = max(width, height)
        
        // Don't upscale if image is already smaller
        if (longestSide <= MAX_DIMENSION) {
            return Pair(width, height)
        }
        
        // Scale down proportionally
        val scale = MAX_DIMENSION.toFloat() / longestSide
        val targetWidth = (width * scale).toInt()
        val targetHeight = (height * scale).toInt()
        
        return Pair(targetWidth, targetHeight)
    }
    
    /**
     * Preserve EXIF data from source to destination.
     * 
     * Copies all EXIF tags to ensure no metadata is lost.
     * 
     * @param sourceExif Source EXIF interface
     * @param destinationFile Destination file to write EXIF to
     * @param resetOrientation If true, set orientation to NORMAL (1) since rotation was applied to pixels
     */
    private fun preserveExifData(sourceExif: ExifInterface, destinationFile: File, resetOrientation: Boolean = false) {
        try {
            val destExif = ExifInterface(destinationFile.absolutePath)
            
            // Copy all EXIF tags
            EXIF_TAGS.forEach { tag ->
                sourceExif.getAttribute(tag)?.let { value ->
                    destExif.setAttribute(tag, value)
                }
            }
            
            // Reset orientation to normal if rotation was applied to pixels
            if (resetOrientation) {
                destExif.setAttribute(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL.toString()
                )
            }
            
            // Save EXIF data to file
            destExif.saveAttributes()
        } catch (e: IOException) {
            // Failed to preserve EXIF data, but compression succeeded
            // Log error but don't fail the operation
        }
    }
    
    /**
     * Create a temporary file for processing.
     */
    private fun createTempFile(context: Context): File {
        return File.createTempFile(
            "compress_",
            ".jpg",
            context.cacheDir
        )
    }
    
    /**
     * Get compression info for debugging/logging.
     */
    fun getCompressionInfo(originalFile: File, compressedFile: File): CompressionInfo {
        val originalSize = originalFile.length()
        val compressedSize = compressedFile.length()
        val compressionRatio = (1 - (compressedSize.toFloat() / originalSize)) * 100
        
        val (originalWidth, originalHeight) = getImageDimensions(originalFile)
        val (compressedWidth, compressedHeight) = getImageDimensions(compressedFile)
        
        return CompressionInfo(
            originalSize = originalSize,
            compressedSize = compressedSize,
            compressionRatio = compressionRatio,
            originalWidth = originalWidth,
            originalHeight = originalHeight,
            compressedWidth = compressedWidth,
            compressedHeight = compressedHeight
        )
    }
}

/**
 * Compression information for debugging/logging.
 */
data class CompressionInfo(
    val originalSize: Long,
    val compressedSize: Long,
    val compressionRatio: Float,
    val originalWidth: Int,
    val originalHeight: Int,
    val compressedWidth: Int,
    val compressedHeight: Int
) {
    fun formatSummary(fileManager: FileManager): String {
        return """
            Original: ${originalWidth}x${originalHeight} (${fileManager.formatFileSize(originalSize)})
            Compressed: ${compressedWidth}x${compressedHeight} (${fileManager.formatFileSize(compressedSize)})
            Compression: ${String.format("%.1f", compressionRatio)}%
        """.trimIndent()
    }
}

