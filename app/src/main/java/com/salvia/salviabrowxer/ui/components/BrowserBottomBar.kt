package com.salvia.salviabrowxer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.salvia.salviabrowxer.R
import com.salvia.salviabrowxer.ui.theme.AccentIndicatorBrush
import com.salvia.salviabrowxer.ui.theme.BlushPink
import com.salvia.salviabrowxer.ui.theme.BottomBarBrush
import com.salvia.salviabrowxer.ui.theme.MatteCharcoal
import com.salvia.salviabrowxer.ui.theme.NebulaVioletLight
import com.salvia.salviabrowxer.ui.theme.PearlEdgeBrush
import com.salvia.salviabrowxer.ui.theme.PearlWhite
import com.salvia.salviabrowxer.ui.theme.SilverMid

@Composable
fun BrowserBottomBar(
    onHomeClick: () -> Unit,
    onDownloadsClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
    activeDownloadCount: Int = 0
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // Iridescent hairline on top of the bar
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(PearlEdgeBrush))
        Row(
            modifier = Modifier.fillMaxWidth().height(62.dp).background(BottomBarBrush).padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically
        ) {
            BottomBarAction(icon = Icons.Default.Home, label = stringResource(R.string.home), onClick = onHomeClick, badge = 0, modifier = Modifier.weight(1f))
            BottomBarAction(icon = Icons.Default.Download, label = stringResource(R.string.downloads), onClick = onDownloadsClick, badge = activeDownloadCount, modifier = Modifier.weight(1f))
            BottomBarAction(icon = Icons.Default.Settings, label = stringResource(R.string.settings), onClick = onSettingsClick, badge = 0, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun BottomBarAction(icon: ImageVector, label: String, badge: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier.height(54.dp).clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(contentAlignment = Alignment.TopEnd) {
                Icon(imageVector = icon, contentDescription = label, tint = PearlWhite, modifier = Modifier.size(22.dp))
                if (badge > 0) {
                    Box(
                        modifier = Modifier
                            .offset(x = 4.dp, y = (-2).dp)
                            .size(17.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(AccentIndicatorBrush),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = if (badge > 9) "9+" else badge.toString(), style = MaterialTheme.typography.labelSmall, color = MatteCharcoal, textAlign = TextAlign.Center, modifier = Modifier.padding(1.dp))
                    }
                }
            }
            Text(text = label, style = MaterialTheme.typography.labelMedium, color = SilverMid, modifier = Modifier.padding(top = 2.dp))
        }
    }
}
