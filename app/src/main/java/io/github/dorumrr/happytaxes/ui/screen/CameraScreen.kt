package io.github.dorumrr.happytaxes.ui.screen

import android.Manifest
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import io.github.dorumrr.happytaxes.ui.theme.Spacing
import io.github.dorumrr.happytaxes.ui.theme.Alpha
import io.github.dorumrr.happytaxes.ui.theme.Dimensions
import io.github.dorumrr.happytaxes.ui.util.rememberDebouncedNavigation
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import coil.compose.rememberAsyncImagePainter
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Flash mode for camera.
 */
enum class FlashMode {
    OFF, ON, AUTO;

    fun next(): FlashMode = when (this) {
        OFF -> ON
        ON -> AUTO
        AUTO -> OFF
    }

    fun toImageCaptureMode(): Int = when (this) {
        OFF -> ImageCapture.FLASH_MODE_OFF
        ON -> ImageCapture.FLASH_MODE_ON
        AUTO -> ImageCapture.FLASH_MODE_AUTO
    }
}

/**
 * Camera screen with enhanced features.
 *
 * Features:
 * - Flash toggle (OFF/ON/AUTO)
 * - Camera switch (front/back)
 * - Gallery import
 * - Capture preview (retake/use)
 * - Automatic device-driven focus
 * - Error handling
 * - Loading states
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraScreen(
    onImageCaptured: (Uri) -> Unit,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Permission state
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }

    // Camera permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
        if (!isGranted) {
            scope.launch {
                snackbarHostState.showSnackbar(
                    message = "Camera permission is required to take photos",
                    duration = SnackbarDuration.Long
                )
            }
        }
    }

    // Gallery picker launcher with validation
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { selectedUri ->
            // Validate image format
            val mimeType = context.contentResolver.getType(selectedUri)
            if (mimeType !in listOf(
                "image/jpeg",
                "image/jpg",
                "image/png",
                "image/heic",  // iPhone High Efficiency format
                "image/heif"   // High Efficiency Image Format
            )) {
                scope.launch {
                    snackbarHostState.showSnackbar(
                        message = "Unsupported image format. Please select JPEG, PNG, or HEIC.",
                        duration = SnackbarDuration.Long
                    )
                }
                return@let
            }

            // Validate image size (max 10MB)
            try {
                val size = context.contentResolver.openInputStream(selectedUri)?.use { stream ->
                    stream.available().toLong()
                } ?: 0L

                if (size > 10 * 1024 * 1024) {  // 10MB
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            message = "Image too large. Maximum size is 10MB.",
                            duration = SnackbarDuration.Long
                        )
                    }
                    return@let
                }
            } catch (e: Exception) {
                scope.launch {
                    snackbarHostState.showSnackbar(
                        message = "Failed to read image: ${e.message}",
                        duration = SnackbarDuration.Short
                    )
                }
                return@let
            }

            // Image is valid, proceed
            onImageCaptured(selectedUri)
        }
    }

    // Debounced navigation to prevent double-clicks
    val debouncedBack = rememberDebouncedNavigation(onNavigate = onNavigateBack)

    // Request permission on first launch
    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Take Receipt Photo") },
                navigationIcon = {
                    IconButton(onClick = debouncedBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (hasCameraPermission) {
            CameraPreview(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                onImageCaptured = onImageCaptured,
                onGalleryClick = { galleryLauncher.launch("image/*") },
                onError = { error ->
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            message = error,
                            duration = SnackbarDuration.Short
                        )
                    }
                }
            )
        } else {
            // Permission denied state
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Spacing.medium)
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = null,
                        modifier = Modifier.size(Dimensions.componentMedium),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Camera permission is required",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Grant permission to take receipt photos",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                        Text("Grant Permission")
                    }
                    TextButton(onClick = { galleryLauncher.launch("image/*") }) {
                        Text("Import from Gallery")
                    }
                }
            }
        }
    }
}

/**
 * Camera preview with enhanced controls.
 */
