package com.salvia.salviabrowxer.ui.utils

import android.util.Log
import android.webkit.MimeTypeMap
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.salvia.salviabrowxer.core.model.MediaCandidate
import com.salvia.salviabrowxer.core.model.MediaCandidate.MediaSource
import java.util.concurrent.ConcurrentHashMap

/**
 * High-performance WebViewClient with:
 * - InShot-style media sniffing: intercepts HLS/DASH/blob and raw mp4/mp3 etc.
 * - Per-URL throttle (200ms) + dedupe set to prevent flooding the FAB.
 * - Header-aware MIME detection (Content-Type overrides extension).
 * - Never blocks the renderer thread.
 */
class WebViewClientWrapper(
    private val onPageStartedHook: (WebView, String?, android.graphics.Bitmap?) -> Unit = { _, _, _ -> },
    private val onPageFinishedHook: (WebView, String?) -> Unit = { _, _ -> },
    private val onMediaDetectedHook: (MediaCandidate) -> Unit = {}
) : WebViewClient() {

    // URL -> last emit timestamp (ms)
    private val lastEmit = ConcurrentHashMap<String, Long>()
    private val seenUrls = ConcurrentHashMap<String, Boolean>()

    override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
        super.onPageStarted(view, url, favicon)
        // Reset dedupe only when navigating to a new origin (keep within same page)
        view?.let { safe("onPageStarted") { onPageStartedHook(it, url, favicon) } }
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        view?.let { safe("onPageFinished") { onPageFinishedHook(it, url) } }
    }

    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
        request?.let { req ->
            val url = req.url.toString()
            // Quick reject: too short, data: images, tiny beacons
            if (url.length < 8) return super.shouldInterceptRequest(view, request)
            if (url.startsWith("data:image/")) return super.shouldInterceptRequest(view, request)

            val headers = req.requestHeaders
            val accept = headers["Accept"] ?: headers["accept"] ?: ""
            val contentType = headers["Content-Type"] ?: headers["content-type"] ?: ""

            val ext = mediaExtension(url)
            val mimeFromAccept = mimeFromAcceptHeader(accept)
            val isHlsOrDash = url.contains(".m3u8", true) || url.contains(".mpd", true) || url.contains("manifest", true)
            val isBlob = url.startsWith("blob:")

            val isMedia = ext != null || mimeFromAccept != null || isHlsOrDash || isBlob
            if (!isMedia) return super.shouldInterceptRequest(view, request)

            // Blob: URLs are already direct media; emit immediately
            if (isBlob) {
                emitCandidate(view, url, ext ?: "mp4", mimeFromAccept ?: "video/mp4")
                return super.shouldInterceptRequest(view, request)
            }

            // Throttle: at most once per 200ms per URL
            val now = System.currentTimeMillis()
            val last = lastEmit[url] ?: 0L
            if (now - last < 200) return super.shouldInterceptRequest(view, request)

            // Dedupe across the page lifetime (still allow re-emit after 5s)
            val firstSeen = seenUrls.putIfAbsent(url, true) == null
            if (!firstSeen && now - last < 5000) return super.shouldInterceptRequest(view, request)

            lastEmit[url] = now

            val extension = ext ?: extensionFromMime(mimeFromAccept) ?: "mp4"
            val mime = mimeFromAccept ?: mimeTypeFor(extension) ?: "video/mp4"
            emitCandidate(view, url, extension, mime)
        }
        return super.shouldInterceptRequest(view, request)
    }

    private fun emitCandidate(view: WebView?, url: String, extension: String, mime: String) {
        val title = url.substringBefore('?').substringAfterLast('/').substringBeforeLast('.').takeIf { it.isNotBlank() }
        val candidate = MediaCandidate(
            pageUrl = view?.url ?: "",
            mediaUrl = url,
            title = title,
            mimeType = mime,
            extension = extension.lowercase(),
            source = MediaSource.WEBVIEW,
            confidence = when {
                url.contains(".m3u8", true) || url.contains(".mpd", true) -> 0.95f
                extension in setOf("mp4", "webm", "mkv", "mov") -> 0.9f
                else -> 0.78f
            }
        )
        safe("onMediaDetected") { onMediaDetectedHook(candidate) }
    }

    private fun mimeFromAcceptHeader(accept: String): String? {
        if (accept.isBlank()) return null
        val lower = accept.lowercase()
        // Only accept if it explicitly asks for video/audio
        return when {
            "video/" in lower -> lower.substringAfter("video/").substringBefore(',').substringBefore(';').let { "video/$it" }
            "audio/" in lower -> lower.substringAfter("audio/").substringBefore(',').substringBefore(';').let { "audio/$it" }
            "application/vnd.apple.mpegurl" in lower -> "application/vnd.apple.mpegurl"
            "application/x-mpegurl" in lower -> "application/vnd.apple.mpegurl"
            "application/dash+xml" in lower -> "application/dash+xml"
            else -> null
        }
    }

    private fun extensionFromMime(mime: String?): String? = when {
        mime == null -> null
        mime.contains("mpegurl") || mime.contains("x-mpegurl") -> "m3u8"
        mime.contains("dash+xml") -> "mpd"
        mime.startsWith("video/mp4") -> "mp4"
        mime.startsWith("video/webm") -> "webm"
        mime.startsWith("audio/mpeg") -> "mp3"
        mime.startsWith("audio/mp4") -> "m4a"
        else -> null
    }

    private inline fun safe(where: String, block: () -> Unit) {
        try {
            block()
        } catch (error: Throwable) {
            if (error is Error && error !is StackOverflowError) throw error
            Log.w(TAG, "WebViewClientWrapper.$where failed", error)
        }
    }

    private fun mediaExtension(url: String): String? {
        val path = url.substringBefore('#').substringBefore('?')
        val lastDot = path.lastIndexOf('.')
        val lastSlash = path.lastIndexOf('/')
        if (lastDot <= lastSlash || lastDot == path.length - 1) return null
        val ext = path.substring(lastDot + 1).lowercase()
        // Reject obvious non-media even if extension matches by accident (e.g. /api.mp4/thumbnail)
        if (path.contains("/api/") && ext == "mp4" && path.length > 300) return null
        return ext.takeIf { it in DOWNLOADABLE_EXTENSIONS }
    }

    private fun mimeTypeFor(extension: String): String? =
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)

    companion object {
        private const val TAG = "WebViewClientWrapper"
        private val DOWNLOADABLE_EXTENSIONS: Set<String> = (
            Constants.SUPPORTED_VIDEO_EXTENSIONS +
                Constants.SUPPORTED_AUDIO_EXTENSIONS +
                Constants.SUPPORTED_PLAYLIST_EXTENSIONS
            ).toSet()
    }
}
