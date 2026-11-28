package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ui.PageLoadError
import com.example.ui.i18n.LocalAppStrings

/** Browser-owned error page for failed main-frame loads and renderer crashes. */
@Composable
fun BrowserErrorPage(
    error: PageLoadError,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = LocalAppStrings.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = if (error.isRendererCrash) Icons.Default.ErrorOutline else Icons.Default.CloudOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = if (error.isRendererCrash) s.errorPageCrashTitle else s.errorPageTitle,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        if (error.url.isNotBlank()) {
            Text(
                text = error.url,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
        }
        Text(
            text = error.description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRetry) { Text(s.errorPageRetry) }
    }
}

/** What the user long-pressed in a page. */
data class PageContextTarget(
    val linkUrl: String?,
    val imageUrl: String?,
)

/** Long-press menu for links and images, like the context menu of mobile Chrome/Firefox. */
@Composable
fun PageContextMenuDialog(
    target: PageContextTarget,
    isIncognito: Boolean,
    onOpenInNewTab: (String) -> Unit,
    onOpenInIncognitoTab: (String) -> Unit,
    onCopy: (String) -> Unit,
    onShare: (String) -> Unit,
    onDownload: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val s = LocalAppStrings.current
    val link = target.linkUrl
    val image = target.imageUrl
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = link ?: image.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (link != null) {
                    if (!isIncognito) {
                        MenuAction(s.ctxOpenInNewTab) { onOpenInNewTab(link) }
                    }
                    MenuAction(s.ctxOpenInIncognitoTab) { onOpenInIncognitoTab(link) }
                    MenuAction(s.ctxCopyLink) { onCopy(link) }
                    MenuAction(s.ctxShareLink) { onShare(link) }
                    MenuAction(s.ctxDownloadLink) { onDownload(link) }
                }
                if (image != null) {
                    MenuAction(s.ctxOpenImage) { if (isIncognito) onOpenInIncognitoTab(image) else onOpenInNewTab(image) }
                    MenuAction(s.ctxDownloadImage) { onDownload(image) }
                    MenuAction(s.ctxCopyImageLink) { onCopy(image) }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(s.dialogCancel) } },
    )
}

@Composable
private fun MenuAction(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
    }
}

/** Confirmation before handing a navigation to another app (Chrome-style "Open in app?"). */
@Composable
fun ExternalAppDialog(
    targetLabel: String,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
) {
    val s = LocalAppStrings.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(s.externalAppTitle) },
        text = { Text(String.format(s.externalAppBodyFormat, targetLabel)) },
        confirmButton = { Button(onClick = onOpen) { Text(s.externalAppOpen) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(s.dialogCancel) } },
    )
}
