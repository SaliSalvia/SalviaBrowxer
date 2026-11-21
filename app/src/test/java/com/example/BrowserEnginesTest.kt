package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AdBlockRuleEntity
import com.example.engine.AdBlockerEngine
import com.example.engine.BlockCategory
import com.example.engine.ExtensionEngine
import com.example.engine.MediaSnifferEngine
import com.example.engine.WebViewConfigurator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BrowserEnginesTest {

    @Test
    fun `adblocker blocks ad and tracker domains while whitelisting gmail and google auth`() {
        val adDecision = AdBlockerEngine.evaluate(
            url = "https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js",
            pageHost = "example.com",
            adBlockEnabled = true,
            trackerBlockEnabled = true,
            customRules = emptyList(),
        )
        assertTrue(adDecision.isBlocked)
        assertEquals(BlockCategory.AD, adDecision.category)

        val trackerDecision = AdBlockerEngine.evaluate(
            url = "https://www.google-analytics.com/analytics.js",
            pageHost = "example.com",
            adBlockEnabled = true,
            trackerBlockEnabled = true,
            customRules = emptyList(),
        )
        assertTrue(trackerDecision.isBlocked)
        assertEquals(BlockCategory.TRACKER, trackerDecision.category)

        val gmailDecision = AdBlockerEngine.evaluate(
            url = "https://mail.google.com/mail/u/0/",
            pageHost = "mail.google.com",
            adBlockEnabled = true,
            trackerBlockEnabled = true,
            customRules = emptyList(),
        )
        assertFalse(gmailDecision.isBlocked)

        val googleAuthDecision = AdBlockerEngine.evaluate(
            url = "https://accounts.google.com/v3/signin/identifier",
            pageHost = "accounts.google.com",
            adBlockEnabled = true,
            trackerBlockEnabled = true,
            customRules = emptyList(),
        )
        assertFalse(googleAuthDecision.isBlocked)
    }

    @Test
    fun `aloha adblocker supports easylist syntax cryptominer blocking per-site pause and redirects`() {
        val minerDecision = AdBlockerEngine.evaluate(
            url = "https://coinhive.com/lib/coinhive.min.js",
            pageHost = "video.example.org",
            adBlockEnabled = true,
            trackerBlockEnabled = true,
            customRules = emptyList(),
        )
        assertTrue(minerDecision.isBlocked)
        assertEquals(BlockCategory.CRYPTO_MINER, minerDecision.category)

        val rules = listOf(
            AdBlockRuleEntity(id = 1L, pattern = "||badtracker.example^", isWhitelist = false, isEnabled = true),
            AdBlockRuleEntity(id = 2L, pattern = "@@||goodcdn.example^", isWhitelist = false, isEnabled = true),
            AdBlockRuleEntity(id = 3L, pattern = "trusted-news.com", isWhitelist = true, isEnabled = true),
        )
        val easyBlock = AdBlockerEngine.evaluate(
            url = "https://sub.badtracker.example/pixel.js",
            pageHost = "other.com",
            adBlockEnabled = true,
            trackerBlockEnabled = true,
            customRules = rules,
        )
        assertTrue(easyBlock.isBlocked)
        assertEquals(BlockCategory.CUSTOM_RULE, easyBlock.category)

        val easyException = AdBlockerEngine.evaluate(
            url = "https://goodcdn.example/player.js",
            pageHost = "other.com",
            adBlockEnabled = true,
            trackerBlockEnabled = true,
            customRules = rules,
        )
        assertFalse(easyException.isBlocked)

        assertTrue(AdBlockerEngine.isSiteWhitelisted("trusted-news.com", rules))
        val pausedSiteAd = AdBlockerEngine.evaluate(
            url = "https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js",
            pageHost = "trusted-news.com",
            adBlockEnabled = true,
            trackerBlockEnabled = true,
            customRules = rules,
        )
        assertFalse(pausedSiteAd.isBlocked)

        assertTrue(
            AdBlockerEngine.shouldBlockAdRedirect(
                targetUrl = "https://popads.net/click?id=99",
                pageHost = "movies.example.com",
                adBlockEnabled = true,
            ),
        )
        assertFalse(
            AdBlockerEngine.shouldBlockAdRedirect(
                targetUrl = "https://accounts.google.com/o/oauth2/auth",
                pageHost = "movies.example.com",
                adBlockEnabled = true,
            ),
        )
    }

    @Test
    fun `media sniffer filters out 5s pre-roll ad clips and prioritizes main page video`() {
        val sniffer = MediaSnifferEngine()
        val payload = """
            [
              {
                "src": "https://cdn.example.com/ads/preroll_5s.mp4",
                "sources": [{"url": "https://cdn.example.com/ads/preroll_5s.mp4", "height": 720}],
                "duration": 5.0,
                "width": 640,
                "height": 360,
                "area": 100000,
                "visible": true,
                "isAudio": false,
                "isAdElement": false,
                "title": "Ad"
              },
              {
                "src": "https://cdn.example.com/clips/secondary.mp4",
                "sources": [{"url": "https://cdn.example.com/clips/secondary.mp4", "height": 480}],
                "duration": 45.0,
                "width": 640,
                "height": 480,
                "area": 80000,
                "visible": true,
                "isAudio": false,
                "isAdElement": false,
                "title": "Secondary Video"
              },
              {
                "src": "https://cdn.example.com/main/movie_1080.mp4",
                "sources": [
                  {"url": "https://cdn.example.com/main/movie_1080.mp4", "height": 1080},
                  {"url": "https://cdn.example.com/main/movie_720.mp4", "height": 720}
                ],
                "duration": 620.0,
                "width": 1920,
                "height": 1080,
                "area": 360000,
                "visible": true,
                "isAudio": false,
                "isAdElement": false,
                "title": "Main Feature Video"
              }
            ]
        """.trimIndent()

        val detected = sniffer.parseDomMediaPayload(payload, "https://example.com/watch", "Watch Page")
        assertEquals(2, detected.size)
        assertEquals("Main Feature Video", detected[0].title)
        assertTrue(detected[0].isMainVideo)
        assertFalse(detected[1].isMainVideo)
        assertTrue(detected.none { it.primaryUrl.contains("preroll") })
    }

    @Test
    fun `media sniffer identifies dash hls mp4 while ignoring ts m4s chunks and provides early blob hook`() {
        val sniffer = MediaSnifferEngine()
        assertTrue(sniffer.isCandidateMediaUrl("https://cdn.example.com/stream/master.m3u8?token=abc"))
        assertTrue(sniffer.isCandidateMediaUrl("https://cdn.example.com/stream/manifest.mpd"))
        assertTrue(sniffer.isCandidateMediaUrl("https://cdn.example.com/video/movie.mp4"))
        assertTrue(sniffer.isCandidateMediaUrl("https://cdn.example.com/videoplayback?mime%3Dvideo%2Fmp4"))
        assertFalse(sniffer.isCandidateMediaUrl("https://cdn.example.com/stream/seg_001.ts"))
        assertFalse(sniffer.isCandidateMediaUrl("https://cdn.example.com/stream/chunk_01.m4s"))
        assertFalse(sniffer.isCandidateMediaUrl("blob:https://example.com/1234-5678"))
        assertTrue(sniffer.earlyMediaHookScript().contains("XMLHttpRequest"))
        assertTrue(sniffer.earlyMediaHookScript().contains("window.fetch"))
        assertTrue(sniffer.domSnifferScript().contains("MutationObserver"))
    }

    @Test
    fun `user agent strips wv token for full sign-in compatibility`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val mobileUa = WebViewConfigurator.resolveUserAgent(context, desktopMode = false)
        assertFalse(mobileUa.contains("; wv"))
        assertTrue(mobileUa.contains("Chrome/"))
    }

    @Test
    fun `extension engine matches glob patterns and builds injection script`() {
        assertTrue(ExtensionEngine.matchesUrl("*://*.youtube.com/*", "https://m.youtube.com/watch?v=123"))
        assertFalse(ExtensionEngine.matchesUrl("*://*.github.com/*", "https://example.com"))
        val script = ExtensionEngine.buildInjectionScript(
            extensions = ExtensionEngine.builtInExtensions(),
            url = "https://example.com",
            phase = "document_end",
        )
        assertTrue(script.contains("nava_ext_css_"))
    }
}
