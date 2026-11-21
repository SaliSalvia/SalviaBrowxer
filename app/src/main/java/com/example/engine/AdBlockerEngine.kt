package com.example.engine

import android.net.Uri
import android.webkit.WebResourceResponse
import com.example.data.local.AdBlockRuleEntity
import org.json.JSONObject
import java.io.ByteArrayInputStream

enum class BlockCategory {
    NONE,
    AD,
    TRACKER,
    CRYPTO_MINER,
    POPUP_REDIRECT,
    CUSTOM_RULE,
}

data class BlockDecision(
    val isBlocked: Boolean,
    val category: BlockCategory = BlockCategory.NONE,
    val matchedRule: String? = null,
)

/**
 * Aloha-style Multi-Layer AdBlock, Anti-Popup, Anti-CryptoMiner & EasyList Filter Engine:
 * - Supports AdBlock Plus / EasyList syntax (`||domain^`, `@@||domain^`, `##selector`, `*` wildcards)
 * - Supports per-site Shield Pause (whitelisting the active host cleanly)
 * - Blocks ad networks, popunder networks, analytics trackers, and WebAssembly/JS cryptominers
 * - Strictly preserves Google/Gmail/OAuth/SSO & CAPTCHA hosts so sign-in works everywhere
 */
object AdBlockerEngine {

    private val authWhitelistSuffixes = setOf(
        "accounts.google.com",
        "accounts.youtube.com",
        "mail.google.com",
        "inbox.google.com",
        "apis.google.com",
        "oauth2.googleapis.com",
        "people-pa.googleapis.com",
        "ogads-pa.clients6.google.com",
        "contacts.google.com",
        "ssl.gstatic.com",
        "fonts.gstatic.com",
        "fonts.googleapis.com",
        "www.gstatic.com",
        "lh3.googleusercontent.com",
        "play.google.com",
        "appleid.apple.com",
        "login.microsoftonline.com",
        "login.live.com",
        "challenges.cloudflare.com",
        "recaptcha.net",
        "hcaptcha.com",
    )

    private val adDomains = setOf(
        "doubleclick.net",
        "googlesyndication.com",
        "googleadservices.com",
        "googletagservices.com",
        "imasdk.googleapis.com",
        "adservice.google.com",
        "adnxs.com",
        "ads.yahoo.com",
        "advertising.com",
        "amazon-adsystem.com",
        "taboola.com",
        "outbrain.com",
        "criteo.com",
        "criteo.net",
        "pubmatic.com",
        "rubiconproject.com",
        "openx.net",
        "casalemedia.com",
        "moatads.com",
        "adsafeprotected.com",
        "adcolony.com",
        "unityads.unity3d.com",
        "chartboost.com",
        "popads.net",
        "popcash.net",
        "propellerads.com",
        "onclickalgo.com",
        "juicyads.com",
        "exoclick.com",
        "trafficjunky.net",
        "adsterra.com",
        "profitabledisplaynetwork.com",
        "hilltopads.net",
        "zedo.com",
        "revcontent.com",
        "mgid.com",
        "bidswitch.net",
        "mediavine.com",
        "adthrive.com",
        "smartadserver.com",
        "media.net",
        "yieldmo.com",
        "triplelift.com",
        "teads.tv",
        "spotxchange.com",
        "springserve.com",
        "yektanet.com",
        "tapsell.ir",
        "sanjagh.com",
        "sabavision.com",
        "mediaad.org",
        "anetwork.ir",
        "clickyab.com",
        "najva.com",
    )

    private val trackerDomains = setOf(
        "google-analytics.com",
        "googletagmanager.com",
        "scorecardresearch.com",
        "quantserve.com",
        "hotjar.com",
        "mixpanel.com",
        "segment.io",
        "segment.com",
        "amplitude.com",
        "branch.io",
        "adjust.com",
        "appsflyer.com",
        "mouseflow.com",
        "fullstory.com",
        "clarity.ms",
        "newrelic.com",
        "nr-data.net",
        "omtrdc.net",
        "demdex.net",
        "bluekai.com",
        "krxd.net",
        "exelator.com",
        "rlcdn.com",
        "agkn.com",
        "tapad.com",
        "addthis.com",
        "sharethis.com",
        "facebook.net",
        "pixel.facebook.com",
        "analytics.twitter.com",
        "ads-twitter.com",
        "analytics.tiktok.com",
        "bat.bing.com",
        "mc.yandex.ru",
    )

    private val cryptoMinerDomains = setOf(
        "coinhive.com",
        "coin-hive.com",
        "cryptoloot.pro",
        "crypto-loot.com",
        "jsecoin.com",
        "minero.cc",
        "webminepool.com",
        "authedmine.com",
        "monerominer.rocks",
        "coinimp.com",
    )

