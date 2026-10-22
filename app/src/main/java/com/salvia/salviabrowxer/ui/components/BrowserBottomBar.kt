package com.salvia.salviabrowxer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Downloading
import androidx.compose.material.icons.filled.Home
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
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
import com.salvia.salviabrowxer.ui.theme.AuroraTeal
import com.salvia.salviabrowxer.ui.theme.BottomBarBrush
import com.salvia.salviabrowxer.ui.theme.CharcoalBorder
import com.salvia.salviabrowxer.ui.theme.CharcoalElevated
import com.salvia.salviabrowxer.ui.theme.DownloadCtaBrush
import com.salvia.salviabrowxer.ui.theme.MatteCharcoal
import com.salvia.salviabrowxer.ui.theme.PearlWhite
import com.salvia.salviabrowxer.ui.theme.SilverMid

/** The downloads-screen tab the dock's left ring lands on: transfers that are running. */
const val DOWNLOADS_TAB_ACTIVE = 0

/** The downloads-screen tab the dock's right ring lands on: transfers that have finished. */
const val DOWNLOADS_TAB_COMPLETED = 2

/**
 * Browser chrome, bottom half: a slim action row and, under it, the three-ring dock.
 *
 * The slim row keeps the controls the page itself needs one thumb away (reload / stop, tabs,
 * settings, overflow); the dock answers the question a media browser is asked mid-page - where are
 * my downloads? The left ring counts what is running and opens the Active tab, the big centre ring
 * is home, the right ring counts what has finished and opens the Completed tab. Every control
 * keeps a 48 dp touch target on a 360 dp phone.
 *
 * The ring glyphs carry no content description: the visible label is the accessible name, and
 * describing the glyph too would make TalkBack announce every item twice.
 */
@Composable
fun BrowserBottomBar(
    onReloadStopClick: () -> Unit,
    /** Carries the downloads-screen tab to land on ([DOWNLOADS_TAB_ACTIVE] or [DOWNLOADS_TAB_COMPLETED]). */
    onDownloadsClick: (Int) -> Unit,
    onSettingsClick: () -> Unit,
    onTabsClick: () -> Unit,
    menuItems: List<BrowserMenuItem>,
    /** The big centre ring: the browsable homepage when a page is open, the start screen otherwise. */
    onHomeClick: () -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    activeDownloadCount: Int = 0,
    completedDownloadCount: Int = 0,
    tabsCount: Int = 1,
    menuContentDescription: String = ""
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // A subtle divider gives the page and browser controls a clear edge.
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(CharcoalBorder.copy(alpha = 0.72f)))
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp).background(BottomBarBrush).padding(horizontal = 4.dp),
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
        // The dock: three circles, the middle one larger and lifted, so the eye finds home first
        // and the two download rings read as its companions.
        Row(
            modifier = Modifier.fillMaxWidth().height(80.dp).background(BottomBarBrush),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                DockRing(
                    icon = Icons.Default.Downloading,
                    label = stringResource(R.string.download_active),
                    badge = activeDownloadCount,
                    badgeState = if (activeDownloadCount > 0) stringResource(R.string.downloads_badge_description, activeDownloadCount) else null,
                    highlighted = activeDownloadCount > 0,
                    onClick = { onDownloadsClick(DOWNLOADS_TAB_ACTIVE) }
                )
            }
            Box(modifier = Modifier.weight(1.1f), contentAlignment = Alignment.Center) {
                HomeRing(onClick = onHomeClick)
            }
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                DockRing(
                    icon = Icons.Default.DownloadDone,
                    label = stringResource(R.string.download_completed),
                    badge = completedDownloadCount,
                    badgeState = if (completedDownloadCount > 0) stringResource(R.string.downloads_finished_badge_description, completedDownloadCount) else null,
                    highlighted = completedDownloadCount > 0,
                    onClick = { onDownloadsClick(DOWNLOADS_TAB_COMPLETED) }
                )
            }
        }
    }
}

/**
 * One ring of the dock. A circle on purpose: circles read as destinations, and the count lives on
 * the rim where it can be seen without opening anything. The ring is highlighted only while its
 * count is non-zero, so an empty state stays quiet.
 */
@Composable
private fun DockRing(
    icon: ImageVector,
    label: String,
    badge: Int,
    badgeState: String?,
    highlighted: Boolean,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .shadow(4.dp, CircleShape)
                    .clip(CircleShape)
                    .background(CharcoalElevated)
                    .border(1.dp, if (highlighted) AuroraTeal.copy(alpha = 0.65f) else CharcoalBorder, CircleShape)
                    .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
                    .semantics { if (badge > 0 && badgeState != null) stateDescription = badgeState },
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = if (highlighted) AuroraTeal else PearlWhite, modifier = Modifier.size(21.dp))
                if (badge > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 3.dp, y = (-3).dp)
                            .size(17.dp)
                            .clip(CircleShape)
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
        }
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = SilverMid, modifier = Modifier.padding(top = 4.dp))
    }
}

/**
 * The big centre ring. Larger than its neighbours because it is the way back: home is what a
 * browser is most likely to be asked for, and it wears the download button's own gradient so the
 * whole dock speaks one visual language.
 */
@Composable
private fun HomeRing(onClick: () -> Unit) {
    val label = stringResource(R.string.home)
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.offset(y = (-4).dp)) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .shadow(10.dp, CircleShape)
                .clip(CircleShape)
                .background(DownloadCtaBrush)
                .clickable(role = Role.Button, onClickLabel = label, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = Icons.Default.Home, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
        }
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = SilverMid, modifier = Modifier.padding(top = 4.dp))
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
