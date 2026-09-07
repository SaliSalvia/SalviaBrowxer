package com.salvia.salviabrowxer.ui.utils

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.salvia.salviabrowxer.core.model.MediaCandidate
import com.salvia.salviabrowxer.core.model.MediaCandidate.MediaSource
import com.salvia.salviabrowxer.core.model.MediaFileTypes

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
            val mimeType = req.requestHeaders["Accept"]
            if (isMediaRequest(url, mimeType)) {
                val candidate = MediaCandidate(
                    pageUrl = view?.url ?: "",
                    mediaUrl = url,
                    mimeType = mimeType?.substringBefore(';')?.trim()?.takeIf { it.isNotBlank() },
                    extension = MediaFileTypes.extensionFromUrl(url),
                    source = MediaSource.WEBVIEW,
                    confidence = 0.8f
                )
                onMediaDetected(candidate)
            }
        }
        return super.shouldInterceptRequest(view, request)
    }

    private fun isMediaRequest(url: String, mimeType: String?): Boolean =
        MediaFileTypes.isMediaRequest(url, mimeType)
}