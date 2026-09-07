package com.salvia.salviabrowxer.core.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaFileTypesTest {

    @Test
    fun `ts segments are not media candidates - only playlists are`() {
        assertFalse(MediaFileTypes.isMediaUrl("https://cdn.example.com/stream/segment001.ts"))
        assertTrue(MediaFileTypes.isMediaUrl("https://cdn.example.com/stream/playlist.m3u8"))
        assertTrue(MediaFileTypes.isMediaUrl("https://cdn.example.com/stream/manifest.mpd"))
    }

    @Test
    fun `video and audio extensions are recognized`() {
        assertTrue(MediaFileTypes.isMediaUrl("https://example.com/movie.mp4"))
        assertTrue(MediaFileTypes.isMediaUrl("https://example.com/song.flac"))
    }

    @Test
    fun `unknown query parameter does not create a false extension`() {
        assertNull(MediaFileTypes.extensionFromUrl("https://example.com/stream?file=video.mp4"))
    }

    @Test
    fun `mime type matching is case-insensitive and handles charsets`() {
        assertTrue(MediaFileTypes.isKnownMimeType("video/mp4"))
        assertFalse(MediaFileTypes.isKnownMimeType("text/html"))
    }
}
