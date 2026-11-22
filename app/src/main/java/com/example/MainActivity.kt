package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.AppLanguage
import com.example.data.local.ThemePreference
import com.example.ui.ActiveSheet
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.i18n.stringsFor
import com.example.ui.BrowserViewModel
import com.example.ui.components.BookmarksSheet
import com.example.ui.components.BrowserBottomBar
import com.example.ui.components.BrowserTopBar
import com.example.ui.components.CosmicSpaceBackdrop
import com.example.ui.components.DevToolsSheet
import com.example.ui.components.DownloadsSheet
import com.example.ui.components.ExtensionsSheet
import com.example.ui.components.FloatingDownloadBubble
import com.example.ui.components.HistorySheet
import com.example.ui.components.MediaDownloaderSheet
import com.example.ui.components.SettingsSheet
import com.example.ui.components.SiteInfoSheet
import com.example.ui.components.StartPageScreen
import com.example.ui.components.TabSwitcherScreen
import com.example.ui.components.WebViewContainer
import com.example.ui.theme.NavaBrowserTheme

class MainActivity : ComponentActivity() {

    private val viewModel: BrowserViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIncomingIntent(intent)

        setContent {
            SalviaBrowserApp(viewModel = viewModel)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        viewModel.onTrimMemory(level)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        val dataUrl = intent?.dataString
        if (!dataUrl.isNullOrBlank() && (dataUrl.startsWith("http://") || dataUrl.startsWith("https://"))) {
            viewModel.createNewTab(isIncognito = false, initialUrl = dataUrl)
        }
    }
}

