package com.example.engine

import com.example.data.local.ExtensionEntity
import org.json.JSONObject

object ExtensionEngine {

    fun builtInExtensions(): List<ExtensionEntity> = listOf(
        ExtensionEntity(
            id = "ext_dark_reader_pro",
            name = "Dark Reader Pro (Smart Night Mode)",
            version = "4.9.1",
            author = "Salvia Add-ons",
            description = "Smart adaptive dark theme for bright websites while preserving image and video contrast.",
            iconKey = "dark_mode",
            matchPattern = "*://*/*",
            runAt = "document_end",
            permissions = "tabs, activeTab, scripting",
            cssCode = """
                html {
                    filter: invert(90%) hue-rotate(180deg) !important;
                    background-color: #111 !important;
                }
                img, video, picture, canvas, svg, [style*="background-image"] {
                    filter: invert(100%) hue-rotate(180deg) !important;
                }
            """.trimIndent(),
            jsCode = """
                (function() {
                    if (window.__navaDarkReaderApplied) return;
                    window.__navaDarkReaderApplied = true;
                    const bg = getComputedStyle(document.documentElement).backgroundColor;
                    if (bg && bg.includes('rgb(')) {
                        const parts = bg.match(/\d+/g);
                        if (parts && parts.length >= 3) {
                            const luma = (0.299*parts[0] + 0.587*parts[1] + 0.114*parts[2]);
                            if (luma < 85) {
                                // Site is already dark; avoid double-inverting
                                document.documentElement.style.filter = 'none';
                                document.querySelectorAll('img,video,picture,canvas,svg').forEach(el => {
                                    el.style.filter = 'none';
                                });
                            }
                        }
                    }
                })();
            """.trimIndent(),
            isInstalled = true,
            isEnabled = false,
            isBuiltIn = true,
        ),
        ExtensionEntity(
            id = "ext_ublock_script_shield",
            name = "uBlock Script Shield (Popup & Ad Shield)",
            version = "2.4.0",
            author = "Salvia Privacy Lab",
            description = "Cleans intrusive cookie banners, blocks ad popups, and neutralizes anti-adblock scripts.",
            iconKey = "shield",
            matchPattern = "*://*/*",
            runAt = "document_end",
            permissions = "webRequest, scripting",
            cssCode = """
                .adsbygoogle, ins.adsbygoogle, [id*="ad-banner"], [class*="ad-container"],
                [class*="sponsored-banner"], [id*="popunder"], #cookie-banner, .cookie-consent-banner {
                    display: none !important;
                    visibility: hidden !important;
                    height: 0 !important;
                }
            """.trimIndent(),
            jsCode = """
                (function() {
                    if (window.__navaShieldInjected) return;
                    window.__navaShieldInjected = true;
                    const origOpen = window.open;
                    window.open = function(url, target, features) {
                        if (!url || /doubleclick|popads|propeller|adclick|taboola|outbrain/i.test(url)) {
                            console.info('[Nava Shield] Blocked popup:', url);
                            return null;
                        }
                        return origOpen.call(window, url, target, features);
                    };
                })();
            """.trimIndent(),
            isInstalled = true,
            isEnabled = true,
            isBuiltIn = true,
        ),
        ExtensionEntity(
            id = "ext_video_ad_skipper",
            name = "Video Ad Auto-Skipper",
            version = "1.9.0",
            author = "Salvia Media Labs",
            description = "Automatically clicks Skip Ad buttons and fast-forwards embedded pre-roll video ads.",
            iconKey = "fast_forward",
            matchPattern = "*://*/*",
            runAt = "document_end",
            permissions = "activeTab, media",
            cssCode = "",
            jsCode = """
                (function() {
                    if (window.__navaAdSkipperActive) return;
                    window.__navaAdSkipperActive = true;
                    var skipperTimer = null;
                    function startSkipper() {
                        if (skipperTimer === null) skipperTimer = setInterval(skipTick, 600);
                    }
                    function stopSkipper() {
                        if (skipperTimer !== null) { clearInterval(skipperTimer); skipperTimer = null; }
                    }
                    // The poll only runs while the page is visible and stops in the bfcache.
                    document.addEventListener('visibilitychange', function() {
                        if (document.hidden) stopSkipper(); else startSkipper();
                    });
                    window.addEventListener('pagehide', stopSkipper);
                    window.addEventListener('pageshow', startSkipper);
                    function skipTick() {
                        const skipSelectors = [
                            '.ytp-ad-skip-button', '.ytp-ad-skip-button-modern',
                            '.ytp-skip-ad-button', '[class*="skip-ad"]', '[class*="skipAd"]',
                            'button[aria-label*="Skip"]', '.vast-skip-button'
                        ];
                        skipSelectors.forEach(sel => {
                            const btn = document.querySelector(sel);
                            if (btn && btn.offsetParent !== null) {
                                btn.click();
                                console.info('[Nava Skipper] Auto-skipped video ad');
                            }
                        });
                        const adShowing = document.querySelector('.ad-showing video, .ytp-ad-player-overlay ~ video');
                        if (adShowing && isFinite(adShowing.duration) && adShowing.duration > 0) {
                            adShowing.muted = true;
                            adShowing.playbackRate = 16.0;
                            if (adShowing.duration - adShowing.currentTime > 0.5) {
                                adShowing.currentTime = adShowing.duration - 0.1;
                            }
                        }
                    }
                    if (!document.hidden) startSkipper();
                })();
            """.trimIndent(),
            isInstalled = true,
            isEnabled = true,
            isBuiltIn = true,
        ),
        ExtensionEntity(
            id = "ext_force_copy",
            name = "Force Copy & Context Menu",
            version = "1.3.0",
            author = "OpenWeb Tools",
            description = "Re-enables text selection, copy, and context menus on restricted websites.",
            iconKey = "content_copy",
            matchPattern = "*://*/*",
            runAt = "document_end",
            permissions = "activeTab",
            cssCode = """
                *, *::before, *::after {
                    -webkit-user-select: text !important;
                    user-select: text !important;
                }
            """.trimIndent(),
            jsCode = """
                (function() {
                    ['copy', 'cut', 'contextmenu', 'selectstart'].forEach(evt => {
                        document.addEventListener(evt, function(e) {
                            e.stopPropagation();
                        }, true);
                    });
                })();
            """.trimIndent(),
            isInstalled = false,
            isEnabled = false,
            isBuiltIn = true,
        ),
        ExtensionEntity(
            id = "ext_video_speed",
            name = "Video Speed Controller",
            version = "2.2.0",
            author = "Salvia Media Labs",
            description = "Adds a floating playback speed controller (0.75x to 2.5x) onto HTML5 video players.",
            iconKey = "speed",
            matchPattern = "*://*/*",
            runAt = "document_end",
            permissions = "activeTab",
            cssCode = "",
            jsCode = """
                (function() {
                    if (window.__navaSpeedWidget) return;
                    window.__navaSpeedWidget = true;
                    // One shared badge that controls the active video, instead of one stacked
                    // fixed badge per video element.
                    const speeds = [1.0, 1.25, 1.5, 2.0, 2.5, 0.75];
                    let active = null;
                    let badge = null;
                    function label() {
                        if (badge && active) badge.textContent = active.playbackRate + 'x';
                    }
                    function ensureBadge() {
                        if (badge || !document.body) return;
                        badge = document.createElement('button');
                        badge.id = 'nava-speed-badge';
                        badge.style.cssText = 'position:fixed;bottom:84px;left:14px;z-index:2147483640;background:rgba(10,14,23,0.82);color:#22D3EE;border:1px solid #22D3EE;border-radius:16px;padding:4px 10px;font-size:12px;font-weight:700;backdrop-filter:blur(6px);display:none;';
                        badge.onclick = function(e) {
                            e.stopPropagation();
                            e.preventDefault();
                            if (!active) return;
                            const idx = speeds.indexOf(active.playbackRate);
                            active.playbackRate = speeds[(idx + 1) % speeds.length];
                            label();
                        };
                        document.body.appendChild(badge);
                    }
                    function setActive(video) {
                        if (!video || (video.videoWidth <= 180 && video.clientWidth <= 180)) return;
                        active = video;
                        ensureBadge();
                        if (badge) badge.style.display = 'block';
                        label();
                    }
                    document.addEventListener('play', function(e) {
                        if (e.target && e.target.tagName === 'VIDEO') setActive(e.target);
                    }, true);
                    document.addEventListener('ratechange', function(e) {
                        if (e.target === active) label();
                    }, true);
                    const first = Array.prototype.find.call(document.querySelectorAll('video'), function(v) {
                        return v.videoWidth > 180 || v.clientWidth > 180;
                    });
                    if (first) setActive(first);
                })();
            """.trimIndent(),
            isInstalled = false,
            isEnabled = false,
            isBuiltIn = true,
        ),
        ExtensionEntity(
            id = "ext_json_prettifier",
            name = "JSON Formatter Pro",
            version = "1.5.4",
            author = "Salvia DevTools",
            description = "Detects raw JSON responses in the browser tab and formats them with clean developer styling.",
            iconKey = "code",
            matchPattern = "*://*/*",
            runAt = "document_end",
            permissions = "activeTab",
            cssCode = "",
            jsCode = """
                (function() {
                    try {
                        if (document.contentType === 'application/json' ||
                            (document.body && document.body.children.length === 1 && document.body.firstElementChild.tagName === 'PRE')) {
                            const raw = document.body.innerText.trim();
                            if ((raw.startsWith('{') && raw.endsWith('}')) || (raw.startsWith('[') && raw.endsWith(']'))) {
                                const parsed = JSON.parse(raw);
                                document.body.style.background = '#0A0E17';
                                document.body.style.color = '#E2E8F0';
                                document.body.style.fontFamily = 'monospace';
                                document.body.style.padding = '16px';
                                document.body.innerHTML = '<pre style="white-space:pre-wrap;word-break:break-word;line-height:1.5;color:#38BDF8;">' +
                                    JSON.stringify(parsed, null, 2).replace(/</g, '&lt;') + '</pre>';
                            }
                        }
                    } catch (e) {}
                })();
            """.trimIndent(),
            isInstalled = true,
            isEnabled = true,
            isBuiltIn = true,
        ),
    )

