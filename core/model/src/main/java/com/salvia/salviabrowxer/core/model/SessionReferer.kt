package com.salvia.salviabrowxer.core.model

/**
 * Which `Referer` a request should carry when the browser fetches media on a page's behalf.
 *
 * Media servers routinely refuse a request that arrives without one — the classic hotlink guard — so
 * a download that looked like a bare client failed on exactly the URLs the page had just played.
 * Sending the page is what makes the transfer look like the page's own request, and it is what every
 * browser does when the user saves a file from a page.
 *
 * The policy is deliberately the browser's default, `strict-origin-when-cross-origin`, rather than
 * "always send the whole page URL":
 *
 *  - same site (the page and the media share a registrable domain): the **full URL**, so a server that
 *    checks the referer path keeps working;
 *  - cross site: only the **origin** (`https://site.example/`), which is what a hotlink check needs and
 *    all a third party is entitled to learn about the page the user was reading;
 *  - an insecure target (`https://` page, `http://` media): **nothing**, because a browser strips the
 *    referrer on a downgrade;
 *  - no usable page (blank, `data:`, `blob:`, `file:`): **nothing**;
 *  - the request would be its own referrer (a pasted link, whose only "page" is the file itself):
 *    **nothing**, because a request that referred itself is a shape no browser ever sends.
 *
 * Pure and side-effect free, so the whole rule set is unit-tested off-device.
 */
object SessionReferer {

    /** The value for the `Referer` header, or null when the request must not carry one. */
    fun headerFor(pageUrl: String?, targetUrl: String): String? {
        val page = pageUrl?.trim()?.takeIf { it.isNotBlank() } ?: return null
        if (!page.startsWith("http://", true) && !page.startsWith("https://", true)) return null
        // A pasted direct link is its own "page": sending it would make the request refer to itself.
        if (page == targetUrl) return null

        val pageScheme = page.substringBefore("://").lowercase()
        val targetScheme = targetUrl.substringBefore("://", "").lowercase()
        if (pageScheme == "https" && targetScheme == "http") return null

        return if (sameSite(page, targetUrl)) page else originOf(page)
    }

    /**
     * The value for the `Origin` header, or null when the request must not carry one.
     *
     * A page's own `fetch()` of a cross-origin manifest carries `Origin` — the page's origin and
     * nothing else — and a hotlink check that compares it is the last guard some CDNs keep, so a
     * re-fetch without it arrives as a client the page never was. Same-origin requests carry no
     * `Origin` at all, and this returns exactly that there: a header the browser would never have
     * sent is how a download announces itself as synthetic.
     */
    fun originHeaderFor(pageUrl: String?, targetUrl: String): String? {
        val page = pageUrl?.trim()?.takeIf { it.isNotBlank() } ?: return null
        if (!page.startsWith("http://", true) && !page.startsWith("https://", true)) return null
        if (sameSite(page, targetUrl)) return null
        return originOf(page).trimEnd('/')
    }

    /** `scheme://host[:port]/`, the shape a browser sends cross-origin. */
    private fun originOf(url: String): String {
        val schemeEnd = url.indexOf("://")
        if (schemeEnd <= 0) return url
        val scheme = url.substring(0, schemeEnd)
        val rest = url.substring(schemeEnd + 3)
        val authority = rest.substringBefore('/').substringBefore('?').substringBefore('#')
        return "$scheme://$authority/"
    }

    /**
     * Whether the two URLs belong to the same site.
     *
     * Equal hosts and sibling subdomains of the same registrable domain count as one site — a browser
     * sends the full page to its own site, and that is what a hotlink check on `cdn.example.com`
     * expects to see from `www.example.com`. Anything else is cross-site: a different registrable
     * domain, a different IP literal, an unparsable authority.
     *
     * Deliberately conservative where the public-suffix list would have to decide: a domain whose
     * second-level suffix is not in [SECOND_LEVEL_SUFFIXES] is compared without one, so the worst case
     * is that two unrelated sites are treated as *different* (only the origin is sent) rather than as
     * the same. A wrong "same site" would put the full page URL into a third party's logs.
     */
    private fun sameSite(page: String, targetUrl: String): Boolean {
        val pageHost = hostOf(page) ?: return false
        val targetHost = hostOf(targetUrl) ?: return false
        if (pageHost == targetHost) return true
        if (isIpLiteral(pageHost) || isIpLiteral(targetHost)) return false
        return siteOf(pageHost) == siteOf(targetHost)
    }

    private fun hostOf(url: String): String? {
        val schemeEnd = url.indexOf("://")
        if (schemeEnd <= 0) return null
        val authority = url.substring(schemeEnd + 3).substringBefore('/').substringBefore('?').substringBefore('#')
        if (authority.isEmpty()) return null
        val host = authority.substringAfterLast('@').substringBefore(':').lowercase()
        return host.takeIf { it.isNotBlank() }
    }

    private fun isIpLiteral(host: String): Boolean =
        host.all { it.isDigit() || it == '.' } || host.contains(':')

    /**
     * The registrable-domain stand-in for a host: its last two labels, or its last three when the
     * second-to-last is a known second-level suffix (`bbc.co.uk` is `bbc.co.uk`, not `co.uk`).
     */
    private fun siteOf(host: String): String {
        val labels = host.trimEnd('.').split('.')
        if (labels.size <= 2) return host
        val lastTwo = labels.takeLast(2).joinToString(".")
        val take = if (lastTwo in SECOND_LEVEL_SUFFIXES) 3 else 2
        return labels.takeLast(take).joinToString(".")
    }

    /**
     * The most common `x.y` public suffixes, as far as a comparison of hosts is concerned. Without
     * them `bbc.co.uk` and `itv.co.uk` would look like two names on one site and each would be sent
     * the other's full page URL. Incomplete on purpose — see [sameSite] for which way that errs.
     */
    private val SECOND_LEVEL_SUFFIXES = setOf(
        "co.uk", "org.uk", "ac.uk", "gov.uk", "me.uk", "net.uk", "sch.uk",
        "com.au", "net.au", "org.au", "edu.au", "gov.au",
        "co.jp", "or.jp", "ne.jp", "ac.jp", "go.jp",
        "com.br", "com.cn", "com.hk", "com.tw", "com.sg", "com.my", "com.tr", "com.mx",
        "com.ar", "com.co", "com.pe", "com.ve", "com.uy", "com.ec", "com.bo", "com.do",
        "co.kr", "co.in", "co.nz", "co.za", "co.il", "co.id", "co.th", "co.ke", "co.ug",
        "or.kr", "ac.in", "gov.in", "nic.in", "org.in", "net.in", "edu.in", "res.in",
        "com.pk", "com.ph", "com.vn", "com.eg", "com.sa", "com.ua", "com.pl", "com.ru",
        "com.gr", "com.pt", "com.es", "com.it", "com.de", "com.fr", "com.nl"
    )
}
