package com.salvia.salviabrowxer.ui.utils

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.salvia.salviabrowxer.core.model.MediaCandidate
import com.salvia.salviabrowxer.core.model.MediaCandidate.MediaSource

class WebViewClientWrapper(
    private val onPageStarted: (WebView, String?, android.graphics.Bitmap?) -> Unit = { _, _, _ -> },
    private val onPageFinished: (WebView, String?) -> Unit = { _, _ -> },
    private val onMediaDetected: (MediaCandidate) -> Unit = {}
) : WebViewClient() {

    override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
        super.onPageStarted(view, url, favicon)
        view?.let { onPageStarted(it, url, favicon) }
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        view?.let { onPageFinished(it, url) }
    }

    override fun shouldInterceptRequest(
        view: WebView?,
        request: WebResourceRequest?
    ): WebResourceResponse? {
        request?.let { req ->
            val url = req.url.toString()
            if (isMediaUrl(url)) {
                val candidate = MediaCandidate(
                    pageUrl = view?.url ?: "",
                    mediaUrl = url,
                    mimeType = req.mimeType,
                    source = MediaSource.WEBVIEW,
                    confidence = 0.8f
                )
                onMediaDetected(candidate)
            }
        }
        return super.shouldInterceptRequest(view, request)
    }

    private fun isMediaUrl(url: String): Boolean {
        val mediaExtensions = listOf(
            "mp4", "webm", "mov", "avi", "3gp", "m4v", "mkv", "flv",
            "m3u8", "mpd", "ts",
            "mp3", "m4a", "aac", "wav", "flac", "ogg", "wma",
            "jpg", "jpeg", "png", "gif", "webp", "bmp", "svg"
        )
        return mediaExtensions.any { ext -> url.endsWith(".$ext", ignoreCase = true) }
    }
}