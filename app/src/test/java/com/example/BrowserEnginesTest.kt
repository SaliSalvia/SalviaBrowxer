package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AdBlockRuleEntity
import com.example.data.local.AppLanguage
import com.example.data.local.BrowserSettings
import com.example.engine.AdBlockerEngine
import com.example.engine.BlockCategory
import com.example.engine.ExtensionEngine
import com.example.engine.MediaSnifferEngine
import com.example.engine.MediaPlatformDetector
import com.example.engine.WebViewConfigurator
import com.example.ui.MediaCaptureStatus
import com.example.ui.MediaDetectionSource
import com.example.ui.DetectedMedia
import com.example.ui.MediaQualityOption
import com.example.ui.i18n.stringsFor
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

        val wildcardRules = listOf(
            AdBlockRuleEntity(id = 4L, pattern = "*://*.ads-cdn.example/*banner*", isWhitelist = false, isEnabled = true),
        )
        assertTrue(
            AdBlockerEngine.evaluate(
                url = "https://static.ads-cdn.example/v2/top-banner.js",
                pageHost = "news.example.com",
                adBlockEnabled = true,
                trackerBlockEnabled = true,
                customRules = wildcardRules,
            ).isBlocked,
        )
        assertFalse(
            AdBlockerEngine.evaluate(
                url = "https://static.ads-cdn.example/v2/player.js",
                pageHost = "news.example.com",
                adBlockEnabled = true,
                trackerBlockEnabled = true,
                customRules = wildcardRules,
            ).isBlocked,
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
        assertTrue(sniffer.domSnifferScript().contains("mediastream:"))
        assertTrue(sniffer.domSnifferScript().contains("isLive"))
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

    @Test
    fun `default language is english with persian support and updated adblocker branding`() {
        val defaultSettings = BrowserSettings()
        assertEquals(AppLanguage.ENGLISH, defaultSettings.appLanguage)

        val en = stringsFor(AppLanguage.ENGLISH)
        val fa = stringsFor(AppLanguage.PERSIAN)

        assertEquals("AdBlocker", en.adBlockerBadge)
        assertEquals("AdBlocker", en.adBlockerLabel)
        assertEquals("ضد تبلیغ", fa.adBlockerBadge)
        assertEquals("ضد تبلیغ", fa.adBlockerLabel)

        val enDump = listOf(
            en.adBlockerBadge, en.adBlockerLabel, en.siteAdBlockerTitle,
            en.settingAdBlockTitle, en.searchEngineHintFormat, en.incognitoTabButton,
        ).joinToString(" ")
        val faDump = listOf(
            fa.adBlockerBadge, fa.adBlockerLabel, fa.siteAdBlockerTitle,
            fa.settingAdBlockTitle, fa.searchEngineHintFormat, fa.incognitoTabButton,
        ).joinToString(" ")

        assertFalse(enDump.contains("Aloha", ignoreCase = true))
        assertFalse(faDump.contains("Aloha", ignoreCase = true))
        assertFalse(enDump.contains("Salar Farzaneh", ignoreCase = true))
        assertFalse(faDump.contains("Salar Farzaneh", ignoreCase = true))
        assertFalse(faDump.contains("دسترسی سریع"))
    }
    @Test
    fun `blob MSE references are retained without exposing a fake download option`() {
        val sniffer = MediaSnifferEngine()
        val payload = """
            [{
              "src": "blob:https://www.youtube.com/4f62",
              "sources": [{"url": "blob:https://www.youtube.com/4f62", "height": 1080}],
              "source": "HTML5_ELEMENT",
              "duration": 120.0,
              "width": 1920,
              "height": 1080,
              "area": 400000,
              "visible": true,
              "isAudio": false,
              "isLive": false,
              "isAdElement": false,
              "title": "MSE player"
            }]
        """.trimIndent()

        val result = sniffer.parseDomMediaPayload(
            payload,
            "https://www.youtube.com/watch?v=test",
            "Video page",
        ).single()

        assertEquals(MediaDetectionSource.MSE_REFERENCE, result.detectionSource)
        assertEquals(MediaCaptureStatus.BLOB_OR_MSE_REFERENCE_ONLY, result.captureStatus)
        assertTrue(result.qualities.isEmpty())
        assertFalse(result.isDownloadable)
        assertEquals("YouTube", result.platformLabel)
    }

    @Test
    fun `live media stream references are labelled as unsupported for download`() {
        val sniffer = MediaSnifferEngine()
        val payload = """
            [{
              "src": "mediastream:https://example.com/live#element-0",
              "sources": [],
              "source": "HTML5_ELEMENT",
              "duration": 0,
              "width": 1280,
              "height": 720,
              "area": 300000,
              "visible": true,
              "isAudio": false,
              "isLive": true,
              "isAdElement": false,
              "title": "Live stream"
            }]
        """.trimIndent()

        val result = sniffer.parseDomMediaPayload(
            payload,
            "https://example.com/live",
            "Live page",
        ).single()

        assertEquals(MediaCaptureStatus.LIVE_MEDIA_STREAM_REFERENCE_ONLY, result.captureStatus)
        assertTrue(result.primaryUrl.startsWith("mediastream:"))
        assertEquals(result.primaryUrl, result.id)
        assertTrue(result.qualities.isEmpty())
        assertFalse(result.isDownloadable)
    }

    @Test
    fun `DOM source evidence and platform confidence are preserved`() {
        val sniffer = MediaSnifferEngine()
        val payload = """
            [{
              "src": "../videos/feature.mp4",
              "sources": [{"url": "../videos/feature.mp4", "height": 1080}],
              "source": "JSON_LD",
              "duration": 240.0,
              "width": 1920,
              "height": 1080,
              "area": 400000,
              "visible": true,
              "isAudio": false,
              "isLive": false,
              "isAdElement": false,
              "title": "Feature video"
            }]
        """.trimIndent()

        val result = sniffer.parseDomMediaPayload(
            payload,
            "https://m.youtube.com/watch?v=test",
            "Feature page",
        ).single()

        assertEquals(MediaDetectionSource.JSON_LD, result.detectionSource)
        assertEquals(MediaCaptureStatus.DOWNLOADABLE, result.captureStatus)
        assertTrue(result.isDownloadable)
        assertEquals("https://m.youtube.com/videos/feature.mp4", result.primaryUrl)
        assertEquals("https://m.youtube.com/videos/feature.mp4", result.qualities.single().url)
        assertEquals("YouTube", result.platformLabel)
        assertTrue(result.confidenceScore >= 80)
    }

    @Test
    fun `platform matching uses exact host suffixes rather than substring matches`() {
        assertEquals(
            "YouTube",
            MediaPlatformDetector.detect(
                "https://www.youtube.com/watch?v=1",
                "https://cdn.example.com/video.mp4",
            ).label,
        )
        assertEquals(
            "Web",
            MediaPlatformDetector.detect(
                "https://youtube.com.attacker.example/watch",
                "https://cdn.example.com/video.mp4",
            ).label,
        )
    }


    @Test
    fun `relative and protocol relative media URLs resolve against the page`() {
        val sniffer = MediaSnifferEngine()

        assertEquals(
            "https://example.com/videos/master.m3u8",
            sniffer.resolveMediaUrl(
                "https://example.com/watch/page",
                "../videos/master.m3u8",
            ),
        )
        assertEquals(
            "https://cdn.example.com/video.mp4",
            sniffer.resolveMediaUrl(
                "https://example.com/watch",
                "//cdn.example.com/video.mp4",
            ),
        )
        assertEquals(
            "https://example.com/video.mp4",
            sniffer.resolveMediaUrl(
                "https://example.com/watch",
                "../../../../../video.mp4",
            ),
        )
        assertEquals(
            "https://example.com/video.mp4?quality=high#player",
            sniffer.resolveMediaUrl(
                "https://example.com/watch",
                "../video.mp4?quality=high#player",
            ),
        )
    }

    @Test
    fun `media URL admission rejects local and unsupported schemes`() {
        val sniffer = MediaSnifferEngine()
        assertFalse(sniffer.isCandidateMediaUrl("file:///sdcard/Movies/video.mp4"))
        assertFalse(sniffer.isCandidateMediaUrl("javascript:https://cdn.example.com/video.mp4"))
        assertFalse(sniffer.isCandidateMediaUrl("blob:https://example.com/video"))
    }

    @Test
    fun `downloadability requires an absolute HTTP or HTTPS host`() {
        val media = DetectedMedia(
            id = "local-file",
            title = "Local file",
            sourcePageUrl = "https://example.com",
            primaryUrl = "file:///sdcard/Movies/video.mp4",
            qualities = listOf(
                MediaQualityOption(
                    label = "Local",
                    resolutionHeight = 720,
                    url = "file:///sdcard/Movies/video.mp4",
                    format = "MP4",
                    mimeType = "video/mp4",
                ),
            ),
        )

        assertFalse(media.isDownloadable)
    }

}
