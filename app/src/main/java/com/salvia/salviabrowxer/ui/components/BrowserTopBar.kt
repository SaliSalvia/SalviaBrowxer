package com.salvia.salviabrowxer.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.salvia.salviabrowxer.R
import com.salvia.salviabrowxer.ui.theme.Gold

@Composable
fun BrowserTopBar(
    url: String,
    isLoading: Boolean,
    isSecure: Boolean,
    canGoBack: Boolean,
    canGoForward: Boolean,
    onUrlChange: (String) -> Unit,
    onBackClick: () -> Unit,
    onForwardClick: () -> Unit,
    onRefreshClick: () -> Unit,
    onStopClick: () -> Unit,
    onUrlSubmit: (String) -> Unit
) {
    var urlText by remember { mutableStateOf(TextFieldValue(url)) }
    var isUrlFocused by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onBackClick,
            enabled = canGoBack
        ) {
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = stringResource(R.string.go_back),
                tint = if (canGoBack) Gold else Color.Gray
            )
        }

        IconButton(
            onClick = onForwardClick,
            enabled = canGoForward
        ) {
            Icon(
                imageVector = Icons.Default.ArrowForward,
                contentDescription = stringResource(R.string.go_forward),
                tint = if (canGoForward) Gold else Color.Gray
            )
        }

        IconButton(
            onClick = if (isLoading) onStopClick else onRefreshClick
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = if (isLoading) stringResource(R.string.stop) else stringResource(R.string.reload),
                tint = Gold
            )
        }

        if (isSecure) {
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = "Secure connection",
                tint = Gold,
                modifier = Modifier.padding(start = 4.dp)
            )
        }

        BasicTextField(
            value = urlText,
            onValueChange = { newValue ->
                urlText = newValue
                if (!isUrlFocused) {
                    onUrlChange(newValue.text)
                }
            },
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
                .clip(MaterialTheme.shapes.small),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = Gold),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(Gold),
            decorationBox = { innerTextField ->
                if (urlText.text.isEmpty() && !isUrlFocused) {
                    android.widget.Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
                innerTextField()
            },
            keyboardOptions = androidx.compose.ui.text.input.KeyboardOptions(
                imeAction = androidx.compose.ui.text.input.ImeAction.Go
            ),
            keyboardActions = androidx.compose.ui.text.input.KeyboardActions(
                onGo = {
                    onUrlSubmit(urlText.text)
                }
            )
        )

        if (urlText.text.isNotEmpty()) {
            IconButton(
                onClick = {
                    urlText = TextFieldValue("")
                    onUrlChange("")
                }
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Clear",
                    tint = Color.Gray
                )
            }
        }
    }
}