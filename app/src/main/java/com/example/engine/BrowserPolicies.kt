package com.example.engine

import java.util.concurrent.ConcurrentHashMap

/**
 * Hardening for the JavaScript bridges. @JavascriptInterface methods are reachable from every
 * frame of every page, so each call is size-capped and rate-limited per tab and channel. A
 * hostile page or iframe can therefore not flood the media pipeline, the probe client, or the
 * DevTools console buffer. Downstream URL/MIME verification still applies to every payload.
 */
class BridgeGuard(
    private val maxCallsPerWindow: Int = 60,
    private val windowMs: Long = 1_000L,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private class Window(var startedAt: Long, var count: Int)

    private val windows = ConcurrentHashMap<String, Window>()

    /** Returns true when the call is within budget. */
    fun allow(tabId: String, channel: String): Boolean {
        val key = "$tabId|$channel"
        val now = clock()
        val window = windows.getOrPut(key) { Window(now, 0) }
        synchronized(window) {
            if (now - window.startedAt >= windowMs) {
                window.startedAt = now
                window.count = 0
            }
            window.count++
            return window.count <= maxCallsPerWindow
        }
    }

    fun forgetTab(tabId: String) {
        windows.keys.removeIf { it.startsWith("$tabId|") }
    }

    companion object {
        const val MAX_URL_CHARS = 8_192
        const val MAX_TITLE_CHARS = 512
        const val MAX_JSON_CHARS = 512 * 1024
        const val MAX_LOG_CHARS = 4_096

        fun clip(value: String?, max: Int): String {
            val v = value.orEmpty()
            return if (v.length > max) v.substring(0, max) else v
        }

        fun isAcceptableUrl(value: String?): Boolean {
            val v = value?.trim().orEmpty()
            return v.isNotEmpty() && v.length <= MAX_URL_CHARS
        }
    }
}

/**
 * HTTPS-First navigation policy for in-page navigations (links, redirects, form posts), not
 * just omnibox input. A host that fails over HTTPS (or redirects back to HTTP) is remembered
 * for the session and loaded over HTTP, exactly like Chrome's HTTPS-First mode fallback.
 */
class HttpsUpgradePolicy(private val clock: () -> Long = System::currentTimeMillis) {

    private val httpOnlyHosts = ConcurrentHashMap.newKeySet<String>()
    private val recentUpgrades = ConcurrentHashMap<String, Pair<String, Long>>()

    /** Returns the https:// URL to load instead of [url], or null to keep it unchanged. */
    fun upgradeFor(tabId: String, url: String): String? {
        if (!url.startsWith("http://", ignoreCase = true)) return null
        val host = hostOf(url) ?: return null
        if (!isUpgradeableHost(host) || host in httpOnlyHosts) return null
        val previous = recentUpgrades[tabId]
        if (previous != null && previous.first.equals(url, ignoreCase = true) &&
            clock() - previous.second < LOOP_WINDOW_MS
        ) {
            // The HTTPS origin redirected straight back to HTTP: stop upgrading this host.
            httpOnlyHosts.add(host)
            recentUpgrades.remove(tabId)
            return null
        }
        recentUpgrades[tabId] = url to clock()
        return "https://" + url.substring("http://".length)
    }

    /**
     * Called when a main-frame HTTPS load fails. Returns the original http:// URL to fall back
     * to when that load was an automatic upgrade, otherwise null.
     */
    fun fallbackFor(tabId: String, failedUrl: String): String? {
        val upgrade = recentUpgrades[tabId] ?: return null
        val expected = "https://" + upgrade.first.substring("http://".length)
        if (!sameHost(expected, failedUrl)) return null
        hostOf(upgrade.first)?.let { httpOnlyHosts.add(it) }
        recentUpgrades.remove(tabId)
        return upgrade.first
    }

    /** True when the HTTPS load of [url] in [tabId] was produced by an automatic upgrade. */
    fun isAutoUpgraded(tabId: String, url: String): Boolean {
        val upgrade = recentUpgrades[tabId] ?: return false
        return sameHost("https://" + upgrade.first.substring("http://".length), url)
    }

    fun clear() {
        httpOnlyHosts.clear()
        recentUpgrades.clear()
    }

    private fun sameHost(a: String, b: String): Boolean {
        val ha = hostOf(a) ?: return false
        return ha == hostOf(b)
    }

    companion object {
        private const val LOOP_WINDOW_MS = 10_000L

        fun hostOf(url: String): String? =
            runCatching { java.net.URI(url.trim()).host?.lowercase() }.getOrNull()?.takeIf { it.isNotBlank() }

        /** Never upgrade IP literals, localhost, or LAN-only names (routers, NAS, dev servers). */
        fun isUpgradeableHost(host: String): Boolean {
            val h = host.trim('[', ']').lowercase()
            if (h == "localhost" || !h.contains('.')) return false
            if (h.endsWith(".local") || h.endsWith(".lan") || h.endsWith(".home") ||
                h.endsWith(".internal") || h.endsWith(".localhost") || h.endsWith(".test")
            ) {
                return false
            }
            if (Regex("^\\d{1,3}(\\.\\d{1,3}){3}$").matches(h)) return false
            if (h.contains(':')) return false // IPv6 literal
            return true
        }
    }
}

