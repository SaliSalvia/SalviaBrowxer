package com.salvia.salviabrowxer.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.salvia.salviabrowxer.ui.theme.AuroraTeal
import com.salvia.salviabrowxer.ui.theme.AuroraTealDeep
import com.salvia.salviabrowxer.ui.theme.AuroraTealLight
import com.salvia.salviabrowxer.ui.theme.CharcoalBorder
import com.salvia.salviabrowxer.ui.theme.CharcoalElevated
import com.salvia.salviabrowxer.ui.theme.NebulaViolet
import com.salvia.salviabrowxer.ui.theme.PearlWhite
import kotlin.math.roundToInt

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

    // Allocate gradients once — recreating brushes on every recomposition causes GC churn
    // and dropped frames while dragging the button.
    val activeBrush = remember {
        Brush.radialGradient(colors = listOf(AuroraTealLight, AuroraTeal, AuroraTealDeep))
    }
    val inactiveBrush = remember {
        Brush.radialGradient(colors = listOf(Color(0xFF3E3E46), Color(0xFF2B2B32), Color(0xFF1E1E24)))
    }

    val scale by animateFloatAsState(
        targetValue = if (isMediaDetected) 1.06f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
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
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val r = size.minDimension / 2f
            drawCircle(brush = if (isMediaDetected) activeBrush else inactiveBrush, radius = r * scale)
            // Subtle pearl rim
            drawCircle(color = Color.White.copy(alpha = 0.12f), radius = r, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.2.dp.toPx()))
            // Specular highlight
            drawCircle(color = Color.White.copy(alpha = if (isMediaDetected) 0.18f else 0.08f), radius = r * 0.45f, center = center.copy(x = center.x - r * 0.18f, y = center.y - r * 0.22f))
        }

        Icon(
            imageVector = Icons.Default.Download,
            contentDescription = "Download",
            tint = if (isMediaDetected) Color.White else PearlWhite.copy(alpha = 0.88f),
            modifier = Modifier.size(25.dp)
        )

        if (isMediaDetected && mediaCount > 0) {
            Box(
                modifier = Modifier.align(Alignment.TopEnd).offset(x = 3.dp, y = (-3).dp).size(19.dp).clip(CircleShape)
                    .background(Brush.radialGradient(colors = listOf(NebulaViolet, NebulaViolet.copy(alpha = 0.85f))))
                    .shadow(4.dp, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(text = if (mediaCount > 9) "9+" else mediaCount.toString(), color = Color.White, style = MaterialTheme.typography.labelSmall, modifier = Modifier.offset(y = 0.5.dp))
            }
        }
    }
}
