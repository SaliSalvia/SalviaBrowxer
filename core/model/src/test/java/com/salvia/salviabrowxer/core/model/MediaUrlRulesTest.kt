package com.salvia.salviabrowxer.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaUrlRulesTest {

    // The bug this rule exists for: `url.contains("manifest")` was the HLS test in every layer, so
    // a Progressive Web App's site.webmanifest was offered in the tray as a playlist.
    @Test
    fun `a web app manifest is not an HLS playlist`() {
        assertFalse(MediaUrlRules.isPlaylistUrl("https://example.com/site.webmanifest"))
        assertFalse(MediaUrlRules.isPlaylistUrl("https://example.com/manifest.json"))
        assertFalse(MediaUrlRules.isPlaylistUrl("https://example.com/app/manifest.webmanifest?x=1"))
        assertFalse(MediaUrlRules.isPlaylistUrl("https://example.com/sitemap.xml"))
    }

    @Test
    fun `real HLS manifests are still playlists`() {
        assertTrue(MediaUrlRules.isPlaylistUrl("https://example.com/master.m3u8"))
        assertTrue(MediaUrlRules.isPlaylistUrl("https://example.com/hls/manifest.m3u8"))
        assertTrue(MediaUrlRules.isPlaylistUrl("https://example.com/x", "application/vnd.apple.mpegurl"))
        assertTrue(MediaUrlRules.isPlaylistUrl("https://example.com/x", "application/x-mpegURL"))
        // A bare `manifest` path is a real HLS signal on many CDNs, so it is kept — the document
        // formats that also end in `manifest` are what the exclusions above remove.
        assertTrue(MediaUrlRules.isPlaylistUrl("https://cdn.example.com/hls/manifest?token=abc"))
    }

    @Test
    fun `an mpeg-ts fragment is a segment, not media`() {
        assertTrue(MediaUrlRules.isSegmentMime("video/mp2t"))
        assertFalse(MediaUrlRules.isMediaMime("video/mp2t"))
        assertTrue(MediaUrlRules.isMediaMime("video/mp4"))
        assertTrue(MediaUrlRules.isMediaMime("audio/mpeg"))
        assertTrue(MediaUrlRules.isMediaMime("application/vnd.apple.mpegurl"))
        assertFalse(MediaUrlRules.isMediaMime("application/octet-stream"))
        assertFalse(MediaUrlRules.isMediaMime(null))
    }

    @Test
    fun `a content type with parameters still compares`() {
        assertEquals("video/mp4", MediaUrlRules.normalizeMime("Video/MP4; codecs=\"avc1.42E01E\""))
        assertTrue(MediaUrlRules.isMediaMime("video/mp4; codecs=avc1"))
        assertTrue(MediaUrlRules.isDashMime("application/dash+xml; charset=utf-8"))
        assertNull(MediaUrlRules.normalizeMime("   "))
        assertNull(MediaUrlRules.normalizeMime(null))
    }

    @Test
    fun `an extension-less CDN path has no extension to read`() {
        assertNull(MediaUrlRules.pathExtension("https://scontent.cdninstagram.com/v/t50.2886-16/abc123"))
        assertNull(MediaUrlRules.pathExtension("https://v16-webapp.tiktok.com/abc/?a=1988"))
        assertNull(MediaUrlRules.pathExtension("https://example.com/media/"))
        assertNull(MediaUrlRules.pathExtension(null))
        assertEquals("mp4", MediaUrlRules.pathExtension("https://example.com/clip.mp4?v=2#t"))
    }

    @Test
    fun `fragments and non-media assets are recognisable`() {
        assertTrue(MediaUrlRules.looksLikeSegment("https://example.com/seg-1.m4s"))
        assertTrue(MediaUrlRules.looksLikeSegment("https://example.com/file?bytestart=0&byteend=1024"))
        assertFalse(MediaUrlRules.looksLikeSegment("https://example.com/clip.mp4"))
        assertTrue(MediaUrlRules.looksLikeNonMedia("https://example.com/app.js"))
        assertTrue(MediaUrlRules.looksLikeNonMedia("https://example.com/thumb.jpg"))
        assertFalse(MediaUrlRules.looksLikeNonMedia("https://example.com/clip.mp4"))
        assertFalse(MediaUrlRules.looksLikeNonMedia("https://cdn.example.com/v/t50/abc123"))
    }

    @Test
    fun `dash is recognised but is not a media extension`() {
        assertTrue(MediaUrlRules.isDashUrl("https://example.com/stream.mpd"))
        assertTrue(MediaUrlRules.isDashUrl("https://example.com/x", MediaUrlRules.DASH_MIME_TYPE))
        assertFalse(MediaUrlRules.hasMediaExtension("https://example.com/stream.mpd"))
        assertFalse(MediaUrlRules.isMediaMime(MediaUrlRules.DASH_MIME_TYPE))
    }

    @Test
    fun `the shared alternation matches every media extension`() {
        val regex = Regex("\\.(${MediaUrlRules.EXTENSION_ALTERNATION})(\\?|#|$)", RegexOption.IGNORE_CASE)
        MediaUrlRules.MEDIA_EXTENSIONS.forEach { extension ->
            assertTrue(".$extension should match", regex.containsMatchIn("https://example.com/clip.$extension"))
        }
        assertFalse(regex.containsMatchIn("https://example.com/clip.webmanifest"))
        assertFalse(regex.containsMatchIn("https://example.com/clip.mp4a"))
    }

    @Test
    fun `extensionForMime names the container`() {
        assertEquals("m3u8", MediaUrlRules.extensionForMime("application/vnd.apple.mpegurl"))
        assertEquals("mp4", MediaUrlRules.extensionForMime("video/mp4; codecs=avc1"))
        assertEquals("webm", MediaUrlRules.extensionForMime("video/webm"))
        assertEquals("m4a", MediaUrlRules.extensionForMime("audio/mp4"))
        assertEquals("mpd", MediaUrlRules.extensionForMime(MediaUrlRules.DASH_MIME_TYPE))
        assertNull(MediaUrlRules.extensionForMime("application/octet-stream"))
    }
}
