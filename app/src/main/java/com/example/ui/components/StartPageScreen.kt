package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
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
import com.example.ui.theme.ChromeSilver
import com.example.ui.theme.CosmicViolet
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

    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.25f
    val infiniteTransition = rememberInfiniteTransition(label = "cosmic_portal")
    val orbitAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 48000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "orbit_angle",
    )
    val haloPulse by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "halo_pulse",
    )

    val titleColor = if (isDark) StarlightWhite else MaterialTheme.colorScheme.onBackground
    val secondaryText = if (isDark) Color(0xFFB7C7E8) else MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            // Floating Cosmic Portal Centerpiece (No boxy opaque card — floats cleanly in open space)
            item {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(164.dp),
                    ) {
                        // Orbiting translucent cosmic prism infinity rings
                        Image(
                            painter = painterResource(id = R.drawable.salvia_prism_rings_alpha),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    rotationZ = orbitAngle
                                    scaleX = haloPulse
                                    scaleY = haloPulse
                                }
                                .alpha(if (isIncognito) 0.65f else 0.92f),
                        )

                        // Soft radial sapphire glow behind central emblem
                        Box(
                            modifier = Modifier
                                .size(106.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.radialGradient(
                                        listOf(
                                            (if (isIncognito) StealthViolet else ElectricCyan).copy(alpha = 0.34f),
                                            (if (isIncognito) NebulaMagenta else SapphireBlue).copy(alpha = 0.14f),
                                            Color.Transparent,
                                        ),
                                    ),
                                ),
                        )

                        // Central SalviaBrowxser Emblem
                        val emblemRing = if (isIncognito) {
                            Brush.sweepGradient(listOf(StealthViolet, NebulaMagenta, ChromeSilver, StealthViolet))
                        } else {
                            Brush.sweepGradient(listOf(ElectricCyan, SapphireBlue, CosmicViolet, NebulaMagenta, ChromeSilver, ElectricCyan))
                        }
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(92.dp)
                                .shadow(
                                    elevation = 18.dp,
                                    shape = CircleShape,
                                    ambientColor = if (isIncognito) StealthViolet else SapphireGlow,
                                    spotColor = if (isIncognito) NebulaMagenta else ElectricCyan,
                                )
                                .clip(CircleShape)
                                .border(1.8.dp, emblemRing, CircleShape)
                                .padding(2.5.dp),
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_salvia_emblem_1795066312153),
                                contentDescription = "لوگوی اختصاصی SalviaBrowxser",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "SalviaBrowxser",
                        style = TextStyle(
                            fontFamily = AlexBrushFontFamily,
                            fontSize = 38.sp,
                            brush = Brush.horizontalGradient(
                                if (isIncognito) {
                                    listOf(StarlightWhite, StealthViolet, NebulaMagenta, ChromeSilver)
                                } else {
                                    listOf(StarlightWhite, ElectricCyan, SapphireGlow, NebulaMagenta, ChromeSilver)
                                },
                            ),
                        ),
                        maxLines = 1,
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 2.dp),
                    ) {
                        Text(
                            text = if (isIncognito) "حالت ناشناس کیهانی" else "Salar Farzaneh",
                            style = if (isIncognito) {
                                MaterialTheme.typography.labelLarge
                            } else {
                                TextStyle(
                                    fontFamily = AlexBrushFontFamily,
                                    fontSize = 19.sp,
                                    color = ChromeSilver,
                                )
                            },
                        )

                        val badgeColor = if (isIncognito) StealthViolet else EmeraldShield
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(badgeColor.copy(alpha = 0.14f))
                                .border(0.8.dp, badgeColor.copy(alpha = 0.50f), RoundedCornerShape(999.dp))
                                .padding(horizontal = 9.dp, vertical = 3.dp),
                        ) {
                            Icon(
                                imageVector = if (isIncognito) Icons.Default.VisibilityOff else Icons.Default.Shield,
                                contentDescription = null,
                                tint = badgeColor,
                                modifier = Modifier.size(12.dp),
                            )
                            Text(
                                text = if (isIncognito) "ایزوله کامل" else "سپر Aloha فعال",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
                                color = badgeColor,
                                maxLines = 1,
                            )
                        }
                    }

                    if (isIncognito) {
                        Text(
                            text = "هیچ تاریخچه، کوکی ماندگار یا ردپایی روی دستگاه ذخیره نمی‌شود.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFE9D5FF),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 8.dp, start = 12.dp, end = 12.dp),
                        )
                    } else {
                        Spacer(modifier = Modifier.height(14.dp))
                        // Translucent Floating Telemetry Ribbon
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(glassCardModifier(cornerRadius = 20.dp))
                                .padding(vertical = 10.dp, horizontal = 8.dp),
                        ) {
                            TelemetryMetric(
                                value = settings.totalAdsBlockedLifetime.toString(),
                                label = "تبلیغ مسدود",
                                accent = ElectricCyan,
                                labelColor = secondaryText,
                                modifier = Modifier.weight(1f),
                            )
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .height(26.dp)
                                    .background(SapphireGlow.copy(alpha = 0.28f)),
                            )
                            TelemetryMetric(
                                value = settings.totalTrackersBlockedLifetime.toString(),
                                label = "ردیاب مسدود",
                                accent = NebulaMagenta,
                                labelColor = secondaryText,
                                modifier = Modifier.weight(1f),
                            )
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .height(26.dp)
                                    .background(SapphireGlow.copy(alpha = 0.28f)),
                            )
                            TelemetryMetric(
                                value = activeExtensionsCount.toString(),
                                label = "افزونه فعال",
                                accent = ChromeSilver,
                                labelColor = secondaryText,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            // Luminous Quantum Prism Search Omnibox
            item {
                val searchBorder = if (isIncognito) {
                    Brush.horizontalGradient(listOf(StealthViolet, NebulaMagenta, ChromeSilver.copy(alpha = 0.7f)))
                } else {
                    Brush.horizontalGradient(listOf(SapphireBlue, ElectricCyan, NebulaMagenta, CosmicViolet))
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp)
                        .shadow(
                            elevation = 16.dp,
                            shape = RoundedCornerShape(29.dp),
                            ambientColor = if (isIncognito) StealthViolet else SapphireGlow,
                            spotColor = if (isIncognito) NebulaMagenta else ElectricCyan,
                        )
                        .clip(RoundedCornerShape(29.dp))
                        .background(
                            if (isDark) {
                                Brush.horizontalGradient(
                                    listOf(
                                        Color(0xFF091334).copy(alpha = 0.86f),
                                        Color(0xFF0D1840).copy(alpha = 0.84f),
                                        Color(0xFF091230).copy(alpha = 0.88f),
                                    ),
                                )
                            } else {
                                Brush.horizontalGradient(
                                    listOf(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.surfaceVariant),
                                )
                            },
                        )
                        .border(1.3.dp, searchBorder, RoundedCornerShape(29.dp)),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 18.dp),
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background((if (isIncognito) StealthViolet else ElectricCyan).copy(alpha = 0.16f)),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "جستجو",
                                tint = if (isIncognito) StealthViolet else ElectricCyan,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        BasicTextField(
                            value = searchText,
                            onValueChange = { searchText = it },
                            singleLine = true,
                            textStyle = TextStyle(
                                color = titleColor,
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
                                        color = secondaryText,
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
                                modifier = Modifier.minimumInteractiveComponentSize(),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "پاک کردن جستجو",
                                    tint = secondaryText,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            IconButton(
                                onClick = { onNavigate(searchText) },
                                modifier = Modifier.minimumInteractiveComponentSize(),
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = "جستجو و رفتن",
                                    tint = ElectricCyan,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }
            }

            // Frosted Space-Glass Quick Action Capsules
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    QuickToolGlassPill(
                        label = "افزونه‌ها",
                        icon = Icons.Default.Extension,
                        accent = NebulaMagenta,
                        titleColor = titleColor,
                        onClick = { onOpenSheet(ActiveSheet.EXTENSIONS) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("start_extensions_chip"),
                    )
                    QuickToolGlassPill(
                        label = "دانلودها",
                        icon = Icons.Default.Download,
                        accent = ChromeSilver,
                        titleColor = titleColor,
                        onClick = { onOpenSheet(ActiveSheet.DOWNLOADS) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("start_downloads_chip"),
                    )
                    QuickToolGlassPill(
                        label = "DevTools",
                        icon = Icons.Default.DeveloperMode,
                        accent = ElectricCyan,
                        titleColor = titleColor,
                        onClick = { onOpenSheet(ActiveSheet.DEVTOOLS) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("start_devtools_chip"),
                    )
                }
            }

            // Floating Cosmic Speed Dial Orbs
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "دسترسی سریع و ورود امن",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = titleColor,
                    )
                    if (!isIncognito) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(StealthViolet.copy(alpha = 0.16f))
                                .border(0.9.dp, StealthViolet.copy(alpha = 0.48f), RoundedCornerShape(14.dp))
                                .clickable(onClick = onNewIncognitoTab)
                                .minimumInteractiveComponentSize()
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.VisibilityOff,
                                contentDescription = null,
                                tint = StealthViolet,
                                modifier = Modifier.size(14.dp),
                            )
                            Text(
                                text = "تب ناشناس",
                                style = MaterialTheme.typography.labelMedium,
                                color = StealthViolet,
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))

                val rowsList = remember(speedDialItems) { speedDialItems.chunked(4) }
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    rowsList.forEach { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            rowItems.forEach { item ->
                                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                    SpeedDialOrbTile(
                                        bookmark = item,
                                        titleColor = titleColor,
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
private fun TelemetryMetric(
    value: String,
    label: String,
    accent: Color,
    labelColor: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.padding(horizontal = 4.dp),
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
            color = labelColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun QuickToolGlassPill(
    label: String,
    icon: ImageVector,
    accent: Color,
    titleColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = modifier
            .heightIn(min = 48.dp)
            .then(glassCardModifier(cornerRadius = 18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = titleColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SpeedDialOrbTile(
    bookmark: BookmarkEntity,
    titleColor: Color,
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
        bookmark.url.contains("mail.google") -> Color(0xFFF87171)
        bookmark.url.contains("youtube") -> Color(0xFFFB7185)
        bookmark.url.contains("aparat") -> NebulaMagenta
        bookmark.url.contains("github") -> ElectricCyan
        bookmark.url.contains("duckduckgo") -> ChromeSilver
        else -> SapphireGlow
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp)
            .testTag("speed_dial_${bookmark.title.lowercase().replace(" ", "_")}"),
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .shadow(
                    elevation = 10.dp,
                    shape = CircleShape,
                    ambientColor = badgeColor.copy(alpha = 0.55f),
                    spotColor = badgeColor,
                )
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(
                            badgeColor.copy(alpha = 0.30f),
                            ObsidianSurface.copy(alpha = 0.85f),
                        ),
                    ),
                )
                .border(
                    1.2.dp,
                    Brush.linearGradient(
                        listOf(
                            badgeColor.copy(alpha = 0.85f),
                            ChromeSilver.copy(alpha = 0.45f),
                        ),
                    ),
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = bookmark.title,
                tint = badgeColor,
                modifier = Modifier.size(25.dp),
            )
        }
        Spacer(modifier = Modifier.height(7.dp))
        Text(
            text = bookmark.title,
            style = MaterialTheme.typography.labelMedium,
            color = titleColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}
