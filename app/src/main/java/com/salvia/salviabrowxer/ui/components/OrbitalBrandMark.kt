package com.salvia.salviabrowxer.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.salvia.salviabrowxer.R

/**
 * Shared transparent app mark used on the splash screen and throughout the app.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun OrbitalBrandMark(
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
    animate: Boolean = false,
    glowIntensity: Float = 1f
) {
    Image(
        painter = painterResource(R.drawable.salvia_official_logo),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = modifier.size(size)
    )
}
