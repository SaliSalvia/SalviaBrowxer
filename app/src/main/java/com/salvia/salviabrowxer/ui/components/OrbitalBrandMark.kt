package com.salvia.salviabrowxer.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.salvia.salviabrowxer.ui.theme.OrbitBlue
import com.salvia.salviabrowxer.ui.theme.OrbitBlueLight
import com.salvia.salviabrowxer.ui.theme.OrbitGold
import com.salvia.salviabrowxer.ui.theme.OrbitGoldLight
import kotlin.math.cos
import kotlin.math.sin

/**
 * The SalviaBrowxer orbital mark: interlocking luminous rings (solar gold +
 * electric blue) swept around a deep black core — rendered live in Compose so
 * splash, headers and empty states share one brand mark with zero image assets.
 */
@Composable
fun OrbitalBrandMark(
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
    animate: Boolean = true,
    glowIntensity: Float = 1f
) {
    val transition = rememberInfiniteTransition(label = "orbital")
    val sweep by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 14000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbitalSweep"
    )

    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(size)) {
            val r = this.size.minDimension / 2f
            val stroke = r * 0.115f

            // Black core with a faint blue nebula glow
            drawCircle(color = Color.Black, radius = r)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        OrbitBlue.copy(alpha = 0.20f * glowIntensity),
                        Color.Transparent
                    ),
                    center = center,
                    radius = r * 0.85f
                ),
                radius = r * 0.85f
            )

            fun ringBrush(a: Color, b: Color, c: Color) = Brush.sweepGradient(
                colors = listOf(a, b, c, b, a),
                center = center
            )

            val goldBrush = ringBrush(OrbitGoldLight, OrbitGold, OrbitGoldLight)
            val blueBrush = ringBrush(OrbitBlueLight, OrbitBlue, OrbitBlueLight)

            // Three interleaved elliptical orbits — gold and blue crossing each other
            val tilts = listOf(-24f, 18f, 62f)
            tilts.forEachIndexed { index, tilt ->
                val isGold = index % 2 == 0
                rotate(degrees = tilt + if (animate) sweep * (if (isGold) 0.15f else -0.1f) else 0f) {
                    val rx = r * (0.94f - index * 0.10f)
                    val ry = r * (0.52f - index * 0.07f)
                    val ellipse = Path()
                    val steps = 72
                    for (i in 0..steps) {
                        val t = i * (2.0 * Math.PI / steps)
                        val x = center.x + rx * cos(t).toFloat()
                        val y = center.y + ry * sin(t).toFloat()
                        if (i == 0) ellipse.moveTo(x, y) else ellipse.lineTo(x, y)
                    }
                    ellipse.close()
                    drawPath(
                        path = ellipse,
                        brush = if (isGold) goldBrush else blueBrush,
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                    if (animate) {
                        val ang = Math.toRadians((sweep + index * 120f).toDouble())
                        val hx = center.x + rx * cos(ang).toFloat()
                        val hy = center.y + ry * sin(ang).toFloat()
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    (if (isGold) OrbitGoldLight else OrbitBlueLight).copy(alpha = 0.9f * glowIntensity),
                                    Color.Transparent
                                ),
                                center = Offset(hx, hy),
                                radius = stroke * 3.2f
                            ),
                            radius = stroke * 3.2f,
                            center = Offset(hx, hy)
                        )
                    }
                }
            }

            // Luminous rim
            drawCircle(
                color = Color.White.copy(alpha = 0.14f * glowIntensity),
                radius = r,
                style = Stroke(width = 1.4.dp.toPx())
            )
        }
    }
}
