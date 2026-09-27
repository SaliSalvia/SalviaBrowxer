package com.salvia.salviabrowxer.feature.browser

import android.annotation.SuppressLint
import android.content.Context
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import com.salvia.salviabrowxer.core.model.MediaCandidate
import com.salvia.salviabrowxer.ui.bridge.BlobDownloadBridge
import com.salvia.salviabrowxer.ui.utils.WebViewClientWrapper
import com.salvia.salviabrowxer.ui.utils.WebViewSetup
import java.io.File

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
        fun onProgress(tabId: String, progress: Int)
        fun onNavigationState(tabId: String, canGoBack: Boolean, canGoForward: Boolean)
        fun onPageHtml(tabId: String, pageUrl: String, html: String)
        fun onMediaDetected(candidate: MediaCandidate)
        fun onBlobCaptured(pageUrl: String, blobUrl: String, file: File, mimeType: String)
        fun onTabHibernated(tabId: String, url: String, title: String)
        fun onFindResult(tabId: String, matches: Int, activeMatch: Int)
        /** Hand a non-web scheme to the system. Return true when it was handled. */
        fun onExternalScheme(url: String): Boolean
    }

    private val live = mutableMapOf<String, WebView>()
    private val usage = ArrayDeque<String>()

    private var javaScriptEnabled = true
    private var desktopMode = false
    private var cookiesEnabled = true
    private var defaultUserAgent: String? = null

    private val blobBridge = BlobDownloadBridge(
        onBlobCaptured = { pageUrl, blobUrl, file, mime -> callbacks.onBlobCaptured(pageUrl, blobUrl, file, mime) },
        // Blob staging gets its own cache subdirectory so FileProvider only exposes that folder.
        cacheDirProvider = { File(context.cacheDir, "blob") }
    )

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
        val url = view.url.orEmpty()
        val title = view.title.orEmpty()
        destroyView(view)
        callbacks.onTabHibernated(tabId, url, title)
    }

    fun destroy(tabId: String) {
        val view = live.remove(tabId) ?: return
        usage.remove(tabId)
        destroyView(view)
    }

    fun destroyAll() {
        live.keys.toList().forEach { destroy(it) }
        usage.clear()
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
        view.addJavascriptInterface(blobBridge, BRIDGE_NAME)
        view.webViewClient = WebViewClientWrapper(
            onPageStartedHook = { _, url, _ -> url?.takeIf { it.isNotBlank() }?.let { callbacks.onPageStarted(tabId, it) } },
            onPageFinishedHook = { web, url ->
                val pageUrl = web.url ?: url.orEmpty()
                callbacks.onPageFinished(tabId, pageUrl, web.title)
                callbacks.onNavigationState(tabId, web.canGoBack(), web.canGoForward())
                web.evaluateJavascript(NETWORK_SNIFFER_JS, null)
                web.evaluateJavascript(MEDIA_DETECTION_JS) { rawHtml ->
                    if (pageUrl.isNotBlank()) decodeJavascriptString(rawHtml)?.let { html -> callbacks.onPageHtml(tabId, pageUrl, html) }
                }
            },
            onMediaDetectedHook = { candidate -> callbacks.onMediaDetected(candidate) },
            onExternalSchemeHook = { url -> callbacks.onExternalScheme(url) }
        )
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

    private fun destroyView(view: WebView) {
        (view.parent as? ViewGroup)?.removeView(view)
        runCatching { view.stopLoading() }
        runCatching { view.removeJavascriptInterface(BRIDGE_NAME) }
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

/** Hooks XHR, fetch, video.src and blob URLs so media the page already fetched is visible. */
private const val NETWORK_SNIFFER_JS = """
    (function(){
        if(window.__salviaSnifferInstalled) return;
        window.__salviaSnifferInstalled = true;
        window.__salviaFound = window.__salviaFound || [];
        function isMediaUrl(u){
            if(!u) return false;
            if(u.indexOf('blob:')===0) return true;
            return /\.(mp4|webm|mov|avi|3gp|m4v|mkv|flv|m3u8|ts|mp3|m4a|aac|wav|flac|ogg|wma)(\?|#|$)/i.test(u) ||
                   /mime=video|mime=audio|video\/|audio\//i.test(u);
        }
        function pushUrl(u, src){
            if(!u || !isMediaUrl(u)) return;
            if(window.__salviaFound.indexOf(u)!==-1) return;
            window.__salviaFound.push(u);
            if(window.__salviaFound.length>40) window.__salviaFound.shift();
            if(u.indexOf('blob:')===0 && window.SalviaBridge){
                try{ window.SalviaBridge.onBlobUrlFound(location.href, u); }catch(e){}
            }
        }
        try{
            var origDescriptor = Object.getOwnPropertyDescriptor(HTMLMediaElement.prototype,'src');
            Object.defineProperty(HTMLMediaElement.prototype,'src',{
                get: function(){ return origDescriptor.get.call(this); },
                set: function(v){ pushUrl(v,'media.src'); return origDescriptor.set.call(this,v); },
                configurable:true
            });
        }catch(e){}
        try{
            var openOrig = XMLHttpRequest.prototype.open;
            XMLHttpRequest.prototype.open = function(method,url){
                this.__salviaUrl = url;
                return openOrig.apply(this, arguments);
            };
            var sendOrig = XMLHttpRequest.prototype.send;
            XMLHttpRequest.prototype.send = function(){
                if(this.__salviaUrl) pushUrl(this.__salviaUrl,'xhr');
                return sendOrig.apply(this, arguments);
            };
        }catch(e){}
        try{
            var fetchOrig = window.fetch;
            window.fetch = function(input,init){
                var u = typeof input==='string'? input : (input && input.url);
                pushUrl(u,'fetch');
                return fetchOrig.apply(this, arguments);
            };
        }catch(e){}
        try{
            var obs = new MutationObserver(function(mutations){
                mutations.forEach(function(m){
                    m.addedNodes.forEach(function(n){
                        if(!n || !n.tagName) return;
                        var t=n.tagName.toLowerCase();
                        if(t==='video'||t==='audio'){
                            var s=n.getAttribute('src'); if(s) pushUrl(s,'mut');
                            n.querySelectorAll&&n.querySelectorAll('source').forEach(function(s2){ var u=s2.getAttribute('src'); if(u) pushUrl(u,'mut-src');});
                        } else if(t==='source'){
                            var su=n.getAttribute('src'); if(su) pushUrl(su,'mut-src');
                        } else if(t==='a'){
                            var hr=n.getAttribute('href'); if(hr) pushUrl(hr,'mut-a');
                        }
                    });
                });
            });
            obs.observe(document.documentElement,{childList:true,subtree:true});
        }catch(e){}
        setInterval(function(){
            try{
                document.querySelectorAll('video,audio').forEach(function(v){
                    var s=v.currentSrc||v.src; if(s) pushUrl(s,'poll');
                    if(v.src && v.src.indexOf('blob:')===0) pushUrl(v.src,'blob');
                });
            }catch(e){}
        },1500);
    })();
"""

/** Serialises the page's media-bearing markup for [com.salvia.salviabrowxer.media.detector.DomMediaDetector]. */
private const val MEDIA_DETECTION_JS = """
    (function () {
        if(window.__salviaFound && window.__salviaFound.length){
            var out='<html><body>';
            for(var i=0;i<window.__salviaFound.length;i++){
                var u=window.__salviaFound[i];
                var esc=u.replace(/&/g,'&amp;').replace(/"/g,'&quot;');
                out+='<a href="'+esc+'"></a>';
            }
            try{
                var nodes=document.querySelectorAll('video,audio,source,a[href]');
                var mediaPath=/\.(mp4|webm|mov|avi|3gp|m4v|mkv|flv|m3u8|ts|mp3|m4a|aac|wav|flac|ogg|wma)(\?|#|$)/i;
                for(var j=0;j<nodes.length&&out.length<58000;j++){
                    var n=nodes[j];
                    if(n.tagName==='A' && !mediaPath.test(n.getAttribute('href')||'')) continue;
                    var h=n.outerHTML;
                    if(out.length+h.length>62000) break;
                    out+=h;
                }
            }catch(e){}
            return out+'</body></html>';
        }
        var maxCharacters = 62000;
        var mediaPath = /\.(mp4|webm|mov|avi|3gp|m4v|mkv|flv|m3u8|ts|mp3|m4a|aac|wav|flac|ogg|wma)(\?|#|$)/i;
        var nodes = Array.prototype.slice.call(
            document.querySelectorAll('video, audio, source, a[href], [src]')
        );
        var html = '<html><body>';
        nodes.sort(function(a,b){
            var aP = (a.tagName==='VIDEO'||a.tagName==='AUDIO')?0:1;
            var bP = (b.tagName==='VIDEO'||b.tagName==='AUDIO')?0:1;
            return aP-bP;
        });
        for (var i = 0; i < nodes.length && html.length < maxCharacters; i++) {
            var node = nodes[i];
            if(node.tagName==='A' && !mediaPath.test(node.getAttribute('href')||'')) continue;
            var outer = node.outerHTML || '';
            if(!outer) continue;
            if(outer.length>8000) continue;
            if (html.length + outer.length > maxCharacters) break;
            html += outer;
        }
        return html + '</body></html>';
    })();
"""
