package com.salvia.salviabrowxer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tab
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.salvia.salviabrowxer.R
import com.salvia.salviabrowxer.ui.theme.AccentIndicatorBrush
import com.salvia.salviabrowxer.ui.theme.BottomBarBrush
import com.salvia.salviabrowxer.ui.theme.MatteCharcoal
import com.salvia.salviabrowxer.ui.theme.PearlEdgeBrush
import com.salvia.salviabrowxer.ui.theme.PearlWhite
import com.salvia.salviabrowxer.ui.theme.SilverMid

/**
 * Browser chrome, bottom half: the four controls the user needs one thumb away.
 *
 * Reload/stop sits here rather than in the top bar so every control on both bars keeps a 48 dp
 * touch target on a 360 dp phone. Home, downloads and settings are reachable from the overflow
 * menu — they are not peers of navigation.
 *
 * The button icon carries no content description: the visible label is the accessible name, and
 * describing the icon too would make TalkBack announce every item twice.
 */
@Composable
fun BrowserBottomBar(
    onReloadStopClick: () -> Unit,
    onDownloadsClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onTabsClick: () -> Unit,
    menuItems: List<BrowserMenuItem>,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    activeDownloadCount: Int = 0,
    tabsCount: Int = 1,
    menuContentDescription: String = ""
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // Iridescent hairline on top of the bar
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(PearlEdgeBrush))
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 62.dp).background(BottomBarBrush).padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically
        ) {
            BottomBarAction(
                // The glyph matches the action: a refresh arrow that claimed to be "Stop" was a
                // small lie the screen reader repeated.
                icon = if (isLoading) Icons.Default.Close else Icons.Default.Refresh,
                label = stringResource(if (isLoading) R.string.stop else R.string.reload),
                onClick = onReloadStopClick,
                modifier = Modifier.weight(1f)
            )
            BottomBarAction(
                icon = Icons.Default.Download,
                label = stringResource(R.string.downloads),
                onClick = onDownloadsClick,
                badge = activeDownloadCount,
                badgeState = stringResource(R.string.downloads_badge_description, activeDownloadCount),
                modifier = Modifier.weight(1f)
            )
            BottomBarAction(
                icon = Icons.Default.Tab,
                label = stringResource(R.string.tabs),
                onClick = onTabsClick,
                badge = tabsCount,
                modifier = Modifier.weight(1f)
            )
            BottomBarAction(
                icon = Icons.Default.Settings,
                label = stringResource(R.string.settings),
                onClick = onSettingsClick,
                modifier = Modifier.weight(1f)
            )
            BrowserMenuButton(items = menuItems, contentDescription = menuContentDescription, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun BottomBarAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: Int = 0,
    badgeState: String? = null
) {
    Box(
        modifier = modifier
            .heightIn(min = 54.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .semantics { if (badge > 0 && badgeState != null) stateDescription = badgeState },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(contentAlignment = Alignment.TopEnd) {
                Icon(imageVector = icon, contentDescription = null, tint = PearlWhite, modifier = Modifier.size(22.dp))
                if (badge > 0) {
                    Box(
                        modifier = Modifier
                            .offset(x = 4.dp, y = (-2).dp)
                            .size(17.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(AccentIndicatorBrush)
                            .clearAndSetSemantics {},
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (badge > 9) "9+" else badge.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MatteCharcoal,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(1.dp)
                        )
                    }
                }
            }
            Text(text = label, style = MaterialTheme.typography.labelMedium, color = SilverMid, modifier = Modifier.padding(top = 2.dp))
        }
    }
}
