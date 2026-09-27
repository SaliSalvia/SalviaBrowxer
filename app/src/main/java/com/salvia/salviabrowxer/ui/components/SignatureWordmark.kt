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
 * Silver script signature wordmark "Salar Farzaneh" — the owner's signature,
 * pre-rendered at high resolution with a silver metallic gradient on a
 * transparent background, shown wherever the brand needs a personal touch:
 * settings header, about card and the browser splash.
 */
@Composable
fun SignatureWordmark(
    modifier: Modifier = Modifier,
    width: Dp = 220.dp,
    @Suppress("UNUSED_PARAMETER") animateDraw: Boolean = false
) {
    Image(
        painter = painterResource(R.drawable.salvia_signature),
        contentDescription = "Salar Farzaneh",
        contentScale = ContentScale.FillWidth,
        modifier = modifier.size(width = width, height = width * SIGNATURE_ASPECT)
    )
}

/** Height/width ratio of the generated signature artwork. */
const val SIGNATURE_ASPECT = 0.36f
