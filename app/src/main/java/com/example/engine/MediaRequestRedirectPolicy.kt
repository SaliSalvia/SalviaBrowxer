package com.example.engine

import okhttp3.HttpUrl
import okhttp3.Request
import okhttp3.Response

/**
 * Applies the browser's conservative redirect boundary to media requests.
 *
 * Cookies are resolved separately for the destination profile/origin. On a cross-origin
 * transition, credentials and helper-replayable headers are removed; destination context
 * can be added afresh only after an independent origin match.
 */
object MediaRequestRedirectPolicy {
    fun nextRequest(
        response: Response,
        target: HttpUrl,
        resolveCookiePerHop: Boolean = false,
        resetRefererOnCrossOrigin: Boolean = true,
    ): Request? {
        val current = response.request.url
        if (target.scheme != "http" && target.scheme != "https") return null
        if (current.isHttps && !target.isHttps) return null

        val crossOrigin = current.scheme != target.scheme ||
            current.host != target.host ||
            current.port != target.port
        val next = response.request.newBuilder().url(target)
        if (crossOrigin || resolveCookiePerHop) next.removeHeader("Cookie")

        if (crossOrigin) {
            next.removeHeader("Authorization")
            next.removeHeader("Proxy-Authorization")
            MediaRequestHeaderPolicy.replayableHeaderNames.forEach(next::removeHeader)
            if (resetRefererOnCrossOrigin) {
                val safeReferer = current.newBuilder()
                    .encodedPath("/")
                    .query(null)
                    .fragment(null)
                    .build()
                next.header("Referer", safeReferer.toString())
            }
        }
        return next.build()
    }
}
