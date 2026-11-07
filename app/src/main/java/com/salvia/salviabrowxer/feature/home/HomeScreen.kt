package com.salvia.salviabrowxer.feature.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.salvia.salviabrowxer.R
import com.salvia.salviabrowxer.ui.components.DownloadItem
import com.salvia.salviabrowxer.ui.components.OrbitalBrandMark
import com.salvia.salviabrowxer.ui.theme.AuroraCyan
import com.salvia.salviabrowxer.ui.theme.AuroraCyanContainer
import com.salvia.salviabrowxer.ui.theme.AuroraCyanLight
import com.salvia.salviabrowxer.ui.theme.BlushPink
import com.salvia.salviabrowxer.ui.theme.CharcoalBorder
import com.salvia.salviabrowxer.ui.theme.CharcoalSurface
import com.salvia.salviabrowxer.ui.theme.EmptySurface
import com.salvia.salviabrowxer.ui.theme.IrisViolet
import com.salvia.salviabrowxer.ui.theme.IrisVioletFieldFocus
import com.salvia.salviabrowxer.ui.theme.LogoIridescenceBrush
import com.salvia.salviabrowxer.ui.theme.MatteCharcoal
import com.salvia.salviabrowxer.ui.theme.PearlFieldHint
import com.salvia.salviabrowxer.ui.theme.PearlIridescentBrush
import com.salvia.salviabrowxer.ui.theme.PearlWhite
import com.salvia.salviabrowxer.ui.theme.PinGold
import com.salvia.salviabrowxer.ui.theme.SilverDeep
import com.salvia.salviabrowxer.ui.theme.SilverMid
import com.salvia.salviabrowxer.ui.theme.TopBarBrush
import kotlinx.coroutines.flow.collectLatest

/**
 * The home screen: the product's front door, and since the welcome ceremony ends here, the first
 * room of the app.
 *
 * The brand mark rests in the depth of the page — the same artwork as the launcher icon, without
 * its background, at a whisper of opacity — so the screen reads as Salvia's own space before a
 * single label is read. Over it: the paste card (a downloader's entry point is the link, so it
 * stays the loudest thing here), four doors into the rest of the app, and the transfers in flight.
 *
 * There is exactly one decision behind the primary action: a URL that already names a file
 * (`…/clip.mp4`, `…/master.m3u8`) goes straight to the quality sheet, because there is nothing to
 * discover. Anything else is a page, and this app has no site-specific extractors by design, so
 * the honest thing is to open it and let the media tray find what the page exposes — which is why
 * the button's label changes with the link instead of promising a download that a page cannot
 * produce.
 */
@Composable
fun HomeScreen(
    onOpenLink: (url: String) -> Unit,
    onNavigateToBrowser: () -> Unit,
    onNavigateToDownloads: () -> Unit,
    onNavigateToBookmarks: () -> Unit = {},
    onNavigateToHistory: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val keyboard = LocalSoftwareKeyboardController.current
    val onPasteTitle = stringResource(R.string.home_paste_title)

    val submit: () -> Unit = remember(viewModel, keyboard) {
        {
            keyboard?.hide()
            viewModel.submitInput()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.messages.collectLatest { message -> if (message.isNotBlank()) snackbarHostState.showSnackbar(message) }
    }
    LaunchedEffect(Unit) {
        viewModel.openRequests.collectLatest { url -> onOpenLink(url) }
    }

    // The clipboard is only readable by a foreground app, so resuming is exactly when to look —
    // and it is what makes "copy a link elsewhere, come back, paste is already filled" work.
    LifecycleResumeEffect(Unit) {
        viewModel.onForeground()
        onPauseOrDispose { }
    }

    Box(modifier = Modifier.fillMaxSize().background(MatteCharcoal)) {
        // The mark in the depth: no background of its own, seven percent of itself, exactly where
        // the eye rests. Content scrolls over it; it never moves, never competes, never leaves.
        Image(
            painter = painterResource(R.drawable.salvia_brand),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            alpha = 0.07f,
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.92f)
        )

        Box(modifier = Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                HomeHeader(onNavigateToBrowser = onNavigateToBrowser)
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                    Column(modifier = Modifier.fillMaxWidth().widthIn(max = 760.dp).padding(horizontal = 20.dp)) {
                        Spacer(Modifier.height(28.dp))
                        HomeIntro()
                        Spacer(Modifier.height(20.dp))
                        HomeQuickAccess(
                            onNavigateToBrowser = onNavigateToBrowser,
                            onNavigateToBookmarks = onNavigateToBookmarks,
                            onNavigateToHistory = onNavigateToHistory,
                            onNavigateToDownloads = onNavigateToDownloads
                        )
                        Spacer(Modifier.height(22.dp))
                        PasteCard(
                            state = state,
                            fieldLabel = onPasteTitle,
                            onInputChange = viewModel::onInputChange,
                            onClear = viewModel::clearInput,
                            onPaste = viewModel::pasteFromClipboard,
                            onUseSuggestion = viewModel::useClipboardSuggestion,
                            onDismissSuggestion = viewModel::dismissClipboardSuggestion,
                            onSubmit = submit
                        )
                        Spacer(Modifier.height(26.dp))
                        ActiveDownloads(state = state, viewModel = viewModel, onNavigateToDownloads = onNavigateToDownloads)
                        Spacer(Modifier.height(24.dp))
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp))
        }
    }
}

