package com.salvia.salviabrowxer.media.detector

import android.webkit.WebView
import android.webkit.WebViewClient
import com.salvia.salviabrowxer.core.model.MediaCandidate
import com.salvia.salviabrowxer.core.model.MediaCandidate.MediaSource
import com.salvia.salviabrowxer.core.model.MediaFileTypes

class WebViewMediaDetector : MediaDetector {

    override suspend fun detect(pageUrl: String, html: String?): List<MediaCandidate> {
        return emptyList()
    }

    fun createInterceptingWebViewClient(
        onMediaDetected: (MediaCandidate) -> Unit
    ): WebViewClient {
        return object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView?, request: android.webkit.WebResourceRequest?): android.webkit.WebResourceResponse? {
                request?.let { webRequest ->
                    val url = webRequest.url.toString()
                    val mimeType = webRequest.requestHeaders["Accept"]

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
        }
    }

    private fun isMediaRequest(url: String, mimeType: String?): Boolean =
        MediaFileTypes.isMediaRequest(url, mimeType)
}