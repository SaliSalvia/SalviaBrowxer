package com.salvia.salviabrowxer.media.downloader

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/**
 * Feeds OkHttp the cookies the page's own WebView already holds.
 *
 * Without this, a transfer went out with no cookies at all while the page that exposed the media had
 * a full session — so any site that guards its media behind a signed-in session answered 403 to a URL
 * it had just served to that very WebView. Reading the shared cookie store is what every browser does
 * when the user saves a file from a page they are looking at; it is not an extractor, it performs no
 * request of its own, and it reaches nothing the page had not already fetched.
 *
 * **Read-only, deliberately.** [saveFromResponse] does nothing: the downloader never invents a
 * cookie, never refreshes one and never writes back into the WebView's jar. A download must not be
 * able to change the session the user is browsing with.
 *
 * The provider is injected instead of the Android type, so the whole module stays testable on the JVM
 * and `:media:downloader` keeps no dependency on `android.webkit`.
 */
class SessionCookieJar(private val cookieHeaderFor: (String) -> String?) : CookieJar {

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) = Unit

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val header = cookieHeaderFor(url.toString()) ?: return emptyList()
        // `CookieManager.getCookie` answers with exactly what it would send: `a=1; b=2`.
        return header.split(';').mapNotNull { part ->
            val pair = part.trim()
            if (pair.isEmpty()) null else Cookie.parse(url, pair)
        }
    }
}
