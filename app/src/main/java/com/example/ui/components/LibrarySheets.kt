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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
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
import com.example.data.local.BookmarkEntity
import com.example.data.local.BrowserSettings
import com.example.data.local.DownloadEntity
import com.example.data.local.HistoryEntity
import com.example.data.local.SearchEngine
import com.example.data.local.ThemePreference
import com.example.ui.BrowserTab
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
    val context = LocalContext.current

    FullSheetScaffold(
        title = "مدیریت دانلودها (${downloads.size})",
        icon = Icons.Default.Download,
        onDismiss = onDismiss,
        trailingAction = {
            if (downloads.isNotEmpty()) {
                TextButton(onClick = onClearFinished) { Text("پاکسازی") }
            }
        },
    ) {
        if (downloads.isEmpty()) {
            EmptyStateBox(
                icon = Icons.Default.Download,
                title = "لیست دانلودها خالی است",
                subtitle = "فایل‌ها و ویدیوهای دانلود شده با جزئیات سرعت، توقف و ادامه اینجا نمایش داده می‌شوند.",
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(downloads, key = { it.id }) { dl ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
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
                                        " • ${formatBytes(dl.speedBytesPerSec)}/s"
                                    } else ""
                                    Text(
                                        text = "${dl.qualityLabel} • ${formatBytes(dl.downloadedBytes)}" +
                                            (if (dl.totalBytes > 0) " / ${formatBytes(dl.totalBytes)}" else "") +
                                            speedStr,
                                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }

                                when (dl.status) {
                                    "DOWNLOADING", "QUEUED" -> Row {
                                        if (dl.supportsResume) {
                                            IconButton(onClick = { onPause(dl) }) {
                                                Icon(Icons.Default.Pause, contentDescription = "توقف موقت", tint = ElectricCyan)
                                            }
                                        }
                                        IconButton(onClick = { onCancel(dl) }) {
                                            Icon(Icons.Default.Cancel, contentDescription = "لغو دانلود", tint = RoseDanger)
                                        }
                                    }
                                    "PAUSED" -> Row {
                                        IconButton(onClick = { onResume(dl) }) {
                                            Icon(Icons.Default.PlayArrow, contentDescription = "ادامه دانلود", tint = EmeraldShield)
                                        }
                                        IconButton(onClick = { onCancel(dl) }) {
                                            Icon(Icons.Default.Cancel, contentDescription = "لغو دانلود", tint = RoseDanger)
                                        }
                                    }
                                    "COMPLETED" -> {
                                        Row {
                                            if (dl.localUri.isNotBlank()) {
                                                IconButton(
                                                    onClick = {
                                                        runCatching {
                                                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                                                setDataAndType(Uri.parse(dl.localUri), dl.mimeType.ifBlank { "video/*" })
                                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                            }
                                                            context.startActivity(intent)
                                                        }
                                                    },
                                                ) {
                                                    Icon(Icons.Default.OpenInNew, contentDescription = "باز کردن فایل", tint = EmeraldShield)
                                                }
                                            }
                                            IconButton(onClick = { onDelete(dl) }) {
                                                Icon(Icons.Default.Delete, contentDescription = "حذف")
                                            }
                                        }
                                    }
                                    else -> Row {
                                        IconButton(onClick = { onResume(dl) }) {
                                            Icon(Icons.Default.Refresh, contentDescription = "تلاش مجدد", tint = ElectricCyan)
                                        }
                                        IconButton(onClick = { onDelete(dl) }) {
                                            Icon(Icons.Default.Delete, contentDescription = "حذف")
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
                                    Text("متوقف شده — آماده ادامه از ادامه حجم دریافت‌شده", style = MaterialTheme.typography.labelSmall, color = ElectricCyan)
                                }
                            } else {
                                val statusColor = when (dl.status) {
                                    "COMPLETED" -> EmeraldShield
                                    "FAILED" -> RoseDanger
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                                val statusText = when (dl.status) {
                                    "COMPLETED" -> "تکمیل شده • ذخیره در Downloads/SalviaBrowxser"
                                    "FAILED" -> "خطا در دانلود"
                                    else -> "لغو شده"
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
    var showAddDialog by remember { mutableStateOf(false) }

    FullSheetScaffold(
        title = "نشانک‌ها (${bookmarks.size})",
        icon = Icons.Default.Bookmark,
        onDismiss = onDismiss,
        trailingAction = {
            IconButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "افزودن نشانک")
            }
        },
    ) {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(bookmarks, key = { it.id }) { bm ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
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
                            Icon(Icons.Default.Delete, contentDescription = "حذف نشانک")
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
            title = { Text("افزودن نشانک جدید") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("عنوان") }, singleLine = true)
                    OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text("آدرس وب") }, singleLine = true)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("سنجاق در صفحه شروع")
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
                ) { Text("ذخیره") }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("انصراف") }
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
    FullSheetScaffold(
        title = "تاریخچه مرور (${history.size})",
        icon = Icons.Default.History,
        onDismiss = onDismiss,
        trailingAction = {
            if (history.isNotEmpty()) {
                IconButton(onClick = onClearAll) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = "پاکسازی کل تاریخچه", tint = RoseDanger)
                }
            }
        },
    ) {
        if (history.isEmpty()) {
            EmptyStateBox(
                icon = Icons.Default.History,
                title = "تاریخچه‌ای ثبت نشده است",
                subtitle = "بازدیدهای حالت ناشناس هرگز در تاریخچه ذخیره نمی‌شوند.",
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(history, key = { it.id }) { item ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
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
                                Icon(Icons.Default.Close, contentDescription = "حذف")
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
                    Text(tab.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        text = if (tab.isStartPage) "محیط امن SalviaBrowxser" else if (tab.isSecureHttps) "اتصال رمزنگاری‌شده HTTPS معتبر" else "اتصال غیررمزنگاری HTTP",
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
                        Text("تبلیغ مسدود", style = MaterialTheme.typography.labelSmall)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(tab.blockedTrackersCount.toString(), style = MaterialTheme.typography.titleLarge, color = ElectricCyan)
                        Text("ردیاب مسدود", style = MaterialTheme.typography.labelSmall)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (shieldActiveForSite) "فعال" else if (isSiteWhitelisted) "متوقف" else "خاموش",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (shieldActiveForSite) EmeraldShield else MaterialTheme.colorScheme.tertiary,
                        )
                        Text("سپر Aloha", style = MaterialTheme.typography.labelSmall)
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
                                text = "سپر ضدتبلیغ Aloha در این سایت",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            )
                            Text(
                                text = if (isSiteWhitelisted) "سپر برای $host متوقف شده (Whitelist)" else "مسدودسازی تبلیغ، پاپ‌آپ و ماینر روی $host",
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
                Text(if (tab.isDesktopMode) "تغییر به نمای موبایل" else "تغییر به نمای دسکتاپ")
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
    onSelectSearchEngine: (SearchEngine) -> Unit,
    onSelectTheme: (ThemePreference) -> Unit,
    onAddAdRule: (String, Boolean) -> Unit,
    onDeleteAdRule: (Long) -> Unit,
    onClearAllBrowsingData: () -> Unit,
    onDismiss: () -> Unit,
) {
    BackHandler(onBack = onDismiss)
    var newRuleText by remember { mutableStateOf("") }
    var newRuleIsWhitelist by remember { mutableStateOf(false) }

    FullSheetScaffold(
        title = "تنظیمات و حریم خصوصی",
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
            SectionTitle("سپر حریم خصوصی و مسدودسازی")
            SettingsSwitchRow("مسدودکننده تبلیغات پیشرفته (AdBlock)", "حذف بنرها، پاپ‌آپ‌ها و تگ‌های تبلیغاتی", settings.adBlockEnabled, onToggleAdBlock)
            SettingsSwitchRow("مسدودسازی ردیاب‌ها (Trackers)", "جلوگیری از اسکریپت‌های آمارگیر و ردیاب بین‌سایتی", settings.trackerBlockEnabled, onToggleTrackerBlock)
            SettingsSwitchRow("ارتقای خودکار به HTTPS", "تبدیل خودکار آدرس‌های ناامن HTTP به HTTPS", settings.httpsUpgradeEnabled, onToggleHttpsUpgrade)
            SettingsSwitchRow("مسدودسازی کوکی شخص ثالث در حالت ناشناس", "افزایش ایزولاسیون تب‌های ناشناس", settings.blockThirdPartyCookiesIncognito, onToggleIncognito3pCookies)
            SettingsSwitchRow("ارسال درخواست عدم ردیابی (Do Not Track)", "اعلام عدم تمایل به ردیابی به وب‌سایت‌ها", settings.doNotTrackEnabled, onToggleDoNotTrack)
            SettingsSwitchRow("اجرای جاوااسکریپت (JavaScript)", "برای ورود به جیمیل و وب‌سایت‌های مدرن ضروری است", settings.javaScriptEnabled, onToggleJavaScript)

            HorizontalDivider()
            SectionTitle("موتور جستجوی پیش‌فرض")
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
                    Text(engine.displayName, style = MaterialTheme.typography.bodyMedium)
                }
            }

            HorizontalDivider()
            SectionTitle("ظاهر مرورگر")
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
                    Text(theme.label, style = MaterialTheme.typography.bodyMedium)
                }
            }

            HorizontalDivider()
            SectionTitle("قوانین سفارشی AdBlock (پشتیبانی از EasyList)")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = newRuleText,
                    onValueChange = { newRuleText = it },
                    label = { Text("مثلاً ||ads.site^ یا ##.banner") },
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
                    Text("افزودن")
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("حالت استثنا (Whitelist به جای مسدودسازی)", style = MaterialTheme.typography.bodySmall)
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
                        Icon(Icons.Default.Delete, contentDescription = "حذف قانون")
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
                Text("پاکسازی کامل تاریخچه، کوکی‌ها و کش")
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
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(title, style = MaterialTheme.typography.titleLarge)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    trailingAction()
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "بستن")
                    }
                }
            }
            HorizontalDivider()
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
