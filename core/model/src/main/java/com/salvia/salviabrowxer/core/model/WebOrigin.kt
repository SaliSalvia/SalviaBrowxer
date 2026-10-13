package com.salvia.salviabrowxer.core.model

/**
 * The `scheme://host[:port]` of an http(s) URL — "the same origin" as the app's own gates mean it.
 *
 * This exists because two different questions are asked about a URL in the same breath, and confusing
 * them is how a sniffer leaks one page's media into another's tray:
 *
 *  - *which site is this?* ([of]) — used to decide whether a sighting, a cookie or a record request
 *    belongs to the page the user is looking at;
 *  - *what should the `Referer` say?* (`SessionReferer`) — which is a header format, not an identity.
 *
 * Pure and side-effect free: no URI parser, no locale, no exceptions — a URL that cannot be
 * understood answers `null`, and every caller treats that as "not the same origin", never as a match.
 */
object WebOrigin {

    /** The origin of [url], or null when it is not an http(s) URL with a host. */
    fun of(url: String?): String? {
        val raw = url?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val schemeEnd = raw.indexOf("://")
        if (schemeEnd <= 0) return null
        val scheme = raw.substring(0, schemeEnd).lowercase()
        if (scheme != "http" && scheme != "https") return null
        val authority = raw.substring(schemeEnd + 3)
            .substringBefore('/')
            .substringBefore('?')
            .substringBefore('#')
        val hostPort = authority.substringAfterLast('@').lowercase().trimEnd('.')
        if (hostPort.isBlank()) return null
        return "$scheme://$hostPort"
    }

    /** Whether two URLs come from the same origin. Null and unparsable URLs are never a match. */
    fun same(first: String?, second: String?): Boolean {
        val left = of(first) ?: return false
        return left == of(second)
    }

    /**
     * Whether [candidate] may speak for the page at [pageUrl].
     *
     * Same origin, or the frame is not cross-origin at all (an absent candidate origin is a frame that
     * did not report one, which the app never treats as a different site — a browser's own same-origin
     * check would still see a same-origin frame).
     */
    fun matchesPage(pageUrl: String?, candidate: String?): Boolean {
        val page = of(pageUrl) ?: return true
        val other = of(candidate) ?: return true
        return page == other
    }
}
