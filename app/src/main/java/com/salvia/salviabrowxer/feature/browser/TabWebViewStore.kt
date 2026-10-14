package com.salvia.salviabrowxer.feature.browser

import android.annotation.SuppressLint
import android.content.Context
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.ServiceWorkerClientCompat
import androidx.webkit.ServiceWorkerControllerCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.salvia.salviabrowxer.core.model.MediaCandidate
import com.salvia.salviabrowxer.core.model.MediaUrlRules
import com.salvia.salviabrowxer.core.model.WebOrigin
import com.salvia.salviabrowxer.ui.bridge.BlobDownloadBridge
import com.salvia.salviabrowxer.ui.bridge.MediaSnifferBridge
import com.salvia.salviabrowxer.ui.bridge.MseRecordingBridge
import com.salvia.salviabrowxer.ui.utils.MediaRequestObserver
import com.salvia.salviabrowxer.ui.utils.MediaSniffer
import com.salvia.salviabrowxer.ui.utils.MseRecordingSupport
import com.salvia.salviabrowxer.ui.utils.frameClaimAccepted
import com.salvia.salviabrowxer.ui.utils.PageErrorKind
import com.salvia.salviabrowxer.ui.utils.WebViewClientWrapper
import com.salvia.salviabrowxer.ui.utils.WebViewSetup
import java.io.File
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

/**
 * Owns one WebView per live tab, and nothing else.
 *
 * Rules this class enforces:
 * - A live tab keeps its full WebView state (back/forward stack, scroll position, form input),
 *   so switching tabs is instant and never reloads.
 * - At most [maxLiveTabs] WebViews exist at once. Beyond that the least recently used tab is
 *   *hibernated*: its last committed URL and title are reported to [Callbacks.onTabHibernated]
 *   and the WebView is destroyed. Restoring that tab loads its URL again — a reload, never a
 *   blank page.
 * - WebViews for closed tabs are destroyed by [retain].
 * - A page whose renderer process crashes costs that tab its WebView and nothing else: the dead view
 *   is destroyed, the tab is reported through [Callbacks.onRendererGone], and showing the tab again
 *   loads the URL the view model still holds. A browser that dies with one bad page is not a browser.
 *
 * The store deliberately does no bookkeeping of its own beyond the live set: the view model owns
 * tab metadata (title, URL, private flag) and this class owns pixels and render state.
 */
