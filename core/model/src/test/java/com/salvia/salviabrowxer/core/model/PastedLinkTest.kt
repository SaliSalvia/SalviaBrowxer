package com.salvia.salviabrowxer.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PastedLinkTest {

    @Test
    fun `social hosts are pages even when their paths name a container`() {
        for (host in listOf("instagram.com", "tiktok.com", "youtube.com", "youtu.be", "facebook.com", "x.com")) {
            for (prefix in listOf("", "www.", "m.")) {
                val url = "https://$prefix$host/clip.mp4"
                assertEquals(url, PastedLinkKind.WEB_PAGE, PastedLink.classify(url))
                assertNull(url, PastedLink.mediaCandidate(url))
                assertTrue(url, !PastedLink.isMediaFile(url))
            }
        }
        assertEquals(PastedLinkKind.WEB_PAGE, PastedLink.classify("https://YouTube.COM./clip.mp4"))
    }

    @Test
    fun `social names in an unrelated host path or query do not block real files`() {
        for (url in listOf(
            "https://notyoutube.com/clip.mp4",
            "https://youtube.com.example.org/clip.mp4",
            "https://cdn.example.org/youtube.com/clip.mp4?source=instagram.com"
        )) assertEquals(url, PastedLinkKind.MEDIA_FILE, PastedLink.classify(url))
    }

    @Test
    fun `malformed and credential bearing links do not become candidates`() {
        for (url in listOf(
            "https:clip.mp4", "https:///clip.mp4", "https://bad host/clip.mp4",
            "https://youtube.com@cdn.example.org/clip.mp4", "https://example.org:99999/clip.mp4"
        )) {
            assertNull(url, PastedLink.classify(url))
            assertNull(url, PastedLink.mediaCandidate(url))
            assertTrue(url, !PastedLink.isMediaFile(url))
        }
    }

    @Test
    fun `only web links are classified`() {
        assertNull(PastedLink.classify(""))
        assertNull(PastedLink.classify("   "))
        // A scheme on its own is not a link.
        assertNull(PastedLink.classify("https://"))
        assertNull(PastedLink.classify("mailto:someone@example.com"))
        assertNull(PastedLink.classify("ftp://example.com/clip.mp4"))
        assertNull(PastedLink.classify("just some words"))
        assertNull(PastedLink.classify("file:///sdcard/clip.mp4"))
        assertNull(PastedLink.classify("blob:https://example.com/8f7e6d5c"))
    }

    @Test
    fun `a link that names its own container is a media file`() {
        assertEquals(PastedLinkKind.MEDIA_FILE, PastedLink.classify("https://cdn.example.com/clip.mp4"))
        assertEquals(PastedLinkKind.MEDIA_FILE, PastedLink.classify("http://cdn.example.com/clip.webm"))
        assertEquals(PastedLinkKind.MEDIA_FILE, PastedLink.classify("https://cdn.example.com/song.mp3"))
        assertEquals(PastedLinkKind.MEDIA_FILE, PastedLink.classify("https://cdn.example.com/hls/master.m3u8"))
        assertEquals(PastedLinkKind.MEDIA_FILE, PastedLink.classify("https://cdn.example.com/stream.mpd"))
        // A playlist is allowed to have no extension at all; the path segment is the signal.
        assertEquals(PastedLinkKind.MEDIA_FILE, PastedLink.classify("https://cdn.example.com/hls/manifest?token=abc"))
    }

    @Test
    fun `anything else is a page that has to be opened first`() {
        assertEquals(PastedLinkKind.WEB_PAGE, PastedLink.classify("https://www.example.com/post/1"))
        assertEquals(PastedLinkKind.WEB_PAGE, PastedLink.classify("https://www.example.com/"))
        assertEquals(PastedLinkKind.WEB_PAGE, PastedLink.classify("https://www.example.com/report.pdf"))
    }

    @Test
    fun `a pasted media file becomes a candidate the quality sheet can open`() {
        val candidate = PastedLink.mediaCandidate("https://cdn.example.com/clip.mp4")
        assertNotNull(candidate)
        assertEquals("https://cdn.example.com/clip.mp4", candidate!!.mediaUrl)
        assertEquals("mp4", candidate.extension)
        assertEquals("video/mp4", candidate.mimeType)
        assertEquals("clip", candidate.title)
    }

    @Test
    fun `the container is read from the link`() {
        assertEquals("audio/mp3", PastedLink.mediaCandidate("https://cdn.example.com/song.mp3")?.mimeType)
        assertEquals(
            "application/vnd.apple.mpegurl",
            PastedLink.mediaCandidate("https://cdn.example.com/hls/master.m3u8")?.mimeType
        )
        assertEquals("m3u8", PastedLink.mediaCandidate("https://cdn.example.com/hls/manifest")?.extension)
        // DASH is offered too, so the sheet can explain why it cannot be segmented rather than the
        // pasted link silently doing nothing.
        assertEquals(
            MediaUrlRules.DASH_MIME_TYPE,
            PastedLink.mediaCandidate("https://cdn.example.com/stream.mpd")?.mimeType
        )
    }

    @Test
    fun `a page or a non-web link has no candidate`() {
        assertNull(PastedLink.mediaCandidate("https://www.example.com/post/1"))
        assertNull(PastedLink.mediaCandidate("mailto:someone@example.com"))
        assertNull(PastedLink.mediaCandidate(""))
    }

    @Test
    fun `isMediaFile agrees with classify`() {
        assertTrue(PastedLink.isMediaFile("https://cdn.example.com/clip.mp4"))
        assertTrue(!PastedLink.isMediaFile("https://www.example.com/post/1"))
    }
}
