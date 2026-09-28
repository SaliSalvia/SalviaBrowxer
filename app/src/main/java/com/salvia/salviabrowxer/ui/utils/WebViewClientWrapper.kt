package com.salvia.salviabrowxer.ui.utils

import android.util.Log
import android.webkit.MimeTypeMap
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.salvia.salviabrowxer.core.model.MediaCandidate
import com.salvia.salviabrowxer.core.model.MediaCandidate.MediaSource
import com.salvia.salviabrowxer.core.model.MediaUrlRules
import java.util.concurrent.ConcurrentHashMap

/**
 * High-performance WebViewClient with:
 * - Media sniffing: intercepts HLS, blob and media requests the page made itself.
 * - Per-URL throttle (200ms) + dedupe set to prevent flooding the FAB.
 * - Header-aware MIME detection (Content-Type overrides extension).
 * - Never blocks the renderer thread.
 *
 * What this layer can and cannot see is worth stating, because the rest of detection used to assume
 * it saw more than it does. `shouldInterceptRequest` receives **request** headers, never the
 * response `Content-Type`, so a URL with no extension and a generic `Accept` is invisible here —
 * which is most media on social CDNs. That case belongs to [MediaSniffer], which runs in the page
 * and can read the response. This class covers the same-origin and extension-bearing cases, plus
 * `blob:` URLs, and deliberately does not try to guess at the rest: a wrong guess here means a row
 * in the tray that fails at download time.
 */
class WebViewClientWrapper(
    private val onPageStartedHook: (WebView, String?, android.graphics.Bitmap?) -> Unit = { _, _, _ -> },
    private val onPageFinishedHook: (WebView, String?) -> Unit = { _, _ -> },
    private val onMediaDetectedHook: (MediaCandidate) -> Unit = {},
    /** Return true when a non-web scheme (tel:, mailto:, intent:, market:) was handed to the system. */
    private val onExternalSchemeHook: (String) -> Boolean = { false }
) : WebViewClient() {

    // URL -> last emit timestamp (ms)
    private val lastEmit = ConcurrentHashMap<String, Long>()
    private val seenUrls = ConcurrentHashMap<String, Boolean>()

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        val url = request?.url?.toString() ?: return false
        if (isWebUrl(url)) return false
        return safe("onExternalScheme") { onExternalSchemeHook(url) } ?: false
    }

    private fun isWebUrl(url: String): Boolean =
        url.startsWith("http://", true) ||
            url.startsWith("https://", true) ||
            url.startsWith("about:", true) ||
            url.startsWith("data:", true) ||
            url.startsWith("blob:", true) ||
            url.startsWith("file:", true) ||
            url.startsWith("javascript:", true)

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

            val ext = MediaUrlRules.pathExtension(url)?.takeIf { it in MediaUrlRules.MEDIA_EXTENSIONS }
            val mimeFromAccept = MediaUrlRules.normalizeMime(mimeFromAcceptHeader(accept))
            val isDash = MediaUrlRules.isDashUrl(url, contentType.ifBlank { null })
            // A bare `manifest` in the URL used to count as HLS, so `site.webmanifest` and
            // `manifest.json` offered themselves as playlists that could never download.
            // MediaUrlRules.isPlaylistUrl keeps the real CDN hint and excludes document manifests.
            val isHls = MediaUrlRules.isPlaylistUrl(url, contentType.ifBlank { null })
            val isBlob = url.startsWith("blob:")

            val isMedia = ext != null || mimeFromAccept != null || isHls || isDash || isBlob
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

            // A DASH manifest keeps its real identity: the tray explains that it cannot be saved
            // instead of offering it as an mp4 that would always fail at download time.
            val extension = when {
                isDash -> MediaUrlRules.DASH_EXTENSION
                else -> ext ?: MediaUrlRules.extensionForMime(mimeFromAccept) ?: "mp4"
            }
            val mime = when {
                isDash -> MediaUrlRules.DASH_MIME_TYPE
                else -> mimeFromAccept ?: mimeTypeFor(extension) ?: "video/mp4"
            }
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
                MediaUrlRules.isPlaylistUrl(url, mime) -> 0.95f
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
            else -> null
        }
    }

    private inline fun <T> safe(where: String, block: () -> T): T? {
        return try {
            block()
        } catch (error: Throwable) {
            if (error is Error && error !is StackOverflowError) throw error
            Log.w(TAG, "WebViewClientWrapper.$where failed", error)
            null
        }
    }

    private fun mimeTypeFor(extension: String): String? =
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)

    companion object {
        private const val TAG = "WebViewClientWrapper"
    }
}