class TabWebViewStore(
    private val context: Context,
    private val callbacks: Callbacks,
    private val maxLiveTabs: Int = MAX_LIVE_TABS
) {

    interface Callbacks {
        fun onPageStarted(tabId: String, url: String)
        fun onPageFinished(tabId: String, url: String, title: String?)
        fun onHistoryUrlChanged(tabId: String, url: String)
        fun onProgress(tabId: String, progress: Int)
        fun onNavigationState(tabId: String, canGoBack: Boolean, canGoForward: Boolean)
        fun onPageHtml(tabId: String, pageUrl: String, html: String)
        fun onMediaDetected(tabId: String, candidate: MediaCandidate)
        fun onVisibleMedia(tabId: String, pageUrl: String, mediaUrl: String)
        fun onMediaExpired(tabId: String, pageUrl: String, mediaUrl: String)
        fun onBlobCaptured(tabId: String, pageUrl: String, blobUrl: String, file: File, mimeType: String)
        fun onTabHibernated(tabId: String, url: String, title: String)
        fun onFindResult(tabId: String, matches: Int, activeMatch: Int)
        /** Hand a non-web scheme to the system. Return true when it was handled. */
        fun onExternalScheme(url: String): Boolean
        /**
         * The page's renderer process died and this tab's WebView has been destroyed. The tab's URL
         * is still known to the caller, so showing the tab again builds a fresh WebView for it.
         */
        fun onRendererGone(tabId: String)
        /** A main-frame load failed. The chrome decides how to say it. */
        fun onPageError(tabId: String, kind: PageErrorKind, description: String)
        /**
         * The page asked to save something the browser cannot render — a PDF, a ZIP, an installer.
         * Without this the click does nothing at all, which is a broken browser on the open web.
         */
        fun onDownloadRequested(
            tabId: String,
            url: String,
            userAgent: String?,
            contentDisposition: String?,
            mimeType: String?,
            contentLength: Long
        )
        /**
         * A page says it is about to feed the recording armed for this tab, and which mime it is.
         * [sourceOrigin] and [isMainFrame] are the frame's own coordinates, which is what keeps an
         * unrelated ad frame from writing into the file the user asked for.
         */
        fun onMseOpen(tabId: String, mimeType: String, sourceOrigin: String?, isMainFrame: Boolean)
        /** One appended segment, as bytes. */
        fun onMseSegment(tabId: String, bytes: ByteArray, sourceOrigin: String?, isMainFrame: Boolean)
        /** The page hit the recording ceiling; the file so far is complete as far as it goes. */
        fun onMseCap(tabId: String)
        /** The stream is protected (DRM). Nothing was kept, and the recording stops here. */
        fun onMseEncrypted(tabId: String)
    }

    private val live = mutableMapOf<String, WebView>()
    private val usage = ArrayDeque<String>()

    /**
     * The tab on screen. A worker's request carries no tab, so it is attributed to the visible page.
     * Written on the main thread, read from the worker observer's own thread.
     */
    @Volatile private var visibleTabId: String? = null

    /** The page the visible tab last started loading, for attribution of worker requests. */
    @Volatile private var lastPageUrl: String = ""

    /**
     * The origins of the frames the current document of a tab contains.
     *
     * This is what makes a report *attributable*: each frame announces itself once through the
     * recording port (the same channel a recording uses, because it is the only one where WebKit
     * hands the app the frame's own origin and whether it is the main frame), and every sighting a
     * frame reports must come from an origin that is actually in the document. A frame claiming to be
     * another site is refused instead of poisoning the tray; a real embed is accepted, which is what
     * makes media inside iframes visible at all. Cleared when the tab loads a new page.
     */
    private val verifiedFrames = ConcurrentHashMap<String, MutableSet<String>>()

    /**
     * The same opt-in blocker the document's client uses. One instance, so a request blocked through
     * a worker and the same request blocked through the document are one decision and one count.
     */
    private val adBlocker = com.salvia.salviabrowxer.ui.utils.AdBlocker()

    /** Service worker requests, observed with the same rule the document's requests go through. */
    private val serviceWorkerObserver = MediaRequestObserver(
        pageUrlProvider = { lastPageUrl },
        onCandidate = { candidate -> visibleTabId?.let { callbacks.onMediaDetected(it, candidate) } }
    )

    private var serviceWorkerObserverInstalled = false

    private var javaScriptEnabled = true
    private var desktopMode = false
    private var cookiesEnabled = true
    private var defaultUserAgent: String? = null

    /** Document-start injection needs a WebView new enough to have the feature. */
    private val documentStartSniffer: Boolean = runCatching {
        WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
    }.getOrDefault(false)

    fun liveTabIds(): Set<String> = live.keys.toSet()

    fun isLive(tabId: String): Boolean = live.containsKey(tabId)

    /** Returns the tab's WebView, creating it (and evicting an LRU tab) when needed. */
    fun webViewFor(tabId: String, initialUrl: String): WebView {
        live[tabId]?.let { touch(tabId); return it }
        evictOverflow(keep = tabId)
        val view = create(tabId)
        live[tabId] = view
        touch(tabId)
        if (initialUrl.isNotBlank()) view.loadUrl(initialUrl)
        return view
    }

    /** Attaches the tab's WebView to [container], detaching whatever was there before. */
    fun attach(container: ViewGroup, tabId: String, initialUrl: String): WebView {
        visibleTabId = tabId
        val view = webViewFor(tabId, initialUrl)
        (view.parent as? ViewGroup)?.takeIf { it !== container }?.removeView(view)
        if (container.childCount > 0 && container.getChildAt(0) !== view) container.removeAllViews()
        if (view.parent == null) {
            container.addView(view, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        return view
    }

    /** Destroys the WebViews of tabs that no longer exist. */
    fun retain(tabIds: Set<String>) {
        live.keys.filterNot { it in tabIds }.forEach { destroy(it) }
    }

    fun currentUrl(tabId: String): String? = live[tabId]?.url

    /**
     * Whether [url] belongs to the document the tab is showing: the page itself, or one of the frames
     * the page actually contains. False for a tab whose frames were never registered (an older WebView
     * without the message port), which is the same answer the app gave before frames could be told
     * apart — so nothing that used to be shown disappears.
     */
    fun isDocumentUrl(tabId: String, url: String): Boolean {
        val frames = verifiedFrames[tabId] ?: return false
        val origin = WebOrigin.of(url) ?: return false
        return frames.contains(origin)
    }

    /**
     * The user agent the tab is actually loaded with — the WebView's own, desktop mode included.
     * A transfer that repeats it looks like the same client to the server, which is what some CDNs
     * check before they hand over a file the page was already playing. Main thread only, like the
     * rest of this class.
     */
    fun userAgent(tabId: String): String? = live[tabId]?.settings?.userAgentString?.takeIf { it.isNotBlank() }

    fun title(tabId: String): String? = live[tabId]?.title

    fun canGoBack(tabId: String): Boolean = live[tabId]?.canGoBack() == true

    fun canGoForward(tabId: String): Boolean = live[tabId]?.canGoForward() == true

    fun loadUrl(tabId: String, url: String) {
        if (url.isBlank()) return
        val existing = live[tabId]
        if (existing == null) {
            // No WebView yet (a tab created moments ago): creating it with the target URL is
            // what keeps a brand-new tab from showing a blank page.
            webViewFor(tabId, url)
        } else {
            touch(tabId)
            existing.loadUrl(url)
        }
    }

    fun goBack(tabId: String) { live[tabId]?.takeIf { it.canGoBack() }?.goBack() }

    fun goForward(tabId: String) { live[tabId]?.takeIf { it.canGoForward() }?.goForward() }

    fun reload(tabId: String) { live[tabId]?.reload() }

    fun stopLoading(tabId: String) { live[tabId]?.stopLoading() }

    fun evaluateJavascript(tabId: String, script: String) { live[tabId]?.evaluateJavascript(script, null) }

    /** Whether this device's WebView can hand a page's `appendBuffer` payload to the app at all. */
    fun supportsMseRecording(): Boolean = MseRecordingSupport.isSupported

    /**
     * Asks the page to start feeding the recording armed for this tab.
     *
     * The answer comes back from the page's own hook, which refuses when it has no recording port
     * (an old WebView) or when the media is protected — the caller turns that into a message rather
     * than a silence the user cannot tell from a bug.
     */
    fun armMseRecording(tabId: String, kind: String, onResult: (Boolean) -> Unit) {
        val view = live[tabId] ?: run { onResult(false); return }
        val requested = if (kind == "audio") "audio" else "video"
        val script = "(function(){try{return (typeof window.__salviaMseArm==='function') " +
            "&& (window.__salviaMseArm('$requested')===true);}catch(e){return false;}})()"
        view.evaluateJavascript(script) { raw -> onResult(raw?.trim() == "true") }
    }

    fun disarmMseRecording(tabId: String) {
        val view = live[tabId] ?: return
        view.evaluateJavascript(
            "(function(){try{if(typeof window.__salviaMseDisarm==='function')window.__salviaMseDisarm();}catch(e){}})()",
            null
        )
    }

    /** Reads a blob: URL the page owns and streams it back through the blob bridge in chunks. */
    fun fetchBlob(tabId: String, blobUrl: String, pageUrl: String) {
        val view = live[tabId] ?: return
        val script = BLOB_FETCH_JS
            .replace("__BLOB_URL__", jsStringLiteral(blobUrl))
            .replace("__PAGE_URL__", jsStringLiteral(pageUrl))
        view.evaluateJavascript(script, null)
    }

    private fun jsStringLiteral(raw: String): String =
        raw.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "").replace("\r", "")

    /** Hibernates a tab: its URL and title are handed back before the WebView is destroyed. */
    fun hibernate(tabId: String) {
        val view = live.remove(tabId) ?: return
        usage.remove(tabId)
        verifiedFrames.remove(tabId)
        val url = view.url.orEmpty()
        val title = view.title.orEmpty()
        destroyView(view)
        callbacks.onTabHibernated(tabId, url, title)
    }

    fun destroy(tabId: String) {
        val view = live.remove(tabId) ?: return
        usage.remove(tabId)
        verifiedFrames.remove(tabId)
        destroyView(view)
    }

    fun destroyAll() {
        live.keys.toList().forEach { destroy(it) }
        usage.clear()
        verifiedFrames.clear()
        visibleTabId = null
    }

    /** Applies the three live settings to every open WebView without reloading on every pass. */
    fun applySettings(javaScriptEnabled: Boolean, cookiesEnabled: Boolean, desktopMode: Boolean) {
        this.javaScriptEnabled = javaScriptEnabled
        this.desktopMode = desktopMode
        if (this.cookiesEnabled != cookiesEnabled) {
            val manager = CookieManager.getInstance()
            manager.setAcceptCookie(cookiesEnabled)
            this.cookiesEnabled = cookiesEnabled
        }
        live.values.forEach { view ->
            if (view.settings.javaScriptEnabled != javaScriptEnabled) view.settings.javaScriptEnabled = javaScriptEnabled
            val ua = WebViewSetup.userAgentFor(defaultUserAgent ?: view.settings.userAgentString, desktopMode)
            if (view.settings.userAgentString != ua) {
                view.settings.userAgentString = ua
                // A user-agent change only takes effect on the next load.
                if (view.url?.isNotBlank() == true) view.reload()
            }
        }
    }

    // region find in page
    fun findAll(tabId: String, query: String) {
        val view = live[tabId] ?: return
        if (query.isBlank()) {
            clearFind(tabId)
            return
        }
        @Suppress("DEPRECATION")
        view.findAllAsync(query)
    }

    fun findNext(tabId: String, forward: Boolean) {
        val view = live[tabId] ?: return
        @Suppress("DEPRECATION")
        view.findNext(forward)
    }

    fun clearFind(tabId: String) {
        val view = live[tabId] ?: return
        @Suppress("DEPRECATION")
        view.clearMatches()
    }
    // endregion

    // region internals
    private fun touch(tabId: String) {
        usage.remove(tabId)
        usage.addLast(tabId)
    }

    private fun evictOverflow(keep: String) {
        while (live.size >= maxLiveTabs) {
            val victim = usage.firstOrNull { it != keep && live.containsKey(it) } ?: return
            hibernate(victim)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun create(tabId: String): WebView {
        val view = WebView(context)
        WebViewSetup.apply(view, javaScriptEnabled, desktopMode)
        if (defaultUserAgent == null) defaultUserAgent = view.settings.userAgentString
        // A bridge per WebView, not one for the store: a blob read belongs to the tab that asked for
        // it, and the view model can then check that tab's page before it accepts a file at all.
        // Blob staging gets its own cache subdirectory so FileProvider only exposes that folder.
        view.addJavascriptInterface(
            BlobDownloadBridge(
                onBlobCaptured = { pageUrl, blobUrl, file, mime ->
                    callbacks.onBlobCaptured(tabId, pageUrl, blobUrl, file, mime)
                },
                cacheDirProvider = { File(context.cacheDir, "blob") },
                // A blob belongs to the frame that read it, and only a frame of the page on screen may
                // hand one over — the bridge is a `JavascriptInterface`, which any frame can call.
                pageAllowed = { pageUrl -> acceptsFrameReport(tabId, pageUrl) }
            ),
            BRIDGE_NAME
        )
        // A bridge per WebView keeps sightings from a background tab out of the foreground tray, and
        // nothing reaches it unverified: the frame that reports a URL has to be a frame of the page
        // the tab is showing.
        view.addJavascriptInterface(MediaSnifferBridge(
            onAdmitted = { candidate ->
                if (acceptsFrameReport(tabId, candidate.pageUrl)) callbacks.onMediaDetected(tabId, candidate)
            },
            onVisible = { pageUrl, mediaUrl ->
                if (acceptsFrameReport(tabId, pageUrl)) callbacks.onVisibleMedia(tabId, pageUrl, mediaUrl)
            },
            onExpired = { pageUrl, mediaUrl ->
                if (acceptsFrameReport(tabId, pageUrl)) callbacks.onMediaExpired(tabId, pageUrl, mediaUrl)
            },
            onEncrypted = { _, _ -> callbacks.onMseEncrypted(tabId) }
        ), MediaSniffer.BRIDGE_NAME)
        installMseRecording(view, tabId)
        installServiceWorkerObserver()
        installSniffer(view)
        view.webViewClient = WebViewClientWrapper(
            adBlocker = adBlocker,
            onPageStartedHook = { _, url, _ ->
                url?.takeIf { it.isNotBlank() }?.let {
                    if (tabId == visibleTabId) lastPageUrl = it
                    // A new document: the frames of the old one are gone, and with them the right to
                    // speak for this tab until they announce themselves again.
                    verifiedFrames[tabId] = synchronizedSet()
                    serviceWorkerObserver.reset()
                    callbacks.onPageStarted(tabId, it)
                }
                // The script guards itself with window.__salviaMediaHook, so running it here as
                // well as at document start costs one no-op and covers WebViews where document
                // start injection is not available.
                view.evaluateJavascript(MediaSniffer.script, null)
            },
            onHistoryUrlChangedHook = { url ->
                callbacks.onHistoryUrlChanged(tabId, url)
                view.evaluateJavascript("window.__salviaScan && window.__salviaScan();", null)
                // pushState on a feed does not fire onPageFinished. Re-read only the DOM already
                // in this WebView; the view model checks tab and URL before merging.
                view.evaluateJavascript(MEDIA_DETECTION_JS) { rawHtml ->
                    decodeJavascriptString(rawHtml)?.let { html -> callbacks.onPageHtml(tabId, url, html) }
                }
            },
            onPageFinishedHook = { web, url ->
                val pageUrl = web.url ?: url.orEmpty()
                web.evaluateJavascript(MediaSniffer.script, null)
                callbacks.onPageFinished(tabId, pageUrl, web.title)
                callbacks.onNavigationState(tabId, web.canGoBack(), web.canGoForward())
                web.evaluateJavascript(MEDIA_DETECTION_JS) { rawHtml ->
                    if (pageUrl.isNotBlank()) decodeJavascriptString(rawHtml)?.let { html -> callbacks.onPageHtml(tabId, pageUrl, html) }
                }
            },
            onMediaDetectedHook = { candidate -> callbacks.onMediaDetected(tabId, candidate) },
            onExternalSchemeHook = { url -> callbacks.onExternalScheme(url) },
            onRenderProcessGoneHook = { dead -> handleRendererGone(dead) },
            onPageErrorHook = { kind, description -> callbacks.onPageError(tabId, kind, description) }
        )
        // Anything the page offers as a file: the browser cannot show a PDF or a ZIP, so it goes to
        // the download queue instead of nowhere.
        view.setDownloadListener { url, userAgent, contentDisposition, mimeType, contentLength ->
            if (url.isNullOrBlank()) return@setDownloadListener
            callbacks.onDownloadRequested(tabId, url, userAgent, contentDisposition, mimeType, contentLength)
        }
        view.webChromeClient = object : WebChromeClient() {
            private var lastProgress = 0
            private var lastProgressTime = 0L

            override fun onProgressChanged(web: WebView?, newProgress: Int) {
                super.onProgressChanged(web, newProgress)
                val now = System.currentTimeMillis()
                if (newProgress == 100 || newProgress - lastProgress >= 2 || now - lastProgressTime >= 80) {
                    lastProgress = newProgress
                    lastProgressTime = now
                    callbacks.onProgress(tabId, newProgress)
                }
            }
        }
        view.setFindListener { activeMatchOrdinal, numberOfMatches, isDoneCounting ->
            if (isDoneCounting) callbacks.onFindResult(tabId, numberOfMatches, activeMatchOrdinal)
        }
        return view
    }

    /**
     * Injects the object a page's recording hook posts to, and the listener that receives it.
     *
     * The origin rule is `*` on purpose: a player inside a cross-origin frame is exactly the case this
     * has to cover, and the object is not a way to read anything out of the app — it only accepts
     * messages. What a message may *do* is decided in `MseRecordingController`, which knows whether
     * the user armed this tab and which frame is allowed to speak for the stream.
     */
    private fun installMseRecording(view: WebView, tabId: String) {
        if (!MseRecordingSupport.isSupported) return
        runCatching {
            WebViewCompat.addWebMessageListener(
                view,
                MediaSniffer.MSE_PORT_NAME,
                setOf("*"),
                MseRecordingBridge(
                    onFrame = { claimedUrl, sourceOrigin, isMainFrame ->
                        rememberFrame(tabId, claimedUrl, sourceOrigin, isMainFrame)
                    },
                    onOpen = { mime, origin, isMainFrame -> callbacks.onMseOpen(tabId, mime, origin, isMainFrame) },
                    onSegment = { bytes, origin, isMainFrame -> callbacks.onMseSegment(tabId, bytes, origin, isMainFrame) },
                    onCap = { _, _ -> callbacks.onMseCap(tabId) }
                )
            )
        }
    }

    /**
     * A frame of this document announced itself.
     *
     * The claim is checked against the origin WebKit reported for the posting frame, so a frame can
     * only ever register *itself*: the origin is what a page cannot forge, and the claimed URL has to
     * agree with it. When WebKit says the posting frame is the main frame, the claimed URL is also the
     * page's URL — the most trustworthy page URL the app has, and the one worker requests are
     * attributed to.
     */
    private fun rememberFrame(tabId: String, claimedUrl: String, sourceOrigin: String?, isMainFrame: Boolean) {
        if (!frameClaimAccepted(claimedUrl, sourceOrigin)) return
        val origin = WebOrigin.of(sourceOrigin) ?: WebOrigin.of(claimedUrl) ?: return
        // The listener runs on the WebView's thread and the set is read from a worker's, so the set
        // itself is synchronized rather than assumed to be single-threaded.
        framesOf(tabId).add(origin)
        if (isMainFrame) lastPageUrl = claimedUrl
    }

    /** The registered origins of a tab's document, created on first use. */
    private fun framesOf(tabId: String): MutableSet<String> =
        verifiedFrames.getOrPut(tabId) { synchronizedSet() }

    private fun synchronizedSet(): MutableSet<String> = Collections.synchronizedSet(mutableSetOf())

    /**
     * Whether a report from [reportedPageUrl] may speak for the tab.
     *
     * True while nothing has been registered: a WebView without the message port cannot verify frames
     * at all, and refusing every report there would turn a hardening into a regression. Once frames do
     * announce themselves, a report has to come from one the document actually contains.
     */
    private fun acceptsFrameReport(tabId: String, reportedPageUrl: String): Boolean {
        if (!MseRecordingSupport.isSupported) return true
        val frames = verifiedFrames[tabId] ?: return true
        if (frames.isEmpty()) return true
        val origin = WebOrigin.of(reportedPageUrl) ?: return false
        return frames.contains(origin)
    }

    /**
     * A page's service worker fetches media the document's client never sees.
     *
     * Observed, not answered: the app reads the URL, the method and the `Range` header, returns
     * nothing, and the request goes on exactly as it would have. A worker that downloads a player's
     * segments becomes visible without a single byte being read or rewritten.
     */
    private fun installServiceWorkerObserver() {
        if (serviceWorkerObserverInstalled) return
        serviceWorkerObserverInstalled = true
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.SERVICE_WORKER_SHOULD_INTERCEPT_REQUEST)) return
        runCatching {
            ServiceWorkerControllerCompat.getInstance().setServiceWorkerClient(
                object : ServiceWorkerClientCompat() {
                    override fun shouldInterceptRequest(request: WebResourceRequest): WebResourceResponse? {
                        // A worker's requests meet the same blocker as the document's, for the same
                        // reason: a blocked request must not be observed as if it had happened.
                        if (adBlocker.shouldBlock(request.url?.toString(), lastPageUrl)) {
                            return WebViewClientWrapper.blockedResponse()
                        }
                        val range = request.requestHeaders?.entries
                            ?.firstOrNull { it.key.equals("Range", true) }?.value
                        serviceWorkerObserver.onRequest(request.url?.toString(), request.method, range)
                        return null
                    }
                }
            )
        }
    }

    /**
     * Installs the media sniffer before the page runs any of its own script.
     *
     * This is the difference for a player that fetches its manifest and first segments the moment
     * it loads: by `onPageFinished` those requests are long gone. The feature is gated because it
     * needs a WebView new enough to have it; [MediaSniffer.script] is also evaluated from
     * `onPageStarted`, and the script's own guard makes doing both idempotent.
     */
    private fun installSniffer(view: WebView) {
        if (!documentStartSniffer) return
        runCatching { WebViewCompat.addDocumentStartJavaScript(view, MediaSniffer.script, setOf("*")) }
    }

    /**
     * A dead renderer. The WebView cannot be reused or cleaned up the normal way — every call on it
     * throws once its renderer is gone — so it is only destroyed, and the tab is reported so the
     * composition builds a new WebView for the URL the tab still remembers.
     */
    private fun handleRendererGone(view: WebView) {
        val tabId = live.entries.firstOrNull { it.value === view }?.key
        if (tabId != null) {
            live.remove(tabId)
            usage.remove(tabId)
        }
        runCatching { view.destroy() }
        if (tabId != null) callbacks.onRendererGone(tabId)
    }

    private fun destroyView(view: WebView) {
        (view.parent as? ViewGroup)?.removeView(view)
        runCatching { view.stopLoading() }
        runCatching { view.removeJavascriptInterface(BRIDGE_NAME) }
        runCatching { view.removeJavascriptInterface(MediaSniffer.BRIDGE_NAME) }
        runCatching { view.webViewClient = WebViewClient() }
        runCatching { view.webChromeClient = null }
        runCatching { view.destroy() }
    }

    companion object {
        /** Live WebViews before the least recently used tab is hibernated. */
        const val MAX_LIVE_TABS = 8
        private const val BRIDGE_NAME = "SalviaBridge"
    }
    // endregion
}

private fun decodeJavascriptString(raw: String?): String? {
    if (raw.isNullOrBlank() || raw == "null") return null
    val trimmed = raw.trim()
    if (!trimmed.startsWith("\"")) return trimmed
    return runCatching { org.json.JSONTokener(trimmed).nextValue() as? String }.getOrNull()
        ?: trimmed.removeSurrounding("\"")
}

/** Fetches a blob: URL in Binder-safe chunks — the same path InShot uses. */
private const val BLOB_FETCH_JS = """
    (function(){
        var blobUrl='__BLOB_URL__';
        var pageUrl='__PAGE_URL__';
        var CHUNK=480*1024;
        try{
            if(window.SalviaBridge) window.SalviaBridge.onBlobUrlFound(pageUrl, blobUrl);
            var xhr=new XMLHttpRequest();
            xhr.open('GET', blobUrl, true);
            xhr.responseType='blob';
            xhr.onload=function(){
                if(xhr.status!==200 && xhr.status!==0) return;
                var blob=xhr.response; if(!blob) return;
                var mime=blob.type || 'video/mp4';
                if(blob.size < 4*1024*1024){
                    var r=new FileReader();
                    r.onloadend=function(){
                        try{ if(window.SalviaBridge) window.SalviaBridge.onBlobData(pageUrl, blobUrl, r.result, mime); }catch(e){}
                    };
                    r.readAsDataURL(blob);
                    return;
                }
                var total=Math.ceil(blob.size/CHUNK);
                var idx=0;
                function next(){
                    if(idx>=total) return;
                    var slice=blob.slice(idx*CHUNK, (idx+1)*CHUNK);
                    var rr=new FileReader();
                    (function(cur){
                        rr.onloadend=function(){
                            try{
                                var b64=rr.result;
                                if(window.SalviaBridge) window.SalviaBridge.onBlobChunk(pageUrl, blobUrl, cur, total, b64, mime);
                            }catch(e){}
                            idx++; next();
                        };
                        rr.onerror=function(){ idx++; next(); };
                    })(idx);
                    rr.readAsDataURL(slice);
                }
                next();
            };
            xhr.onerror=function(){};
            xhr.send();
        }catch(e){}
    })();
"""

/*
 * The sniffer that used to live here is now MediaSniffer.script, installed at document start by
 * TabWebViewStore.installSniffer(). It hooked the same APIs but only ever called Kotlin for blob:
 * URLs — and that handler just logged — so everything else it found was written into a page-side
 * array, serialised as HTML below, and then discarded by DomMediaDetector for having no media
 * extension. That is why an extension-less CDN video was never detected.
 */

/**
 * Serialises the page's media-bearing markup for
 * [com.salvia.salviabrowxer.media.detector.DomMediaDetector].
 *
 * The extension alternation is interpolated from
 * [com.salvia.salviabrowxer.core.model.MediaUrlRules] rather than written out again, because this
 * regex used to be a third copy that could disagree with the Kotlin side.
 */
private val MEDIA_DETECTION_JS: String = """
    (function () {
        var maxCharacters = 62000;
        var mediaPath = /\.(__MEDIA_EXT__)(\?|#|$)/i;
        var html = '<html><body>';
        function append(node) {
            var outer = node.outerHTML || '';
            if (outer.length > 8000 || html.length + outer.length > maxCharacters) return;
            html += outer;
        }
        // Metadata describes the post's video even while a pre-roll owns the player. This is
        // markup the page already exposed; no requests, page-specific parsing or URL guessing.
        try {
            var metas = document.querySelectorAll(
                'meta[property="og:video"],meta[property="og:video:url"],'+
                'meta[property="og:video:secure_url"],meta[property="og:audio"],'+
                'meta[name="twitter:player:stream"],link[rel="preload"][as="video"]'
            );
            for (var m=0; m<metas.length && html.length<maxCharacters; m++) append(metas[m]);
        } catch(e) {}
        // Response-backed extension-less media already crossed the sniffer bridge. Do not
        // serialise its lifetime-wide URL log here: on a SPA transition those URLs may belong
        // to the previous post, not the current DOM.
        try {
            var nodes = Array.prototype.slice.call(
                document.querySelectorAll('video,audio,source,a[href],[src]')
            );
            nodes.sort(function(a,b){
                var aP = (a.tagName==='VIDEO'||a.tagName==='AUDIO')?0:1;
                var bP = (b.tagName==='VIDEO'||b.tagName==='AUDIO')?0:1;
                return aP-bP;
            });
            for (var j=0; j<nodes.length && html.length<maxCharacters; j++) {
                var n=nodes[j];
                if(n.tagName==='A' && !mediaPath.test(n.getAttribute('href')||'')) continue;
                append(n);
            }
        } catch(e) {}
        return html+'</body></html>';
    })();
""".replace("__MEDIA_EXT__", MediaUrlRules.EXTENSION_ALTERNATION)
