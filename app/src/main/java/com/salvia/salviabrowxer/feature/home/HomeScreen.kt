package com.salvia.salviabrowxer.feature.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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
import com.salvia.salviabrowxer.ui.theme.AuroraTeal
import com.salvia.salviabrowxer.ui.theme.AuroraTealFieldFocus
import com.salvia.salviabrowxer.ui.theme.BlushPink
import com.salvia.salviabrowxer.ui.theme.CharcoalBorder
import com.salvia.salviabrowxer.ui.theme.CharcoalElevated
import com.salvia.salviabrowxer.ui.theme.CharcoalSurface
import com.salvia.salviabrowxer.ui.theme.DeepCharcoal
import com.salvia.salviabrowxer.ui.theme.EmptySurface
import com.salvia.salviabrowxer.ui.theme.MatteCharcoal
import com.salvia.salviabrowxer.ui.theme.NebulaVioletContainer
import com.salvia.salviabrowxer.ui.theme.NebulaVioletLight
import com.salvia.salviabrowxer.ui.theme.PearlEdgeBrush
import com.salvia.salviabrowxer.ui.theme.PearlFieldHint
import com.salvia.salviabrowxer.ui.theme.PearlWhite
import com.salvia.salviabrowxer.ui.theme.SilverDeep
import com.salvia.salviabrowxer.ui.theme.SilverMid
import com.salvia.salviabrowxer.ui.theme.SurfaceField
import com.salvia.salviabrowxer.ui.theme.TopBarBrush
import kotlinx.coroutines.flow.collectLatest

/**
 * The home screen: the product's front door.
 *
 * A downloader's entry point is the link, so the paste card is the first and loudest thing on the
 * screen and every other element on it is there to answer three questions: what was pasted, what
 * the app will do about it, and whether anything is already downloading. There is exactly one
 * decision behind the primary action: a URL that already names a file (`…/clip.mp4`,
 * `…/master.m3u8`) goes straight to the quality sheet, because there is nothing to discover. Anything
 * else is a page, and this app has no site-specific extractors by design, so the honest thing is to
 * open it and let the media tray find what the page exposes — which is why the button's label changes
 * with the link instead of promising a download that a page cannot produce.
 */
@Composable
fun HomeScreen(
    onOpenLink: (url: String) -> Unit,
    onNavigateToBrowser: () -> Unit,
    onNavigateToDownloads: () -> Unit,
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            HomeHeader(onNavigateToBrowser = onNavigateToBrowser)
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Spacer(Modifier.height(20.dp))
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
                Spacer(Modifier.height(14.dp))
                Text(
                    text = stringResource(R.string.home_how_it_works),
                    style = MaterialTheme.typography.bodySmall,
                    color = SilverDeep
                )
                Spacer(Modifier.height(24.dp))
                ActiveDownloads(state = state, viewModel = viewModel, onNavigateToDownloads = onNavigateToDownloads)
                Spacer(Modifier.height(24.dp))
            }
            Spacer(Modifier.height(8.dp))
        }
        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp))
    }
}

@Composable
private fun HomeHeader(onNavigateToBrowser: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().background(TopBarBrush)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OrbitalBrandMark(size = 34.dp, animate = false, glowIntensity = 0.85f)
            Spacer(Modifier.width(10.dp))
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleMedium,
                color = PearlWhite,
                letterSpacing = 2.sp
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onNavigateToBrowser) {
                Icon(Icons.Default.Public, stringResource(R.string.home_open_browser), tint = PearlWhite)
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 1.dp, max = 1.dp)
                .background(PearlEdgeBrush)
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
        targetValue = if (focused) AuroraTealFieldFocus else CharcoalBorder,
        label = "pasteFieldBorder"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(CharcoalElevated)
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Link, contentDescription = null, tint = AuroraTeal, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.home_paste_title),
                style = MaterialTheme.typography.labelLarge,
                color = PearlWhite,
                letterSpacing = 0.6.sp
            )
        }
        Spacer(Modifier.height(14.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(SurfaceField)
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
                    cursorBrush = Brush.horizontalGradient(listOf(BlushPink, NebulaVioletLight, AuroraTeal)),
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
                Icon(Icons.Default.ContentPaste, stringResource(R.string.home_paste_from_clipboard), tint = AuroraTeal, modifier = Modifier.size(20.dp))
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
                    .background(NebulaVioletContainer)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.ContentPaste, contentDescription = null, tint = NebulaVioletLight, modifier = Modifier.size(18.dp))
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
                    Text(stringResource(R.string.home_clipboard_use), style = MaterialTheme.typography.labelMedium, color = NebulaVioletLight)
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
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(14.dp)),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = AuroraTeal,
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
            Icon(Icons.Default.Download, contentDescription = null, tint = AuroraTeal, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.home_active_title),
                style = MaterialTheme.typography.titleSmall,
                color = PearlWhite
            )
            if (state.inFlightCount > 0) {
                Spacer(Modifier.width(8.dp))
                Text(text = "${state.inFlightCount}", style = MaterialTheme.typography.labelMedium, color = AuroraTeal)
            }
            Spacer(Modifier.weight(1f))
            if (state.inFlightCount > 0) {
                TextButton(onClick = onNavigateToDownloads, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Text(stringResource(R.string.home_active_see_all), style = MaterialTheme.typography.labelMedium, color = AuroraTeal)
                }
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
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(EmptySurface)
            .padding(18.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = SilverMid
        )
    }
}
