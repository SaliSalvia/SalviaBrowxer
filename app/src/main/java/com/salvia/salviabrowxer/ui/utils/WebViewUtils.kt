package com.salvia.salviabrowxer.ui.utils

import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.salvia.salviabrowxer.core.model.MediaCandidate

fun configureWebView(
    webView: WebView,
    isJavaScriptEnabled: Boolean = true,
    isDesktopMode: Boolean = false,
    onMediaDetected: (MediaCandidate) -> Unit = {}
) {
    with(webView.settings) {
        javaScriptEnabled = isJavaScriptEnabled
        domStorageEnabled = true
        databaseEnabled = true
        setSupportZoom(false)
        displayZoomControls = false
        loadWithOverviewMode = true
        useWideViewPort = true
        builtInZoomControls = true
        allowFileAccess = true
        allowContentAccess = true

        if (isDesktopMode) {
            userAgentString = getDesktopUserAgent(this)
        }
    }

    webView.setBackgroundColor(android.graphics.Color.TRANSPARENT)
    webView.isVerticalScrollBarEnabled = false
    webView.isHorizontalScrollBarEnabled = false

    webView.webViewClient = createWebViewClient(onMediaDetected)
}

private fun getDesktopUserAgent(settings: WebSettings): String {
    val defaultUserAgent = settings.userAgentString
    return defaultUserAgent.replace("Mobile", "").replace("Android", "Linux")
}

fun createWebViewClient(
    onMediaDetected: (MediaCandidate) -> Unit = {}
): WebViewClient {
    return object : WebViewClient() {
        override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
            super.onPageStarted(view, url, favicon)
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            super.onPageFinished(view, url)
            view?.evaluateJavascript(
                "(function() { return document.documentElement.outerHTML; })();"
            ) { html ->
                // onMediaDetected will be called from the detector
            }
        }

        override fun shouldInterceptRequest(
            view: WebView?,
            request: android.webkit.WebResourceRequest?
        ): android.webkit.WebResourceResponse? {
            request?.let { req ->
                val url = req.url.toString()
                if (isMediaUrl(url)) {
                    val candidate = MediaCandidate(
                        pageUrl = view?.url ?: "",
                        mediaUrl = url,
                        source = com.salvia.salviabrowxer.core.model.MediaCandidate.MediaSource.WEBVIEW,
                        confidence = 0.7f
                    )
                    onMediaDetected(candidate)
                }
            }
            return super.shouldInterceptRequest(view, request)
        }
    }
}

private fun isMediaUrl(url: String): Boolean {
    val mediaExtensions = listOf(
        "mp4", "webm", "mov", "avi", "3gp", "m4v", "m3u8", "mpd",
        "mp3", "m4a", "aac", "wav"
    )
    val mediaMimeTypes = listOf(
        "video/mp4", "video/webm", "video/quicktime", "video/3gpp",
        "audio/mpeg", "audio/mp4", "audio/aac", "audio/wav",
        "application/vnd.apple.mpegurl", "application/x-mpegURL", "application/dash+xml"
    )

    return mediaExtensions.any { ext -> url.endsWith(ext, ignoreCase = true) } ||
            mediaMimeTypes.any { mime -> url.contains(mime, ignoreCase = true) }
}