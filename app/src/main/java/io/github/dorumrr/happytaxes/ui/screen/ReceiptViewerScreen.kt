package io.github.dorumrr.happytaxes.ui.screen

import android.content.Context
import android.content.SharedPreferences
import android.os.Environment
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import io.github.dorumrr.happytaxes.ui.theme.Spacing
import io.github.dorumrr.happytaxes.ui.theme.Alpha
import io.github.dorumrr.happytaxes.ui.theme.Dimensions
import io.github.dorumrr.happytaxes.ui.util.rememberDebouncedNavigation
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Receipt viewer screen with zoom/pan and arrow navigation.
 *
 * Features:
 * - Full-screen receipt viewer
 * - Pinch to zoom (1x to 5x)
 * - Pan/drag when zoomed
 * - Navigate between multiple receipts with arrow buttons
 * - Delete receipt option
 * - Rotation (90° increments)
 *
 * PRD Compliance:
 * - Section 4.15: Preview/Gallery View – Navigate through receipts, pinch to zoom
 * - Section 4.15: Rotation – 90° increments (only editing allowed)
 * - Section 4.15: No cropping, filters, or other edits
 *
 * Implementation Note:
 * - Arrow buttons used instead of swipe gestures to avoid conflicts with zoom/pan gestures
 * - Arrows auto-hide when at first/last receipt or when only one receipt exists
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptViewerScreen(
    receiptPaths: List<String>,
    initialIndex: Int = 0,
    onNavigateBack: () -> Unit,
    onDeleteReceipt: ((Int) -> Unit)? = null
) {
    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, receiptPaths.size - 1),
        pageCount = { receiptPaths.size }
    )

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    
    // SharedPreferences for persisting rotation angles
    val rotationPrefs = remember {
        context.getSharedPreferences("receipt_rotations", Context.MODE_PRIVATE)
    }

    var showDeleteDialog by remember { mutableStateOf(false) }
    var showDownloadDialog by remember { mutableStateOf(false) }

    // Debounced navigation to prevent double-clicks
    val debouncedBack = rememberDebouncedNavigation(onNavigate = onNavigateBack)

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (receiptPaths.size > 1) {
                            "Receipt ${pagerState.currentPage + 1} of ${receiptPaths.size}"
                        } else {
                            "Receipt"
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = debouncedBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    // Download button
                    IconButton(onClick = { showDownloadDialog = true }) {
                        Icon(
                            Icons.Default.Download,
                            contentDescription = "Download receipt",
                            tint = Color.White
                        )
                    }

                    // Delete button (if callback provided)
                    if (onDeleteReceipt != null) {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Remove receipt",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        },
        containerColor = Color.Black
    ) { padding ->
        if (receiptPaths.isEmpty()) {
            // Empty state
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No receipts to display",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // Receipt pager
                HorizontalPager(
                    state = pagerState,
                    userScrollEnabled = false,  // Disable swipe, use arrows instead
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    ZoomableReceipt(
                        receiptPath = receiptPaths[page],
                        rotationPrefs = rotationPrefs
                    )
                }

                // Navigation arrows (only show if multiple receipts)
                if (receiptPaths.size > 1) {
                    // Previous arrow (left side)
                    if (pagerState.currentPage > 0) {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                }
                            },
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .padding(Spacing.medium)
                        ) {
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = Color.Black.copy(alpha = Alpha.MEDIUM_HIGH),
                                modifier = Modifier.size(Dimensions.iconExtraLarge)
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                    contentDescription = "Previous receipt",
                                    tint = Color.White,
                                    modifier = Modifier.padding(Spacing.small)
                                )
                            }
                        }
                    }

                    // Next arrow (right side)
                    if (pagerState.currentPage < receiptPaths.size - 1) {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                }
                            },
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(Spacing.medium)
                        ) {
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = Color.Black.copy(alpha = Alpha.MEDIUM_HIGH),
                                modifier = Modifier.size(Dimensions.iconExtraLarge)
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = "Next receipt",
                                    tint = Color.White,
                                    modifier = Modifier.padding(Spacing.small)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    
    // Delete confirmation dialog
    if (showDeleteDialog && onDeleteReceipt != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            icon = {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = {
                Text("Remove Attachment?")
            },
            text = {
                Text("This attachment will be removed from the transaction. The file will be permanently deleted when you save.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteReceipt(pagerState.currentPage)
                        showDeleteDialog = false
                        onNavigateBack()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Remove")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Download confirmation dialog
    if (showDownloadDialog) {
        AlertDialog(
            onDismissRequest = { showDownloadDialog = false },
            icon = {
                Icon(
                    Icons.Default.Download,
                    contentDescription = null
                )
            },
            title = {
                Text("Download Receipt")
            },
            text = {
                Text("Save this receipt to your Downloads folder?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        val currentReceiptPath = receiptPaths[pagerState.currentPage]
                        downloadReceiptToDownloads(context, currentReceiptPath)
                        showDownloadDialog = false
                    }
                ) {
                    Text("Download")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDownloadDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * Zoomable receipt image with pinch-to-zoom and pan gestures.
 *
 * Features:
 * - Pinch to zoom (1x to 5x)
 * - Pan when zoomed
 * - Double-tap to zoom (future enhancement)
 * - Rotation (90° increments)
 */
@Composable
private fun ZoomableReceipt(
    receiptPath: String,
    rotationPrefs: SharedPreferences,
    modifier: Modifier = Modifier
) {
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

    // Load persisted rotation angle for this receipt
    var rotation by remember(receiptPath) {
        mutableStateOf(rotationPrefs.getFloat(receiptPath, 0f))
    }

    // Reset zoom when receipt changes
    LaunchedEffect(receiptPath) {
        scale = 1f
        offsetX = 0f
        offsetY = 0f
    }
    
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        SubcomposeAsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(File(receiptPath))
                .crossfade(true)
                .build(),
            contentDescription = "Receipt",
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        // Update scale (1x to 5x)
                        scale = (scale * zoom).coerceIn(1f, 5f)

                        // Update offset (only when zoomed)
                        if (scale > 1f) {
                            offsetX += pan.x
                            offsetY += pan.y
                        } else {
                            // Reset offset when zoomed out
                            offsetX = 0f
                            offsetY = 0f
                        }
                    }
                }
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offsetX,
                    translationY = offsetY,
                    rotationZ = rotation
                ),
            contentScale = ContentScale.Fit,
            loading = {
                // Loading state
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = Color.White
                    )
                }
            },
            error = {
                // Error state
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Missing receipt",
                            tint = Color.White,
                            modifier = Modifier.size(Dimensions.componentMedium)
                        )
                        Spacer(Modifier.height(Spacing.medium))
                        Text(
                            text = "Receipt not found",
                            color = Color.White,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(Modifier.height(Spacing.small))
                        Text(
                            text = "The receipt file may have been deleted or moved",
                            color = Color.White.copy(alpha = Alpha.MEDIUM_HIGH),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        )
        
        // Rotation button (bottom-right corner)
        FloatingActionButton(
            onClick = {
                rotation = (rotation + 90f) % 360f
                // Persist rotation angle
                rotationPrefs.edit().putFloat(receiptPath, rotation).apply()
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(Spacing.medium)
        ) {
            Icon(
                Icons.AutoMirrored.Filled.RotateRight,
                contentDescription = "Rotate 90°"
            )
        }
        
        // Zoom indicator (top-center)
        if (scale > 1f) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(Spacing.medium),
                shape = MaterialTheme.shapes.small,
                color = Color.Black.copy(alpha = Alpha.MEDIUM_HIGH)
            ) {
                Text(
                    text = "${(scale * 100).toInt()}%",
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = Spacing.mediumSmall, vertical = Spacing.verySmall)
                )
            }
        }
    }
}

/**
 * Download receipt to Downloads folder.
 * Copies the receipt file from internal storage to public Downloads directory.
 */
private fun downloadReceiptToDownloads(context: Context, receiptPath: String) {
    try {
        val sourceFile = File(receiptPath)
        if (!sourceFile.exists()) {
            Toast.makeText(context, "Receipt file not found", Toast.LENGTH_SHORT).show()
            return
        }

        // Get Downloads directory
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!downloadsDir.exists()) {
            downloadsDir.mkdirs()
        }

        // Generate filename with timestamp to avoid conflicts
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val extension = sourceFile.extension
        val filename = "HappyTaxes_Receipt_$timestamp.$extension"

        val destFile = File(downloadsDir, filename)

        // Copy file
        FileInputStream(sourceFile).use { input ->
            FileOutputStream(destFile).use { output ->
                input.copyTo(output)
            }
        }

        Toast.makeText(context, "Receipt saved to Downloads/$filename", Toast.LENGTH_LONG).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Failed to download receipt: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

