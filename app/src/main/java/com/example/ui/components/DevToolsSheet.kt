package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DeveloperMode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.ui.BrowserTab
import com.example.ui.ConsoleLogEntry
import com.example.ui.DevToolsTab
import com.example.ui.NetworkRequestEntry
import com.example.ui.theme.AmberWarn
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldShield
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.RoseDanger

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevToolsSheet(
    tab: BrowserTab,
    selectedTab: DevToolsTab,
    onSelectDevToolsTab: (DevToolsTab) -> Unit,
    onEvaluateJs: (String) -> Unit,
    onClearConsole: () -> Unit,
    onClearNetwork: () -> Unit,
    onRefreshDom: () -> Unit,
    onCopyFeedback: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    BackHandler(onBack = onDismiss)

    CosmicSpaceBackdrop(isIncognito = tab.isIncognito) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Default.DeveloperMode, contentDescription = null, tint = ElectricCyan)
                    Column {
                        Text("ابزارهای توسعه‌دهنده (DevTools)", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = tab.url.ifBlank { "صفحه شروع" },
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("close_devtools_button"),
                ) {
                    Icon(Icons.Default.Close, contentDescription = "بستن DevTools")
                }
            }

            PrimaryTabRow(
                selectedTabIndex = selectedTab.ordinal,
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
            ) {
                DevToolsTab.entries.forEach { devTab ->
                    Tab(
                        selected = selectedTab == devTab,
                        onClick = { onSelectDevToolsTab(devTab) },
                        text = { Text(devTab.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    )
                }
            }
            PrismHairline(isIncognito = tab.isIncognito, alpha = 0.75f)

            when (selectedTab) {
                DevToolsTab.CONSOLE -> ConsolePane(
                    logs = tab.consoleLogs,
                    onEvaluateJs = onEvaluateJs,
                    onClear = onClearConsole,
                    modifier = Modifier.weight(1f),
                )
                DevToolsTab.NETWORK -> NetworkPane(
                    requests = tab.networkRequests,
                    onClear = onClearNetwork,
                    modifier = Modifier.weight(1f),
                )
                DevToolsTab.DOM -> DomPane(
                    tab = tab,
                    onRefresh = onRefreshDom,
                    onCopy = onCopyFeedback,
                    modifier = Modifier.weight(1f),
                )
                DevToolsTab.STORAGE -> StoragePane(
                    tab = tab,
                    onRefresh = onRefreshDom,
                    onCopy = onCopyFeedback,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun ConsolePane(
    logs: List<ConsoleLogEntry>,
    onEvaluateJs: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var jsInput by remember { mutableStateOf("") }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "لاگ‌های کنسول (${logs.size})",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            IconButton(onClick = onClear) {
                Icon(Icons.Default.DeleteOutline, contentDescription = "پاکسازی کنسول")
            }
        }
        HorizontalDivider()

        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            LazyColumn(
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f),
            ) {
                if (logs.isEmpty()) {
                    item {
                        Text(
                            text = "// No console output yet. Run JavaScript below (e.g. document.title)",
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(logs, key = { it.id }) { entry ->
                    val color = when (entry.level) {
                        "ERROR" -> RoseDanger
                        "WARN", "WARNING" -> AmberWarn
                        "EVAL" -> ElectricCyan
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = "[${entry.level}]",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontWeight = FontWeight.Bold,
                                    ),
                                    color = color,
                                )
                                Text(
                                    text = entry.source,
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                text = entry.message,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                color = color,
                            )
                        }
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(10.dp),
            ) {
                OutlinedTextField(
                    value = jsInput,
                    onValueChange = { jsInput = it },
                    placeholder = {
                        Text(
                            "document.querySelectorAll('video').length",
                            fontFamily = JetBrainsMonoFontFamily,
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            onEvaluateJs(jsInput)
                            jsInput = ""
                        },
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("devtools_js_input"),
                )
                IconButton(
                    onClick = {
                        onEvaluateJs(jsInput)
                        jsInput = ""
                    },
                    modifier = Modifier.testTag("devtools_js_run_button"),
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "اجرای کد جاوااسکریپت", tint = ElectricCyan)
                }
            }
        }
    }
}

@Composable
private fun NetworkPane(
    requests: List<NetworkRequestEntry>,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var filter by remember { mutableStateOf("ALL") }
    val filtered = remember(requests, filter) {
        when (filter) {
            "BLOCKED" -> requests.filter { it.isBlocked }
            "MEDIA" -> requests.filter { it.isMediaDetected || it.resourceType == "MEDIA" }
            "XHR" -> requests.filter { it.resourceType == "XHR" }
            else -> requests
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
            ) {
                FilterChip(selected = filter == "ALL", onClick = { filter = "ALL" }, label = { Text("همه (${requests.size})") })
                FilterChip(selected = filter == "BLOCKED", onClick = { filter = "BLOCKED" }, label = { Text("مسدودشده") })
                FilterChip(selected = filter == "MEDIA", onClick = { filter = "MEDIA" }, label = { Text("مدیا") })
                FilterChip(selected = filter == "XHR", onClick = { filter = "XHR" }, label = { Text("XHR/API") })
            }
            IconButton(onClick = onClear) {
                Icon(Icons.Default.DeleteOutline, contentDescription = "پاکسازی شبکه")
            }
        }
        HorizontalDivider()

        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            LazyColumn(
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(filtered, key = { it.id }) { req ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (req.isBlocked) RoseDanger.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = req.method,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontWeight = FontWeight.Bold,
                                    ),
                                    color = ElectricCyan,
                                )
                                Text(
                                    text = req.resourceType,
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (req.isBlocked) {
                                    Text(
                                        text = "BLOCKED (${req.blockReason})",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = JetBrainsMonoFontFamily,
                                            fontWeight = FontWeight.Bold,
                                        ),
                                        color = RoseDanger,
                                    )
                                } else if (req.isMediaDetected) {
                                    Text(
                                        text = "MEDIA",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = JetBrainsMonoFontFamily,
                                            fontWeight = FontWeight.Bold,
                                        ),
                                        color = EmeraldShield,
                                    )
                                }
                            }
                            Text(
                                text = req.url,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (req.headersSummary.isNotBlank()) {
                                Text(
                                    text = req.headersSummary,
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
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
private fun DomPane(
    tab: BrowserTab,
    onRefresh: () -> Unit,
    onCopy: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val clipboard = LocalClipboardManager.current
    val snap = tab.domSnapshot

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("HTML DOM و متا تگ‌ها", style = MaterialTheme.typography.labelMedium)
            Row {
                IconButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(snap.htmlSource))
                        onCopy("کد سورس HTML کپی شد")
                    },
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "کپی سورس HTML")
                }
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, contentDescription = "به‌روزرسانی DOM")
                }
            }
        }
        HorizontalDivider()

        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (snap.metaTags.isNotEmpty()) {
                    Text(
                        text = "Meta & OpenGraph Tags (${snap.metaTags.size})",
                        style = MaterialTheme.typography.labelMedium,
                        color = ElectricCyan,
                    )
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            snap.metaTags.forEach { (k, v) ->
                                Text(
                                    text = "$k = $v",
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                )
                            }
                        }
                    }
                }

                Text(
                    text = "Document HTML Source",
                    style = MaterialTheme.typography.labelMedium,
                    color = EmeraldShield,
                )
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF060910),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = snap.htmlSource.ifBlank { "یک صفحه وب را بارگذاری کنید و دکمه Refresh را بزنید." },
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                        color = Color(0xFFCBD5E1),
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun StoragePane(
    tab: BrowserTab,
    onRefresh: () -> Unit,
    onCopy: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val clipboard = LocalClipboardManager.current
    val snap = tab.domSnapshot

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("کوکی‌ها، LocalStorage و User-Agent", style = MaterialTheme.typography.labelMedium)
            IconButton(onClick = onRefresh) {
                Icon(Icons.Default.Refresh, contentDescription = "به‌روزرسانی حافظه")
            }
        }
        HorizontalDivider()

        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StorageBlock("Viewport & DPR", snap.viewport.ifBlank { "N/A" }, onCopy = {
                    clipboard.setText(AnnotatedString(snap.viewport))
                    onCopy("کپی شد")
                })
                StorageBlock("User-Agent", snap.userAgent.ifBlank { "Default Chrome Mobile" }, onCopy = {
                    clipboard.setText(AnnotatedString(snap.userAgent))
                    onCopy("User-Agent کپی شد")
                })
                StorageBlock("Cookies", snap.cookies.ifBlank { "(هیچ کوکی فعالی ثبت نشده)" }, onCopy = {
                    clipboard.setText(AnnotatedString(snap.cookies))
                    onCopy("کوکی‌ها کپی شدند")
                })
                StorageBlock("localStorage", snap.localStorageJson, onCopy = {
                    clipboard.setText(AnnotatedString(snap.localStorageJson))
                    onCopy("localStorage کپی شد")
                })
                StorageBlock("sessionStorage", snap.sessionStorageJson, onCopy = {
                    clipboard.setText(AnnotatedString(snap.sessionStorageJson))
                    onCopy("sessionStorage کپی شد")
                })
            }
        }
    }
}

@Composable
private fun StorageBlock(
    title: String,
    value: String,
    onCopy: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = ElectricCyan,
                )
                IconButton(onClick = onCopy, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "کپی $title", modifier = Modifier.size(16.dp))
                }
            }
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
            )
        }
    }
}