    private val adPathPatterns = listOf(
        "/pagead/",
        "/aclk?",
        "/pcs/click",
        "/gampad/",
        "/vast",
        "/vmap",
        "/preroll",
        "/midroll",
        "/adserver/",
        "/ad_break",
        "/popunder",
        "ad_type=",
        "adunit=",
        "doubleclick",
        "google_ads",
    )

    fun isSiteWhitelisted(pageHost: String?, customRules: List<AdBlockRuleEntity>): Boolean {
        val host = pageHost?.lowercase()?.trim().orEmpty()
        if (host.isBlank()) return false
        return customRules.any { rule ->
            rule.isEnabled && rule.isWhitelist && matchesFilterRule(rule.pattern, "https://$host/", host)
        }
    }

    fun evaluate(
        url: String,
        pageHost: String?,
        adBlockEnabled: Boolean,
        trackerBlockEnabled: Boolean,
        customRules: List<AdBlockRuleEntity>,
    ): BlockDecision {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return BlockDecision(false)
        val host = uri.host?.lowercase() ?: return BlockDecision(false)

        if (matchesDomainSet(host, authWhitelistSuffixes)) {
            return BlockDecision(false)
        }

        // Aloha-style Per-Site Shield Pause: if the current site host itself is whitelisted, allow its resources
        if (isSiteWhitelisted(pageHost, customRules)) {
            return BlockDecision(false)
        }

        // Evaluate EasyList / custom exception rules (`@@||domain^` or plain whitelist)
        for (rule in customRules) {
            if (!rule.isEnabled) continue
            val pat = rule.pattern.trim()
            if (pat.startsWith("##")) continue // cosmetic rule
            val isException = rule.isWhitelist || pat.startsWith("@@")
            if (isException && matchesFilterRule(pat.removePrefix("@@"), url, host)) {
                return BlockDecision(false)
            }
        }

        // Evaluate EasyList / custom block rules
        for (rule in customRules) {
            if (!rule.isEnabled) continue
            val pat = rule.pattern.trim()
            if (pat.startsWith("##") || pat.startsWith("@@") || rule.isWhitelist) continue
            if (matchesFilterRule(pat, url, host)) {
                return BlockDecision(true, BlockCategory.CUSTOM_RULE, pat)
            }
        }

        // Always block in-browser cryptominers when AdBlock or TrackerBlock is enabled
        if (adBlockEnabled || trackerBlockEnabled) {
            val matchedMiner = findMatchingDomain(host, cryptoMinerDomains)
            if (matchedMiner != null) {
                return BlockDecision(true, BlockCategory.CRYPTO_MINER, matchedMiner)
            }
        }

        if (adBlockEnabled) {
            val matchedAd = findMatchingDomain(host, adDomains)
            if (matchedAd != null) {
                return BlockDecision(true, BlockCategory.AD, matchedAd)
            }
            val lowerUrl = url.lowercase()
            val isSameHost = pageHost != null && host.endsWith(pageHost.lowercase())
            if (!isSameHost) {
                val matchedPath = adPathPatterns.firstOrNull { lowerUrl.contains(it) }
                if (matchedPath != null) {
                    return BlockDecision(true, BlockCategory.AD, matchedPath)
                }
            }
        }

        if (trackerBlockEnabled) {
            val matchedTracker = findMatchingDomain(host, trackerDomains)
            if (matchedTracker != null) {
                return BlockDecision(true, BlockCategory.TRACKER, matchedTracker)
            }
        }

        return BlockDecision(false)
    }

    /**
     * Blocks automatic off-domain popup/ad-network redirects in shouldOverrideUrlLoading
     * when not triggered by an explicit user gesture on auth/known hosts.
     */
    fun shouldBlockAdRedirect(targetUrl: String, pageHost: String?, adBlockEnabled: Boolean): Boolean {
        if (!adBlockEnabled) return false
        val uri = runCatching { Uri.parse(targetUrl) }.getOrNull() ?: return false
        val host = uri.host?.lowercase() ?: return false
        if (matchesDomainSet(host, authWhitelistSuffixes)) return false
        if (pageHost != null && host.endsWith(pageHost.lowercase())) return false
        return findMatchingDomain(host, adDomains) != null || findMatchingDomain(host, cryptoMinerDomains) != null
    }

    fun isAdOrPreRollMediaUrl(url: String): Boolean {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return false
        val host = uri.host?.lowercase() ?: return false
        if (matchesDomainSet(host, adDomains)) return true
        val lower = url.lowercase()
        val markers = listOf(
            "doubleclick",
            "googlesyndication",
            "imasdk",
            "/vast",
            "/vmap",
            "preroll",
            "pre-roll",
            "midroll",
            "mid-roll",
            "bumper",
            "adsystem",
            "adserver",
            "tapsell",
            "sabavision",
            "ad_break",
            "sponsored_clip",
        )
        return markers.any { lower.contains(it) }
    }

