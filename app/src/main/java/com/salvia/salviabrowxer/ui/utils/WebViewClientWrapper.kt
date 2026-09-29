package com.salvia.salviabrowxer.ui.utils

import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.salvia.salviabrowxer.core.model.MediaCandidate
import com.salvia.salviabrowxer.core.model.MediaRequestAdmission
import java.util.concurrent.ConcurrentHashMap

/**
 * Request-only fallback for explicit media URLs. Response MIME belongs to MediaSniffer: Accept
 * and outgoing Content-Type are not evidence of a downloadable response. No probes or rewriting
 * of the page's requests; fragments and bounded ranges stay out of the tray.
 */
class WebViewClientWrapper(
    private val onPageStartedHook: (WebView, String?, android.graphics.Bitmap?) -> Unit = { _, _, _ -> },
    private val onPageFinishedHook: (WebView, String?) -> Unit = { _, _ -> },
    private val onHistoryUrlChangedHook: (String) -> Unit = {},
    private val onMediaDetectedHook: (MediaCandidate) -> Unit = {},
    /** Return true when a non-web scheme (tel:, mailto:, intent:, market:) was handed to the system. */
    private val onExternalSchemeHook: (String) -> Boolean = { false }
) : WebViewClient() {

    @Volatile private var currentPageUrl = ""

    // URL -> last emit timestamp (ms)
    private val lastEmit = ConcurrentHashMap<String, Long>()

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
        currentPageUrl = url.orEmpty()
        lastEmit.clear()
        view?.let { safe("onPageStarted") { onPageStartedHook(it, url, favicon) } }
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        view?.let { safe("onPageFinished") { onPageFinishedHook(it, url) } }
    }

    override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
        super.doUpdateVisitedHistory(view, url, isReload)
        url?.takeIf { it.startsWith("https://") || it.startsWith("http://") }
            ?.let {
                currentPageUrl = it
                safe("onHistoryUrlChanged") { onHistoryUrlChangedHook(it) }
            }
    }

    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
        if (request != null && request.method.equals("GET", ignoreCase = true)) {
            val range = request.requestHeaders.entries.firstOrNull { it.key.equals("Range", true) }?.value
            val candidate = MediaRequestAdmission.admit(currentPageUrl, request.url.toString(), range)
            if (candidate != null) {
                val now = System.currentTimeMillis()
                val url = candidate.mediaUrl
                val last = lastEmit[url] ?: 0L
                if (now - last >= 5000) {
                    // Bounded even on an infinite feed. The UI has its own URL-keyed cap.
                    if (lastEmit.size >= 120) lastEmit.clear()
                    lastEmit[url] = now
                    safe("onMediaDetected") { onMediaDetectedHook(candidate) }
                }
            }
        }
        return super.shouldInterceptRequest(view, request)
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

    companion object {
        private const val TAG = "WebViewClientWrapper"
    }
}
