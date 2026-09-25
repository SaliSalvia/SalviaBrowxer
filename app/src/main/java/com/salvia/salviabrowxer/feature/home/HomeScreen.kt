package com.salvia.salviabrowxer.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.salvia.salviabrowxer.R
import com.salvia.salviabrowxer.core.database.entities.BookmarkEntity
import com.salvia.salviabrowxer.core.database.entities.HistoryEntity
import com.salvia.salviabrowxer.ui.theme.AuroraTeal
import com.salvia.salviabrowxer.ui.theme.CharcoalBorder
import com.salvia.salviabrowxer.ui.theme.CharcoalElevated
import com.salvia.salviabrowxer.ui.theme.CharcoalSurface
import com.salvia.salviabrowxer.ui.theme.MatteCharcoal
import com.salvia.salviabrowxer.ui.theme.PearlWhite
import com.salvia.salviabrowxer.ui.theme.SilverMid

@Composable
fun HomeScreen(
    bookmarks: List<BookmarkEntity>,
    history: List<HistoryEntity>,
    onBookmarkClick: (String) -> Unit,
    onHistoryClick: (String) -> Unit,
    onSettingsClick: () -> Unit
) {
    // Single LazyColumn for entire screen → one scrollable, one composition pass, no nested scroll interop
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MatteCharcoal)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        item(key = "header") {
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineSmall,
                color = PearlWhite
            )
            Text(
                text = stringResource(R.string.app_description),
                style = MaterialTheme.typography.bodySmall,
                color = SilverMid.copy(alpha = 0.85f),
                modifier = Modifier.padding(top = 4.dp)
            )
            Spacer(Modifier.height(20.dp))
        }

        // Bookmarks section
        item(key = "bookmarks_header") {
            SectionHeader(title = stringResource(R.string.bookmarks), icon = Icons.Default.Bookmark)
            Spacer(Modifier.height(8.dp))
        }
        if (bookmarks.isEmpty()) {
            item(key = "bookmarks_empty") {
                EmptyHint("No bookmarks yet — save a page to see it here")
                Spacer(Modifier.height(16.dp))
            }
        } else {
            items(bookmarks.take(5), key = { "bm_${it.url}" }) { item ->
                HomeRow(title = item.title, url = item.url, onClick = { onBookmarkClick(item.url) })
            }
            item(key = "bookmarks_spacer") { Spacer(Modifier.height(16.dp)) }
        }

        // History section
        item(key = "history_header") {
            SectionHeader(title = stringResource(R.string.history), icon = Icons.Default.History)
            Spacer(Modifier.height(8.dp))
        }
        if (history.isEmpty()) {
            item(key = "history_empty") {
                EmptyHint("No recent pages")
                Spacer(Modifier.height(16.dp))
            }
        } else {
            items(history.take(5), key = { "hi_${it.url}_${it.visitedAt}" }) { item ->
                HomeRow(title = item.title, url = item.url, onClick = { onHistoryClick(item.url) })
            }
            item(key = "history_spacer") { Spacer(Modifier.height(16.dp)) }
        }

        item(key = "settings_card") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(CharcoalElevated)
                    .clickable(onClick = onSettingsClick)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(CharcoalSurface),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Settings, null, tint = AuroraTeal, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings), style = MaterialTheme.typography.bodyMedium, color = PearlWhite)
                    Text("Browser, downloads & privacy", style = MaterialTheme.typography.bodySmall, color = SilverMid)
                }
                Icon(Icons.Default.Settings, null, tint = SilverMid.copy(alpha = 0.45f), modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionHeader(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Icon(icon, null, tint = AuroraTeal, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, color = PearlWhite)
        Spacer(Modifier.weight(1f))
        Text("See all", style = MaterialTheme.typography.labelSmall, color = SilverMid.copy(alpha = 0.9f))
    }
}

@Composable
private fun HomeRow(title: String, url: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(CharcoalElevated)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(CharcoalSurface),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Newspaper, null, tint = SilverMid, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title.ifBlank { url }, style = MaterialTheme.typography.bodyMedium, color = PearlWhite, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(url, style = MaterialTheme.typography.bodySmall, color = SilverMid.copy(alpha = 0.78f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun EmptyHint(text: String) {
    Box(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(CharcoalElevated.copy(alpha = 0.72f)).padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, style = MaterialTheme.typography.bodySmall, color = SilverMid.copy(alpha = 0.85f))
    }
}
