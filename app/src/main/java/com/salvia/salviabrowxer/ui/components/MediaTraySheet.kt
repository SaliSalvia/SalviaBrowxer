package com.salvia.salviabrowxer.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.salvia.salviabrowxer.R
import com.salvia.salviabrowxer.core.model.MediaCandidate
import com.salvia.salviabrowxer.ui.theme.AuroraTeal
import com.salvia.salviabrowxer.ui.theme.CharcoalBorder
import com.salvia.salviabrowxer.ui.theme.CharcoalElevated
import com.salvia.salviabrowxer.ui.theme.CharcoalSurface
import com.salvia.salviabrowxer.ui.theme.PearlWhite
import com.salvia.salviabrowxer.ui.theme.SilverMid
import com.salvia.salviabrowxer.ui.utils.MediaKind
import com.salvia.salviabrowxer.ui.utils.MediaOfferability
import com.salvia.salviabrowxer.ui.utils.UnsupportedMedia

/**
 * The media tray: every candidate the page exposed, in one list.
 *
 * The quality sheet is per-item — this is the step before it. Each row states what it is (kind and
 * container), never a confidence score, and a stream this app cannot save says so on the row
 * instead of opening a sheet that could only fail.
 */
@Composable
fun MediaTraySheet(
    candidates: List<MediaCandidate>,
    unsupportedReason: (MediaCandidate) -> UnsupportedMedia?,
    onSelect: (MediaCandidate) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.BottomCenter
        ) {
            Surface(
                // Swallows taps so touching the sheet itself never dismisses it.
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
                shape = RoundedCornerShape(24.dp),
                color = CharcoalElevated,
                tonalElevation = 8.dp,
                shadowElevation = 16.dp,
                border = BorderStroke(1.dp, CharcoalBorder.copy(alpha = 0.8f))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    // Grabber + title + the real count
                    Box(modifier = Modifier.align(Alignment.CenterHorizontally).width(36.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(SilverMid.copy(alpha = 0.35f)))
                    Spacer(Modifier.height(14.dp))
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.media_tray_title), style = MaterialTheme.typography.titleLarge, color = PearlWhite)
                            Text(
                                text = stringResource(R.string.media_tray_count, candidates.size),
                                style = MaterialTheme.typography.labelMedium,
                                color = AuroraTeal
                            )
                        }
                        IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Close, stringResource(R.string.action_dismiss), tint = SilverMid)
                        }
                    }
                    Spacer(Modifier.height(12.dp))

                    if (candidates.isEmpty()) {
                        Text(stringResource(R.string.media_tray_empty), style = MaterialTheme.typography.bodyMedium, color = SilverMid)
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp),
                            contentPadding = PaddingValues(vertical = 2.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(candidates, key = { it.id }) { candidate ->
                                MediaTrayRow(
                                    candidate = candidate,
                                    reason = unsupportedReason(candidate),
                                    onSelect = { onSelect(candidate) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaTrayRow(candidate: MediaCandidate, reason: UnsupportedMedia?, onSelect: () -> Unit) {
    val kind = MediaOfferability.kindOf(candidate.mimeType, candidate.extension)
    val title = candidate.title?.takeIf { it.isNotBlank() } ?: filenameOf(candidate.mediaUrl)
    val extension = candidate.extension?.takeIf { it.isNotBlank() }?.uppercase()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CharcoalSurface)
            .clickable(enabled = reason == null, onClick = onSelect)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(CharcoalElevated),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = iconFor(kind),
                contentDescription = null,
                tint = if (reason == null) AuroraTeal else SilverMid,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = if (reason == null) PearlWhite else SilverMid,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            val kindLabel = kind?.let { stringResource(kindLabelRes(it)) }
            val meta = listOfNotNull(kindLabel, extension, hostOf(candidate.mediaUrl)).joinToString(" · ")
            Text(
                text = meta,
                style = MaterialTheme.typography.bodySmall,
                color = SilverMid,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (reason != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(reasonLabelRes(reason)),
                    style = MaterialTheme.typography.labelSmall,
                    color = SilverMid
                )
            }
        }
        if (reason == null) {
            IconButton(onClick = onSelect) {
                Icon(Icons.Default.Download, stringResource(R.string.media_open_quality), tint = PearlWhite, modifier = Modifier.size(20.dp))
            }
        }
    }
}

private fun iconFor(kind: MediaKind?): ImageVector = when (kind) {
    MediaKind.VIDEO -> Icons.Default.PlayCircle
    MediaKind.AUDIO -> Icons.Default.MusicNote
    MediaKind.PLAYLIST -> Icons.Default.PlaylistPlay
    null -> Icons.Default.InsertDriveFile
}

private fun kindLabelRes(kind: MediaKind): Int = when (kind) {
    MediaKind.VIDEO -> R.string.media_kind_video
    MediaKind.AUDIO -> R.string.media_kind_audio
    MediaKind.PLAYLIST -> R.string.media_kind_playlist
}

private fun reasonLabelRes(reason: UnsupportedMedia): Int = when (reason) {
    UnsupportedMedia.DASH -> R.string.error_dash_unsupported
    UnsupportedMedia.LIVE -> R.string.error_live_unsupported
}

private fun filenameOf(url: String): String =
    runCatching { java.net.URLDecoder.decode(url.substringBefore('#').substringBefore('?').substringAfterLast('/'), "UTF-8") }
        .getOrNull()?.takeIf { it.isNotBlank() } ?: url

private fun hostOf(url: String): String? =
    runCatching { java.net.URI(url).host }.getOrNull()?.removePrefix("www.")
