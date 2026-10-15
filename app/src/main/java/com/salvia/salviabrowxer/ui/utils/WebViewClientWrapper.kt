package com.salvia.salviabrowxer.ui.utils

import android.net.http.SslError
import android.util.Log
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.salvia.salviabrowxer.core.model.MediaCandidate
import java.io.ByteArrayInputStream

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
    private val onExternalSchemeHook: (String) -> Boolean = { false },
    /**
     * The page's renderer process died. Only `destroy()` may be called on [WebView] afterwards —
     * stopLoading, removeJavascriptInterface and even setWebViewClient throw on a dead renderer.
     */
    private val onRenderProcessGoneHook: (WebView) -> Unit = {},
    /** A main-frame load failed. Sub-resource failures are deliberately not reported. */
    private val onPageErrorHook: (kind: PageErrorKind, description: String) -> Unit = { _, _ -> },
    /**
     * The opt-in ad blocker. Off by default, and the very first thing a request meets: a blocked
     * request is answered here and never reaches the observer, so a blocked ad can never surface in
     * the media tray as something to download.
     */
    private val adBlocker: AdBlocker = AdBlocker()
) : WebViewClient() {

    @Volatile private var currentPageUrl = ""

    /**
     * The document's requests, through the same observer a service worker's requests go through:
     * one admission rule and one dedupe window for both, so a URL that arrives by either route is
     * reported once.
     */
    private val requestObserver = MediaRequestObserver(
        pageUrlProvider = { currentPageUrl },
        onCandidate = { candidate -> safe("onMediaDetected") { onMediaDetectedHook(candidate) } }
    )

    /** The order every request meets: the blocker's veto, then the observer. */
    private val interception = RequestInterception(adBlocker, requestObserver)

    /**
     * Returning `true` says the crash was handled. Returning `false` — the default — makes the
     * framework treat a dead renderer as an unrecoverable app error and kill the whole process, so a
     * single bad page would take the browser down with it.
     */
    override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
        val target = view ?: return true
        safe("onRenderProcessGone") { onRenderProcessGoneHook(target) }
        return true
    }

    override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
        super.onReceivedError(view, request, error)
        // Every blocked ad frame and missing pixel raises this; only the document itself matters.
        if (request?.isForMainFrame != true) return
        val description = runCatching { error?.description?.toString().orEmpty() }.getOrDefault("")
        safe("onReceivedError") { onPageErrorHook(PageErrorKind.NETWORK, description) }
    }

    override fun onReceivedHttpError(view: WebView?, request: WebResourceRequest?, errorResponse: WebResourceResponse?) {
        super.onReceivedHttpError(view, request, errorResponse)
        if (request?.isForMainFrame != true) return
        val status = errorResponse?.statusCode ?: return
        safe("onReceivedHttpError") { onPageErrorHook(PageErrorKind.HTTP, "HTTP $status") }
    }

    /**
     * An untrusted certificate is never overridden — this browser does not offer "proceed anyway" —
     * but the user is told why the tab stayed empty instead of being left with a blank page.
     */
    override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
        val description = runCatching { error?.primaryError?.toString().orEmpty() }.getOrDefault("")
        safe("onReceivedSslError") { onPageErrorHook(PageErrorKind.CERTIFICATE, description) }
        handler?.cancel()
    }

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        val url = request?.url?.toString() ?: return false
        if (isWebUrl(url)) return false
        // A subframe nobody tapped has no business leaving the app: an ad iframe that navigates
        // itself to `intent://` or `market://` used to reach the system's app chooser from a
        // gesture the user never made. The main frame and a real tap both keep their old behaviour.
        if (request.isForMainFrame != true && !request.hasGesture()) return true
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
        requestObserver.reset()
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
        val range = request?.requestHeaders?.entries?.firstOrNull { it.key.equals("Range", true) }?.value
        // One decision, taken in [RequestInterception]: the blocker first, so a blocked request is
        // answered here and never observed, never offered in the tray and never sent. [AdBlockRules]
        // is what keeps media, playlists, segments, loopback addresses and the page's own host out of
        // the blocker's reach.
        val decision = interception.decide(
            url = request?.url?.toString(),
            method = request?.method,
            range = range,
            pageUrl = currentPageUrl
        )
        if (decision == RequestInterception.Decision.BLOCKED) return blockedResponse()
        return super.shouldInterceptRequest(view, request)
    }

    companion object {
        private const val TAG = "WebViewClientWrapper"

        /**
         * What a blocked request is answered with: an empty 200.
         *
         * The alternative — letting the request fail — makes the page's own error handler run and
         * some players treat a failed ad request as a failed stream. An empty successful response is
         * what the page sees from a blocker that already answered it, and it costs nothing.
         */
        fun blockedResponse(): WebResourceResponse =
            WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
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

}
