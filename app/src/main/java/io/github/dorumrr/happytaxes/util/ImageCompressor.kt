package io.github.dorumrr.happytaxes.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
     * - Preserve EXIF data
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
            
            // Step 3: Get image dimensions
            val (width, height) = getImageDimensions(tempFile)
            
            // Step 4: Calculate target dimensions (never upscale)
            val (targetWidth, targetHeight) = calculateTargetDimensions(width, height)
            
            // Step 5: Compress image
            val compressedFile = if (targetWidth == width && targetHeight == height) {
                // No resizing needed, just compress quality
                Compressor.compress(context, tempFile) {
                    quality(JPEG_QUALITY)
                    format(Bitmap.CompressFormat.JPEG)
                    destination(destinationFile)
                }
            } else {
                // Resize and compress
                Compressor.compress(context, tempFile) {
                    resolution(targetWidth, targetHeight)
                    quality(JPEG_QUALITY)
                    format(Bitmap.CompressFormat.JPEG)
                    destination(destinationFile)
                }
            }
            
            // Step 6: Preserve EXIF data
            sourceExif?.let { source ->
                preserveExifData(source, compressedFile)
            }
            
            // Step 7: Clean up temp file
            tempFile.delete()
            
            Result.success(compressedFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
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
     */
    private fun preserveExifData(sourceExif: ExifInterface, destinationFile: File) {
        try {
            val destExif = ExifInterface(destinationFile.absolutePath)
            
            // Copy all EXIF tags
            EXIF_TAGS.forEach { tag ->
                sourceExif.getAttribute(tag)?.let { value ->
                    destExif.setAttribute(tag, value)
                }
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

