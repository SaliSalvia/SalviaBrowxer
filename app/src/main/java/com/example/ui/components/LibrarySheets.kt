package com.example.ui.components

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.AdBlockRuleEntity
import com.example.data.local.AppLanguage
import com.example.data.local.BookmarkEntity
import com.example.data.local.BrowserSettings
import com.example.data.local.DownloadEntity
import com.example.data.local.HistoryEntity
import com.example.data.local.SearchEngine
import com.example.data.local.ThemePreference
import com.example.ui.BrowserTab
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldShield
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.RoseDanger

@Composable
fun DownloadsSheet(
    downloads: List<DownloadEntity>,
    onPause: (DownloadEntity) -> Unit,
    onResume: (DownloadEntity) -> Unit,
    onCancel: (DownloadEntity) -> Unit,
    onDelete: (DownloadEntity) -> Unit,
    onClearFinished: () -> Unit,
    onDismiss: () -> Unit,
) {
    BackHandler(onBack = onDismiss)
    val s = LocalAppStrings.current
    val context = LocalContext.current

    FullSheetScaffold(
        title = String.format(s.downloadsTitleFormat, downloads.size),
        icon = Icons.Default.Download,
        onDismiss = onDismiss,
        trailingAction = {
            if (downloads.isNotEmpty()) {
                TextButton(onClick = onClearFinished) { Text(s.clearAction) }
            }
        },
    ) {
        if (downloads.isEmpty()) {
            EmptyStateBox(
                icon = Icons.Default.Download,
                title = s.downloadsEmptyTitle,
                subtitle = s.downloadsEmptySubtitle,
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(downloads, key = { it.id }) { dl ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(glassCardModifier(cornerRadius = 18.dp, isActive = dl.status == "DOWNLOADING")),
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = dl.fileName,
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    val speedStr = if (dl.status == "DOWNLOADING" && dl.speedBytesPerSec > 0) {
                                        " • ${formatBytes(dl.speedBytesPerSec, s.unknownSize)}/s"
                                    } else ""
                                    Text(
                                        text = "${dl.qualityLabel} • ${formatBytes(dl.downloadedBytes, s.unknownSize)}" +
                                            (if (dl.totalBytes > 0) " / ${formatBytes(dl.totalBytes, s.unknownSize)}" else "") +
                                            speedStr,
                                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }

                                when (dl.status) {
                                    "DOWNLOADING", "QUEUED" -> Row {
                                        if (dl.supportsResume) {
                                            IconButton(onClick = { onPause(dl) }) {
                                                Icon(Icons.Default.Pause, contentDescription = s.pauseDesc, tint = ElectricCyan)
                                            }
                                        }
                                        IconButton(onClick = { onCancel(dl) }) {
                                            Icon(Icons.Default.Cancel, contentDescription = s.cancelDownloadDesc, tint = RoseDanger)
                                        }
                                    }
                                    "PAUSED" -> Row {
                                        IconButton(onClick = { onResume(dl) }) {
                                            Icon(Icons.Default.PlayArrow, contentDescription = s.resumeDesc, tint = EmeraldShield)
                                        }
                                        IconButton(onClick = { onCancel(dl) }) {
                                            Icon(Icons.Default.Cancel, contentDescription = s.cancelDownloadDesc, tint = RoseDanger)
                                        }
                                    }
                                    "COMPLETED" -> {
                                        Row {
                                            if (dl.localUri.isNotBlank()) {
                                                IconButton(
                                                    onClick = {
                                                        // FileProvider/MediaStore URI only; raw file:// would crash on API 24+.
                                                        val intent = com.example.engine.DownloadFileAccess.openIntent(
                                                            context, dl.localUri, dl.mimeType, dl.fileName,
                                                        )
                                                        val opened = intent != null && runCatching { context.startActivity(intent) }.isSuccess
                                                        if (!opened) {
                                                            android.widget.Toast.makeText(context, s.toastOpenFileFailed, android.widget.Toast.LENGTH_SHORT).show()
                                                        }
                                                    },
                                                ) {
                                                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = s.openFileDesc, tint = EmeraldShield)
                                                }
                                                IconButton(
                                                    onClick = {
                                                        com.example.engine.DownloadFileAccess.shareIntent(
                                                            context, dl.localUri, dl.mimeType, dl.fileName,
                                                        )?.let { share -> runCatching { context.startActivity(share) } }
                                                    },
                                                ) {
                                                    Icon(Icons.Default.Share, contentDescription = s.ctxShareLink, tint = ElectricCyan)
                                                }
                                            }
                                            IconButton(onClick = { onDelete(dl) }) {
                                                Icon(Icons.Default.Delete, contentDescription = s.deleteButton)
                                            }
                                        }
                                    }
                                    else -> Row {
                                        IconButton(onClick = { onResume(dl) }) {
                                            Icon(Icons.Default.Refresh, contentDescription = s.retryDesc, tint = ElectricCyan)
                                        }
                                        IconButton(onClick = { onDelete(dl) }) {
                                            Icon(Icons.Default.Delete, contentDescription = s.deleteButton)
                                        }
                                    }
                                }
                            }

                            if (dl.status == "DOWNLOADING" || dl.status == "QUEUED" || dl.status == "PAUSED") {
                                val progress = if (dl.totalBytes > 0L) {
                                    (dl.downloadedBytes.toFloat() / dl.totalBytes.toFloat()).coerceIn(0f, 1f)
                                } else {
                                    null
                                }
                                if (progress != null) {
                                    LinearProgressIndicator(
                                        progress = { progress },
                                        modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)),
                                    )
                                } else {
                                    LinearProgressIndicator(
                                        modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)),
                                    )
                                }
                                if (dl.status == "PAUSED") {
                                    Text(s.pausedResumeHint, style = MaterialTheme.typography.labelSmall, color = ElectricCyan)
                                }
                            } else {
                                val statusColor = when (dl.status) {
                                    "COMPLETED" -> EmeraldShield
                                    "FAILED" -> RoseDanger
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                                val statusText = when (dl.status) {
                                    "COMPLETED" -> s.downloadCompletedText
                                    "FAILED" -> s.downloadFailedText
                                    else -> s.downloadCancelledText
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(
                                        imageVector = if (dl.status == "COMPLETED") Icons.Default.CheckCircle else Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = statusColor,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Text(statusText, style = MaterialTheme.typography.labelSmall, color = statusColor)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BookmarksSheet(
    bookmarks: List<BookmarkEntity>,
    onOpenUrl: (String) -> Unit,
    onAddBookmark: (String, String, Boolean) -> Unit,
    onDeleteBookmark: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    BackHandler(onBack = onDismiss)
    val s = LocalAppStrings.current
    var showAddDialog by remember { mutableStateOf(false) }

    FullSheetScaffold(
        title = String.format(s.bookmarksTitleFormat, bookmarks.size),
        icon = Icons.Default.Bookmark,
        onDismiss = onDismiss,
        trailingAction = {
            IconButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = s.addBookmarkDesc)
            }
        },
    ) {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(bookmarks, key = { it.id }) { bm ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(glassCardModifier(cornerRadius = 16.dp))
                        .clickable {
                            onOpenUrl(bm.url)
                            onDismiss()
                        },
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(bm.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                bm.url,
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        IconButton(onClick = { onDeleteBookmark(bm.id) }) {
                            Icon(Icons.Default.Delete, contentDescription = s.removeBookmarkDesc)
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        var title by remember { mutableStateOf("") }
        var url by remember { mutableStateOf("") }
        var pinned by remember { mutableStateOf(true) }
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text(s.newBookmarkDialogTitle) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text(s.bookmarkTitleLabel) }, singleLine = true)
                    OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text(s.bookmarkUrlLabel) }, singleLine = true)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text(s.pinStartPageLabel)
                        Switch(checked = pinned, onCheckedChange = { pinned = it })
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onAddBookmark(title, url, pinned)
                        showAddDialog = false
                    },
                    enabled = url.isNotBlank(),
                ) { Text(s.saveButton) }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text(s.cancelButton) }
            },
        )
    }
}

