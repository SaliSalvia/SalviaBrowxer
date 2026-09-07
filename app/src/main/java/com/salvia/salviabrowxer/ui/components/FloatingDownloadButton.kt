package com.salvia.salviabrowxer.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.salvia.salviabrowxer.ui.theme.DownloadButtonActive
import com.salvia.salviabrowxer.ui.theme.DownloadButtonInactive
import com.salvia.salviabrowxer.ui.theme.FloatingButtonBackground
import com.salvia.salviabrowxer.ui.theme.FloatingButtonForeground
import com.salvia.salviabrowxer.ui.theme.MediaDetectedIndicator
import kotlin.math.roundToInt

/**
 * Draggable floating download button.
 *
 * The composable occupies **only** the 56.dp circle - there is no transparent full-screen layer on
 * top of the WebView anymore, so page touches keep reaching the web content while the button stays
 * tappable and draggable. [containerSize] (the size of the parent area in pixels) is used to clamp
 * the drag so the button can never be dragged off screen.
 */
@Composable
fun FloatingDownloadButton(
    isMediaDetected: Boolean,
    mediaCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    buttonSize: Dp = 56.dp,
    containerSize: IntSize = IntSize.Zero,
    initialOffset: Offset = Offset.Zero,
    onOffsetChanged: (Offset) -> Unit = {}
) {
    val density = LocalDensity.current
    val buttonSizePx = with(density) { buttonSize.toPx() }

    var offsetX by remember { mutableFloatStateOf(initialOffset.x) }
    var offsetY by remember { mutableFloatStateOf(initialOffset.y) }

    val buttonColor by animateColorAsState(
        targetValue = if (isMediaDetected) DownloadButtonActive else DownloadButtonInactive,
        animationSpec = tween(durationMillis = 300),
        label = "downloadButtonColor"
    )

    fun clamp(x: Float, y: Float): Offset {
        if (containerSize.width <= 0 || containerSize.height <= 0) return Offset(x, y)
        val minX = -(containerSize.width - buttonSizePx).coerceAtLeast(0f)
        val minY = -(containerSize.height - buttonSizePx).coerceAtLeast(0f)
        return Offset(x.coerceIn(minX, 0f), y.coerceIn(minY, 0f))
    }

    Box(
        modifier = modifier
            .size(buttonSize)
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .clip(CircleShape)
            .background(FloatingButtonBackground)
            .pointerInput(containerSize) {
                detectDragGestures(
                    onDragEnd = { onOffsetChanged(Offset(offsetX, offsetY)) }
                ) { change, dragAmount ->
                    change.consume()
                    val next = clamp(offsetX + dragAmount.x, offsetY + dragAmount.y)
                    offsetX = next.x
                    offsetY = next.y
                }
            }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val radius = size.minDimension / 2f
            drawCircle(color = buttonColor, radius = radius)
            drawCircle(
                color = Color.Black.copy(alpha = 0.18f),
                radius = radius,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx())
            )
        }

        Icon(
            imageVector = Icons.Default.Download,
            contentDescription = "Download",
            tint = if (isMediaDetected) FloatingButtonForeground else Color.White.copy(alpha = 0.85f),
            modifier = Modifier.size(24.dp)
        )

        if (isMediaDetected && mediaCount > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 4.dp, y = (-4).dp)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(MediaDetectedIndicator),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (mediaCount > 9) "9+" else mediaCount.toString(),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.offset(y = 1.dp)
                )
            }
        }
    }
}
