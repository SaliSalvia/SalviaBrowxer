package com.salvia.salviabrowxer.feature.browser

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.salvia.salviabrowxer.core.model.MediaCandidate
import com.salvia.salviabrowxer.ui.components.BrowserBottomBar
import com.salvia.salviabrowxer.ui.components.BrowserTopBar
import com.salvia.salviabrowxer.ui.components.FloatingDownloadButton
import kotlinx.coroutines.launch

@Composable
fun BrowserScreen(
    onNavigateToDownloads: () -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: BrowserViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var showQualitySheet by remember { mutableStateOf(false) }
    var selectedMediaCandidate by remember { mutableStateOf<MediaCandidate?>(null) }
    var webView: WebView? by remember { mutableStateOf(null) }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        BrowserTopBar(
            url = viewModel.currentUrl,
            isLoading = viewModel.isLoading,
            isSecure = false,
            canGoBack = viewModel.canGoBack,
            canGoForward = viewModel.canGoForward,
            onUrlChange = { url -> viewModel.updateUrl(url) },
            onBackClick = { webView?.goBack() },
            onForwardClick = { webView?.goForward() },
            onRefreshClick = { webView?.reload() },
            onStopClick = { webView?.stopLoading() },
            onUrlSubmit = { url ->
                if (url.isNotEmpty()) {
                    val finalUrl = if (url.startsWith("http://") || url.startsWith("https://")) {
                        url
                    } else {
                        "https://$url"
                    }
                    webView?.loadUrl(finalUrl)
                    viewModel.updateUrl(finalUrl)
                    viewModel.addHistoryEntry(finalUrl, "Loading...")
                }
            }
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.setSupportZoom(false)
                        settings.displayZoomControls = false
                        settings.loadWithOverviewMode = true
                        settings.useWideViewPort = true
                        settings.builtInZoomControls = true

                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                super.onPageStarted(view, url, favicon)
                                viewModel.updateLoading(true)
                                viewModel.updateUrl(url ?: "")
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                viewModel.updateLoading(false)
                                viewModel.updateTitle(view?.title ?: "")
                                viewModel.updateCanGoBack(view?.canGoBack() ?: false)
                                viewModel.updateCanGoForward(view?.canGoForward() ?: false)

                                view?.evaluateJavascript(
                                    "(function() { return document.documentElement.outerHTML; })();"
                                ) { html ->
                                    viewModel.detectMediaInPage(url ?: "", html.removeSurrounding("\""))
                                }
                            }
                        }

                        if (viewModel.currentUrl.isNotEmpty()) {
                            loadUrl(viewModel.currentUrl)
                        } else {
                            loadUrl("https://www.google.com")
                            viewModel.updateUrl("https://www.google.com")
                        }
                    }
                },
                update = { webView = it },
                modifier = Modifier.fillMaxSize()
            )

            FloatingDownloadButton(
                isMediaDetected = viewModel.isMediaDetected,
                mediaCount = viewModel.detectedMedia.size,
                onClick = {
                    if (viewModel.isMediaDetected && viewModel.detectedMedia.isNotEmpty()) {
                        selectedMediaCandidate = viewModel.detectedMedia.first()
                        showQualitySheet = true
                    }
                },
                modifier = Modifier.align(androidx.compose.ui.Alignment.BottomEnd),
                initialPosition = androidx.compose.ui.geometry.Offset(0f, 0f),
                onPositionChange = { offset ->
                    // Save position to DataStore
                }
            )
        }

        BrowserBottomBar(
            onHomeClick = {
                webView?.loadUrl("https://www.google.com")
                viewModel.updateUrl("https://www.google.com")
            },
            onTabsClick = { /* TODO: Implement tab switcher */ },
            onMenuClick = onNavigateToDownloads
        )
    }

    SnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier.padding(16.dp)
    )

    LaunchedEffect(viewModel.currentTabId) {
        viewModel.currentTabId?.let { tabId ->
            viewModel.tabs.find { it.id == tabId }?.let { tab ->
                webView?.loadUrl(tab.url)
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webView?.destroy()
        }
    }
}