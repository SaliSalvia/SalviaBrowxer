package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.DeveloperMode
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.BrowserTab
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.theme.ChromeSilver
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.NebulaMagenta
import com.example.ui.theme.SapphireGlow

@Composable
fun BrowserBottomBar(
    tab: BrowserTab,
    tabCount: Int,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onHome: () -> Unit,
    onOpenExtensions: () -> Unit,
    onOpenTabSwitcher: () -> Unit,
    onOpenDevTools: () -> Unit,
) {
    val s = LocalAppStrings.current
    val haptic = LocalHapticFeedback.current
    val homeScale by animateFloatAsState(
        targetValue = if (tab.isStartPage) 1.14f else 1f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 420f),
        label = "home_scale",
    )
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.25f
    val dockColor = if (!isDark) {
        MaterialTheme.colorScheme.surface
    } else if (tab.isStartPage) {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.66f)
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
    }

    Surface(
        color = dockColor,
        tonalElevation = 0.dp,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            PrismHairline(isIncognito = tab.isIncognito, height = 1.dp, alpha = 0.8f)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(
                    onClick = onBack,
                    enabled = !tab.isStartPage || tab.canGoBack,
                    modifier = Modifier.testTag("nav_back_button"),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = s.navBackDesc,
                        tint = if (!tab.isStartPage || tab.canGoBack) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                        },
                    )
                }

                IconButton(
                    onClick = onForward,
                    enabled = tab.canGoForward,
                    modifier = Modifier.testTag("nav_forward_button"),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = s.navForwardDesc,
                        tint = if (tab.canGoForward) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                        },
                    )
                }

                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onHome()
                    },
                    modifier = Modifier.testTag("nav_home_button"),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Home,
                            contentDescription = s.navHomeDesc,
                            tint = if (tab.isStartPage) ElectricCyan else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.scale(homeScale),
                        )
                        if (tab.isStartPage) {
                            Box(
                                modifier = Modifier
                                    .padding(top = 2.dp)
                                    .size(4.dp)
                                    .clip(CircleShape)
                                    .background(ElectricCyan),
                            )
                        }
                    }
                }

                IconButton(
                    onClick = onOpenExtensions,
                    modifier = Modifier.testTag("nav_extensions_button"),
                ) {
                    Icon(
                        imageVector = Icons.Default.Extension,
                        contentDescription = s.navExtensionsDesc,
                        tint = if (tab.isIncognito) MaterialTheme.colorScheme.primary else NebulaMagenta,
                    )
                }

                // Tab Switcher Pill [ N ]
                Box(
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onOpenTabSwitcher()
                        }
                        .padding(8.dp)
                        .testTag("tab_switcher_button"),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .border(
                                width = 1.6.dp,
                                brush = Brush.linearGradient(
                                    if (tab.isIncognito) {
                                        listOf(MaterialTheme.colorScheme.primary, NebulaMagenta)
                                    } else {
                                        listOf(ChromeSilver, ElectricCyan, SapphireGlow)
                                    },
                                ),
                                shape = RoundedCornerShape(8.dp),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = tabCount.toString(),
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold,
                            ),
                            color = if (tab.isIncognito) MaterialTheme.colorScheme.primary else ElectricCyan,
                        )
                    }
                }

                // Quick DevTools Button
                IconButton(
                    onClick = onOpenDevTools,
                    modifier = Modifier.testTag("nav_devtools_button"),
                ) {
                    Icon(
                        imageVector = Icons.Default.DeveloperMode,
                        contentDescription = s.navDevToolsDesc,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
