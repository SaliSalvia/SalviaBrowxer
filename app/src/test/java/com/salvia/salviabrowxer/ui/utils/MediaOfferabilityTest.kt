package com.salvia.salviabrowxer.ui.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaOfferabilityTest {

    @Test
    fun `only proven blob files can be offered and MSE always wins over conflicting evidence`() {
        val url = "blob:https://example.com/player"
        assertEquals(UnsupportedMedia.BLOB_STREAM, MediaOfferability.unsupportedReason(url, "video/mp4", "mp4"))
        assertEquals(UnsupportedMedia.BLOB_STREAM, MediaOfferability.unsupportedReason(url, "video/mp4", "mp4", isMediaSource = true))
        assertNull(MediaOfferability.unsupportedReason(url, "video/mp4", "mp4", isBlobFile = true))
        assertEquals(UnsupportedMedia.BLOB_STREAM,
            MediaOfferability.unsupportedReason(url, "video/mp4", "mp4", isMediaSource = true, isBlobFile = true))
    }

    @Test
    fun `a plain mp4 is a video that can be offered`() {
        assertEquals(MediaKind.VIDEO, MediaOfferability.kindOf("video/mp4", "mp4"))
        assertNull(MediaOfferability.unsupportedReason("https://example.com/a.mp4", "video/mp4", "mp4"))
    }

    @Test
    fun `audio and playlists are classified from mime or extension`() {
        assertEquals(MediaKind.AUDIO, MediaOfferability.kindOf("audio/mpeg", "mp3"))
        assertEquals(MediaKind.AUDIO, MediaOfferability.kindOf(null, "m4a"))
        assertEquals(MediaKind.PLAYLIST, MediaOfferability.kindOf("application/vnd.apple.mpegurl", "m3u8"))
        assertEquals(MediaKind.PLAYLIST, MediaOfferability.kindOf(null, "ts"))
    }

    @Test
    fun `an unknown file is not given a kind`() {
        assertNull(MediaOfferability.kindOf(null, null))
        assertNull(MediaOfferability.kindOf("application/octet-stream", "bin"))
    }

    @Test
    fun `mpeg-dash is refused by extension, by mime and by url`() {
        assertEquals(UnsupportedMedia.DASH, MediaOfferability.unsupportedReason("https://example.com/stream", null, "mpd"))
        assertEquals(UnsupportedMedia.DASH, MediaOfferability.unsupportedReason("https://example.com/stream", "application/dash+xml", null))
        assertEquals(UnsupportedMedia.DASH, MediaOfferability.unsupportedReason("https://example.com/manifest.mpd?token=1", null, null))
        assertTrue(MediaOfferability.isDash("https://example.com/a.mpd#frag", null, null))
        assertFalse(MediaOfferability.isDash("https://example.com/a.mp4", "video/mp4", "mp4"))
    }

    @Test
    fun `a live playlist is refused even when it looks entirely normal`() {
        assertEquals(UnsupportedMedia.LIVE, MediaOfferability.unsupportedReason("https://example.com/live.m3u8", "application/vnd.apple.mpegurl", "m3u8", isLive = true))
    }

    @Test
    fun `dash wins over live so the message names the real problem`() {
        assertEquals(UnsupportedMedia.DASH, MediaOfferability.unsupportedReason("https://example.com/live.mpd", "application/dash+xml", "mpd", isLive = true))
    }
}
