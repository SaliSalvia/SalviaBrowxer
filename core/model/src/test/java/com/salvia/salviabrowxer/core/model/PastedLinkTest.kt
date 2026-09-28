package com.salvia.salviabrowxer.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PastedLinkTest {

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
