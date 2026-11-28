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

    @Test
    fun `platform hosts are matched on label boundaries only`() {
        assertNull(PlatformMediaExtractors.matchXStatus("https://box.com/a/status/123"))
        assertNull(PlatformMediaExtractors.matchXStatus("https://notx.com/a/status/123"))
        assertEquals("123", PlatformMediaExtractors.matchXStatus("https://mobile.x.com/a/status/123"))
        assertNull(PlatformMediaExtractors.matchTikTokVideo("https://eviltiktok.com/@a/video/1"))
        assertNull(PlatformMediaExtractors.matchInstagramPost("https://fakeinstagram.com/p/CxAbCdEf/"))
        assertFalse(PlatformMediaExtractors.isSupported("https://box.com/a/status/123"))
    }

    // --- Aparat / Dailymotion / Reddit ---

    @Test
    fun `aparat dailymotion and reddit urls are matched with exact hosts`() {
        assertEquals("abc123", PlatformMediaExtractors.matchAparatVideo("https://www.aparat.com/v/abc123"))
        assertEquals(
            "abc123",
            PlatformMediaExtractors.matchAparatVideo("https://www.aparat.com/video/video/embed/videohash/abc123/vt/frame"),
        )
        assertNull(PlatformMediaExtractors.matchAparatVideo("https://www.aparat.com/explore"))
        assertNull(PlatformMediaExtractors.matchAparatVideo("https://notaparat.com/v/abc123"))

        assertEquals("x8abcd1", PlatformMediaExtractors.matchDailymotionVideo("https://www.dailymotion.com/video/x8abcd1"))
        assertEquals("x8abcd1", PlatformMediaExtractors.matchDailymotionVideo("https://www.dailymotion.com/embed/video/x8abcd1"))
        assertEquals("x8abcd1", PlatformMediaExtractors.matchDailymotionVideo("https://dai.ly/x8abcd1"))
        assertEquals(
            "x8abcd1",
            PlatformMediaExtractors.matchDailymotionVideo("https://geo.dailymotion.com/player/xabc.html?video=x8abcd1"),
        )
        assertNull(PlatformMediaExtractors.matchDailymotionVideo("https://www.dailymotion.com/user/someone"))
        assertNull(PlatformMediaExtractors.matchDailymotionVideo("https://dailymotion.com.evil.example/video/x8abcd1"))

        assertEquals(
            "abc123",
            PlatformMediaExtractors.matchRedditPost("https://www.reddit.com/r/videos/comments/abc123/some_title/"),
        )
        assertEquals("abc123", PlatformMediaExtractors.matchRedditPost("https://old.reddit.com/comments/abc123"))
        assertNull(PlatformMediaExtractors.matchRedditPost("https://www.reddit.com/r/videos/"))
        assertNull(PlatformMediaExtractors.matchRedditPost("https://reddit.com.evil.example/r/a/comments/abc123/"))

        assertTrue(PlatformMediaExtractors.isSupported("https://www.aparat.com/v/abc123"))
        assertTrue(PlatformMediaExtractors.isSupported("https://dai.ly/x8abcd1"))
        assertTrue(PlatformMediaExtractors.isSupported("https://www.reddit.com/r/videos/comments/abc123/t/"))
    }

    @Test
    fun `reddit json url keeps known hosts and drops tracking query`() {
        assertEquals(
            "https://www.reddit.com/r/videos/comments/abc123/some_title/.json?raw_json=1",
            PlatformMediaExtractors.redditJsonUrl("https://www.reddit.com/r/videos/comments/abc123/some_title/?utm_source=x"),
        )
        assertEquals(
            "https://old.reddit.com/r/videos/comments/abc123/.json?raw_json=1",
            PlatformMediaExtractors.redditJsonUrl("https://old.reddit.com/r/videos/comments/abc123/"),
        )
        assertEquals(
            "https://www.reddit.com/r/videos/comments/abc123/.json?raw_json=1",
            PlatformMediaExtractors.redditJsonUrl("https://m.reddit.com/r/videos/comments/abc123"),
        )
        assertNull(PlatformMediaExtractors.redditJsonUrl("https://www.reddit.com/r/videos/"))
    }

    @Test
    fun `dailymotion metadata yields progressive qualities and the hls master`() {
        val json = """
            {"title":"Demo clip","duration":125,
             "thumbnails":{"240":"https://s1.dmcdn.net/t/240.jpg","720":"https://s1.dmcdn.net/t/720.jpg"},
             "qualities":{
               "auto":[{"type":"application/x-mpegURL","url":"https://www.dailymotion.com/cdn/manifest/video/x8abcd1.m3u8?sec=abc#cell=core"}],
               "720":[{"type":"video/mp4","url":"https://proxy.dmcdn.net/H264-1280x720/video.mp4#cell=core"}],
               "380":[{"type":"application/vnd.lumberjack.manifest","url":"https://lj.dmcdn.net/manifest"}]
             }}
        """.trimIndent()
        val extraction = PlatformMediaExtractors.parseDailymotionMetadata(json)
        assertNotNull(extraction)
        extraction!!
        assertEquals("Demo clip", extraction.title)
        assertEquals("https://proxy.dmcdn.net/H264-1280x720/video.mp4", extraction.primaryUrl)
        assertEquals("https://www.dailymotion.com/cdn/manifest/video/x8abcd1.m3u8?sec=abc", extraction.hlsManifestUrl)
        assertEquals(720, extraction.height)
        assertEquals(1280, extraction.width)
        assertEquals("https://s1.dmcdn.net/t/720.jpg", extraction.thumbnailUrl)
        assertEquals(125.0, extraction.durationSeconds, 0.001)
        assertEquals(2, extraction.qualities.size)
        assertFalse(extraction.qualities.first().isHls)
        assertTrue(extraction.qualities.any { it.isHls })
    }

    @Test
    fun `dailymotion error objects fail closed`() {
        assertNull(PlatformMediaExtractors.parseDailymotionMetadata("""{"error":{"title":"Geo blocked","raw_message":"DM007"}}"""))
        assertNull(PlatformMediaExtractors.parseDailymotionMetadata("not json"))
        assertNull(PlatformMediaExtractors.parseDailymotionMetadata("""{"title":"x","qualities":{}}"""))
    }

    @Test
    fun `reddit post prefers hls with audio and labels the silent fallback`() {
        val json = """
            [{"data":{"children":[{"data":{
                "title":"Cool &amp; clip",
                "preview":{"images":[{"source":{"url":"https://preview.redd.it/a.jpg?width=640&amp;s=abc"}}]},
                "secure_media":{"reddit_video":{
                    "hls_url":"https://v.redd.it/abc/HLSPlaylist.m3u8?a=1&amp;v=1",
                    "dash_url":"https://v.redd.it/abc/DASHPlaylist.mpd?a=1",
                    "fallback_url":"https://v.redd.it/abc/DASH_720.mp4?source=fallback",
                    "height":720,"width":1280,"bitrate_kbps":2400,"duration":31,"is_gif":false}}
            }}]}},{"data":{"children":[]}}]
        """.trimIndent()
        val extraction = PlatformMediaExtractors.parseRedditPost(json)
        assertNotNull(extraction)
        extraction!!
        assertEquals("Cool & clip", extraction.title)
        assertEquals("https://v.redd.it/abc/HLSPlaylist.m3u8?a=1&v=1", extraction.hlsManifestUrl)
        assertEquals("https://v.redd.it/abc/DASHPlaylist.mpd?a=1", extraction.dashManifestUrl)
        assertEquals(extraction.hlsManifestUrl, extraction.primaryUrl)
        assertEquals(2, extraction.qualities.size)
        assertTrue(extraction.qualities[0].isHls)
        assertTrue(extraction.qualities[1].label.contains("no audio"))
        assertEquals(2_400_000L, extraction.qualities[1].bandwidthBps)
        assertEquals("https://preview.redd.it/a.jpg?width=640&s=abc", extraction.thumbnailUrl)
    }

    @Test
    fun `reddit crossposts are followed and posts without video return null`() {
        val crosspost = """
            [{"data":{"children":[{"data":{"title":"Shared","crosspost_parent_list":[
                {"media":{"reddit_video":{"fallback_url":"https://v.redd.it/xyz/DASH_480.mp4","height":480,"width":854}}}
            ]}}]}}]
        """.trimIndent()
        val extraction = PlatformMediaExtractors.parseRedditPost(crosspost)
        assertNotNull(extraction)
        assertEquals("https://v.redd.it/xyz/DASH_480.mp4", extraction!!.primaryUrl)
        assertEquals(480, extraction.height)

        assertNull(PlatformMediaExtractors.parseRedditPost("""[{"data":{"children":[{"data":{"title":"text post"}}]}}]"""))
        assertNull(PlatformMediaExtractors.parseRedditPost("{}"))
    }

    @Test
    fun `aparat player options yield progressive qualities and the hls master`() {
        val html = """
            <html><head><meta property="og:title" content="ویدیوی آزمایشی"></head><body><script>
            var player = null;
            var options = {"multiSRC":[[{"src":"https://hls.aparat.cam/x/master.m3u8","type":"application/vnd.apple.mpegurl","label":"auto"}],
              [{"src":"https://cdn.aparat.cam/x-720p.mp4","type":"video/mp4","label":"720p"},
               {"src":"https://cdn.aparat.cam/x-360p.mp4","type":"video/mp4","label":"360p"}]],
              "poster":"https://static.cdn.asset.aparat.cloud/p.jpg","duration":95};
            </script></body></html>
        """.trimIndent()
        val extraction = PlatformMediaExtractors.parseAparatPage(html)
        assertNotNull(extraction)
        extraction!!
        assertEquals("https://cdn.aparat.cam/x-720p.mp4", extraction.primaryUrl)
        assertEquals("https://hls.aparat.cam/x/master.m3u8", extraction.hlsManifestUrl)
        assertEquals(720, extraction.height)
        assertEquals(87, extraction.confidenceScore)
        assertEquals("ویدیوی آزمایشی", extraction.title)
        assertEquals("https://static.cdn.asset.aparat.cloud/p.jpg", extraction.thumbnailUrl)
        assertEquals(3, extraction.qualities.size)
        assertFalse(extraction.qualities.first().isHls)
        assertEquals(720, extraction.qualities.first().resolutionHeight)
    }

    @Test
    fun `aparat page without player options falls back to the embed page`() = runTest {
        val embed = """<script>options = {"multiSRC":[[{"src":"https://cdn.aparat.cam/y-480p.mp4","type":"video/mp4","label":"480p"}]]};</script>"""
        val extraction = PlatformMediaExtractors.extract("https://www.aparat.com/v/abc123") { url ->
            if (url.contains("/embed/")) embed else "<html><head><meta property=\"og:title\" content=\"Watch\"></head></html>"
        }
        assertNotNull(extraction)
        assertEquals("https://cdn.aparat.cam/y-480p.mp4", extraction!!.primaryUrl)
        assertEquals("Watch", extraction.title)
    }

    @Test
    fun `js object extraction is string aware and rejects unbalanced input`() {
        val html = "var a = {\"x\":\"}{\",\"y\":{\"z\":1}}; var b = 2;"
        assertEquals(
            "{\"x\":\"}{\",\"y\":{\"z\":1}}",
            PlatformMediaExtractors.extractJsObjectAfter(html, Regex("a\\s*=\\s*")),
        )
        assertNull(PlatformMediaExtractors.extractJsObjectAfter("var a = {\"x\": 1", Regex("a\\s*=\\s*")))
        assertNull(PlatformMediaExtractors.extractJsObjectAfter("nothing here", Regex("a\\s*=\\s*")))
    }
}
