package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.DeveloperMode
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.ActiveSheet
import com.example.ui.BrowserTab
import com.example.ui.theme.CosmicViolet
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.SapphireBlue
import com.example.ui.theme.SapphireGlow

@Composable
fun BrowserTopBar(
    tab: BrowserTab,
    isBookmarked: Boolean,
    adBlockEnabled: Boolean,
    activeExtensionsCount: Int,
    findInPageActive: Boolean,
    findQuery: String,
    onNavigate: (String) -> Unit,
    onReloadOrStop: () -> Unit,
    onOpenSheet: (ActiveSheet) -> Unit,
    onToggleBookmark: () -> Unit,
    onToggleDesktopMode: () -> Unit,
    onCreateIncognitoTab: () -> Unit,
    onFindQueryChange: (String) -> Unit,
    onFindNext: (Boolean) -> Unit,
    onCloseFind: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    var urlInput by remember(tab.id, tab.url) { mutableStateOf(tab.url) }
    var isFocused by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(tab.url, isFocused) {
        if (!isFocused) {
            urlInput = tab.url
        }
    }

    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.25f
    val barColor = if (!isDark) {
        MaterialTheme.colorScheme.surface
    } else if (tab.isStartPage) {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.62f)
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
    }

    Surface(
        color = barColor,
        tonalElevation = 0.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    // Shield / Incognito / Security Icon Pill
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (tab.isIncognito) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
                        } else if (adBlockEnabled) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)
                        },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clip(RoundedCornerShape(16.dp))
                            .border(
                                0.9.dp,
                                if (adBlockEnabled) ElectricCyan.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                RoundedCornerShape(16.dp),
                            )
                            .clickable { onOpenSheet(ActiveSheet.SITE_INFO) }
                            .testTag("site_shield_button"),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        ) {
                            Icon(
                                imageVector = when {
                                    tab.isIncognito -> Icons.Default.VisibilityOff
                                    tab.isStartPage -> Icons.Default.Shield
                                    tab.isSecureHttps -> Icons.Default.Lock
                                    else -> Icons.Default.Warning
                                },
                                contentDescription = "وضعیت امنیت و مسدودکننده تبلیغات",
                                tint = when {
                                    tab.isIncognito -> MaterialTheme.colorScheme.primary
                                    tab.isSecureHttps || tab.isStartPage -> ElectricCyan
                                    else -> MaterialTheme.colorScheme.tertiary
                                },
                                modifier = Modifier.size(18.dp),
                            )
                            if (tab.totalBlockedOnTab > 0) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = tab.totalBlockedOnTab.toString(),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontWeight = FontWeight.Bold,
                                    ),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                        }
                    }

                    // Omnibox URL / Search Field
                    val omniboxBorderColor by animateColorAsState(
                        targetValue = if (isFocused) ElectricCyan else SapphireGlow.copy(alpha = 0.45f),
                        animationSpec = tween(180),
                        label = "omnibox_border",
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (tab.isStartPage && isDark) 0.78f else 0.96f))
                            .border(
                                width = if (isFocused) 1.5.dp else 1.dp,
                                color = omniboxBorderColor,
                                shape = RoundedCornerShape(22.dp),
                            )
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                BasicTextField(
                                    value = urlInput,
                                    onValueChange = { urlInput = it },
                                    singleLine = true,
                                    textStyle = TextStyle(
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontSize = 13.sp,
                                    ),
                                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                                    keyboardActions = KeyboardActions(
                                        onGo = {
                                            focusManager.clearFocus()
                                            onNavigate(urlInput)
                                        },
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .onFocusChanged { focusState ->
                                            isFocused = focusState.isFocused
                                        }
                                        .testTag("omnibox_input"),
                                    decorationBox = { innerTextField ->
                                        if (urlInput.isEmpty()) {
                                            Text(
                                                text = if (tab.isIncognito) "جستجوی ناشناس یا آدرس وب…" else "جستجو یا آدرس وب…",
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                style = MaterialTheme.typography.bodyMedium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                        innerTextField()
                                    },
                                )
                            }

                            if (isFocused && urlInput.isNotEmpty()) {
                                IconButton(
                                    onClick = { urlInput = "" },
                                    modifier = Modifier.minimumInteractiveComponentSize(),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "پاک کردن آدرس",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            } else if (!tab.isStartPage) {
                                IconButton(
                                    onClick = onReloadOrStop,
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("reload_stop_button"),
                                ) {
                                    Icon(
                                        imageVector = if (tab.isLoading) Icons.Default.Close else Icons.Default.Refresh,
                                        contentDescription = if (tab.isLoading) "توقف بارگذاری" else "بارگذاری مجدد",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }
                    }

                // Quick Extensions Icon with active count dot
                Box(contentAlignment = Alignment.TopEnd) {
                    IconButton(
                        onClick = { onOpenSheet(ActiveSheet.EXTENSIONS) },
                        modifier = Modifier.testTag("top_extensions_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Extension,
                            contentDescription = "افزونه‌ها",
                            tint = if (activeExtensionsCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (activeExtensionsCount > 0) {
                        Box(
                            modifier = Modifier
                                .padding(top = 8.dp, end = 8.dp)
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.secondary),
                        )
                    }
                }

                // Overflow Menu
                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.testTag("browser_menu_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "منوی مرورگر",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }

                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        DropdownMenuItem(
                            text = { Text("تب ناشناس جدید") },
                            leadingIcon = { Icon(Icons.Default.VisibilityOff, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onCreateIncognitoTab()
                            },
                        )
                        if (!tab.isStartPage) {
                            DropdownMenuItem(
                                text = { Text(if (isBookmarked) "حذف از نشانک‌ها" else "افزودن به نشانک‌ها") },
                                leadingIcon = {
                                    Icon(
                                        if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                        contentDescription = null,
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    onToggleBookmark()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(if (tab.isDesktopMode) "نمای موبایل" else "نمای دسکتاپ") },
                                leadingIcon = {
                                    Icon(
                                        if (tab.isDesktopMode) Icons.Default.PhoneAndroid else Icons.Default.Computer,
                                        contentDescription = null,
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    onToggleDesktopMode()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("جستجو در صفحه") },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    onOpenSheet(ActiveSheet.FIND_IN_PAGE)
                                },
                            )
                        }
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("نشانک‌ها") },
                            leadingIcon = { Icon(Icons.Default.Bookmark, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onOpenSheet(ActiveSheet.BOOKMARKS)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("تاریخچه مرور") },
                            leadingIcon = { Icon(Icons.Default.History, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onOpenSheet(ActiveSheet.HISTORY)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("مدیریت دانلودها") },
                            leadingIcon = { Icon(Icons.Default.Download, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onOpenSheet(ActiveSheet.DOWNLOADS)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("ابزارهای توسعه‌دهنده (DevTools)") },
                            leadingIcon = { Icon(Icons.Default.DeveloperMode, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onOpenSheet(ActiveSheet.DEVTOOLS)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("تنظیمات و حریم خصوصی") },
                            leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onOpenSheet(ActiveSheet.SETTINGS)
                            },
                        )
                    }
                }
            }

            // Find in page inline bar
            AnimatedVisibility(visible = findInPageActive) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                    BasicTextField(
                        value = findQuery,
                        onValueChange = onFindQueryChange,
                        singleLine = true,
                        textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier.weight(1f),
                        decorationBox = { inner ->
                            if (findQuery.isEmpty()) {
                                Text("جستجو در متن صفحه…", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                            }
                            inner()
                        },
                    )
                    IconButton(onClick = { onFindNext(false) }, modifier = Modifier.minimumInteractiveComponentSize()) {
                        Icon(Icons.Default.KeyboardArrowUp, contentDescription = "نتیجه قبلی")
                    }
                    IconButton(onClick = { onFindNext(true) }, modifier = Modifier.minimumInteractiveComponentSize()) {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "نتیجه بعدی")
                    }
                    IconButton(onClick = onCloseFind, modifier = Modifier.minimumInteractiveComponentSize()) {
                        Icon(Icons.Default.Close, contentDescription = "بستن جستجو")
                    }
                }
            }

            // Smooth animated loading progress line
            val animatedProgress by animateFloatAsState(
                targetValue = (tab.progress.coerceIn(5, 100)) / 100f,
                animationSpec = tween(durationMillis = 160),
                label = "page_progress",
            )
            AnimatedVisibility(
                visible = tab.isLoading && !tab.isStartPage,
                enter = fadeIn(tween(120)),
                exit = fadeOut(tween(220)),
            ) {
                Column {
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.5.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = ElectricCyan,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                }
            }
        }
            PrismHairline(isIncognito = tab.isIncognito, height = 1.dp, alpha = 0.8f)
        }
    }
}
