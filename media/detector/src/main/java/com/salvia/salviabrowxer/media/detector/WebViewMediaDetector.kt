package com.salvia.salviabrowxer.media.detector

import android.webkit.WebView
import android.webkit.WebViewClient
import com.salvia.salviabrowxer.core.model.MediaCandidate
import com.salvia.salviabrowxer.core.model.MediaCandidate.MediaSource

class WebViewMediaDetector : MediaDetector {

    private val mediaMimeTypes = listOf(
        "video/mp4", "video/webm", "video/quicktime", "video/3gpp",
        "audio/mpeg", "audio/mp4", "audio/aac", "audio/wav",
        "application/vnd.apple.mpegurl", "application/x-mpegURL", "application/dash+xml"
    )

    private val mediaExtensions = listOf(
        "mp4", "webm", "mov", "avi", "3gp", "m4v", "m3u8", "mpd",
        "mp3", "m4a", "aac", "wav"
    )

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
                    val mimeType = webRequest.mimeType

                    if (isMediaRequest(url, mimeType)) {
                        val candidate = MediaCandidate(
                            pageUrl = view?.url ?: "",
                            mediaUrl = url,
                            mimeType = mimeType,
                            extension = getExtension(url),
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

    private fun isMediaRequest(url: String, mimeType: String?): Boolean {
        return (mimeType != null && mediaMimeTypes.any { mimeType.contains(it) }) ||
                mediaExtensions.any { ext -> url.endsWith(ext, ignoreCase = true) }
    }

    private fun getExtension(url: String): String? {
        val lastDotIndex = url.lastIndexOf('.')
        val lastSlashIndex = url.lastIndexOf('/')
        return if (lastDotIndex > lastSlashIndex && lastDotIndex < url.length - 1) {
            url.substring(lastDotIndex + 1).lowercase()
        } else {
            null
        }
    }
}