/** Small page scripts used by browser features (privacy signals, reader mode). */
object BrowserScripts {

    /**
     * Exposes the user's privacy preference to page scripts: navigator.globalPrivacyControl
     * (GPC, legally binding in several jurisdictions) and navigator.doNotTrack.
     */
    fun privacySignalsScript(): String = """
        (function() {
            try {
                if (window.__salviaPrivacySignals) return;
                window.__salviaPrivacySignals = true;
                Object.defineProperty(Navigator.prototype, 'globalPrivacyControl', { get: function() { return true; }, configurable: true });
                Object.defineProperty(Navigator.prototype, 'doNotTrack', { get: function() { return '1'; }, configurable: true });
            } catch (e) {}
        })();
    """.trimIndent()

    /** HTTP request headers that accompany browser-initiated navigations when enabled. */
    fun privacyHeaders(enabled: Boolean): Map<String, String> =
        if (enabled) mapOf("DNT" to "1", "Sec-GPC" to "1") else emptyMap()

    /**
     * Reader mode: scores block elements by paragraph text density (a compact Readability-style
     * heuristic), then renders the winning article in a clean, theme-aware, RTL-aware layout.
     * Calling it again restores the original page.
     */
    fun readerModeScript(dark: Boolean): String {
        val bg = if (dark) "#0B1020" else "#FBFAF7"
        val fg = if (dark) "#E5E7EB" else "#1F2937"
        val accent = if (dark) "#67E8F9" else "#0E7490"
        return """
        (function() {
            try {
                if (window.__salviaReaderOriginal) {
                    document.documentElement.innerHTML = window.__salviaReaderOriginal;
                    window.__salviaReaderOriginal = null;
                    return 'restored';
                }
                var candidates = document.querySelectorAll('article, main, [role=main], section, div');
                var best = null, bestScore = 0;
                for (var i = 0; i < candidates.length && i < 4000; i++) {
                    var el = candidates[i];
                    var ps = el.querySelectorAll(':scope > p, :scope > div > p');
                    if (ps.length < 2) continue;
                    var text = 0;
                    for (var j = 0; j < ps.length; j++) text += (ps[j].innerText || '').trim().length;
                    var cls = ((el.className || '') + ' ' + (el.id || '')).toString().toLowerCase();
                    if (/comment|footer|sidebar|nav|menu|promo|related|share|ad-|advert/.test(cls)) text *= 0.3;
                    if (el.tagName === 'ARTICLE') text *= 1.4;
                    if (text > bestScore) { bestScore = text; best = el; }
                }
                if (!best || bestScore < 400) return 'unavailable';
                var title = (document.querySelector('h1') || {}).innerText || document.title || '';
                var clone = best.cloneNode(true);
                clone.querySelectorAll('script, style, iframe, form, nav, aside, button, input, noscript, svg, [aria-hidden=true]').forEach(function(n) { n.remove(); });
                clone.querySelectorAll('*').forEach(function(n) {
                    n.removeAttribute('style');
                    n.removeAttribute('class');
                    Array.prototype.slice.call(n.attributes).forEach(function(a) { if (/^on/i.test(a.name)) n.removeAttribute(a.name); });
                });
                var dir = /[\u0590-\u08FF]/.test(title + (clone.innerText || '').slice(0, 400)) ? 'rtl' : 'ltr';
                window.__salviaReaderOriginal = document.documentElement.innerHTML;
                var safeTitle = title.replace(/[&<>]/g, function(c) { return {'&':'&amp;','<':'&lt;','>':'&gt;'}[c]; });
                document.documentElement.innerHTML =
                    '<head><meta name="viewport" content="width=device-width, initial-scale=1"><title>' + safeTitle + '</title>' +
                    '<style>body{margin:0;background:$bg;color:$fg;font:19px/1.7 Georgia,"Noto Naskh Arabic",serif;}' +
                    'main{max-width:720px;margin:0 auto;padding:28px 20px 80px;}h1{font:700 28px/1.3 system-ui,sans-serif;margin:0 0 20px;}' +
                    'img,video,figure{max-width:100%;height:auto;border-radius:10px;}a{color:$accent;}' +
                    'pre,code{white-space:pre-wrap;font-size:15px;}blockquote{border-inline-start:3px solid $accent;margin:0;padding-inline-start:14px;opacity:.9;}</style></head>' +
                    '<body dir="' + dir + '"><main><h1>' + safeTitle + '</h1>' + clone.innerHTML + '</main></body>';
                window.scrollTo(0, 0);
                return 'reader';
            } catch (e) { return 'unavailable'; }
        })();
        """.trimIndent()
    }
}
