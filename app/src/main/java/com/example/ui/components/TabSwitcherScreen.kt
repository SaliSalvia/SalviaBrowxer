package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.BrowserTab
import com.example.ui.theme.ChromeSilver
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldShield
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.NebulaMagenta
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.SapphireBlue
import com.example.ui.theme.SapphireGlow
import com.example.ui.theme.StarlightWhite
import com.example.ui.theme.StealthViolet

@Composable
fun TabSwitcherScreen(
    tabs: List<BrowserTab>,
    activeTabId: String,
    onSelectTab: (String) -> Unit,
    onCloseTab: (String) -> Unit,
    onNewTab: (isIncognito: Boolean) -> Unit,
    onCloseAllIncognito: () -> Unit,
    onDismiss: () -> Unit,
) {
    BackHandler(onBack = onDismiss)
    val activeTab = tabs.firstOrNull { it.id == activeTabId }
    var showIncognitoOnly by remember(activeTab?.isIncognito) {
        mutableStateOf(activeTab?.isIncognito == true)
    }

    val normalTabs = remember(tabs) { tabs.filterNot { it.isIncognito } }
    val incognitoTabs = remember(tabs) { tabs.filter { it.isIncognito } }
    val displayedTabs = if (showIncognitoOnly) incognitoTabs else normalTabs
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.25f

    CosmicSpaceBackdrop(isIncognito = showIncognitoOnly) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            // Top Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "مدیریت تب‌ها (${tabs.size})",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = if (isDark) StarlightWhite else MaterialTheme.colorScheme.onBackground,
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("close_tab_switcher_button"),
                ) {
                    Icon(Icons.Default.Close, contentDescription = "بستن مدیریت تب‌ها")
                }
            }

            // Segmented Normal vs Incognito Switcher
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) {
                SegmentedButton(
                    selected = !showIncognitoOnly,
                    onClick = { showIncognitoOnly = false },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    icon = { Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    modifier = Modifier.testTag("segment_normal_tabs"),
                ) {
                    Text("تب‌های عادی (${normalTabs.size})")
                }
                SegmentedButton(
                    selected = showIncognitoOnly,
                    onClick = { showIncognitoOnly = true },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    icon = { Icon(Icons.Default.VisibilityOff, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    modifier = Modifier.testTag("segment_incognito_tabs"),
                ) {
                    Text("ناشناس (${incognitoTabs.size})")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (displayedTabs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(110.dp)) {
                            Image(
                                painter = painterResource(id = R.drawable.salvia_prism_rings_alpha),
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .alpha(0.75f),
                            )
                            Icon(
                                imageVector = if (showIncognitoOnly) Icons.Default.VisibilityOff else Icons.Default.Language,
                                contentDescription = null,
                                tint = if (showIncognitoOnly) StealthViolet else ElectricCyan,
                                modifier = Modifier.size(40.dp),
                            )
                        }
                        Text(
                            text = if (showIncognitoOnly) "هیچ تب ناشناسی باز نیست" else "هیچ تب عادی باز نیست",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Button(
                            onClick = { onNewTab(showIncognitoOnly) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (showIncognitoOnly) StealthViolet else SapphireBlue,
                                contentColor = StarlightWhite,
                            ),
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (showIncognitoOnly) "ایجاد تب ناشناس" else "ایجاد تب جدید")
                        }
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 160.dp),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    items(displayedTabs, key = { it.id }) { tab ->
                        TabGridCard(
                            tab = tab,
                            isActive = tab.id == activeTabId,
                            onSelect = { onSelectTab(tab.id) },
                            onClose = { onCloseTab(tab.id) },
                        )
                    }
                }
            }

            // Bottom Frosted Glass Actions Dock
            Surface(
                tonalElevation = 0.dp,
                color = if (isDark) ObsidianSurface.copy(alpha = 0.78f) else MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    PrismHairline(isIncognito = showIncognitoOnly)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Button(
                            onClick = { onNewTab(false) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ElectricCyan,
                                contentColor = Color(0xFF031524),
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("new_normal_tab_button"),
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("تب جدید", fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = { onNewTab(true) },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("new_incognito_tab_button"),
                        ) {
                            Icon(Icons.Default.VisibilityOff, contentDescription = null, tint = StealthViolet)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("تب ناشناس", color = StealthViolet)
                        }

                        if (incognitoTabs.isNotEmpty()) {
                            IconButton(
                                onClick = onCloseAllIncognito,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.14f))
                                    .testTag("burn_incognito_tabs_button"),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteSweep,
                                    contentDescription = "پاکسازی همه تب‌های ناشناس",
                                    tint = MaterialTheme.colorScheme.error,
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
private fun TabGridCard(
    tab: BrowserTab,
    isActive: Boolean,
    onSelect: () -> Unit,
    onClose: () -> Unit,
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.25f
    val targetBorder = when {
        isActive && tab.isIncognito -> StealthViolet
        isActive -> ElectricCyan
        else -> SapphireGlow.copy(alpha = 0.35f)
    }
    val borderColor by animateColorAsState(
        targetValue = targetBorder,
        animationSpec = tween(180),
        label = "tab_border",
    )
    val cardBrush = when {
        !isDark && tab.isIncognito -> Brush.verticalGradient(listOf(Color(0xFFEDE9FE), Color(0xFFF5F3FF)))
        !isDark -> Brush.verticalGradient(listOf(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.surface))
        tab.isIncognito -> Brush.verticalGradient(listOf(Color(0xFF1A1038).copy(alpha = 0.85f), Color(0xFF0B061C).copy(alpha = 0.90f)))
        else -> Brush.verticalGradient(listOf(Color(0xFF0A1436).copy(alpha = 0.84f), ObsidianSurface.copy(alpha = 0.88f)))
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(148.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(cardBrush)
            .border(
                width = if (isActive) 1.8.dp else 1.dp,
                brush = if (isActive) {
                    Brush.linearGradient(
                        if (tab.isIncognito) listOf(StealthViolet, NebulaMagenta, ChromeSilver)
                        else listOf(ElectricCyan, SapphireGlow, NebulaMagenta),
                    )
                } else {
                    Brush.linearGradient(listOf(borderColor, borderColor.copy(alpha = 0.5f)))
                },
                shape = RoundedCornerShape(20.dp),
            )
            .clickable(onClick = onSelect)
            .testTag("tab_card_${tab.id}"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = if (tab.isIncognito) Icons.Default.VisibilityOff else Icons.Default.Language,
                        contentDescription = null,
                        tint = if (tab.isIncognito) StealthViolet else ElectricCyan,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = tab.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "بستن تب",
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            Text(
                text = if (tab.isStartPage) "صفحه شروع کیهانی" else tab.url,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (tab.totalBlockedOnTab > 0) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = EmeraldShield,
                            modifier = Modifier.size(13.dp),
                        )
                        Text(
                            text = "${tab.totalBlockedOnTab} مسدود",
                            style = MaterialTheme.typography.labelSmall,
                            color = EmeraldShield,
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                if (isActive) {
                    Text(
                        text = "فعال",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (tab.isIncognito) StealthViolet else ElectricCyan,
                    )
                } else if (tab.isHibernated) {
                    Text(
                        text = "کم‌مصرف",
                        style = MaterialTheme.typography.labelSmall,
                        color = ChromeSilver.copy(alpha = 0.7f),
                    )
                }
            }
        }
    }
}