@Composable
private fun HomeHeader(onNavigateToBrowser: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().background(TopBarBrush)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OrbitalBrandMark(size = 34.dp, animate = false, glowIntensity = 0.85f)
            Spacer(Modifier.width(10.dp))
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleMedium,
                color = PearlWhite,
                letterSpacing = 1.2.sp
            )
            Spacer(Modifier.weight(1f))
            TextButton(
                onClick = onNavigateToBrowser,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Icon(Icons.Default.Public, contentDescription = null, tint = IrisViolet, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.home_open_browser), style = MaterialTheme.typography.labelMedium, color = IrisViolet)
            }
        }
        // One hairline of the mark's own light — ribbon, starfield, pins — under the header.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 1.dp, max = 1.dp)
                .graphicsLayer { alpha = 0.55f }
                .background(LogoIridescenceBrush)
        )
    }
}

@Composable
private fun HomeIntro() {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.home_hero_title),
            style = MaterialTheme.typography.headlineMedium.copy(brush = PearlIridescentBrush),
            color = Color.Unspecified
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.home_hero_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = SilverMid
        )
    }
}

/**
 * Four doors, one row: the rooms of the app a thumb asks for most, each tinted with one voice of
 * the mark so the row reads as a set without a single word of chrome.
 */
@Composable
private fun HomeQuickAccess(
    onNavigateToBrowser: () -> Unit,
    onNavigateToBookmarks: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToDownloads: () -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        HomeTile(
            icon = Icons.Default.Public,
            label = stringResource(R.string.home_open_browser),
            tint = IrisViolet,
            onClick = onNavigateToBrowser,
            modifier = Modifier.weight(1f)
        )
        HomeTile(
            icon = Icons.Default.Bookmark,
            label = stringResource(R.string.bookmarks),
            tint = AuroraCyan,
            onClick = onNavigateToBookmarks,
            modifier = Modifier.weight(1f)
        )
        HomeTile(
            icon = Icons.Default.History,
            label = stringResource(R.string.history),
            tint = BlushPink,
            onClick = onNavigateToHistory,
            modifier = Modifier.weight(1f)
        )
        HomeTile(
            icon = Icons.Default.Download,
            label = stringResource(R.string.downloads_title),
            tint = PinGold,
            onClick = onNavigateToDownloads,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun HomeTile(
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(17.dp))
                .background(CharcoalSurface)
                .border(1.dp, CharcoalBorder.copy(alpha = 0.72f), RoundedCornerShape(17.dp))
                .border(1.dp, tint.copy(alpha = 0.16f), RoundedCornerShape(17.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(7.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = SilverMid,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun PasteCard(
    state: HomeUiState,
    fieldLabel: String,
    onInputChange: (String) -> Unit,
    onClear: () -> Unit,
    onPaste: () -> Unit,
    onUseSuggestion: () -> Unit,
    onDismissSuggestion: () -> Unit,
    onSubmit: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val borderColor by animateColorAsState(
        targetValue = if (focused) IrisVioletFieldFocus else CharcoalBorder,
        label = "pasteFieldBorder"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(CharcoalSurface.copy(alpha = 0.92f))
            .border(1.dp, CharcoalBorder, RoundedCornerShape(22.dp))
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Link, contentDescription = null, tint = IrisViolet, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.home_paste_title),
                style = MaterialTheme.typography.labelLarge,
                color = PearlWhite,
                letterSpacing = 0.2.sp
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.home_paste_support),
            style = MaterialTheme.typography.bodySmall,
            color = SilverMid
        )
        Spacer(Modifier.height(14.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(MatteCharcoal.copy(alpha = 0.55f))
                .border(1.dp, borderColor, RoundedCornerShape(14.dp))
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.weight(1f)) {
                BasicTextField(
                    value = state.input,
                    onValueChange = onInputChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { focused = it.hasFocus }
                        .semantics { contentDescription = fieldLabel },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = PearlWhite),
                    cursorBrush = Brush.horizontalGradient(listOf(BlushPink, AuroraCyanLight, IrisViolet)),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { onSubmit() }),
                    decorationBox = { inner ->
                        Box(modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), contentAlignment = Alignment.CenterStart) {
                            if (state.input.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.home_paste_hint),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = PearlFieldHint
                                )
                            }
                            inner()
                        }
                    }
                )
            }
            if (state.input.isNotEmpty()) {
                IconButton(onClick = onClear, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.Close, stringResource(R.string.home_clear_input), tint = SilverMid, modifier = Modifier.size(18.dp))
                }
            }
            IconButton(onClick = onPaste, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.ContentPaste, stringResource(R.string.home_paste_from_clipboard), tint = IrisViolet, modifier = Modifier.size(20.dp))
            }
        }

        val suggestion = state.clipboardUrl
        AnimatedVisibility(
            visible = suggestion != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(AuroraCyanContainer)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.ContentPaste, contentDescription = null, tint = AuroraCyanLight, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(
                            if (state.clipboardIsMediaFile) R.string.home_clipboard_media else R.string.home_clipboard_page
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = PearlWhite
                    )
                    if (suggestion != null) {
                        Text(
                            text = suggestion,
                            style = MaterialTheme.typography.bodySmall,
                            color = SilverMid,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                TextButton(onClick = onUseSuggestion, contentPadding = PaddingValues(horizontal = 10.dp)) {
                    Text(stringResource(R.string.home_clipboard_use), style = MaterialTheme.typography.labelMedium, color = AuroraCyanLight)
                }
                IconButton(onClick = onDismissSuggestion, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Close, stringResource(R.string.home_clipboard_dismiss), tint = SilverMid, modifier = Modifier.size(16.dp))
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        Button(
            onClick = onSubmit,
            enabled = state.input.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 54.dp)
                .clip(RoundedCornerShape(15.dp)),
            shape = RoundedCornerShape(15.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = IrisViolet,
                contentColor = MatteCharcoal,
                disabledContainerColor = CharcoalSurface,
                disabledContentColor = SilverDeep
            )
        ) {
            Icon(
                imageVector = if (state.inputIsMediaFile) Icons.Default.Download else Icons.Default.Public,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(if (state.inputIsMediaFile) R.string.home_download_file else R.string.home_open_link),
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

@Composable
private fun ActiveDownloads(
    state: HomeUiState,
    viewModel: HomeViewModel,
    onNavigateToDownloads: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Download, contentDescription = null, tint = PinGold, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.home_active_title),
                style = MaterialTheme.typography.titleSmall,
                color = PearlWhite
            )
            if (state.inFlightCount > 0) {
                Spacer(Modifier.width(8.dp))
                Text(text = "${state.inFlightCount}", style = MaterialTheme.typography.labelMedium, color = PinGold)
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onNavigateToDownloads, contentPadding = PaddingValues(horizontal = 8.dp)) {
                Text(stringResource(R.string.home_active_see_all), style = MaterialTheme.typography.labelMedium, color = IrisViolet)
            }
        }
        Spacer(Modifier.height(8.dp))

        if (state.active.isEmpty()) {
            CardEmpty(
                text = stringResource(R.string.home_active_empty)
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                state.active.take(3).forEach { download ->
                    DownloadItem(
                        download = download,
                        // Only in-flight rows appear here, and a finished row leaves as soon as the
                        // queue publishes it — so open/share/delete cannot be reached from home.
                        onOpenClick = onNavigateToDownloads,
                        onPauseClick = { viewModel.pauseDownload(download.id) },
                        onResumeClick = { viewModel.resumeDownload(download.id) },
                        onCancelClick = { viewModel.cancelDownload(download.id) },
                        onRetryClick = { viewModel.retryDownload(download.id) },
                        onDeleteClick = {}
                    )
                }
            }
        }
    }
}

@Composable
private fun CardEmpty(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(EmptySurface)
            .border(1.dp, CharcoalBorder.copy(alpha = 0.72f), RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(IrisViolet.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Download, contentDescription = null, tint = IrisViolet, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = SilverMid,
            modifier = Modifier.weight(1f)
        )
    }
}
