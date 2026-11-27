package com.example.ui.components

import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.ChromeSilver
import com.example.ui.theme.CosmicViolet
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.NebulaMagenta
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.SapphireBlue
import com.example.ui.theme.SapphireGlow
import com.example.ui.theme.StealthViolet

/**
 * Full-bleed deep cosmic space backdrop using the bespoke starfield nebula image,
 * optional large softly-blurred emblem watermark, and lightweight GPU radial prism glows
 * so the SalviaBrowxser UI floats inside empty starry space.
 */
@Composable
fun CosmicSpaceBackdrop(
    isIncognito: Boolean = false,
    showEmblemWatermark: Boolean = false,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.25f
    val accentPrimary = if (isIncognito) StealthViolet else SapphireBlue
    val accentSecondary = if (isIncognito) NebulaMagenta else ElectricCyan

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(if (isDark) ObsidianBg else MaterialTheme.colorScheme.background),
    ) {
        // The starfield is the browser's canvas in both themes: full strength on the dark
        // cosmic chrome, and a faint veil behind the light theme's light scrim so content
        // stays perfectly readable.
        Image(
            painter = painterResource(id = R.drawable.bg_cosmic_space),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .alpha(
                    when {
                        !isDark -> 0.20f
                        isIncognito -> 0.74f
                        else -> 0.94f
                    },
                ),
        )

        if (isDark) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawBehind {
                        drawRect(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    accentPrimary.copy(alpha = if (isIncognito) 0.20f else 0.16f),
                                    Color.Transparent,
                                ),
                                center = Offset(size.width * 0.5f, size.height * 0.20f),
                                radius = size.maxDimension * 0.56f,
                            ),
                        )
                        drawRect(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    accentSecondary.copy(alpha = 0.10f),
                                    Color.Transparent,
                                ),
                                center = Offset(size.width * 0.85f, size.height * 0.58f),
                                radius = size.maxDimension * 0.44f,
                            ),
                        )
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    ObsidianBg.copy(alpha = 0.34f),
                                    Color.Transparent,
                                    ObsidianBg.copy(alpha = 0.56f),
                                ),
                            ),
                        )
                    },
            )
        } else {
            // Light theme keeps the starfield as a subtle texture instead of a dark canvas.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.background.copy(alpha = 0.88f),
                                MaterialTheme.colorScheme.background.copy(alpha = 0.80f),
                                MaterialTheme.colorScheme.background.copy(alpha = 0.90f),
                            ),
                        ),
                    ),
            )
        }

        if (showEmblemWatermark) {
            val blurModifier = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Modifier.blur(2.4.dp)
            } else {
                Modifier
            }
            Image(
                painter = painterResource(id = R.drawable.salvia_emblem_transparent),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(0.95f)
                    .widthIn(max = 460.dp)
                    .aspectRatio(1f)
                    .then(blurModifier)
                    .alpha(
                        when {
                            !isDark -> 0.08f
                            isIncognito -> 0.11f
                            else -> 0.145f
                        },
                    ),
            )
        }

        content()
    }
}

@Composable
fun PrismHairline(
    isIncognito: Boolean = false,
    height: Dp = 1.dp,
    alpha: Float = 0.75f,
    modifier: Modifier = Modifier,
) {
    val colors = if (isIncognito) {
        listOf(
            StealthViolet.copy(alpha = alpha * 0.55f),
            NebulaMagenta.copy(alpha = alpha),
            ChromeSilver.copy(alpha = alpha * 0.65f),
            StealthViolet.copy(alpha = alpha * 0.55f),
        )
    } else {
        listOf(
            SapphireBlue.copy(alpha = alpha * 0.55f),
            ElectricCyan.copy(alpha = alpha),
            NebulaMagenta.copy(alpha = alpha * 0.85f),
            CosmicViolet.copy(alpha = alpha * 0.6f),
        )
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(Brush.horizontalGradient(colors)),
    )
}

@Composable
fun glassCardModifier(
    cornerRadius: Dp = 20.dp,
    isActive: Boolean = false,
    isIncognito: Boolean = false,
): Modifier {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.25f
    val shape = RoundedCornerShape(cornerRadius)
    val bgBrush = if (isDark) {
        Brush.verticalGradient(
            listOf(
                Color(0xFF0A1434).copy(alpha = if (isActive) 0.86f else 0.72f),
                ObsidianSurface.copy(alpha = if (isActive) 0.90f else 0.78f),
            ),
        )
    } else {
        Brush.verticalGradient(
            listOf(
                MaterialTheme.colorScheme.surfaceVariant,
                MaterialTheme.colorScheme.surface,
            ),
        )
    }

    val borderBrush = when {
        isActive && isIncognito -> Brush.linearGradient(listOf(StealthViolet, NebulaMagenta, ChromeSilver.copy(alpha = 0.7f)))
        isActive -> Brush.linearGradient(listOf(ElectricCyan, SapphireGlow, NebulaMagenta, ChromeSilver.copy(alpha = 0.7f)))
        isIncognito -> Brush.linearGradient(listOf(StealthViolet.copy(alpha = 0.42f), NebulaMagenta.copy(alpha = 0.28f)))
        else -> Brush.linearGradient(listOf(SapphireGlow.copy(alpha = 0.42f), ElectricCyan.copy(alpha = 0.28f), CosmicViolet.copy(alpha = 0.32f)))
    }

    return Modifier
        .clip(shape)
        .background(bgBrush)
        .border(width = if (isActive) 1.5.dp else 0.9.dp, brush = borderBrush, shape = shape)
}
