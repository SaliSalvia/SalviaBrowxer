package com.salvia.salviabrowxer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.salvia.salviabrowxer.R
import com.salvia.salviabrowxer.core.database.entities.DownloadEntity
import com.salvia.salviabrowxer.core.model.DownloadState
import com.salvia.salviabrowxer.ui.theme.AuroraTeal
import com.salvia.salviabrowxer.ui.theme.CharcoalBorder
import com.salvia.salviabrowxer.ui.theme.CharcoalElevated
import com.salvia.salviabrowxer.ui.theme.CharcoalSurface
import com.salvia.salviabrowxer.ui.theme.PearlWhite
import com.salvia.salviabrowxer.ui.theme.SilverMid
import com.salvia.salviabrowxer.ui.utils.formatFileSize

@Composable
fun DownloadItem(
    download: DownloadEntity,
    onOpenClick: () -> Unit = {},
    onPauseClick: () -> Unit,
    onResumeClick: () -> Unit,
    onCancelClick: () -> Unit,
    onRetryClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val totalBytes = download.totalBytes
    val progress = if (totalBytes != null && totalBytes > 0) (download.downloadedBytes.toFloat() / totalBytes) else 0f

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).clip(RoundedCornerShape(14.dp)).background(CharcoalElevated).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)).background(CharcoalSurface), contentAlignment = Alignment.Center) {
            download.thumbnail?.let { thumbnailUrl ->
                AsyncImage(model = ImageRequest.Builder(LocalContext.current).data(thumbnailUrl).crossfade(true).build(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
            }
            if (download.thumbnail == null) {
                Icon(imageVector = when (download.status) { DownloadState.COMPLETED -> Icons.Default.CheckCircle; DownloadState.FAILED, DownloadState.CANCELLED -> Icons.Default.Error; else -> Icons.Default.Refresh }, contentDescription = null, tint = when (download.status) { DownloadState.COMPLETED -> AuroraTeal; DownloadState.FAILED, DownloadState.CANCELLED -> Color(0xFFFF5252); else -> SilverMid }, modifier = Modifier.size(24.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = download.mediaTitle ?: download.filename, style = MaterialTheme.typography.bodyMedium, color = PearlWhite, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            when (download.status) {
                DownloadState.DOWNLOADING, DownloadState.RESOLVING, DownloadState.PREPARING, DownloadState.PROCESSING -> {
                    LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)), color = AuroraTeal, trackColor = CharcoalBorder)
                    Spacer(Modifier.height(4.dp))
                    Text(text = "${formatFileSize(download.downloadedBytes)} / ${download.totalBytes?.let { formatFileSize(it) } ?: "?"}", style = MaterialTheme.typography.bodySmall, color = SilverMid)
                }
                DownloadState.QUEUED, DownloadState.RETRYING -> Text(text = stringResource(R.string.download_queue), style = MaterialTheme.typography.bodySmall, color = SilverMid)
                DownloadState.PAUSED -> Text(text = stringResource(R.string.download_pause), style = MaterialTheme.typography.bodySmall, color = SilverMid)
                DownloadState.COMPLETED -> Text(text = stringResource(R.string.download_completed), style = MaterialTheme.typography.bodySmall, color = AuroraTeal)
                DownloadState.FAILED, DownloadState.CANCELLED -> Text(text = download.error ?: stringResource(R.string.download_failed), style = MaterialTheme.typography.bodySmall, color = Color(0xFFFF8A80), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.width(8.dp))
        when (download.status) {
            DownloadState.DOWNLOADING -> IconButton(onClick = onPauseClick) { Icon(Icons.Default.Pause, stringResource(R.string.download_pause), tint = PearlWhite) }
            DownloadState.PAUSED -> IconButton(onClick = onResumeClick) { Icon(Icons.Default.PlayArrow, stringResource(R.string.download_resume), tint = PearlWhite) }
            DownloadState.QUEUED, DownloadState.RETRYING, DownloadState.RESOLVING, DownloadState.PREPARING, DownloadState.PROCESSING -> IconButton(onClick = onCancelClick) { Icon(Icons.Default.Cancel, stringResource(R.string.download_cancel), tint = SilverMid) }
            DownloadState.FAILED, DownloadState.CANCELLED -> IconButton(onClick = onRetryClick) { Icon(Icons.Default.Refresh, stringResource(R.string.download_retry), tint = AuroraTeal) }
            DownloadState.COMPLETED -> {
                IconButton(onClick = onOpenClick) { Icon(Icons.AutoMirrored.Filled.OpenInNew, stringResource(R.string.download_open), tint = PearlWhite) }
                IconButton(onClick = onDeleteClick) { Icon(Icons.Default.Close, stringResource(R.string.download_delete), tint = SilverMid) }
            }
        }
    }
}


