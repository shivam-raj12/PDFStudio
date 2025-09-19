package com.shivam_raj.pdfstudio.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.shivam_raj.pdfstudio.data.PdfPageItem
import com.shivam_raj.pdfstudio.ui.editorscreen.EditorViewModel
import kotlinx.coroutines.launch


@Composable
fun FullScreenPageView(
    pageItem: PdfPageItem,
    viewModel: EditorViewModel, // ViewModel needed to load high-res image
    onClose: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var fullBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var isLoadingBitmap by remember { mutableStateOf(true) }

    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(pageItem.id) {
        isLoadingBitmap = true
        fullBitmap = null // Clear previous image
        scale = 1f      // Reset zoom
        offset = Offset.Zero // Reset pan
        coroutineScope.launch {
            // Load high-resolution image using the ViewModel
            fullBitmap = viewModel.loadPagePreview(pageItem, highRes = true)?.asImageBitmap()
            isLoadingBitmap = false
        }
    }

    // Use a Box to overlay this view. The AnimatedVisibility in EditorScreen handles its appearance.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.85f)) // Dimming effect
            .clickable( // Click on the scrim to close (optional)
                interactionSource = remember { MutableInteractionSource() },
                indication = null, // No ripple for this background click
                onClick = onClose
            )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f) // Adjust size as preferred
                .fillMaxHeight(0.85f)
                .align(Alignment.Center)
                .pointerInput(Unit) { // Gesture input for zoom and pan
                    detectTransformGestures { _, pan, zoomChange, _ ->
                        scale = (scale * zoomChange).coerceIn(0.5f, 5f) // Limit zoom scale

                        // Simple pan logic, can be improved with boundary checks based on scaled image size
                        val newOffsetX = offset.x + pan.x
                        val newOffsetY = offset.y + pan.y
                        offset = Offset(newOffsetX, newOffsetY)
                    }
                },
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clipToBounds(), // Important to clip the content during zoom/pan
                contentAlignment = Alignment.Center
            ) {
                if (isLoadingBitmap) {
                    CircularProgressIndicator()
                } else if (fullBitmap != null) {
                    Image(
                        bitmap = fullBitmap!!,
                        contentDescription = "Full Page View of ${pageItem.originalPageIndex + 1}",
                        modifier = Modifier
                            .graphicsLayer( // Apply transformations
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offset.x,
                                translationY = offset.y
                            )
                            .fillMaxSize(), // Image will fill the Box, then transformations are applied
                        contentScale = ContentScale.Fit // Fit the image within its bounds initially
                    )
                } else {
                    Text(
                        "Preview not available.",
                        modifier = Modifier.align(Alignment.Center),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Close button for the full-screen view
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                    colors = IconButtonDefaults.iconButtonColors(
                        // Semi-transparent background for better visibility if over image content
                        containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp).copy(alpha = 0.5f),
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Close full screen view")
                }
            }
        }
    }
}