@Composable
private fun CameraPreview(
    modifier: Modifier = Modifier,
    onImageCaptured: (Uri) -> Unit,
    onGalleryClick: () -> Unit,
    onError: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    // Camera state
    var flashMode by remember { mutableStateOf(FlashMode.OFF) }
    var lensFacing by remember { mutableStateOf(CameraSelector.LENS_FACING_BACK) }
    var isCapturing by remember { mutableStateOf(false) }
    var capturedImageUri by remember { mutableStateOf<Uri?>(null) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }

    // Camera use cases
    val preview = remember { Preview.Builder().build() }
    val imageCapture = remember {
        ImageCapture.Builder()
            .setFlashMode(flashMode.toImageCaptureMode())
            .build()
    }
    val cameraSelector = remember(lensFacing) {
        CameraSelector.Builder()
            .requireLensFacing(lensFacing)
            .build()
    }

    // Initialize camera provider
    LaunchedEffect(Unit) {
        try {
            cameraProvider = context.getCameraProvider()
        } catch (e: Exception) {
            onError("Failed to initialize camera: ${e.message}")
        }
    }

    // Bind camera when provider or selector changes
    DisposableEffect(cameraProvider, cameraSelector, flashMode) {
        val provider = cameraProvider
        if (provider != null) {
            try {
                provider.unbindAll()

                // Update flash mode
                imageCapture.flashMode = flashMode.toImageCaptureMode()

                // Bind camera to lifecycle (autofocus handled automatically by device)
                provider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageCapture
                )
            } catch (e: Exception) {
                onError("Failed to start camera: ${e.message}")
            }
        }

        onDispose {
            provider?.unbindAll()
        }
    }



    // Show capture preview or camera preview
    if (capturedImageUri != null) {
        CapturePreview(
            imageUri = capturedImageUri!!,
            onRetake = {
                // Delete temp file
                try {
                    context.contentResolver.delete(capturedImageUri!!, null, null)
                } catch (e: Exception) {
                    // Ignore
                }
                capturedImageUri = null
            },
            onUsePhoto = {
                onImageCaptured(capturedImageUri!!)
            }
        )
    } else {
        Box(modifier = modifier) {
            // Camera preview (autofocus handled automatically by device)
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        preview.setSurfaceProvider(surfaceProvider)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Top controls (flash, camera switch)
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(Spacing.medium),
                horizontalArrangement = Arrangement.spacedBy(Spacing.small)
            ) {
                // Flash toggle
                IconButton(
                    onClick = { flashMode = flashMode.next() }
                ) {
                    Icon(
                        imageVector = when (flashMode) {
                            FlashMode.OFF -> Icons.Default.FlashOff
                            FlashMode.ON -> Icons.Default.FlashOn
                            FlashMode.AUTO -> Icons.Default.FlashAuto
                        },
                        contentDescription = "Flash: ${flashMode.name}",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Camera switch
                IconButton(
                    onClick = {
                        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                            CameraSelector.LENS_FACING_FRONT
                        } else {
                            CameraSelector.LENS_FACING_BACK
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.FlipCameraAndroid,
                        contentDescription = "Switch camera",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Bottom controls (gallery, capture)
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = Spacing.extraLarge)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Gallery button
                IconButton(
                    onClick = onGalleryClick,
                    modifier = Modifier.size(Dimensions.receiptThumbnail)
                ) {
                    Icon(
                        imageVector = Icons.Default.PhotoLibrary,
                        contentDescription = "Import from gallery",
                        modifier = Modifier.size(Spacing.extraLarge)
                    )
                }

                // Capture button
                FloatingActionButton(
                    onClick = {
                        if (!isCapturing) {
                            isCapturing = true
                            captureImage(
                                context = context,
                                imageCapture = imageCapture,
                                onImageCaptured = { uri ->
                                    capturedImageUri = uri
                                    isCapturing = false
                                },
                                onError = { error ->
                                    onError(error)
                                    isCapturing = false
                                }
                            )
                        }
                    },
                    modifier = Modifier.size(Dimensions.listItemHeight)
                ) {
                    if (isCapturing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(Spacing.extraLarge),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = "Capture",
                            modifier = Modifier.size(Spacing.extraLarge)
                        )
                    }
                }

                // Placeholder for symmetry
                Spacer(modifier = Modifier.size(Dimensions.receiptThumbnail))
            }
        }
    }
}



