package com.salvia.salviabrowxer.ui.utils

import com.salvia.salviabrowxer.core.model.MediaUrlRules

/**
 * What the opt-in ad blocker is allowed to block, as pure rules with no Android in them.
 *
 * The app is a downloader before it is an ad blocker, and the one thing an ad blocker must never do
 * here is break the media the user came for. So the rules are deliberately narrow:
 *
 *  - only the request's **host** decides — no path patterns, no regex, nothing that can accidentally
 *    match a video URL;
 *  - a host is matched **label by label** ([matchesDomain]): `example.com` matches `example.com` and
 *    `ads.example.com`, and never `notexample.com`;
 *  - a request whose URL names a **media, playlist or segment** file is never blocked, whatever its
 *    host (see [isMediaRequest]);
 *  - the loopback and private hosts, and the page's own host, are never blocked;
 *  - the list itself is a short, hand-written set of well-known ad and tracker hostnames. No
 *    third-party list is copied into the app, so there is no list licence to track and no
 *    hundred-thousand-entry file to explain.
 *
 * The blocker is **off until the user turns it on**, and a site the user has disabled stays
 * disabled — both live in [AdBlockState], written from settings.
 */
object AdBlockRules {

    /**
     * Well-known advertising and tracking hosts, grouped by the kind of traffic they carry.
     *
     * Curated rather than imported: a host earns a place here by being an ad or analytics endpoint no
     * page needs to function, and by being recognisable enough that a false positive can be
     * diagnosed. Hosts that a site needs for login, content or payments are deliberately absent —
     * `connect.facebook.net`, `gstatic.com`, `googleapis.com` and friends are not in the list.
     */
    val DEFAULT_BLOCKED_HOSTS: Set<String> = setOf(
        // Google's ad and measurement endpoints. Not "google.com": search and sign-in are the site.
        "doubleclick.net",
        "googleadservices.com",
        "googlesyndication.com",
        "googletagservices.com",
        "googletagmanager.com",
        "google-analytics.com",
        "adservice.google.com",
        "pagead2.googlesyndication.com",
        // Large ad exchanges and ad servers.
        "adnxs.com",
        "adsrvr.org",
        "amazon-adsystem.com",
        "casalemedia.com",
        "criteo.com",
        "criteo.net",
        "dotomi.com",
        "openx.net",
        "pubmatic.com",
        "rubiconproject.com",
        "smartadserver.com",
        "sovrn.com",
        "yieldmo.com",
        "33across.com",
        "adform.net",
        "bidswitch.net",
        "districtm.io",
        "mediageneral.com",
        "sharethrough.com",
        "teads.tv",
        "gumgum.com",
        "improvedigital.com",
        "inmobi.com",
        "ironsrc.com",
        "lifeStreet.com",
        "mopub.com",
        "smaato.net",
        "startappservice.com",
        "supersonicads.com",
        "unityads.unity3d.com",
        "vungle.com",
        "chartboost.com",
        "applovin.com",
        "adcolony.com",
        // Content recommendation widgets.
        "taboola.com",
        "outbrain.com",
        "revcontent.com",
        "mgid.com",
        "zergnet.com",
        "aniview.com",
        "spotxchange.com",
        "springserve.com",
        "tremorhub.com",
        // Measurement, analytics and attribution.
        "scorecardresearch.com",
        "quantserve.com",
        "imrworldwide.com",
        "moatads.com",
        "admaster.com.cn",
        "hotjar.com",
        "mixpanel.com",
        "amplitude.com",
        "segment.com",
        "segment.io",
        "branch.io",
        "adjust.com",
        "appsflyer.com",
        "newrelic.com",
        "bugsnag.com",
        "sentry.io"
    )

    /** Hosts that are never blocked, whatever the list says. */
    private val NEVER_BLOCKED_HOSTS: Set<String> = setOf("localhost", "127.0.0.1", "::1", "[::1]", "0.0.0.0")

    /**
     * The host of an absolute http(s) URL, lowercased and without its port or credentials.
     *
     * Null for anything else: a `data:`, `blob:`, `javascript:` or relative URL has no host to
     * match, and inventing one is how a blocker ends up blocking the page it is showing.
     */
    fun hostOf(url: String?): String? {
        val raw = url?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val scheme = raw.substringBefore("://", "")
        if (!scheme.equals("http", true) && !scheme.equals("https", true)) return null
        val afterScheme = raw.substringAfter("://")
        val authority = afterScheme.substringBefore('/').substringBefore('?').substringBefore('#')
        val hostWithPort = authority.substringAfterLast('@')
        if (hostWithPort.isEmpty()) return null
        val host = when {
            hostWithPort.startsWith("[") -> hostWithPort.substringBefore(']') + "]"
            ':' in hostWithPort -> hostWithPort.substringBefore(':')
            else -> hostWithPort
        }
        return host.lowercase().trimEnd('.').takeIf { it.isNotEmpty() }
    }

    /**
     * True when [host] is [domain] itself or a subdomain of it, compared one label at a time.
     *
     * `matchesDomain("ads.example.com", "example.com")` is true;
     * `matchesDomain("notexample.com", "example.com")` is false, because the match has to start at a
     * dot boundary. That single rule is what keeps a block list from swallowing every host that
     * happens to end with the blocked name.
     */
    fun matchesDomain(host: String, domain: String): Boolean {
        val target = domain.lowercase().trim().trimEnd('.')
        if (target.isEmpty()) return false
        return host == target || host.endsWith(".$target")
    }

    /** Loopback, private-range and link-local hosts: the app's own page and the device's own servers. */
    fun isUnblockableHost(host: String): Boolean {
        if (host in NEVER_BLOCKED_HOSTS) return true
        if (host.startsWith("192.168.") || host.startsWith("10.") || host.startsWith("169.254.")) return true
        val second = host.removePrefix("172.").substringBefore('.')
        if (host.startsWith("172.") && second.toIntOrNull()?.let { it in 16..31 } == true) return true
        return false
    }

    /**
     * True when the URL names a file a player would fetch: media, a playlist, or a media segment.
     *
     * These are never blocked. A stream that loses one segment stops playing, and "the downloader
     * broke the video" is the one failure this app cannot afford — an ad segment is a price worth
     * paying for never breaking the thing the user actually wants.
     */
    fun isMediaRequest(url: String): Boolean {
        val path = url.substringBefore('#').substringBefore('?')
        val extension = MediaUrlRules.pathExtension(path) ?: return false
        if (extension in MediaUrlRules.MEDIA_EXTENSIONS) return true
        if (extension in MediaUrlRules.SEGMENT_EXTENSIONS) return true
        return extension == MediaUrlRules.DASH_EXTENSION
    }
}