    private val matchRegexCache = java.util.concurrent.ConcurrentHashMap<String, Regex>()

    fun matchesUrl(pattern: String, url: String): Boolean {
        // Extensions only ever run on web pages, never on file:, content:, data: or about: URLs.
        val lowerUrl = url.trim().lowercase()
        if (!lowerUrl.startsWith("http://") && !lowerUrl.startsWith("https://")) return false
        val trimmed = pattern.trim()
        if (trimmed.isEmpty() || trimmed == "*://*/*" || trimmed == "<all_urls>") return true
        val regex = matchRegexCache[trimmed] ?: run {
            if (matchRegexCache.size >= 128) matchRegexCache.clear()
            val regexStr = buildString {
                append('^')
                for (ch in trimmed) {
                    when (ch) {
                        '*' -> append(".*")
                        '.', '?', '+', '(', ')', '[', ']', '{', '}', '^', '$', '|', '\\' -> {
                            append('\\')
                            append(ch)
                        }
                        else -> append(ch)
                    }
                }
                append('$')
            }
            runCatching { Regex(regexStr, RegexOption.IGNORE_CASE) }.getOrNull()?.also {
                matchRegexCache[trimmed] = it
            }
        } ?: return false
        return regex.containsMatchIn(url)
    }

    /** True when a match pattern grants access to every website. */
    fun isAllSitesPattern(pattern: String): Boolean {
        val trimmed = pattern.trim()
        return trimmed.isEmpty() || trimmed == "<all_urls>" || trimmed == "*://*/*" ||
            trimmed == "*" || trimmed == "http*://*/*" || trimmed == "https://*/*" || trimmed == "http://*/*"
    }

