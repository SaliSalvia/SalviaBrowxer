package com.salvia.salviabrowxer.ui.utils

import android.os.Build
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.salvia.salviabrowxer.core.model.MediaCandidate

/**
 * Shared WebView helpers — kept aligned with [BrowserScreen]'s inline configuration
 * so there is a single source of truth for performance & security flags.
 * The canonical media detection path is [WebViewClientWrapper] + [DomMediaDetector];
 * this file is retained only for legacy callers (e.g. tabs/history previews) and
 * delegates detection to the same [Constants.SUPPORTED_*] sets to avoid drift.
 */
fun configureWebView(
    webView: WebView,
    isJavaScriptEnabled: Boolean = true,
    isDesktopMode: Boolean = false,
    onMediaDetected: (MediaCandidate) -> Unit = {}
) {
    webView.setLayerType(WebView.LAYER_TYPE_HARDWARE, null)
    webView.isVerticalScrollBarEnabled = false
    webView.isHorizontalScrollBarEnabled = false
    webView.overScrollMode = WebView.OVER_SCROLL_NEVER

    with(webView.settings) {
        javaScriptEnabled = isJavaScriptEnabled
        domStorageEnabled = true
        databaseEnabled = true
        // Zoom: enabled but without on-screen controls (mirrors BrowserScreen)
        setSupportZoom(true)
        builtInZoomControls = true
        displayZoomControls = false
        loadWithOverviewMode = true
        useWideViewPort = true
        // Media must autoplay inline — required for sniffing via HTMLMediaElement.src
        mediaPlaybackRequiresUserGesture = false
        // Keep http media reachable on http pages — BrowserScreen uses COMPATIBILITY_MODE for this reason
        mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        allowFileAccess = false
        allowContentAccess = false
        allowFileAccessFromFileURLs = false
        allowUniversalAccessFromFileURLs = false
        javaScriptCanOpenWindowsAutomatically = false
        @Suppress("DEPRECATION")
        setGeolocationEnabled(false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) safeBrowsingEnabled = true
        // Perf: high priority raster
        setRenderPriority(WebSettings.RenderPriority.HIGH)
        @Suppress("DEPRECATION")
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            @Suppress("DEPRECATION")
            cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
        } else {
            cacheMode = WebSettings.LOAD_DEFAULT
        }
        if (isDesktopMode) userAgentString = getDesktopUserAgent(this)
    }

    webView.setBackgroundColor(android.graphics.Color.TRANSPARENT)
    webView.webViewClient = createWebViewClient(onMediaDetected)
}

private fun getDesktopUserAgent(settings: WebSettings): String =
    settings.userAgentString.replace("Mobile", "").replace("Android", "Linux")

fun createWebViewClient(
    onMediaDetected: (MediaCandidate) -> Unit = {}
): WebViewClient = object : WebViewClient() {
    override fun shouldInterceptRequest(
        view: WebView?,
        request: android.webkit.WebResourceRequest?
    ): android.webkit.WebResourceResponse? {
        request?.let { req ->
            val url = req.url.toString()
            if (url.length >= 8 && !url.startsWith("data:image/") && isMediaUrl(url)) {
                val candidate = MediaCandidate(
                    pageUrl = view?.url ?: "",
                    mediaUrl = url,
                    source = MediaCandidate.MediaSource.WEBVIEW,
                    confidence = when {
                        ".m3u8" in url || ".mpd" in url -> 0.95f
                        url.substringAfterLast('.', "").lowercase() in setOf("mp4", "webm", "mkv", "mov") -> 0.9f
                        else -> 0.78f
                    }
                )
                onMediaDetected(candidate)
            }
        }
        return super.shouldInterceptRequest(view, request)
    }
}

/** Single source of truth — derived from [Constants] so detection never drifts between modules. */
private fun isMediaUrl(url: String): Boolean {
    val path = url.substringBefore('#').substringBefore('?')
    val lastDot = path.lastIndexOf('.')
    val lastSlash = path.lastIndexOf('/')
    if (lastDot > lastSlash && lastDot < path.length - 1) {
        val ext = path.substring(lastDot + 1).lowercase()
        if (ext in Constants.SUPPORTED_VIDEO_EXTENSIONS || ext in Constants.SUPPORTED_AUDIO_EXTENSIONS || ext in Constants.SUPPORTED_PLAYLIST_EXTENSIONS) return true
    }
    // HLS/DASH may appear without extension via query e.g. .../manifest?format=m3u8
    if (url.contains(".m3u8", true) || url.contains(".mpd", true) || url.contains("manifest", true)) return true
    if (url.startsWith("blob:")) return true
    return false
}
