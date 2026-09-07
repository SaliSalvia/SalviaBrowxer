package com.salvia.salviabrowxer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.salvia.salviabrowxer.R
import com.salvia.salviabrowxer.core.model.MediaFormat
import com.salvia.salviabrowxer.core.model.MediaInfo
import com.salvia.salviabrowxer.ui.theme.DownloadButtonActive
import com.salvia.salviabrowxer.ui.theme.Gold
import com.salvia.salviabrowxer.ui.theme.Surface as SurfaceColor
import java.text.DecimalFormat

/**
 * Quality picker for a detected media item. Picking a row and confirming enqueues a real download
 * (the callback hands the selected [MediaFormat] to the ViewModel, which writes the queue entry and
 * starts DownloadService).
 */
@Composable
fun MediaQualitySelectionSheet(
    mediaInfo: MediaInfo,
    isResolving: Boolean,
    onDismiss: () -> Unit,
    onQualitySelected: (MediaFormat) -> Unit
) {
    val formats = mediaInfo.combinedFormats.ifEmpty { mediaInfo.formats }
    var selectedFormat by remember(mediaInfo.title, formats.size) {
        mutableStateOf<MediaFormat?>(formats.firstOrNull())
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            color = SurfaceColor,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.select_quality),
                        style = MaterialTheme.typography.titleLarge,
                        color = Gold
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.action_cancel),
                            tint = Gold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        val thumbnail = mediaInfo.thumbnail
                        if (thumbnail != null) {
                            AsyncImage(
                                model = thumbnail,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = null,
                                tint = Gold,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = mediaInfo.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = Gold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        mediaInfo.duration?.let { duration ->
                            Text(
                                text = formatDuration(duration),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                Spacer(modifier = Modifier.height(8.dp))

                if (isResolving && formats.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(28.dp),
                            color = Gold,
                            strokeWidth = 2.dp
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(196.dp)
                    ) {
                        items(formats.size) { index ->
                            val format = formats[index]
                            QualityOptionItem(
                                format = format,
                                isSelected = selectedFormat?.id == format.id,
                                onClick = { selectedFormat = format }
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = { selectedFormat?.let { onQualitySelected(it) } },
                    enabled = selectedFormat != null && !isResolving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DownloadButtonActive,
                        contentColor = MaterialTheme.colorScheme.background
                    )
                ) {
                    Text(
                        text = stringResource(R.string.download_start),
                        style = MaterialTheme.typography.titleSmall
                    )
                }
            }
        }
    }
}

@Composable
fun QualityOptionItem(
    format: MediaFormat,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isSelected) Icons.Default.Check else Icons.Default.Close,
            contentDescription = null,
            tint = if (isSelected) Gold else Color.Transparent,
            modifier = Modifier.size(20.dp)
        )

        Spacer(modifier = Modifier.width(8.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = format.format,
                style = MaterialTheme.typography.bodyLarge,
                color = Gold
            )
            Row {
                format.size?.let { size ->
                    Text(
                        text = formatFileSize(size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
                if (format.width != null && format.height != null) {
                    Text(
                        text = " · ${format.width}x${format.height}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
                if (format.mimeType.isNotBlank()) {
                    Text(
                        text = " · ${format.mimeType}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

private fun formatDuration(milliseconds: Long): String {
    val seconds = milliseconds / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    return when {
        hours > 0 -> String.format("%02d:%02d:%02d", hours, minutes % 60, seconds % 60)
        minutes > 0 -> String.format("%02d:%02d", minutes, seconds % 60)
        else -> String.format("00:%02d", seconds)
    }
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "?"
    val units = arrayOf("B", "KB", "MB", "GB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, 3)
    return DecimalFormat("#,##0.#")
        .format(bytes / Math.pow(1024.0, digitGroups.toDouble())) + " " + units[digitGroups]
}
