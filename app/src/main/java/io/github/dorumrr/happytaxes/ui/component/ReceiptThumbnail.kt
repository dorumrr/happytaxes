package io.github.dorumrr.happytaxes.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import io.github.dorumrr.happytaxes.ui.theme.CornerRadius
import io.github.dorumrr.happytaxes.ui.theme.Dimensions
import io.github.dorumrr.happytaxes.ui.theme.Spacing
import java.io.File

/**
 * Receipt thumbnail component.
 * 
 * Features:
 * - Displays receipt image thumbnail
 * - Shows loading state
 * - Handles missing/corrupted images
 * - Clickable to open full viewer
 * - Shows receipt count badge (if multiple)
 * 
 * PRD Compliance:
 * - Section 4.15: Preview/Gallery View
 * - Section 4.15: Multiple receipts per transaction
 */
@Composable
fun ReceiptThumbnail(
    receiptPath: String,
    modifier: Modifier = Modifier,
    size: Dp = Dimensions.receiptThumbnail,
    onClick: (() -> Unit)? = null,
    showBadge: Boolean = false,
    badgeCount: Int = 0
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CornerRadius.small)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                }
            )
    ) {
        SubcomposeAsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(File(receiptPath))
                .crossfade(true)
                .build(),
            contentDescription = "Receipt thumbnail",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            loading = {
                // Loading state
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(Dimensions.iconMedium),
                        strokeWidth = Dimensions.strokeStandard
                    )
                }
            },
            error = {
                // Error state (missing/corrupted image)
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.BrokenImage,
                        contentDescription = "Missing receipt",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(Dimensions.iconLarge)
                    )
                }
            }
        )

        // Badge for multiple receipts
        if (showBadge && badgeCount > 1) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(Spacing.extraSmall),
                shape = CornerRadius.medium,
                color = MaterialTheme.colorScheme.primary,
                tonalElevation = Spacing.extraExtraSmall
            ) {
                Text(
                    text = "+${badgeCount - 1}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(
                        horizontal = Spacing.verySmall,
                        vertical = Spacing.extraExtraSmall
                    )
                )
            }
        }
    }
}

/**
 * Receipt thumbnail grid for multiple receipts.
 * 
 * Displays up to 4 thumbnails in a grid, with a badge showing total count.
 */
@Composable
fun ReceiptThumbnailGrid(
    receiptPaths: List<String>,
    modifier: Modifier = Modifier,
    onReceiptClick: (Int) -> Unit = {}
) {
    if (receiptPaths.isEmpty()) {
        // Empty state
        Box(
            modifier = modifier
                .size(Dimensions.receiptThumbnail)
                .clip(CornerRadius.small)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Image,
                contentDescription = "No receipts",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(Dimensions.iconMedium)
            )
        }
    } else if (receiptPaths.size == 1) {
        // Single receipt
        ReceiptThumbnail(
            receiptPath = receiptPaths[0],
            modifier = modifier,
            onClick = { onReceiptClick(0) }
        )
    } else {
        // Multiple receipts - show first with badge
        ReceiptThumbnail(
            receiptPath = receiptPaths[0],
            modifier = modifier,
            onClick = { onReceiptClick(0) },
            showBadge = true,
            badgeCount = receiptPaths.size
        )
    }
}

/**
 * Receipt thumbnail list for transaction detail screen.
 * 
 * Displays all receipts in a horizontal scrollable list.
 */
@Composable
fun ReceiptThumbnailList(
    receiptPaths: List<String>,
    modifier: Modifier = Modifier,
    onReceiptClick: (Int) -> Unit = {},
    onReceiptRemove: ((Int) -> Unit)? = null
) {
    if (receiptPaths.isEmpty()) {
        // Empty state
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(Dimensions.receiptThumbnailGrid)
                .clip(CornerRadius.small)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Image,
                    contentDescription = "No receipts",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(Dimensions.iconLarge)
                )
                Spacer(Modifier.height(Spacing.small))
                Text(
                    text = "No receipts attached",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Spacing.small)
        ) {
            receiptPaths.forEachIndexed { index, path ->
                Box {
                    ReceiptThumbnail(
                        receiptPath = path,
                        size = Dimensions.receiptThumbnailGrid,
                        onClick = { onReceiptClick(index) }
                    )

                    // Remove button (if provided)
                    if (onReceiptRemove != null) {
                        IconButton(
                            onClick = { onReceiptRemove(index) },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(Dimensions.iconMedium)
                                .offset(x = Spacing.extraSmall, y = -Spacing.extraSmall)
                        ) {
                            Surface(
                                shape = CornerRadius.medium,
                                color = MaterialTheme.colorScheme.error,
                                tonalElevation = Spacing.extraExtraSmall
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Remove receipt",
                                    tint = MaterialTheme.colorScheme.onError,
                                    modifier = Modifier
                                        .padding(Spacing.extraSmall)
                                        .size(Dimensions.iconSmall)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

