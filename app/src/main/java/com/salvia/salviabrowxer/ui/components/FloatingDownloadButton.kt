package com.salvia.salviabrowxer.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.salvia.salviabrowxer.ui.theme.DownloadButtonActive
import com.salvia.salviabrowxer.ui.theme.DownloadButtonInactive
import com.salvia.salviabrowxer.ui.theme.FloatingButtonBackground
import com.salvia.salviabrowxer.ui.theme.FloatingButtonForeground
import com.salvia.salviabrowxer.ui.theme.MediaDetectedIndicator

@Composable
fun FloatingDownloadButton(
    isMediaDetected: Boolean,
    mediaCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    initialPosition: Offset = Offset(0f, 0f),
    onPositionChange: (Offset) -> Unit = {}
) {
    var position by remember { mutableStateOf(initialPosition) }
    var offsetX by remember { mutableFloatStateOf(initialPosition.x) }
    var offsetY by remember { mutableFloatStateOf(initialPosition.y) }

    val buttonColor by animateColorAsState(
        targetValue = if (isMediaDetected) DownloadButtonActive else DownloadButtonInactive,
        animationSpec = tween(durationMillis = 300),
        label = "downloadButtonColor"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    offsetX += dragAmount.x
                    offsetY += dragAmount.y
                    position = Offset(offsetX, offsetY)
                    onPositionChange(position)
                }
            }
    ) {
        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.toInt(), offsetY.toInt()) }
                .size(56.dp)
                .align(Alignment.BottomEnd)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.matchParentSize()) {
                drawCircle(
                    color = buttonColor,
                    radius = size.minDimension / 2
                )
                drawCircle(
                    color = FloatingButtonBackground.copy(alpha = 0.35f),
                    radius = size.minDimension / 2
                )
            }

            Icon(
                imageVector = Icons.Default.Download,
                contentDescription = "Download",
                tint = FloatingButtonForeground,
                modifier = Modifier.size(24.dp)
            )

            if (isMediaDetected && mediaCount > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 4.dp, y = (-4).dp)
                        .size(18.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.matchParentSize()) {
                        drawCircle(
                            color = MediaDetectedIndicator,
                            radius = size.minDimension / 2
                        )
                    }
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
}