    fun emptyBlockedResponse(): WebResourceResponse =
        WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))

    /**
     * Aloha-style live MutationObserver cosmetic cleaner + transparent clickjacking overlay remover
     * + window.open ad popup blocker + EasyList `##selector` support.
     */
    fun cosmeticAdHideScript(customRules: List<AdBlockRuleEntity> = emptyList()): String {
        val extraSelectors = customRules
            .filter { it.isEnabled && !it.isWhitelist && it.pattern.trim().startsWith("##") }
            .map { it.pattern.trim().removePrefix("##").trim() }
            .filter { it.isNotBlank() }

        val defaultSelectors = listOf(
            "ins.adsbygoogle",
            "iframe[src*=\"doubleclick.net\"]",
            "iframe[src*=\"googlesyndication.com\"]",
            "iframe[src*=\"yektanet\"]",
            "div[id^=\"div-gpt-ad\"]",
            "div[id^=\"google_ads_iframe\"]",
            ".taboola-widget",
            ".OUTBRAIN",
            "[data-ad-slot]",
            "[data-ad-unit]",
            "[class*=\"sponsored-ad\"]",
            "[class*=\"ad-overlay-banner\"]",
            "[id*=\"popunder\"]",
            "[class*=\"sticky-ad-bottom\"]",
        )

        val allCss = (defaultSelectors + extraSelectors).joinToString(",\n") +
            " { display: none !important; height: 0 !important; min-height: 0 !important; opacity: 0 !important; pointer-events: none !important; }"
        val quotedCss = JSONObject.quote(allCss)

        return """
            (function() {
                if (window.__salviaAlohaShield) return;
                window.__salviaAlohaShield = true;

                function injectStyle() {
                    if (document.getElementById('salvia-aloha-cosmetic')) return;
                    var style = document.createElement('style');
                    style.id = 'salvia-aloha-cosmetic';
                    style.textContent = $quotedCss;
                    (document.head || document.documentElement).appendChild(style);
                }
                injectStyle();

                function removeTransparentAdOverlays() {
                    try {
                        var vw = window.innerWidth || 360;
                        var vh = window.innerHeight || 640;
                        document.querySelectorAll('div, a').forEach(function(el) {
                            if (el.dataset.salviaChecked) return;
                            el.dataset.salviaChecked = '1';
                            var cs = getComputedStyle(el);
                            var z = parseInt(cs.zIndex, 10);
                            if ((cs.position === 'fixed' || cs.position === 'absolute') && z >= 9999) {
                                var r = el.getBoundingClientRect();
                                var coversScreen = r.width >= vw * 0.75 && r.height >= vh * 0.75;
                                var isInvisibleTrap = parseFloat(cs.opacity) < 0.08 || cs.backgroundColor === 'rgba(0, 0, 0, 0)';
                                var hasTextOrMedia = el.innerText.trim().length > 20 || el.querySelector('video, input, form');
                                if (coversScreen && isInvisibleTrap && !hasTextOrMedia) {
                                    el.remove();
                                }
                            }
                        });
                    } catch(e) {}
                }

                // Block ad popups while preserving legitimate OAuth / Google Sign-In popups
                var origOpen = window.open;
                window.open = function(url, target, features) {
                    if (!url || /doubleclick|popads|popcash|propeller|adsterra|exoclick|juicyads|adclick|taboola|outbrain/i.test(url)) {
                        return null;
                    }
                    return origOpen.call(window, url, target, features);
                };

                var obs = new MutationObserver(function() {
                    injectStyle();
                    removeTransparentAdOverlays();
                });
                if (document.documentElement) {
                    obs.observe(document.documentElement, { childList: true, subtree: true });
                }
                removeTransparentAdOverlays();
            })();
        """.trimIndent()
    }

    private fun matchesFilterRule(rawRule: String, url: String, host: String): Boolean {
        val rule = rawRule.trim()
        if (rule.isEmpty()) return false
        return when {
            rule.startsWith("||") -> {
                val domainPart = rule.removePrefix("||").substringBefore("^").substringBefore("/").lowercase()
                host == domainPart || host.endsWith(".$domainPart")
            }
            rule.contains("*") -> {
                val regex = Regex(
                    Regex.escape(rule).replace("\\*", ".*"),
                    RegexOption.IGNORE_CASE,
                )
                regex.containsMatchIn(url)
            }
            else -> url.contains(rule, ignoreCase = true) || host == rule.lowercase() || host.endsWith(".${rule.lowercase()}")
        }
    }

    private fun matchesDomainSet(host: String, set: Set<String>): Boolean =
        findMatchingDomain(host, set) != null

    private fun findMatchingDomain(host: String, set: Set<String>): String? {
        var current = host
        while (current.isNotEmpty()) {
            if (set.contains(current)) return current
            val dotIndex = current.indexOf('.')
            if (dotIndex < 0) break
            current = current.substring(dotIndex + 1)
        }
        return null
    }
}