@Composable
fun SalviaBrowserApp(viewModel: BrowserViewModel) {
    val tabs by viewModel.tabs.collectAsStateWithLifecycle()
    val activeTabId by viewModel.activeTabId.collectAsStateWithLifecycle()
    val activeSheet by viewModel.activeSheet.collectAsStateWithLifecycle()
    val devToolsTab by viewModel.devToolsTab.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val extensions by viewModel.extensions.collectAsStateWithLifecycle()
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    val customAdRules by viewModel.customAdRules.collectAsStateWithLifecycle()
    val toastMessage by viewModel.statusToast.collectAsStateWithLifecycle()
    val findQuery by viewModel.findInPageQuery.collectAsStateWithLifecycle()

    val activeTab = tabs.firstOrNull { it.id == activeTabId } ?: tabs.first()
    var hadIncognitoTabs by remember { mutableStateOf(tabs.any { it.isIncognito }) }

    LaunchedEffect(tabs.count { it.isIncognito }) {
        val hasIncognitoTabs = tabs.any { it.isIncognito }
        if (hasIncognitoTabs) {
            hadIncognitoTabs = true
        } else if (hadIncognitoTabs) {
            // Runs after the outgoing WebViewContainer has disposed its popup and primary WebView.
            viewModel.onIncognitoSessionClosed()
            hadIncognitoTabs = false
        }
    }

    val isDarkTheme = when (settings.themePreference) {
        ThemePreference.DARK -> true
        ThemePreference.LIGHT -> false
        ThemePreference.SYSTEM -> isSystemInDarkTheme()
    }
    val activeExtensionsCount = extensions.count { it.isInstalled && it.isEnabled }
    val isBookmarked = !activeTab.isStartPage && bookmarks.any { it.url == activeTab.url }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(toastMessage) {
        val msg = toastMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.clearToast()
    }

    BackHandler(enabled = activeSheet == ActiveSheet.FIND_IN_PAGE) {
        viewModel.closeSheet()
    }

    BackHandler(enabled = activeSheet == ActiveSheet.NONE && !activeTab.isStartPage) {
        viewModel.goBack()
    }

    val appStrings = remember(settings.appLanguage) { stringsFor(settings.appLanguage) }
    val layoutDirection = if (settings.appLanguage == AppLanguage.PERSIAN) LayoutDirection.Rtl else LayoutDirection.Ltr

    CompositionLocalProvider(
        LocalAppStrings provides appStrings,
        LocalLayoutDirection provides layoutDirection,
    ) {
        NavaBrowserTheme(
            darkTheme = isDarkTheme,
            isIncognito = activeTab.isIncognito,
        ) {
        Box(modifier = Modifier.fillMaxSize()) {
            val renderCosmicScaffold = activeTab.isStartPage
            val scaffoldContent: @Composable () -> Unit = {
                Scaffold(
                    containerColor = if (renderCosmicScaffold) Color.Transparent else androidx.compose.material3.MaterialTheme.colorScheme.background,
                    snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                    topBar = {
                        BrowserTopBar(
                            tab = activeTab,
                            isBookmarked = isBookmarked,
                            adBlockEnabled = settings.adBlockEnabled,
                            activeExtensionsCount = activeExtensionsCount,
                            findInPageActive = activeSheet == ActiveSheet.FIND_IN_PAGE,
                            findQuery = findQuery,
                            onNavigate = { viewModel.navigateActiveTab(it) },
                            onReloadOrStop = { viewModel.reloadOrStop() },
                            onOpenSheet = { viewModel.openSheet(it) },
                            onToggleBookmark = { viewModel.toggleBookmarkActiveTab() },
                            onToggleDesktopMode = { viewModel.toggleDesktopModeForActiveTab() },
                            onCreateIncognitoTab = { viewModel.createNewTab(isIncognito = true) },
                            onFindQueryChange = { viewModel.findInPage(it) },
                            onFindNext = { viewModel.findNext(it) },
                            onCloseFind = { viewModel.closeSheet() },
                        )
                    },
                    bottomBar = {
                        BrowserBottomBar(
                            tab = activeTab,
                            tabCount = tabs.size,
                            onBack = { viewModel.goBack() },
                            onForward = { viewModel.goForward() },
                            onHome = { viewModel.goHomeActiveTab() },
                            onOpenExtensions = { viewModel.openSheet(ActiveSheet.EXTENSIONS) },
                            onOpenTabSwitcher = { viewModel.openSheet(ActiveSheet.TABS) },
                            onOpenDevTools = { viewModel.openSheet(ActiveSheet.DEVTOOLS) },
                        )
                    },
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                    ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        if (activeTab.isStartPage) {
                            StartPageScreen(
                                isIncognito = activeTab.isIncognito,
                                settings = settings,
                                bookmarks = bookmarks,
                                activeExtensionsCount = activeExtensionsCount,
                                onNavigate = { viewModel.navigateActiveTab(it) },
                                onOpenSheet = { viewModel.openSheet(it) },
                                onNewIncognitoTab = { viewModel.createNewTab(isIncognito = true) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                            )
                        } else {
                            WebViewContainer(
                                tab = activeTab,
                                settings = settings,
                                viewModel = viewModel,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                            )
                        }
                    }

                    // Floating Magnetic Draggable Download Bubble over browser viewport
                    FloatingDownloadBubble(
                        hasMedia = activeTab.hasMainMedia,
                        mediaCount = activeTab.detectedMedia.size,
                        onClick = { viewModel.openSheet(ActiveSheet.MEDIA_DOWNLOADER) },
                    )
                }
            }
            }

            if (renderCosmicScaffold) {
                CosmicSpaceBackdrop(
                    isIncognito = activeTab.isIncognito,
                    showEmblemWatermark = true,
                ) {
                    scaffoldContent()
                }
            } else {
                scaffoldContent()
            }

            // Overlays & Modal Sheets
            AnimatedContent(
                targetState = activeSheet,
                transitionSpec = {
                    (fadeIn(animationSpec = tween(170)) + scaleIn(initialScale = 0.97f, animationSpec = tween(170)))
                        .togetherWith(fadeOut(animationSpec = tween(130)))
                },
                label = "active_sheet_transition",
            ) { sheet ->
                when (sheet) {
                    ActiveSheet.TABS -> TabSwitcherScreen(
                        tabs = tabs,
                        activeTabId = activeTab.id,
                        onSelectTab = { viewModel.selectTab(it) },
                        onCloseTab = { viewModel.closeTab(it) },
                        onNewTab = { incognito -> viewModel.createNewTab(isIncognito = incognito) },
                        onCloseAllIncognito = { viewModel.closeAllIncognitoTabs() },
                        onDismiss = { viewModel.closeSheet() },
                    )

                    ActiveSheet.MEDIA_DOWNLOADER -> MediaDownloaderSheet(
                        detectedMedia = activeTab.detectedMedia,
                        onStartDownload = { fileName, quality, pageUrl ->
                            viewModel.downloadMedia(fileName, quality, pageUrl)
                        },
                        onRescanMedia = { viewModel.rescanActiveTabMedia() },
                        onDismiss = { viewModel.closeSheet() },
                    )

                ActiveSheet.EXTENSIONS -> ExtensionsSheet(
                    extensions = extensions,
                    onToggleEnabled = { id, enabled -> viewModel.setExtensionEnabled(id, enabled) },
                    onSetInstalled = { id, installed -> viewModel.setExtensionInstalled(id, installed) },
                    onCreateCustomExtension = { name, desc, match, runAt, js, css ->
                        viewModel.saveCustomExtension(name, desc, match, runAt, js, css)
                    },
                    onDeleteCustomExtension = { id -> viewModel.deleteCustomExtension(id) },
                    onDismiss = { viewModel.closeSheet() },
                )

                ActiveSheet.DEVTOOLS -> DevToolsSheet(
                    tab = activeTab,
                    selectedTab = devToolsTab,
                    onSelectDevToolsTab = { viewModel.setDevToolsTab(it) },
                    onEvaluateJs = { viewModel.evaluateJsInActiveTab(it) },
                    onClearConsole = { viewModel.clearConsoleLogs() },
                    onClearNetwork = { viewModel.clearNetworkLogs() },
                    onRefreshDom = { viewModel.refreshDomInspection() },
                    onCopyFeedback = { viewModel.showToast(it) },
                    onDismiss = { viewModel.closeSheet() },
                )

                ActiveSheet.DOWNLOADS -> DownloadsSheet(
                    downloads = downloads,
                    onPause = { viewModel.pauseDownload(it) },
                    onResume = { viewModel.resumeDownload(it) },
                    onCancel = { viewModel.cancelDownload(it) },
                    onDelete = { viewModel.deleteDownload(it) },
                    onClearFinished = { viewModel.clearFinishedDownloads() },
                    onDismiss = { viewModel.closeSheet() },
                )

                ActiveSheet.BOOKMARKS -> BookmarksSheet(
                    bookmarks = bookmarks,
                    onOpenUrl = { viewModel.navigateActiveTab(it) },
                    onAddBookmark = { t, u, p -> viewModel.addQuickBookmark(t, u, p) },
                    onDeleteBookmark = { viewModel.removeBookmark(it) },
                    onDismiss = { viewModel.closeSheet() },
                )

                ActiveSheet.HISTORY -> HistorySheet(
                    history = history,
                    onOpenUrl = { viewModel.navigateActiveTab(it) },
                    onDeleteItem = { viewModel.deleteHistoryItem(it) },
                    onClearAll = { viewModel.clearHistory() },
                    onDismiss = { viewModel.closeSheet() },
                )

                ActiveSheet.SITE_INFO -> SiteInfoSheet(
                    tab = activeTab,
                    adBlockEnabled = settings.adBlockEnabled,
                    isSiteWhitelisted = viewModel.isHostWhitelisted(
                        runCatching { android.net.Uri.parse(activeTab.url).host }.getOrNull(),
                    ),
                    onToggleSiteAdBlock = { host -> viewModel.toggleSiteAdBlock(host) },
                    onToggleDesktopMode = { viewModel.toggleDesktopModeForActiveTab() },
                    onDismiss = { viewModel.closeSheet() },
                )

                ActiveSheet.SETTINGS -> SettingsSheet(
                    settings = settings,
                    customAdRules = customAdRules,
                    onToggleAdBlock = { viewModel.setAdBlockEnabled(it) },
                    onToggleTrackerBlock = { viewModel.setTrackerBlockEnabled(it) },
                    onToggleHttpsUpgrade = { viewModel.setHttpsUpgradeEnabled(it) },
                    onToggleIncognito3pCookies = { viewModel.setIncognito3pCookieBlock(it) },
                    onToggleJavaScript = { viewModel.setJavaScriptEnabled(it) },
                    onToggleDoNotTrack = { viewModel.setDoNotTrackEnabled(it) },
                    onSelectSearchEngine = { viewModel.setSearchEngine(it) },
                    onSelectTheme = { viewModel.setThemePreference(it) },
                    onSelectLanguage = { viewModel.setAppLanguage(it) },
                    onAddAdRule = { pat, wl -> viewModel.addCustomAdRule(pat, wl) },
                    onDeleteAdRule = { viewModel.deleteAdRule(it) },
                    onClearAllBrowsingData = { viewModel.clearAllBrowsingData() },
                    onDismiss = { viewModel.closeSheet() },
                )

                    ActiveSheet.FIND_IN_PAGE, ActiveSheet.NONE -> Unit
                }
            }
        }
    }
    }
}
