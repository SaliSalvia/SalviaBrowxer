package com.salvia.salviabrowxer.core.session

import android.webkit.CookieManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * The WebView's own cookie store, read for requests the browser makes on a page's behalf.
 *
 * This is the one place that touches `android.webkit.CookieManager` for downloads, so the download
 * module only ever sees a `(String) -> String?`. Everything the user is signed into in the browser is
 * therefore also available to a download started from that page, which is the difference between a
 * download that completes and an HTTP 403 on a URL the page was already playing.
 *
 * Always read-only: nothing here writes, refreshes or deletes a cookie.
 */
object WebViewCookies {

    /**
     * `flush()` blocks its caller and may perform I/O, so it never runs on the thread that asked for
     * it. The scope is the object's own: flushing outlives any one screen, and a flush that is
     * cancelled halfway is a cookie the next resume does not have.
     */
    private val flushScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** The `Cookie` header the WebView would send to [url], or null when it has no cookie for it. */
    fun headerFor(url: String): String? = runCatching {
        CookieManager.getInstance().getCookie(url)
    }.getOrNull()?.takeIf { it.isNotBlank() }

    /**
     * Touches the cookie store once, on the main thread, at startup.
     *
     * The store is a singleton whose first access initialises the WebView provider, and a download —
     * which runs on OkHttp's threads — must never be the first caller. Warming it up here keeps a
     * background read from racing that initialisation; it reads nothing and changes nothing.
     */
    fun warmUp() {
        runCatching { CookieManager.getInstance() }
    }

    /**
     * Writes the session to disk, so it is still there for a transfer that outlives this process.
     *
     * A queued download survives a restart; its cookies would not, because the WebView keeps them in
     * memory until something flushes them. Calling this when the app goes to the background is what
     * keeps a resumed transfer signed in — a download resumed into a 403 is worse than a slow one.
     * The call is cheap for the service to accept and never reads or changes a cookie.
     */
    fun flush() {
        flushScope.launch { runCatching { CookieManager.getInstance().flush() } }
    }
}
