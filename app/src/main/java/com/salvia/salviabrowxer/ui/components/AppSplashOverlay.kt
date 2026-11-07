package com.salvia.salviabrowxer.ui.components

import android.app.Activity
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.salvia.salviabrowxer.R
import com.salvia.salviabrowxer.ui.theme.AuroraCyan
import com.salvia.salviabrowxer.ui.theme.IrisViolet
import com.salvia.salviabrowxer.ui.theme.MatteCharcoal
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The welcome beat: a pure black sky, then the brand mark breathing in out of it — a faint
 * iridescent halo rising with it — one moment of stillness, and breathing out again. The whole
 * ceremony runs ~1.6 s, inside the one-to-two seconds the launch should cost, and it is composed
 * over the first real frame so the handoff from the system splash (also pure black) is seamless:
 * nothing pops, nothing flashes, the logo simply arrives and leaves.
 *
 * [onFinished] fires once the mark has fully faded, and the overlay removes itself — the app
 * underneath has been composed and measured the entire time, so home is already awake.
 */
@Composable
fun AppSplashOverlay(onFinished: () -> Unit) {
    val alpha = remember { Animatable(0f) }
    val scale = remember { Animatable(0.94f) }
    val glow = remember { Animatable(0f) }

    // "A completely black screen" means the whole screen: for the length of the ceremony the
    // system bars step out too, and step back in the moment the overlay leaves, so home meets
    // the user with its chrome already in place.
    val view = LocalView.current
    DisposableEffect(Unit) {
        val controller = WindowCompat.getInsetsController(
            (view.context as Activity).window,
            view
        )
        controller.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { controller.show(WindowInsetsCompat.Type.systemBars()) }
    }

    LaunchedEffect(Unit) {
        // Appear: the mark settles from slightly small to its true size on a long ease-out while
        // its opacity climbs, so it feels placed by hand rather than switched on.
        launch { scale.animateTo(1f, tween(760, easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f))) }
        launch { glow.animateTo(1f, tween(760, easing = FastOutSlowInEasing)) }
        alpha.animateTo(1f, tween(760, easing = FastOutSlowInEasing))
        // One breath of stillness at full presence.
        delay(220)
        // Disappear: a touch of growth as it dissolves, the way a light reads when it walks away.
        launch { scale.animateTo(1.05f, tween(620, easing = FastOutLinearInEasing)) }
        launch { glow.animateTo(0f, tween(620, easing = FastOutLinearInEasing)) }
        alpha.animateTo(0f, tween(620, easing = FastOutLinearInEasing))
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MatteCharcoal),
        contentAlignment = Alignment.Center
    ) {
        // The halo: the ribbon's violet and the starfield's cyan, one soft breath behind the mark.
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .aspectRatio(1f)
                .graphicsLayer { alpha = glow.value * 0.75f }
                .background(SplashHaloBrush, CircleShape)
        )
        Image(
            painter = painterResource(R.drawable.salvia_brand),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth(0.62f)
                .graphicsLayer {
                    alpha = alpha.value
                    scaleX = scale.value
                    scaleY = scale.value
                }
        )
    }
}

/** Violet core, cyan middle, nothing at the rim: the mark's own two lights, out of focus. */
private val SplashHaloBrush = Brush.radialGradient(
    colors = listOf(
        IrisViolet.copy(alpha = 0.30f),
        AuroraCyan.copy(alpha = 0.12f),
        Color.Transparent
    )
)
