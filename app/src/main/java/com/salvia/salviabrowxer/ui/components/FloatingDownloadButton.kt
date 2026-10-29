package com.salvia.salviabrowxer.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.salvia.salviabrowxer.R
import com.salvia.salviabrowxer.ui.theme.AuroraTeal
import com.salvia.salviabrowxer.ui.theme.CharcoalElevated
import com.salvia.salviabrowxer.ui.theme.FabActiveBrushNebula
import com.salvia.salviabrowxer.ui.theme.FabInactiveBrushNebula
import com.salvia.salviabrowxer.ui.theme.NebulaViolet
import com.salvia.salviabrowxer.ui.theme.PearlWhite
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

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
    val haptics = LocalHapticFeedback.current
    val buttonSizePx = with(density) { buttonSize.toPx() }

    var offsetX by remember { mutableFloatStateOf(initialOffset.x) }
    var offsetY by remember { mutableFloatStateOf(initialOffset.y) }

    // Allocate gradients once — recreating brushes on every recomposition causes GC churn
    // and dropped frames while dragging the button.
    val activeBrush = remember { FabActiveBrushNebula }
    val inactiveBrush = remember { FabInactiveBrushNebula }

    // The switch-on moment: one springy pop, oversized then settled, so "the page has something
    // to save" is something the thumb feels before the eye reads it.
    val pop = remember { Animatable(1f) }
    LaunchedEffect(isMediaDetected) {
        if (isMediaDetected) {
            pop.snapTo(0.7f)
            pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
        } else {
            pop.animateTo(1f, tween(durationMillis = 180))
        }
    }

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
            // A detected button glows: the shadow itself turns teal, so the state is legible from
            // across the room without a single extra pixel of chrome.
            .shadow(
                elevation = if (isMediaDetected) 14.dp else 6.dp,
                shape = CircleShape,
                clip = false,
                ambientColor = AuroraTeal.copy(alpha = 0.55f),
                spotColor = AuroraTeal.copy(alpha = 0.75f)
            )
            .clip(CircleShape)
            .background(CharcoalElevated)
            .pointerInput(containerSize) {
                // A pickup tick, not a per-frame buzz: the drag is confirmed once, at the
                // moment the button leaves where it was pinned.
                detectDragGestures(
                    onDragStart = { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) },
                    onDragEnd = { onOffsetChanged(Offset(offsetX, offsetY)) }
                ) { change, dragAmount ->
                    change.consume()
                    val next = clamp(offsetX + dragAmount.x, offsetY + dragAmount.y)
                    offsetX = next.x; offsetY = next.y
                }
            }
            // Tap and long-press are one gesture detector so they cannot both fire; the drag above
            // consumes movement, and a press that turns into movement cancels the tap cleanly.
            .pointerInput(onClick, onLongClick) {
                detectTapGestures(
                    // The same cue for both: the thumb confirms the press, and the different
                    // action on screen is what tells tap and long-press apart.
                    onTap = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onClick()
                    },
                    onLongPress = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLongClick()
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        // The visual core moves as one unit: the pop spring scales the whole face, never just a part.
        Box(modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = pop.value; scaleY = pop.value }) {
            if (isMediaDetected) {
                DetectedPulseLayer(brush = activeBrush)
            } else {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val r = size.minDimension / 2f
                    drawCircle(brush = inactiveBrush, radius = r)
                    // A quiet rim on the idle state: present, not shouting.
                    drawCircle(
                        color = Color.White.copy(alpha = 0.16f),
                        radius = r,
                        style = Stroke(width = 1.dp.toPx())
                    )
                }
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

            // The count pops in with the same spring as the button itself, and leaves quietly.
            AnimatedVisibility(
                visible = isMediaDetected && mediaCount > 0,
                enter = scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)) + fadeIn(),
                exit = scaleOut() + fadeOut(),
                modifier = Modifier.align(Alignment.TopEnd)
            ) {
                Box(
                    modifier = Modifier.offset(x = 3.dp, y = (-3).dp).size(19.dp).clip(CircleShape)
                        .background(AuroraTeal)
                        .shadow(4.dp, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = if (mediaCount > 9) "9+" else mediaCount.toString(), color = Color.White, style = MaterialTheme.typography.labelSmall, modifier = Modifier.offset(y = 0.5.dp))
                }
            }
        }
    }
}

/**
 * The detected face of the button: a breathing gradient base, two ripple rings chasing each other
 * out to the rim, and a comet of light that circles the edge. Everything hangs off one phase so
 * the effects stay in step, and the layer is composed only while media is actually detected - an
 * idle button animates nothing and costs nothing.
 */
@Composable
private fun DetectedPulseLayer(brush: Brush) {
    val transition = rememberInfiniteTransition(label = "fabPulse")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 1800, easing = LinearEasing)),
        label = "fabPhase"
    )
    Canvas(modifier = Modifier.fillMaxSize()) {
        val r = size.minDimension / 2f
        // The base breathes gently inward and back, never past the rim.
        val breathe = 1f - 0.025f * (0.5f - 0.5f * sin(phase * 2.0 * PI).toFloat())
        drawCircle(brush = brush, radius = r * breathe)
        // Two ripples half a cycle apart: one is always rising while the other fades.
        for (i in 0..1) {
            val p = (phase + i * 0.5f) % 1f
            drawCircle(
                color = AuroraTeal.copy(alpha = (1f - p) * 0.5f),
                radius = r * (0.62f + 0.36f * p),
                style = Stroke(width = (1f + (1f - p) * 2f).dp.toPx())
            )
        }
        // The comet: a short arc on a rotating sweep gradient, a light that never stops circling.
        rotate(degrees = phase * 360f) {
            drawArc(
                brush = Brush.sweepGradient(listOf(Color.Transparent, AuroraTeal, NebulaViolet, Color.Transparent)),
                startAngle = -90f,
                sweepAngle = 110f,
                useCenter = false,
                style = Stroke(width = 2.5f.dp.toPx())
            )
        }
        // The rim stays readable above the ripples.
        drawCircle(color = AuroraTeal, radius = r, style = Stroke(width = 2.dp.toPx()))
    }
}
