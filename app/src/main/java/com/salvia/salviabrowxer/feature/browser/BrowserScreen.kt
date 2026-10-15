package com.salvia.salviabrowxer.feature.browser

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tab
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.salvia.salviabrowxer.R
import com.salvia.salviabrowxer.core.model.MediaCandidate
import com.salvia.salviabrowxer.ui.components.BrowserBottomBar
import com.salvia.salviabrowxer.ui.components.BrowserMenuItem
import com.salvia.salviabrowxer.ui.components.BrowserTopBar
import com.salvia.salviabrowxer.ui.components.FindInPageBar
import com.salvia.salviabrowxer.ui.components.FloatingDownloadButton
import com.salvia.salviabrowxer.ui.components.MediaQualitySelectionSheet
import com.salvia.salviabrowxer.ui.components.MediaTraySheet
import com.salvia.salviabrowxer.ui.components.OrbitalBrandMark
import com.salvia.salviabrowxer.ui.components.TabSwitcher
import com.salvia.salviabrowxer.ui.theme.AuroraTeal
import com.salvia.salviabrowxer.ui.theme.CharcoalElevated
import com.salvia.salviabrowxer.ui.theme.NebulaVioletLight
import com.salvia.salviabrowxer.ui.theme.PearlEdgeBrush
import com.salvia.salviabrowxer.ui.theme.PearlWhite
import com.salvia.salviabrowxer.ui.theme.SilverMid
import com.salvia.salviabrowxer.ui.theme.SplashNebulaBrush
import com.salvia.salviabrowxer.ui.utils.Constants
import com.salvia.salviabrowxer.ui.utils.PageErrorKind
import java.io.File
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@Composable
fun BrowserScreen(
    onNavigateToDownloads: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToBookmarks: () -> Unit,
    onNavigateToHistory: () -> Unit,
    /** The app's own home screen (paste link), not the browsable homepage. */
    onNavigateToHome: (() -> Unit)? = null,
    /** A link submitted on the home screen: it becomes a download or a page load. */
    submittedLink: String? = null,
    onSubmittedLinkConsumed: () -> Unit = {},
    /** A URL picked inside the app (bookmark, history entry): it loads in the current tab. */
    inAppUrl: String? = null,
    onInAppUrlConsumed: () -> Unit = {},
    /** A URL another app handed us (VIEW / SEND): it gets its own tab. */
    externalUrl: String? = null,
    onExternalUrlConsumed: () -> Unit = {},
    viewModel: BrowserViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val noMediaMessage = stringResource(R.string.no_media_detected)
    val notificationsDeniedMessage = stringResource(R.string.notifications_denied_downloads_anyway)

    var pageAreaSize by remember { mutableStateOf(IntSize.Zero) }

    // One WebView per live tab. The store owns render state, the view model owns tab metadata, and
    // the store instance itself lives in the view model: a `remember`ed store died with this
    // composable, so opening Downloads, Settings or the player destroyed every WebView and coming
    // back reloaded the page from the network.
    val storeCallbacks = remember(viewModel) {
        object : TabWebViewStore.Callbacks {
            override fun onPageStarted(tabId: String, url: String) = viewModel.onPageStarted(tabId, url)
            override fun onPageFinished(tabId: String, url: String, title: String?) = viewModel.onPageFinished(tabId, url, title)
            override fun onHistoryUrlChanged(tabId: String, url: String) = viewModel.onHistoryUrlChanged(tabId, url)
            override fun onProgress(tabId: String, progress: Int) = viewModel.onProgressChanged(tabId, progress)
            override fun onNavigationState(tabId: String, canGoBack: Boolean, canGoForward: Boolean) = viewModel.updateNavigationState(tabId, canGoBack, canGoForward)
            override fun onPageHtml(tabId: String, pageUrl: String, html: String) = viewModel.onPageHtml(tabId, pageUrl, html)
            override fun onMediaDetected(tabId: String, candidate: MediaCandidate) = viewModel.onMediaIntercepted(tabId, candidate)
            override fun onVisibleMedia(tabId: String, pageUrl: String, mediaUrl: String) = viewModel.onVisibleMedia(tabId, pageUrl, mediaUrl)
            override fun onMediaExpired(tabId: String, pageUrl: String, mediaUrl: String) = viewModel.onMediaExpired(tabId, pageUrl, mediaUrl)
            override fun onBlobCaptured(tabId: String, pageUrl: String, blobUrl: String, file: File, mimeType: String) = viewModel.onBlobCaptured(tabId, pageUrl, blobUrl, file, mimeType)
            override fun onMseOpen(tabId: String, mimeType: String, sourceOrigin: String?, isMainFrame: Boolean) = viewModel.onMseOpen(tabId, mimeType, sourceOrigin, isMainFrame)
            override fun onMseSegment(tabId: String, bytes: ByteArray, sourceOrigin: String?, isMainFrame: Boolean) = viewModel.onMseSegment(tabId, bytes, sourceOrigin, isMainFrame)
            override fun onMseCap(tabId: String) = viewModel.onMseCap(tabId)
            override fun onMseEncrypted(tabId: String) = viewModel.onMseEncrypted(tabId)
            override fun onTabHibernated(tabId: String, url: String, title: String) = viewModel.onTabHibernated(tabId, url, title)
            override fun onFindResult(tabId: String, matches: Int, activeMatch: Int) = viewModel.onFindResult(tabId, matches, activeMatch)
            override fun onExternalScheme(url: String): Boolean = viewModel.openExternalScheme(url)
            override fun onRendererGone(tabId: String) = viewModel.onRendererGone(tabId)
            override fun onPageError(tabId: String, kind: PageErrorKind, description: String) = viewModel.onPageError(tabId, kind, description)
            override fun onDownloadRequested(
                tabId: String,
                url: String,
                userAgent: String?,
                contentDisposition: String?,
                mimeType: String?,
                contentLength: Long
            ) = viewModel.onDownloadRequested(tabId, url, userAgent, contentDisposition, mimeType, contentLength)
        }
    }
    val store = remember(context, viewModel, storeCallbacks) { viewModel.webViewStore(context, storeCallbacks) }

    // POST_NOTIFICATIONS is requested in context, the first time a download is enqueued.
    // Denying it never blocks the transfer; it only means no progress notification.
    var notificationAsked by remember { mutableStateOf(false) }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) scope.launch { snackbarHostState.showSnackbar(notificationsDeniedMessage) }
    }
    val ensureNotificationPermission: () -> Unit = remember(context, notificationAsked, notificationLauncher) {
        {
            val missing = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notificationAsked && missing) {
                notificationAsked = true
                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    val isMediaDetected = state.detectedMedia.isNotEmpty()
    val mediaCount = state.detectedMedia.size
    val hasPage = state.url.isNotBlank()
    val onUrlChange = remember(viewModel) { { input: String -> viewModel.onAddressInputChange(input) } }
    val onUrlSubmit = remember(viewModel) { { input: String -> viewModel.loadFromAddressBar(input) } }
    val onBackClick = remember(viewModel) { { viewModel.goBack() } }
    val onForwardClick = remember(viewModel) { { viewModel.goForward() } }
    // One control, two honest states: the bottom bar reloads a settled page and stops a loading
    // one. The glyph and the label both follow the state.
    val onReloadStopClick = remember(viewModel, state.isLoading) {
        { if (state.isLoading) viewModel.stopLoading() else viewModel.reload() }
    }
    // The floating button is the one-tap promise: with a main video on the page it saves *that*,
    // and everything else it can be is the tray — one tap when nothing qualifies as main, a
    // long-press always. The pill in the top bar remains the always-honest list of everything.
    val onFabClick = remember(viewModel, isMediaDetected, state.mainMediaUrl) {
        {
            when {
                state.mainMediaUrl != null -> viewModel.downloadMainMedia()
                isMediaDetected -> viewModel.openMediaTray()
                else -> scope.launch { snackbarHostState.showSnackbar(noMediaMessage) }
            }
            Unit
        }
    }
    val onFabLongClick = remember(viewModel) {
        { viewModel.openMediaTray(); Unit }
    }
    val onMediaClick = remember(viewModel) { { viewModel.openMediaTray() } }

    // A bookmark or a history entry reuses the tab the user came from.
    LaunchedEffect(inAppUrl) {
        val url = inAppUrl ?: return@LaunchedEffect
        viewModel.openInCurrentTab(url)
        onInAppUrlConsumed()
    }

    // A URL handed to the app (VIEW / SEND) opens in its own tab, so it never buries the page
    // the user was reading.
    LaunchedEffect(externalUrl) {
        val url = externalUrl ?: return@LaunchedEffect
        viewModel.openExternalUrl(url)
        onExternalUrlConsumed()
    }

    // A link pasted on the home screen. A direct media URL opens its quality sheet here; a page is
    // loaded and detected like any other. Both decisions live in the view model.
    LaunchedEffect(submittedLink) {
        val url = submittedLink ?: return@LaunchedEffect
        viewModel.openPastedLink(url)
        onSubmittedLinkConsumed()
    }

    LaunchedEffect(store, viewModel) {
        viewModel.commands.collectLatest { command ->
            val tabId = viewModel.uiState.value.currentTabId ?: return@collectLatest
            when (command) {
                is BrowserCommand.Load -> store.loadUrl(tabId, command.url)
                BrowserCommand.Back -> store.goBack(tabId)
                BrowserCommand.Forward -> store.goForward(tabId)
                BrowserCommand.Reload -> store.reload(tabId)
                BrowserCommand.Stop -> store.stopLoading(tabId)
                is BrowserCommand.FetchBlob -> store.fetchBlob(tabId, command.blobUrl, command.pageUrl)
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.messages.collectLatest { message ->
            if (message.isNotBlank()) snackbarHostState.showSnackbar(message)
        }
    }

    // Find in page is driven by the WebView's own match counter.
    val findState = state.findInPage
    LaunchedEffect(findState?.query, state.currentTabId, findState != null) {
        val tabId = state.currentTabId ?: return@LaunchedEffect
        if (findState == null) store.clearFind(tabId) else store.findAll(tabId, findState.query)
    }

    // listOfNotNull, not listOf: the ad-block entry is conditional, and a conditional entry is an
    // expression - which needs an else branch.
    val menuItems = listOfNotNull(
        // Two different things share the word "home" and both have to stay reachable: the browsable
        // homepage (below) and the app's own start screen with the paste field (further down).
        BrowserMenuItem(stringResource(R.string.home), Icons.Default.Home, onClick = { viewModel.goHome() }, isEnabled = hasPage),
        BrowserMenuItem(stringResource(R.string.new_tab), Icons.Default.Add, onClick = { viewModel.createNewTab() }),
        BrowserMenuItem(stringResource(R.string.new_private_tab), Icons.Default.Lock, onClick = { viewModel.createNewTab(isPrivate = true) }),
        BrowserMenuItem(stringResource(R.string.tabs), Icons.Default.Tab, onClick = { viewModel.openTabSwitcher() }),
        BrowserMenuItem(stringResource(R.string.find_in_page), Icons.Default.Search, onClick = { viewModel.openFindInPage() }, isEnabled = hasPage),
        BrowserMenuItem(stringResource(R.string.bookmark_add), Icons.Default.Bookmark, onClick = { viewModel.bookmarkCurrentTab() }, isEnabled = hasPage),
        BrowserMenuItem(stringResource(R.string.bookmarks), Icons.Default.Bookmarks, onClick = onNavigateToBookmarks),
        BrowserMenuItem(stringResource(R.string.history), Icons.Default.History, onClick = onNavigateToHistory),
        BrowserMenuItem(stringResource(R.string.share_page), Icons.Default.Share, onClick = { viewModel.shareCurrentPage() }, isEnabled = hasPage),
        BrowserMenuItem(stringResource(R.string.copy_link), Icons.Default.Link, onClick = { viewModel.copyCurrentLink() }, isEnabled = hasPage),
        BrowserMenuItem(
            stringResource(R.string.settings_desktop_site),
            Icons.Default.Sync,
            onClick = { viewModel.toggleDesktopSite() },
            isChecked = state.isDesktopSite,
            showsCheck = true
        ),
        // Only offered while the feature is on: a per-site switch for a feature that is off is a
        // menu entry that cannot do anything.
        if (state.isAdBlockEnabled) {
            BrowserMenuItem(
                stringResource(if (viewModel.isAdBlockOffForCurrentSite()) R.string.ad_block_enable_here else R.string.ad_block_disable_here),
                Icons.Default.Block,
                onClick = { viewModel.toggleAdBlockForCurrentSite() },
                isEnabled = hasPage
            )
        } else {
            null
        },
        BrowserMenuItem(stringResource(R.string.downloads), Icons.Default.Download, onClick = onNavigateToDownloads),
        BrowserMenuItem(stringResource(R.string.home_start_screen), Icons.Default.Dashboard, onClick = { onNavigateToHome?.invoke() }, isEnabled = onNavigateToHome != null),
        BrowserMenuItem(stringResource(R.string.settings), Icons.Default.Settings, onClick = onNavigateToSettings)
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            BrowserTopBar(
                url = state.url,
                isLoading = state.isLoading,
                isSecure = state.isSecure,
                canGoBack = state.canGoBack,
                canGoForward = state.canGoForward,
                progress = state.progress,
                onUrlChange = onUrlChange,
                onUrlSubmit = onUrlSubmit,
                onBackClick = onBackClick,
                onForwardClick = onForwardClick,
                mediaCount = mediaCount,
                onMediaClick = onMediaClick
            )

            findState?.let { find ->
                FindInPageBar(
                    query = find.query,
                    matches = find.matches,
                    activeMatch = find.activeMatch,
                    onQueryChange = remember(viewModel) { { viewModel.updateFindQuery(it) } },
                    onNext = remember(store, viewModel) { { viewModel.uiState.value.currentTabId?.let { store.findNext(it, true) } } },
                    onPrevious = remember(store, viewModel) { { viewModel.uiState.value.currentTabId?.let { store.findNext(it, false) } } },
                    onClose = remember(viewModel) { { viewModel.closeFindInPage() } }
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .onSizeChanged { size -> pageAreaSize = size }
            ) {
                // Brand splash while the first page loads — nebula glow backdrop
                if (state.url.isBlank() || (state.url == Constants.DEFAULT_HOMEPAGE && state.progress < 25)) {
                    Column(
                        modifier = Modifier.fillMaxSize().background(SplashNebulaBrush).padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        OrbitalBrandMark(size = 112.dp)
                        Spacer(Modifier.height(20.dp))
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleLarge,
                            color = PearlWhite,
                            letterSpacing = 3.sp
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.brand_tagline),
                            style = MaterialTheme.typography.bodySmall,
                            color = SilverMid
                        )
                    }
                }

                AndroidView(
                    factory = { ctx -> FrameLayout(ctx) },
                    update = { container ->
                        val tabId = state.currentTabId
                        if (tabId != null) {
                            // Closed tabs lose their WebView; live ones keep history and scroll.
                            store.retain(state.tabs.map { it.id }.toSet())
                            val initialUrl = state.tabs.firstOrNull { it.id == tabId }?.url.orEmpty()
                            val view: WebView = store.attach(container, tabId, initialUrl)
                            store.applySettings(state.isJavaScriptEnabled, state.areCookiesEnabled, state.isDesktopSite)
                            viewModel.onTabActivated(tabId)
                            viewModel.updateNavigationState(tabId, view.canGoBack(), view.canGoForward())
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                if (isMediaDetected) {
                    Text(
                        text = stringResource(R.string.media_detected, mediaCount),
                        style = MaterialTheme.typography.labelMedium,
                        color = PearlWhite,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(12.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.Black.copy(alpha = 0.52f))
                            .padding(1.dp)
                            .background(PearlEdgeBrush)
                            .padding(horizontal = 11.dp, vertical = 5.dp)
                    )
                }

                // On by default (the InShot-grade affordance this browser promised): one tap saves
                // the main video, long-press opens everything, drag moves it out of the way.
                if (state.isFabAlwaysVisible) {
                    FloatingDownloadButton(
                        isMediaDetected = isMediaDetected,
                        isMainReady = state.mainMediaUrl != null,
                        mediaCount = mediaCount,
                        onClick = onFabClick,
                        onLongClick = onFabLongClick,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp),
                        buttonSize = state.floatingButtonSize.dp,
                        containerSize = pageAreaSize,
                        initialOffset = Offset(state.fabPosition.x, state.fabPosition.y),
                        onOffsetChanged = remember(viewModel) {
                            { offset: Offset -> viewModel.saveFabPosition(offset.x, offset.y) }
                        }
                    )
                }

                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
                )

                if (state.isLoading && state.progress < 10) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(26.dp),
                        color = NebulaVioletLight,
                        strokeWidth = 2.dp
                    )
                }
            }

            state.blockedCleartextUrl?.let {
                Row(
                    modifier = Modifier.fillMaxWidth().background(CharcoalElevated).padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Security, contentDescription = null, tint = AuroraTeal, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.error_cleartext_blocked),
                        style = MaterialTheme.typography.bodySmall,
                        color = PearlWhite,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = remember(viewModel) { { viewModel.allowCleartextAndRetry() } }) {
                        Text(stringResource(R.string.action_allow), color = AuroraTeal)
                    }
                    TextButton(onClick = remember(viewModel) { { viewModel.dismissCleartextBlock() } }) {
                        Text(stringResource(R.string.action_dismiss), color = SilverMid)
                    }
                }
            }

            BrowserBottomBar(
                onReloadStopClick = onReloadStopClick,
                onDownloadsClick = onNavigateToDownloads,
                onSettingsClick = onNavigateToSettings,
                onTabsClick = remember(viewModel) { { viewModel.openTabSwitcher() } },
                menuItems = menuItems,
                isLoading = state.isLoading,
                activeDownloadCount = state.activeDownloadCount,
                tabsCount = state.tabs.size,
                menuContentDescription = stringResource(R.string.menu)
            )
        }

        if (state.isMediaTrayVisible) {
            MediaTraySheet(
                candidates = state.detectedMedia,
                visibleMediaUrl = state.visibleMediaUrl,
                unsupportedReason = remember(viewModel) { { candidate -> viewModel.unsupportedReasonFor(candidate) } },
                onSelect = remember(viewModel) { { candidate -> viewModel.downloadOrOffer(candidate) } },
                onDismiss = remember(viewModel) { { viewModel.closeMediaTray() } }
            )
        }

        if (state.isTabSwitcherVisible) {
            TabSwitcher(
                tabs = state.tabs,
                currentTabId = state.currentTabId,
                hibernatedTabIds = state.hibernatedTabIds,
                onSelect = remember(viewModel) { { id: String -> viewModel.switchTab(id) } },
                onClose = remember(viewModel) { { id: String -> viewModel.closeTab(id) } },
                onNewTab = remember(viewModel) { { viewModel.createNewTab() } },
                onNewPrivateTab = remember(viewModel) { { viewModel.createNewTab(isPrivate = true) } },
                onDismiss = remember(viewModel) { { viewModel.closeTabSwitcher() } }
            )
        }

        // Remembered unconditionally, above the sheet: a conditional remember would change the
        // composition's slot usage when a sheet opens with recording unavailable.
        val recordPlayback = remember(viewModel) { { viewModel.recordPlayback() } }
        val stopRecording = remember(viewModel) { { viewModel.stopMseRecording() } }
        state.qualitySheet?.let { sheet ->
            MediaQualitySelectionSheet(
                mediaInfo = sheet.mediaInfo,
                isResolving = sheet.isResolving,
                unsupported = sheet.unsupported,
                onDismiss = remember(viewModel) { { viewModel.closeQualitySheet() } },
                onQualitySelected = remember(viewModel, ensureNotificationPermission) {
                    { format ->
                        ensureNotificationPermission()
                        viewModel.handleFormatSelected(format)
                    }
                },
                // Recording is offered only where the device can do it, and while the sheet is open
                // the action follows the recording the view model owns: one button that starts, then
                // stops, with the bytes written so far beside it.
                recording = state.mseRecording,
                onRecord = recordPlayback.takeIf { sheet.canRecord },
                onStopRecording = stopRecording
            )
        }
    }

}
