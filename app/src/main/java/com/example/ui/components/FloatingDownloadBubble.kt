package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CosmicViolet
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.MetallicGold
import com.example.ui.theme.NebulaMagenta
import com.example.ui.theme.ObsidianElevated
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Floating Magnetic Media Download Bubble:
 * - Tap to open the InShot-style Media Quality & Download Sheet.
 * - Long-press & drag anywhere over the browser viewport; snaps magnetically to the nearest
 *   horizontal display edge (left or right) when released.
 * - Glows with SalviaBrowxser's cosmic Violet/Cyan/Magenta aura + count badge when a main video is detected.
 */
@Composable
fun FloatingDownloadBubble(
    hasMedia: Boolean,
    mediaCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val bubbleSizeDp = 58.dp
        val edgeMarginDp = 12.dp
        val verticalMarginDp = 14.dp

        val bubbleSizePx = with(density) { bubbleSizeDp.toPx() }
        val edgeMarginPx = with(density) { edgeMarginDp.toPx() }
        val verticalMarginPx = with(density) { verticalMarginDp.toPx() }
        val maxWidthPx = with(density) { maxWidth.toPx() }
        val maxHeightPx = with(density) { maxHeight.toPx() }

        val minX = edgeMarginPx
        val maxX = (maxWidthPx - bubbleSizePx - edgeMarginPx).coerceAtLeast(minX)
        val minY = verticalMarginPx
        val maxY = (maxHeightPx - bubbleSizePx - verticalMarginPx).coerceAtLeast(minY)

        var initialized by remember { mutableStateOf(false) }
        var isDragging by remember { mutableStateOf(false) }
        val offsetX = remember { Animatable(maxX) }
        val offsetY = remember { Animatable((maxHeightPx * 0.72f).coerceIn(minY, maxY)) }

        LaunchedEffect(maxWidthPx, maxHeightPx) {
            if (!initialized && maxWidthPx > 0f) {
                offsetX.snapTo(maxX)
                offsetY.snapTo((maxHeightPx * 0.72f).coerceIn(minY, maxY))
                initialized = true
            } else if (initialized) {
                val snappedX = if (offsetX.value + bubbleSizePx / 2f < maxWidthPx / 2f) minX else maxX
                offsetX.snapTo(snappedX)
                offsetY.snapTo(offsetY.value.coerceIn(minY, maxY))
            }
        }

        val pulseTransition = rememberInfiniteTransition(label = "bubble_pulse")
        val glowScale by pulseTransition.animateFloat(
            initialValue = 1f,
            targetValue = if (hasMedia) 1.09f else 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 850),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "glow_scale",
        )
        val dragScale by animateFloatAsState(
            targetValue = if (isDragging) 1.15f else 1f,
            animationSpec = spring(dampingRatio = 0.65f, stiffness = 400f),
            label = "drag_scale",
        )

        val borderBrush = if (hasMedia) {
            Brush.sweepGradient(listOf(ElectricCyan, CosmicViolet, NebulaMagenta, MetallicGold, ElectricCyan))
        } else {
            Brush.linearGradient(listOf(CosmicViolet.copy(alpha = 0.5f), ElectricCyan.copy(alpha = 0.4f)))
        }

        val orbFill = if (hasMedia) {
            Brush.linearGradient(listOf(CosmicViolet, Color(0xFF4C1D95), Color(0xFF0E7490)))
        } else {
            Brush.linearGradient(listOf(ObsidianElevated.copy(alpha = 0.86f), Color(0xFF0E1022).copy(alpha = 0.86f)))
        }

        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.value.roundToInt(), offsetY.value.roundToInt()) }
                .size(bubbleSizeDp)
                .scale(dragScale * glowScale)
                .alpha(if (hasMedia || isDragging) 1f else 0.74f)
                .shadow(
                    elevation = if (hasMedia || isDragging) 14.dp else 6.dp,
                    shape = CircleShape,
                    ambientColor = if (hasMedia) NebulaMagenta else CosmicViolet,
                    spotColor = if (hasMedia) ElectricCyan else CosmicViolet,
                )
                .clip(CircleShape)
                .background(orbFill)
                .border(width = if (hasMedia) 2.5.dp else 1.5.dp, brush = borderBrush, shape = CircleShape)
                .pointerInput(maxX, maxY) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = {
                            isDragging = true
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            scope.launch {
                                val nextX = (offsetX.value + dragAmount.x).coerceIn(minX, maxX)
                                val nextY = (offsetY.value + dragAmount.y).coerceIn(minY, maxY)
                                offsetX.snapTo(nextX)
                                offsetY.snapTo(nextY)
                            }
                        },
                        onDragEnd = {
                            isDragging = false
                            val center = offsetX.value + bubbleSizePx / 2f
                            val targetEdgeX = if (center < maxWidthPx / 2f) minX else maxX
                            scope.launch {
                                offsetX.animateTo(
                                    targetValue = targetEdgeX,
                                    animationSpec = spring(dampingRatio = 0.68f, stiffness = 360f),
                                )
                            }
                        },
                        onDragCancel = {
                            isDragging = false
                            val center = offsetX.value + bubbleSizePx / 2f
                            val targetEdgeX = if (center < maxWidthPx / 2f) minX else maxX
                            scope.launch {
                                offsetX.animateTo(
                                    targetValue = targetEdgeX,
                                    animationSpec = spring(dampingRatio = 0.68f, stiffness = 360f),
                                )
                            }
                        },
                    )
                }
                .clickable(onClick = onClick)
                .testTag("media_downloader_button"),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Download,
                contentDescription = "حباب شناور تشخیص و دانلود مدیا",
                tint = if (hasMedia) MetallicGold else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(26.dp),
            )

            if (hasMedia && mediaCount > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(CircleShape)
                        .background(MetallicGold)
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                ) {
                    Text(
                        text = mediaCount.toString(),
                        color = Color(0xFF1A1100),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = JetBrainsMonoFontFamily,
                    )
                }
            }
        }
    }
}
