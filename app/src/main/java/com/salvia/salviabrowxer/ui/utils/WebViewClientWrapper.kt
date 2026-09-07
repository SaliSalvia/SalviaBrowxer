package com.salvia.salviabrowxer.ui.utils

import android.util.Log
import android.webkit.MimeTypeMap
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.salvia.salviabrowxer.core.model.MediaCandidate
import com.salvia.salviabrowxer.core.model.MediaCandidate.MediaSource

/**
 * A [WebViewClient] that forwards page lifecycle events and reports media URLs it sees on the
 * wire, so the download FAB can light up without extra network traffic.
 *
 * IMPORTANT: the hook properties are intentionally **not** named `onPageStarted` / `onPageFinished`.
 * A property with the same name as an overridden member is shadowed by that member at the call
 * site, so `onPageStarted(view, url, favicon)` inside the override would call the override again
 * and blow the stack (that is exactly the `StackOverflowError` this class used to produce). Call the
 * hooks through their own, uniquely named properties.
 */
class WebViewClientWrapper(
    private val onPageStartedHook: (WebView, String?, android.graphics.Bitmap?) -> Unit = { _, _, _ -> },
    private val onPageFinishedHook: (WebView, String?) -> Unit = { _, _ -> },
    private val onMediaDetectedHook: (MediaCandidate) -> Unit = {}
) : WebViewClient() {

    override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
        super.onPageStarted(view, url, favicon)
        view?.let { safe("onPageStarted") { onPageStartedHook(it, url, favicon) } }
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        view?.let { safe("onPageFinished") { onPageFinishedHook(it, url) } }
    }

    override fun shouldInterceptRequest(
        view: WebView?,
        request: WebResourceRequest?
    ): WebResourceResponse? {
        request?.let { req ->
            val url = req.url.toString()
            val extension = mediaExtension(url)
            if (extension != null) {
                val candidate = MediaCandidate(
                    pageUrl = view?.url ?: "",
                    mediaUrl = url,
                    title = url.substringBefore('?').substringAfterLast('/').substringBeforeLast('.'),
                    mimeType = mimeTypeFor(extension),
                    extension = extension,
                    source = MediaSource.WEBVIEW,
                    confidence = 0.8f
                )
                safe("onMediaDetected") { onMediaDetectedHook(candidate) }
            }
        }
        return super.shouldInterceptRequest(view, request)
    }

    /** Never let a UI callback take the renderer thread (and the app) down with it. */
    private inline fun safe(where: String, block: () -> Unit) {
        try {
            block()
        } catch (error: Throwable) {
            if (error is Error && error !is StackOverflowError) throw error
            Log.w(TAG, "WebViewClientWrapper.$where failed", error)
        }
    }

    /**
     * Returns the media extension of [url] when the resource looks like downloadable audio/video
     * (images and scripts are deliberately ignored - they are not "media" for this app).
     */
    private fun mediaExtension(url: String): String? {
        val path = url.substringBefore('#').substringBefore('?')
        val lastDot = path.lastIndexOf('.')
        val lastSlash = path.lastIndexOf('/')
        if (lastDot <= lastSlash || lastDot == path.length - 1) return null
        val extension = path.substring(lastDot + 1).lowercase()
        return extension.takeIf {
            it in DOWNLOADABLE_EXTENSIONS
        }
    }

    private fun mimeTypeFor(extension: String): String? =
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)

    companion object {
        private const val TAG = "WebViewClientWrapper"

        private val DOWNLOADABLE_EXTENSIONS: Set<String> =
            (
                Constants.SUPPORTED_VIDEO_EXTENSIONS +
                    Constants.SUPPORTED_AUDIO_EXTENSIONS +
                    Constants.SUPPORTED_PLAYLIST_EXTENSIONS
                ).toSet()
    }
}
