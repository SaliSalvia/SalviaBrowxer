package com.salvia.salviabrowxer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.CheckCircle
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
import androidx.compose.ui.graphics.Brush
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
import com.salvia.salviabrowxer.ui.theme.AuroraTeal
import com.salvia.salviabrowxer.ui.theme.CharcoalBorder
import com.salvia.salviabrowxer.ui.theme.CharcoalElevated
import com.salvia.salviabrowxer.ui.theme.CharcoalSurface
import com.salvia.salviabrowxer.ui.theme.DeepCharcoal
import com.salvia.salviabrowxer.ui.theme.PearlWhite
import com.salvia.salviabrowxer.ui.theme.SilverMid
import com.salvia.salviabrowxer.ui.utils.formatFileSize

@Composable
fun MediaQualitySelectionSheet(
    mediaInfo: MediaInfo,
    isResolving: Boolean,
    onDismiss: () -> Unit,
    onQualitySelected: (MediaFormat) -> Unit
) {
    val formats = mediaInfo.combinedFormats.ifEmpty { mediaInfo.formats }
    var selectedFormat by remember(mediaInfo.title, formats.size) { mutableStateOf<MediaFormat?>(formats.firstOrNull()) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.96f).padding(10.dp),
            shape = RoundedCornerShape(20.dp),
            color = CharcoalElevated,
            tonalElevation = 8.dp,
            shadowElevation = 16.dp
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                // Drag handle
                Box(modifier = Modifier.align(Alignment.CenterHorizontally).width(36.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(SilverMid.copy(alpha = 0.35f)))
                Spacer(Modifier.height(14.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(text = stringResource(R.string.select_quality), style = MaterialTheme.typography.titleLarge, color = PearlWhite)
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) { Icon(imageVector = Icons.Default.Close, contentDescription = stringResource(R.string.action_cancel), tint = SilverMid) }
                }
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(72.dp).clip(RoundedCornerShape(12.dp)).background(CharcoalSurface).border(1.dp, CharcoalBorder, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                        val thumbnail = mediaInfo.thumbnail
                        if (thumbnail != null) AsyncImage(model = thumbnail, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(72.dp).clip(RoundedCornerShape(12.dp)))
                        else Icon(imageVector = Icons.Default.Download, contentDescription = null, tint = AuroraTeal, modifier = Modifier.size(28.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = mediaInfo.title, style = MaterialTheme.typography.titleMedium, color = PearlWhite, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        mediaInfo.duration?.let { Text(text = formatDuration(it), style = MaterialTheme.typography.bodySmall, color = SilverMid) }
                        if (!isResolving && formats.isNotEmpty()) Text(text = "${formats.size} quality option${if (formats.size > 1) "s" else ""}", style = MaterialTheme.typography.labelSmall, color = AuroraTeal)
                    }
                }
                Spacer(Modifier.height(14.dp))
                HorizontalDivider(color = CharcoalBorder.copy(alpha = 0.7f))
                Spacer(Modifier.height(8.dp))
                if (isResolving && formats.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(modifier = Modifier.size(28.dp), color = AuroraTeal, strokeWidth = 2.dp) }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth().height(204.dp)) {
                        items(formats.size, key = { formats[it].id }) { index ->
                            val format = formats[index]
                            QualityOptionItem(format = format, isSelected = selectedFormat?.id == format.id, onClick = { selectedFormat = format })
                            if (index < formats.lastIndex) HorizontalDivider(color = CharcoalBorder.copy(alpha = 0.5f))
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = { selectedFormat?.let { onQualitySelected(it) } },
                    enabled = selectedFormat != null && !isResolving,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AuroraTeal, contentColor = Color(0xFF0A0A0C), disabledContainerColor = CharcoalSurface, disabledContentColor = SilverMid.copy(alpha = 0.5f))
                ) { Text(text = stringResource(R.string.download_start), style = MaterialTheme.typography.titleSmall) }
            }
        }
    }
}

@Composable
fun QualityOptionItem(format: MediaFormat, isSelected: Boolean, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (isSelected) AuroraTeal.copy(alpha = 0.10f) else Color.Transparent).border(if (isSelected) 1.dp else 0.dp, if (isSelected) AuroraTeal.copy(alpha = 0.35f) else Color.Transparent, RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(vertical = 11.dp, horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(22.dp).clip(RoundedCornerShape(6.dp)).background(if (isSelected) AuroraTeal else CharcoalSurface).border(1.dp, if (isSelected) AuroraTeal else CharcoalBorder, RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) {
            if (isSelected) Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = format.format, style = MaterialTheme.typography.bodyLarge, color = if (isSelected) PearlWhite else SilverMid)
            Row {
                format.size?.let { Text(text = formatFileSize(it), style = MaterialTheme.typography.bodySmall, color = SilverMid.copy(alpha = 0.85f)) }
                if (format.width != null && format.height != null) Text(text = " · ${format.width}x${format.height}", style = MaterialTheme.typography.bodySmall, color = SilverMid.copy(alpha = 0.7f))
                if (format.mimeType.isNotBlank()) Text(text = " · ${format.mimeType}", style = MaterialTheme.typography.bodySmall, color = SilverMid.copy(alpha = 0.45f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (format.isHls) Box(modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(AuroraTeal.copy(alpha = 0.18f)).padding(horizontal = 6.dp, vertical = 2.dp)) { Text("HLS", style = MaterialTheme.typography.labelSmall, color = AuroraTeal) }
        if (format.isDash) Box(modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Color(0xFF8B5CF6).copy(alpha = 0.18f)).padding(horizontal = 6.dp, vertical = 2.dp)) { Text("DASH", style = MaterialTheme.typography.labelSmall, color = Color(0xFFB794FF)) }
    }
}

private fun formatDuration(milliseconds: Long): String {
    val seconds = milliseconds / 1000; val minutes = seconds / 60; val hours = minutes / 60
    return when { hours > 0 -> String.format("%02d:%02d:%02d", hours, minutes % 60, seconds % 60); minutes > 0 -> String.format("%02d:%02d", minutes, seconds % 60); else -> String.format("00:%02d", seconds) }
}