    /**
     * Validates a user-supplied match pattern. Only http(s)/wildcard schemes are accepted and a
     * host part is mandatory, mirroring WebExtension match-pattern rules.
     */
    fun isValidMatchPattern(pattern: String): Boolean {
        val trimmed = pattern.trim()
        if (trimmed == "<all_urls>") return true
        val match = Regex("^(\\*|https?)://([^/]+)(/.*)$").matchEntire(trimmed) ?: return false
        val host = match.groupValues[2]
        return host == "*" || Regex("^(\\*\\.)?[A-Za-z0-9.-]+$").matches(host)
    }

    fun buildInjectionScript(
        extensions: List<ExtensionEntity>,
        url: String,
        phase: String,
        isIncognito: Boolean = false,
    ): String {
        val matching = extensions.filter {
            it.isInstalled && it.isEnabled &&
                it.runAt == phase &&
                // User-created scripts are not allowed in private tabs (like "Allow in
                // incognito" being off by default in desktop browsers).
                (it.isBuiltIn || !isIncognito) &&
                matchesUrl(it.matchPattern, url)
        }
        if (matching.isEmpty()) return ""
        return buildString {
            for (ext in matching) {
                if (ext.cssCode.isNotBlank()) {
                    val safeId = ext.id.replace(Regex("[^a-zA-Z0-9_-]"), "_")
                    val quotedCss = JSONObject.quote(ext.cssCode)
                    append(
                        """
                        (function(){
                            var id = 'nava_ext_css_$safeId';
                            if (!document.getElementById(id)) {
                                var s = document.createElement('style');
                                s.id = id;
                                s.textContent = $quotedCss;
                                (document.head || document.documentElement).appendChild(s);
                            }
                        })();
                        """.trimIndent(),
                    )
                    append('\n')
                }
                if (ext.jsCode.isNotBlank()) {
                    append("try {\n")
                    append(ext.jsCode)
                    append("\n} catch(e) { console.error('[Ext ${ext.name}]', e.message); }\n")
                }
            }
        }
    }
}
