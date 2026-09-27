package com.salvia.salviabrowxer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.salvia.salviabrowxer.R
import com.salvia.salviabrowxer.core.model.Tab
import com.salvia.salviabrowxer.ui.theme.AuroraTeal
import com.salvia.salviabrowxer.ui.theme.CharcoalBorder
import com.salvia.salviabrowxer.ui.theme.CharcoalElevated
import com.salvia.salviabrowxer.ui.theme.CharcoalSurface
import com.salvia.salviabrowxer.ui.theme.MatteCharcoal
import com.salvia.salviabrowxer.ui.theme.NebulaViolet
import com.salvia.salviabrowxer.ui.theme.NebulaVioletLight
import com.salvia.salviabrowxer.ui.theme.PearlWhite
import com.salvia.salviabrowxer.ui.theme.SilverMid

/**
 * Tab switcher.
 *
 * Cards carry a monogram, title and host rather than a screenshot: capturing a live WebView
 * preview on minSdk 24 is expensive and easy to fake, and a fake preview is worse than none.
 * A hibernated tab says so, because selecting it reloads the page.
 */
@Composable
fun TabSwitcher(
    tabs: List<Tab>,
    currentTabId: String?,
    hibernatedTabIds: Set<String>,
    onSelect: (String) -> Unit,
    onClose: (String) -> Unit,
    onNewTab: () -> Unit,
    onNewPrivateTab: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val normalTabs = tabs.filterNot { it.isPrivate }
    val privateTabs = tabs.filter { it.isPrivate }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(MatteCharcoal, CharcoalElevated)))
            .padding(horizontal = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(56.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.tabs),
                style = MaterialTheme.typography.titleLarge,
                color = PearlWhite,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, stringResource(R.string.action_dismiss), tint = PearlWhite)
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 12.dp)
        ) {
            items(normalTabs, key = { it.id }) { tab ->
                TabCard(
                    tab = tab,
                    isCurrent = tab.id == currentTabId,
                    isHibernated = tab.id in hibernatedTabIds,
                    onSelect = { onSelect(tab.id) },
                    onClose = { onClose(tab.id) }
                )
            }
            if (privateTabs.isNotEmpty()) {
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                    PrivateSectionHeader()
                }
                items(privateTabs, key = { it.id }) { tab ->
                    TabCard(
                        tab = tab,
                        isCurrent = tab.id == currentTabId,
                        isHibernated = tab.id in hibernatedTabIds,
                        onSelect = { onSelect(tab.id) },
                        onClose = { onClose(tab.id) }
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            TextButton(onClick = onNewTab, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Add, null, tint = AuroraTeal, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.new_tab), color = AuroraTeal)
            }
            TextButton(onClick = onNewPrivateTab, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Lock, null, tint = NebulaVioletLight, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.new_private_tab), color = NebulaVioletLight)
            }
        }
    }
}

@Composable
private fun PrivateSectionHeader() {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
        Icon(Icons.Default.Nightlight, null, tint = NebulaVioletLight, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(stringResource(R.string.private_browsing), style = MaterialTheme.typography.titleMedium, color = NebulaVioletLight)
    }
}

@Composable
private fun TabCard(
    tab: Tab,
    isCurrent: Boolean,
    isHibernated: Boolean,
    onSelect: () -> Unit,
    onClose: () -> Unit
) {
    val accent = if (tab.isPrivate) NebulaViolet else AuroraTeal
    val title = tab.title.takeIf { it.isNotBlank() && it != "New Tab" } ?: hostLabel(tab.url).ifBlank { stringResource(R.string.new_tab) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.05f)
            .clip(RoundedCornerShape(16.dp))
            .background(CharcoalSurface)
            .border(if (isCurrent) 1.4.dp else 0.8.dp, if (isCurrent) accent else CharcoalBorder, RoundedCornerShape(16.dp))
            .clickable(onClick = onSelect)
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(34.dp).clip(CircleShape).background(accent.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = monogram(title),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (tab.isPrivate) NebulaVioletLight else accent
                    )
                }
                if (tab.isPrivate) {
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Default.Lock, stringResource(R.string.private_browsing), tint = NebulaVioletLight, modifier = Modifier.size(14.dp))
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = PearlWhite,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = hostLabel(tab.url),
                style = MaterialTheme.typography.bodySmall,
                color = SilverMid,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.weight(1f))
            if (isHibernated) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.DarkMode, null, tint = SilverMid, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.tab_hibernated), style = MaterialTheme.typography.labelSmall, color = SilverMid)
                }
            }
        }

        IconButton(
            onClick = onClose,
            modifier = Modifier.align(Alignment.TopEnd).size(34.dp)
        ) {
            Icon(Icons.Default.Close, stringResource(R.string.close_tab), tint = SilverMid, modifier = Modifier.size(16.dp))
        }
    }
}

private fun monogram(text: String): String =
    text.trim().firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "?"

private fun hostLabel(url: String): String {
    if (url.isBlank()) return ""
    return runCatching { java.net.URI(url).host }.getOrNull()?.removePrefix("www.") ?: url
}
