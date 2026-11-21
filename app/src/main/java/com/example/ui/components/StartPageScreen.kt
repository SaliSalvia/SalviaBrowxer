package com.example.ui.components

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeveloperMode
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.local.BookmarkEntity
import com.example.data.local.BrowserSettings
import com.example.ui.ActiveSheet
import com.example.ui.theme.AlexBrushFontFamily
import com.example.ui.theme.CosmicViolet
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.MetallicGold
import com.example.ui.theme.NebulaMagenta
import com.example.ui.theme.StealthViolet

@Composable
fun StartPageScreen(
    isIncognito: Boolean,
    settings: BrowserSettings,
    bookmarks: List<BookmarkEntity>,
    activeExtensionsCount: Int,
    onNavigate: (String) -> Unit,
    onOpenSheet: (ActiveSheet) -> Unit,
    onNewIncognitoTab: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var searchText by remember { mutableStateOf("") }
    val speedDialItems = remember(bookmarks) {
        val pinned = bookmarks.filter { it.isPinnedSpeedDial }
        if (pinned.isNotEmpty()) pinned else bookmarks.take(8)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter,
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            // SalviaBrowxser Signature Hero Card
            item {
                val borderBrush = if (isIncognito) {
                    Brush.linearGradient(listOf(StealthViolet, NebulaMagenta))
                } else {
                    Brush.linearGradient(listOf(CosmicViolet, ElectricCyan, MetallicGold.copy(alpha = 0.7f)))
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(26.dp))
                        .background(Color(0xFF06060C))
                        .border(width = 1.2.dp, brush = borderBrush, shape = RoundedCornerShape(26.dp)),
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.salvia_cosmic_banner_1795066311285),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .matchParentSize()
                            .alpha(0.55f),
                    )

                    Column(
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.padding(20.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_salvia_emblem_1795066312153),
                                contentDescription = "لوگوی اختصاصی SalviaBrowxser",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .border(1.5.dp, MetallicGold.copy(alpha = 0.8f), CircleShape),
                            )

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "SalviaBrowxser",
                                    style = TextStyle(
                                        fontFamily = AlexBrushFontFamily,
                                        fontSize = 32.sp,
                                        brush = Brush.horizontalGradient(
                                            listOf(ElectricCyan, Color(0xFFE0E7FF), NebulaMagenta),
                                        ),
                                    ),
                                    maxLines = 1,
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(
                                        text = if (isIncognito) "حالت ناشناس کیهانی" else "Salar Farzaneh",
                                        style = if (isIncognito) {
                                            MaterialTheme.typography.labelMedium
                                        } else {
                                            TextStyle(
                                                fontFamily = AlexBrushFontFamily,
                                                fontSize = 18.sp,
                                                color = MetallicGold,
                                            )
                                        },
                                    )
                                    if (!isIncognito) {
                                        Text(
                                            text = "• مرورگر کرومیوم امن",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }

                        if (isIncognito) {
                            Text(
                                text = "مرور کاملاً ایزوله فعال است؛ هیچ تاریخچه، کوکی ماندگار یا فرم ورودی روی دستگاه ذخیره نمی‌شود.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFD8B4FE),
                            )
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                StatPill(
                                    label = "تبلیغات مسدود",
                                    value = settings.totalAdsBlockedLifetime.toString(),
                                    accent = ElectricCyan,
                                    modifier = Modifier.weight(1f),
                                )
                                StatPill(
                                    label = "ردیاب مسدود",
                                    value = settings.totalTrackersBlockedLifetime.toString(),
                                    accent = NebulaMagenta,
                                    modifier = Modifier.weight(1f),
                                )
                                StatPill(
                                    label = "افزونه‌ها",
                                    value = activeExtensionsCount.toString(),
                                    accent = MetallicGold,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }

            // Central Search Pill
            item {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    tonalElevation = 4.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .border(
                            1.dp,
                            Brush.horizontalGradient(
                                listOf(CosmicViolet.copy(alpha = 0.7f), ElectricCyan.copy(alpha = 0.6f)),
                            ),
                            RoundedCornerShape(24.dp),
                        ),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "جستجو",
                            tint = ElectricCyan,
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        BasicTextField(
                            value = searchText,
                            onValueChange = { searchText = it },
                            singleLine = true,
                            textStyle = TextStyle(
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 15.sp,
                            ),
                            cursorBrush = SolidColor(ElectricCyan),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(
                                onSearch = {
                                    if (searchText.isNotBlank()) {
                                        onNavigate(searchText)
                                    }
                                },
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("start_page_search_input"),
                            decorationBox = { inner ->
                                if (searchText.isEmpty()) {
                                    Text(
                                        text = "جستجو با ${settings.searchEngine.displayName} یا آدرس وب…",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                inner()
                            },
                        )
                        if (searchText.isNotEmpty()) {
                            IconButton(
                                onClick = { searchText = "" },
                                modifier = Modifier.size(34.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "پاک کردن جستجو",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                            IconButton(
                                onClick = { onNavigate(searchText) },
                                modifier = Modifier.size(36.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = "جستجو و رفتن",
                                    tint = ElectricCyan,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
            }

            // Quick Tools Row
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AssistChip(
                        onClick = { onOpenSheet(ActiveSheet.EXTENSIONS) },
                        label = { Text("افزونه‌ها") },
                        leadingIcon = { Icon(Icons.Default.Extension, contentDescription = null, modifier = Modifier.size(18.dp), tint = NebulaMagenta) },
                        modifier = Modifier.weight(1f).testTag("start_extensions_chip"),
                    )
                    AssistChip(
                        onClick = { onOpenSheet(ActiveSheet.DOWNLOADS) },
                        label = { Text("دانلودها") },
                        leadingIcon = { Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp), tint = MetallicGold) },
                        modifier = Modifier.weight(1f).testTag("start_downloads_chip"),
                    )
                    AssistChip(
                        onClick = { onOpenSheet(ActiveSheet.DEVTOOLS) },
                        label = { Text("DevTools") },
                        leadingIcon = { Icon(Icons.Default.DeveloperMode, contentDescription = null, modifier = Modifier.size(18.dp), tint = ElectricCyan) },
                        modifier = Modifier.weight(1f).testTag("start_devtools_chip"),
                    )
                }
            }

            // Speed Dial Grid
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "دسترسی سریع و ورود امن",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    if (!isIncognito) {
                        Text(
                            text = "+ تب ناشناس",
                            style = MaterialTheme.typography.labelLarge,
                            color = StealthViolet,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(onClick = onNewIncognitoTab)
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))

                val rowsList = remember(speedDialItems) { speedDialItems.chunked(4) }
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    rowsList.forEach { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            rowItems.forEach { item ->
                                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                    SpeedDialTile(
                                        bookmark = item,
                                        onClick = { onNavigate(item.url) },
                                    )
                                }
                            }
                            repeat(4 - rowItems.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatPill(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF101224).copy(alpha = 0.85f))
            .border(0.8.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(
                fontFamily = JetBrainsMonoFontFamily,
                fontWeight = FontWeight.Bold,
            ),
            color = accent,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SpeedDialTile(
    bookmark: BookmarkEntity,
    onClick: () -> Unit,
) {
    val icon: ImageVector = when {
        bookmark.url.contains("mail.google") -> Icons.Default.Mail
        bookmark.url.contains("youtube") || bookmark.url.contains("aparat") -> Icons.Default.PlayCircle
        bookmark.url.contains("duckduckgo") -> Icons.Default.Security
        bookmark.url.contains("github") || bookmark.url.contains("developer.mozilla") -> Icons.Default.DeveloperMode
        else -> Icons.Default.Language
    }

    val badgeColor = when {
        bookmark.url.contains("mail.google") -> Color(0xFFEA4335)
        bookmark.url.contains("youtube") -> Color(0xFFFF2E4C)
        bookmark.url.contains("aparat") -> NebulaMagenta
        bookmark.url.contains("github") -> ElectricCyan
        bookmark.url.contains("duckduckgo") -> MetallicGold
        else -> CosmicViolet
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(6.dp)
            .testTag("speed_dial_${bookmark.title.lowercase().replace(" ", "_")}"),
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(badgeColor.copy(alpha = 0.16f))
                .border(1.dp, badgeColor.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = bookmark.title,
                tint = badgeColor,
                modifier = Modifier.size(26.dp),
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = bookmark.title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}
