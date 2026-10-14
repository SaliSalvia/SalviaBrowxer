package com.salvia.salviabrowxer.ui.utils

import java.util.concurrent.atomic.AtomicInteger

/**
 * The settings the blocker reads, as a process-wide value.
 *
 * A `WebViewClient` is built by the tab store, which has no settings scope of its own, and the
 * decision has to be made on the WebView's own thread. So the three facts the decision needs — is it
 * on, which sites are exempt, how many requests it has blocked — live here, are written by one
 * collector, and are read without touching a `Flow`.
 */
object AdBlockState {

    /** Off until the user turns it on; the app never blocks requests unasked. */
    @Volatile
    var isEnabled: Boolean = false

    /** Sites the user exempted, matched the same label-by-label way the block list is. */
    @Volatile
    var disabledSites: Set<String> = emptySet()

    private val blockedCounter = AtomicInteger(0)

    /** How many requests this run has answered with nothing. Shown next to the settings row. */
    val blockedThisRun: Int get() = blockedCounter.get()

    /** Called once per blocked request; the counter is what makes the feature debuggable. */
    fun recordBlocked() {
        blockedCounter.incrementAndGet()
        pendingFlush.incrementAndGet()
    }

    /** Increments waiting to be written to the persisted total. */
    val pendingFlush = AtomicInteger(0)

    /** Test seam: the singleton would otherwise leak counters between tests. */
    fun resetForTest() {
        isEnabled = false
        disabledSites = emptySet()
        blockedCounter.set(0)
        pendingFlush.set(0)
    }
}

/**
 * The opt-in ad blocker's decision, with no Android in it.
 *
 * One question, one answer: [shouldBlock] takes the request URL and the page it belongs to and says
 * whether the WebView should be handed an empty response instead of the network's. Everything it
 * needs to know about *what* is being blocked is in [AdBlockRules]; this class only sequences the
 * vetoes, and the order is the point:
 *
 *  1. off → never block (the default, and the only state the app ships in);
 *  2. not an http(s) request or no host → not ours to answer;
 *  3. media, playlist or segment → never block, because the download is the product;
 *  4. loopback or private address → never block, that is the device itself;
 *  5. the page's own host and its parent domains → never block, the site must load;
 *  6. the site is exempted by the user → never block;
 *  7. the host, or a subdomain of it, is on the list → block.
 */
class AdBlocker(
    private val blockList: Set<String> = AdBlockRules.DEFAULT_BLOCKED_HOSTS,
    private val state: AdBlockState = AdBlockState
) {

    fun shouldBlock(requestUrl: String?, pageUrl: String?): Boolean {
        if (!state.isEnabled) return false
        val host = AdBlockRules.hostOf(requestUrl) ?: return false
        if (AdBlockRules.isMediaRequest(requestUrl!!)) return false
        if (AdBlockRules.isUnblockableHost(host)) return false

        // The page's own host, and every parent domain of it: a site that cannot load its own origin
        // is a broken browser, and the same goes for the apex of the host the user is on.
        val pageHost = AdBlockRules.hostOf(pageUrl)
        if (pageHost != null && (host == pageHost || AdBlockRules.matchesDomain(pageHost, host))) return false

        // "Off for this site" means the whole site, not one subdomain of it.
        if (pageHost != null && state.disabledSites.any { AdBlockRules.matchesDomain(pageHost, it) }) return false

        val blocked = blockList.any { AdBlockRules.matchesDomain(host, it) }
        if (blocked) state.recordBlocked()
        return blocked
    }
}
