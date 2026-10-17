package com.salvia.salviabrowxer.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.salvia.salviabrowxer.R
import com.salvia.salviabrowxer.ui.theme.CharcoalElevated
import com.salvia.salviabrowxer.ui.theme.FabActiveBrushNebula
import com.salvia.salviabrowxer.ui.theme.FabInactiveBrushNebula
import com.salvia.salviabrowxer.ui.theme.AuroraTeal
import com.salvia.salviabrowxer.ui.theme.PearlWhite
import kotlin.math.roundToInt

@Composable
fun FloatingDownloadButton(
    isMediaDetected: Boolean,
    mediaCount: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    isMainReady: Boolean = false,
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

    // Allocate gradients once — recreating brushes on every recomposition causes GC churn
    // and dropped frames while dragging the button.
    val activeBrush = remember { FabActiveBrushNebula }
    val inactiveBrush = remember { FabInactiveBrushNebula }

    // A short, subtle scale cue feels immediate and avoids the over-bouncy download-FAB effect.
    val scale by animateFloatAsState(
        targetValue = if (isMediaDetected) 1.04f else 1f,
        animationSpec = tween(durationMillis = 150),
        label = "fabScale"
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
            .shadow(elevation = if (isMediaDetected) 12.dp else 6.dp, shape = CircleShape, clip = false)
            .clip(CircleShape)
            .background(CharcoalElevated)
            .pointerInput(containerSize) {
                detectDragGestures(onDragEnd = { onOffsetChanged(Offset(offsetX, offsetY)) }) { change, dragAmount ->
                    change.consume()
                    val next = clamp(offsetX + dragAmount.x, offsetY + dragAmount.y)
                    offsetX = next.x; offsetY = next.y
                }
            }
            // Tap and long-press are one gesture detector so they cannot both fire; the drag above
            // consumes movement, and a press that turns into movement cancels the tap cleanly.
            .pointerInput(onClick, onLongClick) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { onLongClick() }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val r = size.minDimension / 2f
            drawCircle(brush = if (isMediaDetected) activeBrush else inactiveBrush, radius = r * scale)
            // A solid status rim is easy to read and cheaper than a continuously shaded gloss effect.
            drawCircle(
                color = if (isMediaDetected) AuroraTeal else Color.White.copy(alpha = 0.16f),
                radius = r,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = (if (isMediaDetected) 2.dp else 1.dp).toPx())
            )
        }

        Icon(
            imageVector = Icons.Default.Download,
            contentDescription = if (isMainReady) {
                stringResource(R.string.fab_main_video_description)
            } else if (isMediaDetected && mediaCount > 0) {
                stringResource(R.string.media_button_description, mediaCount)
            } else {
                stringResource(R.string.media_button_description_empty)
            },
            tint = if (isMediaDetected) Color.White else PearlWhite.copy(alpha = 0.88f),
            modifier = Modifier.size(25.dp)
        )

        // The main-video mark: a quiet play glyph at the opposite corner from the count, present
        // only when the one-tap promise is actually backed by a selected candidate.
        if (isMainReady) {
            Box(
                modifier = Modifier.align(Alignment.BottomStart).offset(x = (-3).dp, y = 3.dp).size(14.dp).clip(CircleShape)
                    .background(AuroraTeal)
                    .shadow(3.dp, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(10.dp)
                )
            }
        }

        if (isMediaDetected && mediaCount > 0) {
            Box(
                modifier = Modifier.align(Alignment.TopEnd).offset(x = 3.dp, y = (-3).dp).size(19.dp).clip(CircleShape)
                    .background(AuroraTeal)
                    .shadow(4.dp, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(text = if (mediaCount > 9) "9+" else mediaCount.toString(), color = Color.White, style = MaterialTheme.typography.labelSmall, modifier = Modifier.offset(y = 0.5.dp))
            }
        }
    }
}
