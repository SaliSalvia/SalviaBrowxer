package com.salvia.salviabrowxer.feature.browser

import android.os.Build
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.salvia.salviabrowxer.R
import com.salvia.salviabrowxer.ui.components.BrowserBottomBar
import com.salvia.salviabrowxer.ui.components.BrowserTopBar
import com.salvia.salviabrowxer.ui.components.FloatingDownloadButton
import com.salvia.salviabrowxer.ui.components.MediaQualitySelectionSheet
import com.salvia.salviabrowxer.ui.theme.Gold
import com.salvia.salviabrowxer.ui.utils.Constants
import com.salvia.salviabrowxer.ui.utils.WebViewClientWrapper
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@Composable
fun BrowserScreen(
    onNavigateToDownloads: () -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: BrowserViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val noMediaMessage = stringResource(R.string.no_media_detected)

    var webView: WebView? by remember { mutableStateOf(null) }
    var pageAreaSize by remember { mutableStateOf(IntSize.Zero) }
    val initialUrl = remember { state.url }
    var defaultUserAgent by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        viewModel.commands.collectLatest { command ->
            webView?.let { view ->
                when (command) {
                    is BrowserCommand.Load -> if (command.url.isNotBlank()) view.loadUrl(command.url)
                    BrowserCommand.Back -> if (view.canGoBack()) view.goBack()
                    BrowserCommand.Forward -> if (view.canGoForward()) view.goForward()
                    BrowserCommand.Reload -> view.reload()
                    BrowserCommand.Stop -> view.stopLoading()
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.messages.collectLatest { message ->
            if (message.isNotBlank()) snackbarHostState.showSnackbar(message)
        }
    }

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
            onUrlChange = { input -> viewModel.onAddressInputChange(input) },
            onUrlSubmit = { input -> viewModel.loadFromAddressBar(input) },
            onBackClick = { viewModel.goBack() },
            onForwardClick = { viewModel.goForward() },
            onRefreshClick = { viewModel.reload() },
            onStopClick = { viewModel.stopLoading() }
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .onSizeChanged { size -> pageAreaSize = size }
        ) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = state.isJavaScriptEnabled
                        settings.domStorageEnabled = true
                        settings.setSupportZoom(true)
                        settings.builtInZoomControls = true
                        settings.displayZoomControls = false
                        settings.loadWithOverviewMode = true
                        settings.useWideViewPort = true
                        settings.cacheMode = WebSettings.LOAD_DEFAULT
                        settings.mediaPlaybackRequiresUserGesture = false
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            settings.safeBrowsingEnabled = true
                        }
                        defaultUserAgent = settings.userAgentString

                        webViewClient = WebViewClientWrapper(
                            onPageStartedHook = { _, url, _ -> viewModel.onPageStarted(url) },
                            onPageFinishedHook = { view, url ->
                                val pageUrl = view.url ?: url ?: ""
                                viewModel.onPageFinished(pageUrl, view.title)
                                viewModel.updateNavigationState(
                                    canGoBack = view.canGoBack(),
                                    canGoForward = view.canGoForward()
                                )
                                view.evaluateJavascript(
                                    MEDIA_DETECTION_JAVASCRIPT,
                                    { rawHtml ->
                                        if (pageUrl.isNotEmpty()) {
                                            decodeJavascriptString(rawHtml)
                                                ?.let { html -> viewModel.detectMediaInPage(pageUrl, html) }
                                        }
                                    }
                                )
                            },
                            onMediaDetectedHook = { candidate -> viewModel.onMediaIntercepted(candidate) }
                        )

                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                super.onProgressChanged(view, newProgress)
                                viewModel.onProgressChanged(newProgress)
                            }
                        }

                        if (initialUrl.isNotBlank()) {
                            loadUrl(initialUrl)
                        }
                    }
                },
                update = { view ->
                    webView = view
                    view.settings.javaScriptEnabled = state.isJavaScriptEnabled
                    CookieManager.getInstance().setAcceptCookie(state.areCookiesEnabled)
                    val agent = if (state.isDesktopSite) {
                        Constants.DESKTOP_USER_AGENT
                    } else {
                        defaultUserAgent ?: view.settings.userAgentString
                    }
                    if (view.settings.userAgentString != agent) {
                        view.settings.userAgentString = agent
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            if (state.isMediaDetected) {
                Text(
                    text = stringResource(R.string.media_detected, state.detectedMedia.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }

            // Only the 56.dp circle occupies this Box, so page touches keep reaching the WebView.
            FloatingDownloadButton(
                isMediaDetected = state.isMediaDetected,
                mediaCount = state.detectedMedia.size,
                onClick = {
                    if (state.isMediaDetected) {
                        viewModel.openQualitySheet()
                    } else {
                        scope.launch { snackbarHostState.showSnackbar(noMediaMessage) }
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                containerSize = pageAreaSize,
                initialOffset = Offset(state.fabPosition.x, state.fabPosition.y),
                onOffsetChanged = { offset -> viewModel.saveFabPosition(offset.x, offset.y) }
            )

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
                        .size(28.dp),
                    color = Gold,
                    strokeWidth = 2.dp
                )
            }
        }

        BrowserBottomBar(
            onHomeClick = { viewModel.goHome() },
            onDownloadsClick = onNavigateToDownloads,
            onSettingsClick = onNavigateToSettings,
            activeDownloadCount = state.activeDownloadCount
        )
    }

    state.qualitySheet?.let { sheet ->
        MediaQualitySelectionSheet(
            mediaInfo = sheet.mediaInfo,
            isResolving = sheet.isResolving,
            onDismiss = { viewModel.closeQualitySheet() },
            onQualitySelected = { format -> viewModel.enqueueDownload(format) }
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            webView?.destroy()
            webView = null
        }
    }
}

/** `evaluateJavascript` hands back a JSON encoded string; turn it into raw HTML again. */
private fun decodeJavascriptString(raw: String?): String? {
    if (raw.isNullOrBlank() || raw == "null") return null
    val trimmed = raw.trim()
    if (!trimmed.startsWith("\"")) return trimmed
    return runCatching { org.json.JSONTokener(trimmed).nextValue() as? String }
        .getOrNull()
        ?: trimmed.removeSurrounding("\"")
}

/**
 * Return only the DOM nodes the detector needs instead of `document.outerHTML`.
 *
 * `evaluateJavascript` sends its result across a Binder boundary before Kotlin receives it. A
 * large page can therefore crash the WebView renderer or host process even if Kotlin trims the
 * callback value afterwards. This snapshot preserves video/audio/source nodes and direct media
 * links, while enforcing the size limit inside the renderer before the result crosses processes.
 */
private const val MEDIA_DETECTION_JAVASCRIPT = """
    (function () {
        // Keep ample headroom for JSON escaping in the evaluateJavascript callback.
        var maxCharacters = 65536;
        var mediaPath = /\.(mp4|webm|mov|avi|3gp|m4v|mkv|flv|m3u8|mpd|ts|mp3|m4a|aac|wav|flac|ogg|wma)(?:[?#]|$)/i;
        var nodes = Array.prototype.slice.call(
            document.querySelectorAll('video, audio, source, a[href]')
        ).filter(function (node) {
            return node.tagName !== 'A' || mediaPath.test(node.getAttribute('href') || '');
        });
        var html = '<html><body>';
        for (var i = 0; i < nodes.length && html.length < maxCharacters; i++) {
            var nodeHtml = nodes[i].outerHTML;
            if (html.length + nodeHtml.length > maxCharacters) break;
            html += nodeHtml;
        }
        return html + '</body></html>';
    })();
"""
