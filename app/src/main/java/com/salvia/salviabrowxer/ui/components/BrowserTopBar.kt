package com.salvia.salviabrowxer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.salvia.salviabrowxer.R
import com.salvia.salviabrowxer.ui.theme.Gold

/**
 * Address bar. Typing edits local state, pressing Enter (or the search button) hands the raw text
 * to the ViewModel which decides between "navigate to URL" and "run a search".
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
    onRefreshClick: () -> Unit,
    onStopClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    var input by remember { mutableStateOf(TextFieldValue(url)) }
    var isEditing by remember { mutableStateOf(false) }

    // Keep the field in sync with the page URL, but never while the user is typing in it.
    androidx.compose.runtime.LaunchedEffect(url, isEditing) {
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

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBackClick,
                enabled = canGoBack,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = stringResource(R.string.go_back),
                    tint = if (canGoBack) Gold else Color.Gray
                )
            }

            IconButton(
                onClick = onForwardClick,
                enabled = canGoForward,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowForward,
                    contentDescription = stringResource(R.string.go_forward),
                    tint = if (canGoForward) Gold else Color.Gray
                )
            }

            IconButton(
                onClick = if (isLoading) onStopClick else onRefreshClick,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = if (isLoading) {
                        stringResource(R.string.stop)
                    } else {
                        stringResource(R.string.reload)
                    },
                    tint = Gold
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isSecure) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = Gold,
                            modifier = Modifier
                                .padding(end = 6.dp)
                                .size(14.dp)
                        )
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        BasicTextField(
                            value = input,
                            onValueChange = { newValue ->
                                input = newValue
                                onUrlChange(newValue.text)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .onFocusChanged { focusState -> isEditing = focusState.hasFocus }
                                .onPreviewKeyEvent { event ->
                                    if (event.key == Key.Enter && event.type == KeyEventType.KeyUp) {
                                        submit()
                                        true
                                    } else {
                                        false
                                    }
                                },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = Gold),
                            cursorBrush = SolidColor(Gold),
                            decorationBox = { innerTextField ->
                                if (input.text.isEmpty()) {
                                    Text(
                                        text = stringResource(R.string.search_or_type_url),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.Gray
                                    )
                                }
                                innerTextField()
                            }
                        )
                    }
                    if (input.text.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                input = TextFieldValue("")
                                onUrlChange("")
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.action_clear),
                                tint = Color.Gray,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            IconButton(
                onClick = { submit() },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = stringResource(R.string.settings_search_engine),
                    tint = Gold
                )
            }
        }

        if (isLoading) {
            LinearProgressIndicator(
                progress = { (progress.coerceIn(0, 100)) / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
                color = Gold,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
    }
}