@Composable
fun HistorySheet(
    history: List<HistoryEntity>,
    onOpenUrl: (String) -> Unit,
    onDeleteItem: (Long) -> Unit,
    onClearAll: () -> Unit,
    onDismiss: () -> Unit,
) {
    BackHandler(onBack = onDismiss)
    val s = LocalAppStrings.current
    FullSheetScaffold(
        title = String.format(s.historyTitleFormat, history.size),
        icon = Icons.Default.History,
        onDismiss = onDismiss,
        trailingAction = {
            if (history.isNotEmpty()) {
                IconButton(onClick = onClearAll) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = s.clearHistoryDesc, tint = RoseDanger)
                }
            }
        },
    ) {
        if (history.isEmpty()) {
            EmptyStateBox(
                icon = Icons.Default.History,
                title = s.historyEmptyTitle,
                subtitle = s.historyEmptySubtitle,
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(history, key = { it.id }) { item ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(glassCardModifier(cornerRadius = 16.dp))
                            .clickable {
                                onOpenUrl(item.url)
                                onDismiss()
                            },
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    item.url,
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            IconButton(onClick = { onDeleteItem(item.id) }) {
                                Icon(Icons.Default.Close, contentDescription = s.deleteButton)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SiteInfoSheet(
    tab: BrowserTab,
    adBlockEnabled: Boolean,
    isSiteWhitelisted: Boolean,
    onToggleSiteAdBlock: (String) -> Unit,
    onToggleDesktopMode: () -> Unit,
    onDismiss: () -> Unit,
) {
    BackHandler(onBack = onDismiss)
    val s = LocalAppStrings.current
    val host = remember(tab.url) { runCatching { Uri.parse(tab.url).host.orEmpty() }.getOrDefault("") }
    val shieldActiveForSite = adBlockEnabled && !isSiteWhitelisted

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(
                    imageVector = if (tab.isSecureHttps || tab.isStartPage) Icons.Default.Lock else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (tab.isSecureHttps || tab.isStartPage) EmeraldShield else MaterialTheme.colorScheme.tertiary,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (tab.isStartPage) {
                            if (tab.isIncognito) s.incognitoStartTitle else s.startPageTitle
                        } else tab.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = if (tab.isStartPage) s.secureEnvLabel else if (tab.isSecureHttps) s.httpsValidLabel else s.httpUnencryptedLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (tab.isSecureHttps || tab.isStartPage) EmeraldShield else MaterialTheme.colorScheme.tertiary,
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(tab.blockedAdsCount.toString(), style = MaterialTheme.typography.titleLarge, color = EmeraldShield)
                        Text(s.statAdsBlocked, style = MaterialTheme.typography.labelSmall)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(tab.blockedTrackersCount.toString(), style = MaterialTheme.typography.titleLarge, color = ElectricCyan)
                        Text(s.statTrackersBlocked, style = MaterialTheme.typography.labelSmall)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (shieldActiveForSite) s.statusActive else if (isSiteWhitelisted) s.statusPaused else s.statusOff,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (shieldActiveForSite) EmeraldShield else MaterialTheme.colorScheme.tertiary,
                        )
                        Text(s.adBlockerLabel, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            if (!tab.isStartPage && host.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(
                                text = s.siteAdBlockerTitle,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            )
                            Text(
                                text = if (isSiteWhitelisted) String.format(s.siteAdBlockerPausedFormat, host) else String.format(s.siteAdBlockerActiveFormat, host),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Switch(
                            checked = shieldActiveForSite,
                            onCheckedChange = {
                                onToggleSiteAdBlock(host)
                                onDismiss()
                            },
                            modifier = Modifier.testTag("site_adblock_shield_switch"),
                        )
                    }
                }
            }

            OutlinedButton(
                onClick = {
                    onToggleDesktopMode()
                    onDismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("site_toggle_desktop_button"),
            ) {
                Text(if (tab.isDesktopMode) s.switchToMobileView else s.switchToDesktopView)
            }
        }
    }
}

@Composable
fun SettingsSheet(
    settings: BrowserSettings,
    customAdRules: List<AdBlockRuleEntity>,
    onToggleAdBlock: (Boolean) -> Unit,
    onToggleTrackerBlock: (Boolean) -> Unit,
    onToggleHttpsUpgrade: (Boolean) -> Unit,
    onToggleIncognito3pCookies: (Boolean) -> Unit,
    onToggleJavaScript: (Boolean) -> Unit,
    onToggleDoNotTrack: (Boolean) -> Unit,
    onTogglePullToRefresh: (Boolean) -> Unit,
    onSelectSearchEngine: (SearchEngine) -> Unit,
    onSelectTheme: (ThemePreference) -> Unit,
    onSelectLanguage: (AppLanguage) -> Unit,
    onAddAdRule: (String, Boolean) -> Unit,
    onDeleteAdRule: (Long) -> Unit,
    onClearAllBrowsingData: () -> Unit,
    onDismiss: () -> Unit,
) {
    BackHandler(onBack = onDismiss)
    val s = LocalAppStrings.current
    var newRuleText by remember { mutableStateOf("") }
    var newRuleIsWhitelist by remember { mutableStateOf(false) }

    FullSheetScaffold(
        title = s.settingsTitle,
        icon = Icons.Default.Settings,
        onDismiss = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SectionTitle(s.settingsLanguageSection)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AppLanguage.entries.forEach { lang ->
                    val selected = settings.appLanguage == lang
                    if (selected) {
                        Button(
                            onClick = { onSelectLanguage(lang) },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("language_option_${lang.code}"),
                        ) {
                            Text(lang.nativeLabel, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        OutlinedButton(
                            onClick = { onSelectLanguage(lang) },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("language_option_${lang.code}"),
                        ) {
                            Text(lang.nativeLabel)
                        }
                    }
                }
            }

            HorizontalDivider()
            SectionTitle(s.settingsPrivacySection)
            SettingsSwitchRow(s.settingAdBlockTitle, s.settingAdBlockSub, settings.adBlockEnabled, onToggleAdBlock)
            SettingsSwitchRow(s.settingTrackerTitle, s.settingTrackerSub, settings.trackerBlockEnabled, onToggleTrackerBlock)
            SettingsSwitchRow(s.settingHttpsTitle, s.settingHttpsSub, settings.httpsUpgradeEnabled, onToggleHttpsUpgrade)
            SettingsSwitchRow(s.settingIncognitoCookieTitle, s.settingIncognitoCookieSub, settings.blockThirdPartyCookiesIncognito, onToggleIncognito3pCookies)
            SettingsSwitchRow(s.settingDntTitle, s.settingDntSub, settings.doNotTrackEnabled, onToggleDoNotTrack)
            SettingsSwitchRow(s.settingJsTitle, s.settingJsSub, settings.javaScriptEnabled, onToggleJavaScript)
            SettingsSwitchRow(s.settingPullToRefreshTitle, s.settingPullToRefreshSub, settings.pullToRefreshEnabled, onTogglePullToRefresh)

            HorizontalDivider()
            SectionTitle(s.settingsSearchEngineSection)
            SearchEngine.entries.forEach { engine ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onSelectSearchEngine(engine) }
                        .padding(vertical = 4.dp),
                ) {
                    RadioButton(selected = settings.searchEngine == engine, onClick = { onSelectSearchEngine(engine) })
                    Text(s.searchEngineLabel(engine), style = MaterialTheme.typography.bodyMedium)
                }
            }

            HorizontalDivider()
            SectionTitle(s.settingsThemeSection)
            ThemePreference.entries.forEach { theme ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onSelectTheme(theme) }
                        .padding(vertical = 4.dp),
                ) {
                    RadioButton(selected = settings.themePreference == theme, onClick = { onSelectTheme(theme) })
                    Text(s.themeLabel(theme), style = MaterialTheme.typography.bodyMedium)
                }
            }

            HorizontalDivider()
            SectionTitle(s.settingsCustomRulesSection)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = newRuleText,
                    onValueChange = { newRuleText = it },
                    label = { Text(s.customRuleHint) },
                    singleLine = true,
                    modifier = Modifier.weight(1f).testTag("custom_adrule_input"),
                )
                Button(
                    onClick = {
                        onAddAdRule(newRuleText, newRuleIsWhitelist)
                        newRuleText = ""
                    },
                    enabled = newRuleText.isNotBlank(),
                    modifier = Modifier.height(48.dp).testTag("add_adrule_button"),
                ) {
                    Text(s.addButton)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(s.whitelistModeLabel, style = MaterialTheme.typography.bodySmall)
                Switch(checked = newRuleIsWhitelist, onCheckedChange = { newRuleIsWhitelist = it })
            }
            customAdRules.forEach { rule ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = (if (rule.isWhitelist) "[Whitelist] " else "[Block] ") + rule.pattern,
                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                    )
                    IconButton(onClick = { onDeleteAdRule(rule.id) }) {
                        Icon(Icons.Default.Delete, contentDescription = s.deleteRuleDesc)
                    }
                }
            }

            HorizontalDivider()
            Button(
                onClick = onClearAllBrowsingData,
                colors = ButtonDefaults.buttonColors(containerColor = RoseDanger),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("clear_all_browsing_data_button"),
            ) {
                Icon(Icons.Default.DeleteSweep, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(s.clearAllDataButton)
            }
        }
    }
}

@Composable
private fun FullSheetScaffold(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onDismiss: () -> Unit,
    trailingAction: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {
    val s = LocalAppStrings.current
    CosmicSpaceBackdrop {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.65f))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(icon, contentDescription = null, tint = ElectricCyan)
                    Text(title, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    trailingAction()
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = s.closeDesc)
                    }
                }
            }
            PrismHairline(alpha = 0.8f)
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                content()
            }
        }
    }
}

@Composable
private fun EmptyStateBox(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
) {
    Box(modifier = Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(52.dp))
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
