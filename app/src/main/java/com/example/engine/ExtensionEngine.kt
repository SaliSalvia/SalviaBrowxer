package com.example.engine

import com.example.data.local.ExtensionEntity
import org.json.JSONObject

object ExtensionEngine {

    fun builtInExtensions(): List<ExtensionEntity> = listOf(
        ExtensionEntity(
            id = "ext_dark_reader_pro",
            name = "Dark Reader Pro (حالت شب هوشمند)",
            version = "4.9.1",
            author = "Nava Add-ons",
            description = "تم تاریک هوشمند و چشم‌نواز برای تمام وب‌سایت‌های روشن با حفظ کنتراست تصاویر و ویدیوها.",
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
            name = "uBlock Script Shield (محافظ پاپ‌آپ و ضد مسدودیت)",
            version = "2.4.0",
            author = "Nava Privacy Lab",
            description = "پاکسازی بنرهای مزاحم کوکی، مسدودسازی پاپ‌آپ‌های تبلیغاتی و خنثی‌سازی اسکریپت‌های ضد مسدودکننده تبلیغات.",
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
            name = "Video Ad Auto-Skipper (رد خودکار تبلیغ ویدیویی)",
            version = "1.8.2",
            author = "Nava Media Labs",
            description = "فشردن خودکار دکمه رد کردن تبلیغ (Skip Ad) و افزایش سرعت کلیپ‌های تبلیغاتی داخل پخش‌کننده‌های وب.",
            iconKey = "fast_forward",
            matchPattern = "*://*/*",
            runAt = "document_end",
            permissions = "activeTab, media",
            cssCode = "",
            jsCode = """
                (function() {
                    if (window.__navaAdSkipperActive) return;
                    window.__navaAdSkipperActive = true;
                    setInterval(function() {
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
                    }, 600);
                })();
            """.trimIndent(),
            isInstalled = true,
            isEnabled = true,
            isBuiltIn = true,
        ),
        ExtensionEntity(
            id = "ext_force_copy",
            name = "Force Copy & Context Menu (آزادسازی کپی متن)",
            version = "1.3.0",
            author = "OpenWeb Tools",
            description = "فعال‌سازی انتخاب متن، کپی و منوی راست‌کلیک در سایت‌هایی که کپی کردن را غیرفعال کرده‌اند.",
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
            name = "Video Speed Controller (کنترلر سرعت پخش)",
            version = "2.1.0",
            author = "Nava Media Labs",
            description = "افزودن کنترلر شناور تغییر سرعت پخش (0.5x تا 3x) روی تمامی پخش‌کننده‌های ویدیوی HTML5.",
            iconKey = "speed",
            matchPattern = "*://*/*",
            runAt = "document_end",
            permissions = "activeTab",
            cssCode = "",
            jsCode = """
                (function() {
                    if (window.__navaSpeedWidget) return;
                    window.__navaSpeedWidget = true;
                    function attachBadge(video) {
                        if (video.dataset.navaSpeedAttached) return;
                        video.dataset.navaSpeedAttached = '1';
                        const badge = document.createElement('button');
                        badge.textContent = video.playbackRate + 'x';
                        badge.style.cssText = 'position:fixed;bottom:84px;left:14px;z-index:2147483640;background:rgba(10,14,23,0.82);color:#22D3EE;border:1px solid #22D3EE;border-radius:16px;padding:4px 10px;font-size:12px;font-weight:700;backdrop-filter:blur(6px);';
                        const speeds = [1.0, 1.25, 1.5, 2.0, 2.5, 0.75];
                        let idx = 0;
                        badge.onclick = function(e) {
                            e.stopPropagation();
                            idx = (idx + 1) % speeds.length;
                            video.playbackRate = speeds[idx];
                            badge.textContent = speeds[idx] + 'x';
                        };
                        document.body && document.body.appendChild(badge);
                    }
                    setInterval(() => {
                        document.querySelectorAll('video').forEach(v => {
                            if (v.videoWidth > 180 || v.clientWidth > 180) attachBadge(v);
                        });
                    }, 1500);
                })();
            """.trimIndent(),
            isInstalled = false,
            isEnabled = false,
            isBuiltIn = true,
        ),
        ExtensionEntity(
            id = "ext_json_prettifier",
            name = "JSON Formatter Pro (نمایشگر زیبای JSON)",
            version = "1.5.4",
            author = "Nava DevTools",
            description = "تشخیص خودکار پاسخ‌های خام JSON در تب مرورگر و قالب‌بندی رنگی و خوانا برای توسعه‌دهندگان.",
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

    fun matchesUrl(pattern: String, url: String): Boolean {
        val trimmed = pattern.trim()
        if (trimmed.isEmpty() || trimmed == "*://*/*" || trimmed == "<all_urls>") return true
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
        return runCatching { Regex(regexStr, RegexOption.IGNORE_CASE).containsMatchIn(url) }
            .getOrDefault(false)
    }

    fun buildInjectionScript(extensions: List<ExtensionEntity>, url: String, phase: String): String {
        val matching = extensions.filter {
            it.isInstalled && it.isEnabled &&
                it.runAt == phase &&
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
