package com.salvia.salviabrowxer.core.model

import com.salvia.salviabrowxer.core.model.MediaCandidate.MediaSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class MediaSniffAdmissionTest {

    private val page = "https://www.example.com/post/1"

    // The whole reason the sniffer was rewritten: a URL that says nothing about being media, on a
    // CDN path with no extension, is admitted because the server named the type.
    @Test
    fun `an extension-less CDN video is admitted from the response header`() {
        val candidate = MediaSniffAdmission.admit(
            pageUrl = page,
            url = "https://scontent.cdninstagram.com/v/t50.2886-16/abc123",
            mimeType = "video/mp4",
            origin = SniffOrigin.XHR_RESPONSE
        )
        assertNotNull(candidate)
        assertEquals("mp4", candidate!!.extension)
        assertEquals("video/mp4", candidate.mimeType)
        assertEquals(MediaSource.JS, candidate.source)
    }

    @Test
    fun `a URL with nothing but a plausible shape is refused`() {
        assertNull(
            MediaSniffAdmission.admit(page, "https://api.example.com/v2/feed?limit=20", null, SniffOrigin.XHR_RESPONSE)
        )
        assertNull(
            MediaSniffAdmission.admit(page, "https://cdn.example.com/v/t50/abc123", null, SniffOrigin.FETCH_RESPONSE)
        )
    }

    @Test
    fun `octet-stream needs an extension to count`() {
        assertNotNull(
            MediaSniffAdmission.admit(
                page, "https://cdn.example.com/clip.mp4", "application/octet-stream", SniffOrigin.FETCH_RESPONSE
            )
        )
        assertNull(
            MediaSniffAdmission.admit(
                page, "https://cdn.example.com/v/t50/abc123", "application/octet-stream", SniffOrigin.FETCH_RESPONSE
            )
        )
    }

    @Test
    fun `HLS fragments are never candidates`() {
        // An MPEG-TS segment: a video mime that must still not count as media.
        assertNull(MediaSniffAdmission.admit(page, "https://cdn.example.com/hls/seg-1.ts", "video/mp2t", SniffOrigin.XHR_RESPONSE))
        // An MSE/DASH fragment, recognisable by extension.
        assertNull(MediaSniffAdmission.admit(page, "https://cdn.example.com/dash/seg-1.m4s", "video/mp4", SniffOrigin.FETCH_RESPONSE))
        // A partial read is a player pulling pieces of something it already has.
        assertNull(
            MediaSniffAdmission.admit(
                page, "https://cdn.example.com/v/t50/abc123", "video/mp4", SniffOrigin.FETCH_RESPONSE, partialRange = true
            )
        )
    }

    @Test
    fun `a whole-file range is not a partial read`() {
        // `Range: bytes=0-` is how plenty of players fetch a complete progressive MP4. The script
        // only marks a read partial when it does not start at zero or has a bounded end, so this
        // must be admitted rather than mistaken for a fragment.
        assertNotNull(
            MediaSniffAdmission.admit(
                page, "https://cdn.example.com/v/t50/abc123", "video/mp4", SniffOrigin.FETCH_RESPONSE, partialRange = false
            )
        )
    }

    @Test
    fun `a player that loaded the URL admits it without a header or an extension`() {
        val candidate = MediaSniffAdmission.admit(
            pageUrl = page,
            url = "https://cdn.example.com/playback/9f8e7d",
            mimeType = null,
            origin = SniffOrigin.ELEMENT_METADATA,
            elementKind = "video"
        )
        assertNotNull(candidate)
        assertEquals("mp4", candidate!!.extension)
        assertEquals("video/mp4", candidate.mimeType)
    }

    @Test
    fun `an audio element lands as audio`() {
        val candidate = MediaSniffAdmission.admit(
            pageUrl = page,
            url = "https://cdn.example.com/playback/9f8e7d",
            mimeType = null,
            origin = SniffOrigin.ELEMENT_METADATA,
            elementKind = "audio"
        )
        assertNotNull(candidate)
        assertEquals("m4a", candidate!!.extension)
        assertEquals("audio/mp4", candidate.mimeType)
    }

    @Test
    fun `markup alone is not evidence`() {
        assertNull(
            MediaSniffAdmission.admit(
                page, "https://cdn.example.com/playback/9f8e7d", null, SniffOrigin.ELEMENT_SOURCE, elementKind = "video"
            )
        )
        assertNotNull(MediaSniffAdmission.admit(page, "https://cdn.example.com/clip.webm", null, SniffOrigin.ELEMENT_SOURCE))
    }

    @Test
    fun `a blob is admitted on the type the media source declared`() {
        val blob = "blob:https://www.example.com/8f7e6d5c"
        assertNotNull(MediaSniffAdmission.admit(page, blob, "video/mp4; codecs=avc1", SniffOrigin.ELEMENT_METADATA))
        assertNull(MediaSniffAdmission.admit(page, blob, null, SniffOrigin.ELEMENT_METADATA))
        assertNull(MediaSniffAdmission.admit(page, blob, "video/mp2t", SniffOrigin.ELEMENT_METADATA))
    }

    @Test
    fun `an unknown origin is refused`() {
        assertNull(MediaSniffAdmission.admit(page, "https://cdn.example.com/clip.mp4", "video/mp4", null))
        assertNull(MediaSniffAdmission.admit(page, "https://cdn.example.com/clip.mp4", "video/mp4", SniffOrigin.fromWire("mystery")))
    }

    @Test
    fun `a web app manifest is not a playlist`() {
        assertNull(
            MediaSniffAdmission.admit(
                page, "https://www.example.com/site.webmanifest", "application/manifest+json", SniffOrigin.FETCH_RESPONSE
            )
        )
    }

    @Test
    fun `an HLS playlist is admitted as one`() {
        val candidate = MediaSniffAdmission.admit(page, "https://cdn.example.com/hls/master.m3u8", null, SniffOrigin.XHR_RESPONSE)
        assertNotNull(candidate)
        assertEquals("m3u8", candidate!!.extension)
        assertEquals("application/vnd.apple.mpegurl", candidate.mimeType)
    }

    @Test
    fun `wire names are the contract with the injected script`() {
        assertEquals("xhr", SniffOrigin.fromWire("xhr")?.wireName)
        assertEquals("fetch", SniffOrigin.fromWire("FETCH")?.wireName)
        assertEquals("element-metadata", SniffOrigin.fromWire("element-metadata")?.wireName)
        assertEquals("element-source", SniffOrigin.fromWire("element-source")?.wireName)
        assertNull(SniffOrigin.fromWire(null))
    }
}
