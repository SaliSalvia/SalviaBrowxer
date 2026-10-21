package com.salvia.salviabrowxer.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.salvia.salviabrowxer.R
import com.salvia.salviabrowxer.feature.browser.AddressSuggestion
import com.salvia.salviabrowxer.feature.browser.AddressSuggestionSource
import com.salvia.salviabrowxer.ui.theme.AuroraTeal
import com.salvia.salviabrowxer.ui.theme.CharcoalBorder
import com.salvia.salviabrowxer.ui.theme.CharcoalSurface
import com.salvia.salviabrowxer.ui.theme.NebulaViolet
import com.salvia.salviabrowxer.ui.theme.PearlWhite
import com.salvia.salviabrowxer.ui.theme.SilverMid
import com.salvia.salviabrowxer.ui.theme.SurfaceField
import com.salvia.salviabrowxer.ui.theme.TopBarBrush

/**
 * Browser chrome, top half: navigation, the address field and a media tray shortcut.
 *
 * The local history/bookmark menu stays anchored to the address field, and the media count uses a
 * compact badge so neither competes with the URL on a narrow phone.
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
    addressSuggestions: List<AddressSuggestion> = emptyList(),
    currentTabId: String? = null,
    onSuggestionSelected: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    var input by remember { mutableStateOf(TextFieldValue(url)) }
    var isEditing by remember { mutableStateOf(false) }
    var hasEditedAddress by remember { mutableStateOf(false) }
    var observedTabId by remember { mutableStateOf(currentTabId) }
    val fieldLabel = stringResource(R.string.address_field)
    val fieldBorder by animateColorAsState(
        targetValue = if (isEditing) AuroraTeal.copy(alpha = 0.82f) else CharcoalBorder,
        label = "addressFieldBorder"
    )

    // Keep a focused draft after actual typing, but reflect redirects and tab changes when it is untouched.
    LaunchedEffect(url, currentTabId, isEditing) {
        val tabChanged = observedTabId != currentTabId
        if ((!isEditing || !hasEditedAddress || tabChanged) && url != input.text) {
            input = TextFieldValue(text = url, selection = TextRange(url.length))
        }
        if (!isEditing || tabChanged) hasEditedAddress = false
        if (tabChanged) observedTabId = currentTabId
    }

    fun submit(text: String = input.text) {
        val address = text.trim()
        if (address.isBlank()) return
        onUrlSubmit(address)
        focusManager.clearFocus()
        keyboardController?.hide()
    }

    Column(modifier = modifier.fillMaxWidth().background(TopBarBrush)) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick, enabled = canGoBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.go_back),
                    tint = if (canGoBack) PearlWhite else SilverMid.copy(alpha = 0.38f)
                )
            }
            IconButton(onClick = onForwardClick, enabled = canGoForward) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = stringResource(R.string.go_forward),
                    tint = if (canGoForward) PearlWhite else SilverMid.copy(alpha = 0.38f)
                )
            }

            BoxWithConstraints(modifier = Modifier.weight(1f).padding(horizontal = 4.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(17.dp))
                        .background(SurfaceField)
                        .border(1.dp, fieldBorder, RoundedCornerShape(17.dp))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .padding(start = 12.dp, end = 0.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isSecure) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = AuroraTeal,
                                modifier = Modifier.padding(end = 7.dp).size(15.dp)
                            )
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            BasicTextField(
                                value = input,
                                onValueChange = { newValue ->
                                    input = newValue
                                    hasEditedAddress = true
                                    onUrlChange(newValue.text)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onFocusChanged { focusState ->
                                        if (focusState.isFocused && !isEditing) {
                                            hasEditedAddress = false
                                            input = input.copy(selection = TextRange(0, input.text.length))
                                        }
                                        isEditing = focusState.hasFocus
                                    }
                                    .semantics { contentDescription = fieldLabel },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium.copy(color = PearlWhite),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = { submit() }),
                                cursorBrush = Brush.horizontalGradient(listOf(AuroraTeal, NebulaViolet)),
                                decorationBox = { inner ->
                                    if (input.text.isEmpty()) {
                                        Text(
                                            text = stringResource(R.string.search_or_type_url),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = SilverMid.copy(alpha = 0.82f)
                                        )
                                    }
                                    inner()
                                }
                            )
                        }
                        // Keep the trailing slot stable so the address capsule never jumps as the user types.
                        if (input.text.isNotEmpty()) {
                            IconButton(onClick = {
                                input = TextFieldValue("")
                                hasEditedAddress = true
                                onUrlChange("")
                            }) {
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

                DropdownMenu(
                    expanded = isEditing && addressSuggestions.isNotEmpty(),
                    onDismissRequest = { focusManager.clearFocus() },
                    modifier = Modifier.width(maxWidth).heightIn(max = 280.dp)
                ) {
                    addressSuggestions.forEach { suggestion ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(
                                        text = suggestion.title,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = PearlWhite,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = suggestion.url,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = SilverMid,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (suggestion.source == AddressSuggestionSource.BOOKMARK) {
                                        Icons.Default.Bookmark
                                    } else {
                                        Icons.Default.History
                                    },
                                    contentDescription = null,
                                    tint = AuroraTeal,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            onClick = {
                                input = TextFieldValue(suggestion.url, TextRange(suggestion.url.length))
                                onSuggestionSelected(suggestion.url)
                                focusManager.clearFocus()
                                keyboardController?.hide()
                            }
                        )
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
        if (isLoading) {
            LinearProgressIndicator(
                progress = { (progress.coerceIn(0, 100)) / 100f },
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = AuroraTeal,
                trackColor = CharcoalSurface
            )
        }
    }
}

/** The media shortcut appears only when the current page has a real candidate. */
@Composable
private fun MediaPill(count: Int, onClick: () -> Unit) {
    val description = stringResource(R.string.media_pill_description, count)
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(CharcoalSurface)
            .border(1.dp, AuroraTeal.copy(alpha = 0.5f), RoundedCornerShape(15.dp))
            .clickable(role = Role.Button, onClickLabel = description, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Download,
            contentDescription = null,
            tint = AuroraTeal,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = if (count > 9) "9+" else count.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = PearlWhite,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 3.dp, end = 4.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(NebulaViolet)
                .padding(horizontal = 3.dp, vertical = 1.dp)
        )
    }
}
