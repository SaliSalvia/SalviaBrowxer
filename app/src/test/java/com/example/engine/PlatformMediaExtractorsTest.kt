package com.example.engine

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for the pure platform extraction parsers. Network fetching is never exercised;
 * every test supplies text through the fetchText callback exactly like
 * MediaSnifferEngine.probePlatformPage does.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlatformMediaExtractorsTest {

    // --- URL matching ---

    @Test
    fun `instagram post urls are matched and non-post pages rejected`() {
        assertEquals("/p/CxAbCdEf/", PlatformMediaExtractors.matchInstagramPost("https://www.instagram.com/p/CxAbCdEf/"))
        assertEquals("/reel/CxAbCdEf", PlatformMediaExtractors.matchInstagramPost("https://instagram.com/reel/CxAbCdEf"))
        assertEquals("/tv/CxAbCdEf/", PlatformMediaExtractors.matchInstagramPost("https://www.instagram.com/tv/CxAbCdEf/"))
        assertNull(PlatformMediaExtractors.matchInstagramPost("https://www.instagram.com/explore/"))
        assertNull(PlatformMediaExtractors.matchInstagramPost("https://example.com/p/CxAbCdEf/"))
        assertNull(PlatformMediaExtractors.matchInstagramPost("not a url"))
    }

    @Test
    fun `tiktok video and short links are matched`() {
        assertEquals(
            "/@creator/video/7300000000000000000",
            PlatformMediaExtractors.matchTikTokVideo("https://www.tiktok.com/@creator/video/7300000000000000000"),
        )
        assertEquals("/ZMabc123", PlatformMediaExtractors.matchTikTokVideo("https://vm.tiktok.com/ZMabc123"))
        assertEquals("/ZMabc123", PlatformMediaExtractors.matchTikTokVideo("https://vt.tiktok.com/ZMabc123"))
        assertNull(PlatformMediaExtractors.matchTikTokVideo("https://www.tiktok.com/@creator"))
        assertNull(PlatformMediaExtractors.matchTikTokVideo("https://example.com/video/123"))
    }

    @Test
    fun `x status urls expose the numeric tweet id`() {
        assertEquals("1234567890", PlatformMediaExtractors.matchXStatus("https://x.com/user/status/1234567890"))
        assertEquals("1234567890", PlatformMediaExtractors.matchXStatus("https://mobile.x.com/user/status/1234567890?s=20"))
        assertEquals("9876543210", PlatformMediaExtractors.matchXStatus("https://twitter.com/i/web/status/9876543210"))
        assertNull(PlatformMediaExtractors.matchXStatus("https://x.com/user"))
        assertNull(PlatformMediaExtractors.matchXStatus("https://x.com/user/status/notanumber"))
        assertNull(PlatformMediaExtractors.matchXStatus("https://example.com/user/status/123"))
    }

    @Test
    fun `isSupported agrees with the individual matchers`() {
        assertTrue(PlatformMediaExtractors.isSupported("https://www.instagram.com/p/CxAbCdEf/"))
        assertTrue(PlatformMediaExtractors.isSupported("https://vm.tiktok.com/ZMabc123"))
        assertTrue(PlatformMediaExtractors.isSupported("https://x.com/user/status/1234567890"))
        assertFalse(PlatformMediaExtractors.isSupported("https://example.com/video.mp4"))
    }

    @Test
    fun `sanitizeMediaUrl rejects opaque and credential-bearing urls`() {
        assertNull(PlatformMediaExtractors.sanitizeMediaUrl(null))
        assertNull(PlatformMediaExtractors.sanitizeMediaUrl(""))
        assertNull(PlatformMediaExtractors.sanitizeMediaUrl("blob:https://example.com/abc"))
        assertNull(PlatformMediaExtractors.sanitizeMediaUrl("data:video/mp4;base64,AAAA"))
        assertNull(PlatformMediaExtractors.sanitizeMediaUrl("ftp://example.com/video.mp4"))
        assertNull(PlatformMediaExtractors.sanitizeMediaUrl("https://user:pass@example.com/video.mp4"))
        assertEquals("https://example.com/video.mp4", PlatformMediaExtractors.sanitizeMediaUrl(" https://example.com/video.mp4 "))
    }

    // --- Instagram parsing ---

    @Test
    fun `instagram og metadata and embedded video versions are merged`() = runTest {
        val html = """
            <html><head>
            <meta property="og:title" content="Designer on Instagram: &quot;Behind the scenes&quot;" />
            <meta property="og:image" content="https://scontent.cdninstagram.com/img/abc.jpg" />
            <meta property="og:video:secure_url" content="https://scontent.cdninstagram.com/v/video_720.mp4?_nc_ht=scontent" />
            </head><body>
            <script type="text/javascript">window.__additionalDataLoaded('extra',{"items":[{"video_versions":[
              {"url":"https://scontent.cdninstagram.com/v/seg_480.mp4?a=1","width":480,"height":854,"type":"video/mp4"},
              {"url":"https://scontent.cdninstagram.com/v/seg_1080.mp4?a=1","width":1080,"height":1920,"type":"video/mp4"}
            ]}]});</script>
            </body></html>
        """.trimIndent()

        val extraction = PlatformMediaExtractors.extract("https://www.instagram.com/p/CxAbCdEf/") { html }

        assertNotNull(extraction)
        val urls = extraction!!.qualities.map { it.url }
        assertTrue(urls.contains("https://scontent.cdninstagram.com/v/seg_1080.mp4?a=1"))
        assertTrue(urls.contains("https://scontent.cdninstagram.com/v/seg_480.mp4?a=1"))
        assertTrue(urls.contains("https://scontent.cdninstagram.com/v/video_720.mp4?_nc_ht=scontent"))
        assertEquals("https://scontent.cdninstagram.com/v/seg_1080.mp4?a=1", extraction.primaryUrl)
        assertEquals(1920, extraction.height)
        assertTrue(extraction.title.contains("Behind the scenes"))
        assertEquals("https://scontent.cdninstagram.com/img/abc.jpg", extraction.thumbnailUrl)
        assertTrue(extraction.qualities.none { it.isHls })
    }

    @Test
    fun `instagram page without media evidence returns null`() = runTest {
        val html = "<html><head><title>Instagram</title></head><body>login required</body></html>"
        assertNull(PlatformMediaExtractors.extract("https://www.instagram.com/p/CxAbCdEf/") { html })
    }

    // --- TikTok parsing ---

    @Test
    fun `tiktok hydration payload yields bitrate variants and audio`() = runTest {
        val html = """
            <html><head><meta property="og:image" content="https://p16-sign-va.tiktokcdn.com/cover.jpg" /></head><body>
            <script id="__UNIVERSAL_DATA_FOR_REHYDRATION__" type="application/json">{"__DEFAULT_SCOPE__":{"webapp.video-detail":{"itemInfo":{"itemStruct":{"id":"7300","desc":"My caption #fyp","music":{"playUrl":"https://sf16-ies-music.tiktokcdn.com/obj/audio.mp3"},"video":{"duration":21,"width":576,"height":1024,"cover":"https://p16-sign-va.tiktokcdn.com/cover.jpg","playAddr":"https://v16-webapp.tiktok.com/video/fallback.mp4?is_play_url=1","downloadAddr":"https://v16-webapp.tiktok.com/video/wm.mp4?is_play_url=1","bitrateInfo":[
              {"bitrate":2000000,"PlayAddr":{"UrlList":["https://v16-webapp.tiktok.com/video/1080.mp4?expires=1"],"DataSize":3000000,"Width":1080,"Height":1920}},
              {"bitrate":800000,"PlayAddr":{"UrlList":["https://v16-webapp.tiktok.com/video/540.mp4?expires=1"],"DataSize":900000,"Width":540,"Height":960}}
            ]}}}}}}</script>
            </body></html>
        """.trimIndent()

        val extraction = PlatformMediaExtractors.extract("https://www.tiktok.com/@creator/video/7300") { html }

        assertNotNull(extraction)
        assertEquals("https://v16-webapp.tiktok.com/video/1080.mp4?expires=1", extraction!!.primaryUrl)
        assertEquals(1920, extraction.height)
        assertEquals(21.0, extraction.durationSeconds, 0.001)
        assertEquals("My caption #fyp", extraction.title)
        val urls = extraction.qualities.map { it.url }
        assertTrue(urls.contains("https://v16-webapp.tiktok.com/video/540.mp4?expires=1"))
        assertTrue(urls.contains("https://v16-webapp.tiktok.com/video/fallback.mp4?is_play_url=1"))
        assertTrue(urls.contains("https://v16-webapp.tiktok.com/video/wm.mp4?is_play_url=1"))
        assertTrue(urls.contains("https://sf16-ies-music.tiktokcdn.com/obj/audio.mp3"))
        assertTrue(extraction.qualities.any { it.format == "MP3" })
        assertTrue(extraction.qualities.none { it.isHls || it.isDash })
    }

    @Test
    fun `tiktok og fallback is used when the hydration payload is missing`() = runTest {
        val html = """
            <html><head>
            <meta property="og:title" content="TikTok moment" />
            <meta property="og:image" content="https://p16-sign-va.tiktokcdn.com/cover.jpg" />
            <meta property="og:video:secure_url" content="https://v16-webapp.tiktok.com/video/og.mp4" />
            </head><body></body></html>
        """.trimIndent()

        val extraction = PlatformMediaExtractors.extract("https://www.tiktok.com/@creator/video/7300") { html }

        assertNotNull(extraction)
        assertEquals("https://v16-webapp.tiktok.com/video/og.mp4", extraction!!.primaryUrl)
        assertEquals("TikTok moment", extraction.title)
        assertEquals(1, extraction.qualities.size)
    }

    // --- X (Twitter) parsing ---

    @Test
    fun `x syndication payload yields mp4 and hls qualities`() = runTest {
        val json = """
            {
              "text": "Launching the new engine today",
              "user": {"name": "Salvia"},
              "mediaDetails": [
                {"type":"photo","media_url_https":"https://pbs.twimg.com/media/abc.jpg"},
                {"type":"video","duration_millis":45000,
                 "video_info":{"aspect_ratio":[16,9],"variants":[
                    {"bitrate":288000,"content_type":"application/x-mpegURL","url":"https://video.twimg.com/vid/pl.m3u8?tag=12"},
                    {"bitrate":320000,"content_type":"video/mp4","url":"https://video.twimg.com/vid/240.mp4?tag=12"},
                    {"bitrate":2176000,"content_type":"video/mp4","url":"https://video.twimg.com/vid/720.mp4?tag=12"}
                 ]}}
              ]
            }
        """.trimIndent()

        val extraction = PlatformMediaExtractors.extract("https://x.com/user/status/1234567890") { json }

        assertNotNull(extraction)
        assertEquals("https://video.twimg.com/vid/720.mp4?tag=12", extraction!!.primaryUrl)
        assertEquals(45.0, extraction.durationSeconds, 0.001)
        assertEquals("Launching the new engine today", extraction.title)
        assertEquals("https://pbs.twimg.com/media/abc.jpg", extraction.thumbnailUrl)
        assertTrue(extraction.qualities.any { it.isHls })
        assertTrue(extraction.qualities.none { it.isHls && it.url != "https://video.twimg.com/vid/pl.m3u8?tag=12" })
    }

    @Test
    fun `x falls back to og metadata when syndication returns nothing`() = runTest {
        val html = """
            <html><head>
            <meta property="og:title" content="Post by Salvia on X" />
            <meta property="og:image" content="https://pbs.twimg.com/media/thumb.jpg" />
            <meta property="og:video:secure_url" content="https://video.twimg.com/vid/og.mp4" />
            </head><body></body></html>
        """.trimIndent()

        val extraction = PlatformMediaExtractors.extract("https://x.com/user/status/1234567890") { url ->
            if (url.startsWith("https://cdn.syndication.twimg.com")) null else html
        }

        assertNotNull(extraction)
        assertEquals("https://video.twimg.com/vid/og.mp4", extraction!!.primaryUrl)
        assertEquals("Post by Salvia on X", extraction.title)
    }

    @Test
    fun `x extraction returns null when neither syndication nor og yields video`() = runTest {
        val extraction = PlatformMediaExtractors.extract("https://x.com/user/status/1234567890") { null }
        assertNull(extraction)
    }

    // --- Meta / JSON helpers ---

    @Test
    fun `metaContent reads both attribute orders and decodes entities`() {
        val html = """
            <meta content="https://cdn.example.com/v/a.mp4?x=1&amp;y=2" property="og:video:secure_url" />
            <meta property="og:image" content="https://cdn.example.com/i.jpg" />
        """.trimIndent()
        assertEquals("https://cdn.example.com/v/a.mp4?x=1&y=2", PlatformMediaExtractors.metaContent(html, "og:video:secure_url"))
        assertEquals("https://cdn.example.com/i.jpg", PlatformMediaExtractors.metaContent(html, "og:image"))
        assertNull(PlatformMediaExtractors.metaContent(html, "og:missing"))
    }
}