/**
 * Capture preview screen showing the captured image.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CapturePreview(
    imageUri: Uri,
    onRetake: () -> Unit,
    onUsePhoto: () -> Unit
) {
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("Preview") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black
                )
            )
        },
        containerColor = Color.Black
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Captured image
            Image(
                painter = rememberAsyncImagePainter(imageUri),
                contentDescription = "Captured receipt",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )

            // Bottom buttons
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = Spacing.extraLarge)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Retake button
                OutlinedButton(
                    onClick = onRetake,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color.White
                    )
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(Modifier.width(Spacing.small))
                    Text("Retake")
                }

                // Use photo button
                Button(
                    onClick = onUsePhoto
                ) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(Modifier.width(Spacing.small))
                    Text("Use Photo")
                }
            }
        }
    }
}

/**
 * Get camera provider asynchronously.
 */
private suspend fun Context.getCameraProvider(): ProcessCameraProvider =
    suspendCoroutine { continuation ->
        ProcessCameraProvider.getInstance(this).also { future ->
            future.addListener(
                { continuation.resume(future.get()) },
                ContextCompat.getMainExecutor(this)
            )
        }
    }

/**
 * Capture image and save to temp file.
 * Automatically retries once on failure (PRD Section 15.6).
 * Validates image size after capture to prevent OOM errors.
 */
private fun captureImage(
    context: Context,
    imageCapture: ImageCapture,
    onImageCaptured: (Uri) -> Unit,
    onError: (String) -> Unit,
    retryCount: Int = 0
) {
    val photoFile = File(
        context.cacheDir,
        "receipt_temp_${System.currentTimeMillis()}.jpg"
    )

    val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

    imageCapture.takePicture(
        outputOptions,
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                // Validate captured image size and dimensions
                val validationResult = validateCapturedImage(photoFile)
                
                if (validationResult.isSuccess) {
                    onImageCaptured(Uri.fromFile(photoFile))
                } else {
                    // Delete invalid image
                    photoFile.delete()
                    onError(validationResult.exceptionOrNull()?.message
                        ?: "Invalid image captured")
                }
            }

            override fun onError(exception: ImageCaptureException) {
                // Clean up failed capture
                photoFile.delete()
                
                // Retry once automatically (PRD requirement)
                if (retryCount < 1) {
                    captureImage(context, imageCapture, onImageCaptured, onError, retryCount + 1)
                } else {
                    onError("Failed to capture image: ${exception.message}")
                }
            }
        }
    )
}

/**
 * Validate captured image to prevent OOM errors.
 * Checks file size and image dimensions.
 *
 * @param file Captured image file
 * @return Result with success or validation error
 */
private fun validateCapturedImage(file: File): Result<Unit> {
    try {
        // Check file size (max 50MB - very generous for camera captures)
        val fileSize = file.length()
        val maxSize = 50L * 1024 * 1024 // 50MB
        
        if (fileSize > maxSize) {
            return Result.failure(
                Exception("Image too large (${fileSize / (1024 * 1024)}MB). Maximum size is 50MB.")
            )
        }
        
        // Check image dimensions to prevent OOM
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(file.absolutePath, options)
        
        val width = options.outWidth
        val height = options.outHeight
        
        // Check if dimensions are valid
        if (width <= 0 || height <= 0) {
            return Result.failure(
                Exception("Invalid image dimensions. Please try again.")
            )
        }
        
        // Check for extremely large dimensions that could cause OOM
        // Most phone cameras are 12-48MP, so 100MP is a safe upper limit
        val maxPixels = 100L * 1024 * 1024 // 100 megapixels
        val totalPixels = width.toLong() * height.toLong()
        
        if (totalPixels > maxPixels) {
            return Result.failure(
                Exception("Image resolution too high (${width}x${height}). Please try again.")
            )
        }
        
        return Result.success(Unit)
    } catch (e: Exception) {
        return Result.failure(
            Exception("Failed to validate image: ${e.message}")
        )
    }
}

