package com.salvia.salviabrowxer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.salvia.salviabrowxer.ui.theme.Gold
import com.salvia.salviabrowxer.ui.theme.MediaDetectedIndicator
import com.salvia.salviabrowxer.ui.theme.SurfaceVariant
import java.text.DecimalFormat

@Composable
fun DownloadItem(
    download: DownloadEntity,
    onPauseClick: () -> Unit,
    onResumeClick: () -> Unit,
    onCancelClick: () -> Unit,
    onRetryClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val progress = if (download.totalBytes != null && download.totalBytes > 0) {
        (download.downloadedBytes.toFloat() / download.totalBytes) * 100
    } else {
        0f
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .background(SurfaceVariant, RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surface)
        ) {
            download.thumbnail?.let { thumbnailUrl ->
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(thumbnailUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize()
                )
            }
            if (download.thumbnail == null) {
                Icon(
                    imageVector = when (download.status) {
                        DownloadState.COMPLETED -> Icons.Default.CheckCircle
                        DownloadState.FAILED, DownloadState.CANCELLED -> Icons.Default.Error
                        else -> Icons.Default.Refresh
                    },
                    contentDescription = null,
                    tint = when (download.status) {
                        DownloadState.COMPLETED -> Color.Green
                        DownloadState.FAILED, DownloadState.CANCELLED -> Color.Red
                        else -> Gold
                    },
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = download.mediaTitle ?: download.filename,
                style = MaterialTheme.typography.bodyMedium,
                color = Gold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            when (download.status) {
                DownloadState.DOWNLOADING, DownloadState.RESOLVING, DownloadState.PREPARING, DownloadState.PROCESSING -> {
                    LinearProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp),
                        color = MediaDetectedIndicator
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${formatFileSize(download.downloadedBytes)} / ${download.totalBytes?.let { formatFileSize(it) } ?: "?"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
                DownloadState.QUEUED, DownloadState.RETRYING -> {
                    Text(
                        text = stringResource(R.string.download_queue),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
                DownloadState.PAUSED -> {
                    Text(
                        text = stringResource(R.string.download_pause),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
                DownloadState.COMPLETED -> {
                    Text(
                        text = stringResource(R.string.download_completed),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Green
                    )
                }
                DownloadState.FAILED, DownloadState.CANCELLED -> {
                    Text(
                        text = download.error ?: stringResource(R.string.download_failed),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Red
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        when (download.status) {
            DownloadState.DOWNLOADING -> {
                IconButton(onClick = onPauseClick) {
                    Icon(
                        imageVector = Icons.Default.Pause,
                        contentDescription = stringResource(R.string.download_pause),
                        tint = Gold
                    )
                }
            }
            DownloadState.PAUSED -> {
                IconButton(onClick = onResumeClick) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = stringResource(R.string.download_resume),
                        tint = Gold
                    )
                }
            }
            DownloadState.QUEUED, DownloadState.RETRYING, DownloadState.RESOLVING, DownloadState.PREPARING, DownloadState.PROCESSING -> {
                IconButton(onClick = onCancelClick) {
                    Icon(
                        imageVector = Icons.Default.Cancel,
                        contentDescription = stringResource(R.string.download_cancel),
                        tint = Gold
                    )
                }
            }
            DownloadState.FAILED, DownloadState.CANCELLED -> {
                IconButton(onClick = onRetryClick) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.download_retry),
                        tint = Gold
                    )
                }
            }
            DownloadState.COMPLETED -> {
                IconButton(onClick = onDeleteClick) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.download_delete),
                        tint = Gold
                    )
                }
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    return DecimalFormat("#,##0.#").format(bytes / Math.pow(1024.0, digitGroups.toDouble())) + " " + units[digitGroups]
}