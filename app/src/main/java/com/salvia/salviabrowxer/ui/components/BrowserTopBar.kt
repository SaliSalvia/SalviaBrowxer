package com.salvia.salviabrowxer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.salvia.salviabrowxer.R
import com.salvia.salviabrowxer.ui.theme.AuroraTeal
import com.salvia.salviabrowxer.ui.theme.BlushPink
import com.salvia.salviabrowxer.ui.theme.CharcoalBorder
import com.salvia.salviabrowxer.ui.theme.CharcoalSurface
import com.salvia.salviabrowxer.ui.theme.NebulaEdge
import com.salvia.salviabrowxer.ui.theme.NebulaMist
import com.salvia.salviabrowxer.ui.theme.NebulaVioletLight
import com.salvia.salviabrowxer.ui.theme.PearlEdgeBrush
import com.salvia.salviabrowxer.ui.theme.PearlWhite
import com.salvia.salviabrowxer.ui.theme.SilverMid
import com.salvia.salviabrowxer.ui.theme.TopBarBrush

/**
 * Browser chrome, top half: history, the address field, and the media pill.
 *
 * Reload/stop deliberately lives in the bottom bar. Five buttons stacked next to a URL field
 * cannot all hold a 48 dp touch target on a 360 dp phone, and the field is the one control on
 * this bar that has to stay wide enough to read a URL in.
 *
 * The row is `heightIn(min = …)` rather than a fixed height so a 1.3x font scale grows the bar
 * instead of clipping the placeholder.
 */
@Composable
fun BrowserTopBar(
    url: String,
    isLoading: Boolean,
    isSecure: Boolean,
    canGoBack: Boolean,
    canGoForward: Boolean,
    progress: Int,
    onUrlChange: (String) -> Unit,
    onUrlSubmit: (String) -> Unit,
    onBackClick: () -> Unit,
    onForwardClick: () -> Unit,
    /** Detected media on this page. Zero hides the pill entirely. */
    mediaCount: Int = 0,
    onMediaClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    var input by remember { mutableStateOf(TextFieldValue(url)) }
    var isEditing by remember { mutableStateOf(false) }
    val fieldLabel = stringResource(R.string.address_field)

    LaunchedEffect(url, isEditing) {
        if (!isEditing && url != input.text) {
            input = TextFieldValue(text = url, selection = TextRange(url.length))
        }
    }

    fun submit() {
        val text = input.text
        if (text.isBlank()) return
        onUrlSubmit(text.trim())
        keyboardController?.hide()
    }

    Column(modifier = modifier.fillMaxWidth().background(TopBarBrush)) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // AutoMirrored arrows: in RTL the back arrow points right, which is what "back" means
            // to a right-to-left reader. A plain ArrowBack would keep pointing left.
            IconButton(onClick = onBackClick, enabled = canGoBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.go_back),
                    tint = if (canGoBack) PearlWhite else SilverMid.copy(alpha = 0.35f)
                )
            }
            IconButton(onClick = onForwardClick, enabled = canGoForward) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = stringResource(R.string.go_forward),
                    tint = if (canGoForward) PearlWhite else SilverMid.copy(alpha = 0.35f)
                )
            }
            // Nebula address capsule with pearl-iridescent focus rim
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(if (isEditing) PearlEdgeBrush else SolidColor(CharcoalBorder.copy(alpha = 0.65f)))
                    .padding(1.2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(23.dp))
                        .background(SolidColor(if (isEditing) NebulaMist else CharcoalSurface))
                        .padding(start = 12.dp, end = 2.dp, top = 2.dp, bottom = 2.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isSecure) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = AuroraTeal,
                                modifier = Modifier.padding(end = 6.dp).size(13.dp)
                            )
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            BasicTextField(
                                value = input,
                                onValueChange = { newValue -> input = newValue; onUrlChange(newValue.text) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onFocusChanged { isEditing = it.hasFocus }
                                    .semantics { contentDescription = fieldLabel },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium.copy(color = PearlWhite),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = { submit() }),
                                cursorBrush = Brush.horizontalGradient(colors = listOf(BlushPink, NebulaVioletLight, AuroraTeal)),
                                decorationBox = { inner ->
                                    if (input.text.isEmpty()) {
                                        Text(
                                            text = stringResource(R.string.search_or_type_url),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = SilverMid.copy(alpha = 0.75f)
                                        )
                                    }
                                    inner()
                                }
                            )
                        }
                        // The slot is always reserved. Its height therefore never changes with
                        // the field's content, and the capsule cannot jump when the user types.
                        if (input.text.isNotEmpty()) {
                            // No size override: the default IconButton keeps the 48 dp touch
                            // target while the glyph stays small enough for the capsule.
                            IconButton(onClick = { input = TextFieldValue(""); onUrlChange("") }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = stringResource(R.string.address_clear),
                                    tint = SilverMid,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        } else {
                            Spacer(Modifier.size(48.dp))
                        }
                    }
                }
            }
            if (mediaCount > 0) {
                MediaPill(count = mediaCount, onClick = onMediaClick)
            }
            IconButton(onClick = { submit() }) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = stringResource(R.string.address_go),
                    tint = PearlWhite
                )
            }
        }
        // Iridescent progress line — the pearl 7-color sweep
        if (isLoading) {
            LinearProgressIndicator(
                progress = { (progress.coerceIn(0, 100)) / 100f },
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = NebulaVioletLight,
                trackColor = NebulaEdge
            )
        }
    }
}

/**
 * The media affordance: a quiet pill that appears only when the page exposed something, showing
 * how many. It replaces the draggable floating button as the default way into the media tray.
 *
 * One focus target: the download glyph is decorative because the pill's description already
 * carries the count, so TalkBack does not say the same number twice.
 */
@Composable
private fun MediaPill(count: Int, onClick: () -> Unit) {
    val description = stringResource(R.string.media_pill_description, count)
    Box(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .widthIn(min = 48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(CharcoalSurface)
            .border(1.dp, AuroraTeal.copy(alpha = 0.45f), RoundedCornerShape(24.dp))
            .clickable(role = Role.Button, onClickLabel = description, onClick = onClick)
            .semantics { contentDescription = description }
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Download,
                contentDescription = null,
                tint = AuroraTeal,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = PearlWhite
            )
        }
    }
}
