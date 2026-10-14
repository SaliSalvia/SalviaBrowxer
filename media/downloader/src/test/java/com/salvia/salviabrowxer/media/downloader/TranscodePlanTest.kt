package com.salvia.salviabrowxer.media.downloader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the app promises about conversion, file by file.
 *
 * The claim being tested is narrow on purpose: a container the platform's own demuxer can open is
 * offered a real re-encode; a container it cannot open (AVI, WMV) is refused with a reason instead of
 * being attempted and failing a minute later. "Convert anything" is not a claim this test, or this
 * app, makes.
 */
class TranscodePlanTest {

    @Test
    fun `video containers the platform opens are re-encoded to H264`() {
        for (name in listOf("clip.mp4", "clip.m4v", "clip.mov", "clip.mkv", "clip.webm", "clip.ts", "clip.m2ts", "clip.3gp")) {
            assertEquals(name, TranscodePlan.Decision.VIDEO_TO_H264_MP4, TranscodePlan.decide(name))
            assertTrue(name, TranscodePlan.isOffered(name))
        }
    }

    @Test
    fun `audio-only files become AAC in m4a`() {
        for (name in listOf("song.mp3", "song.m4a", "song.wav", "song.ogg", "song.flac", "song.opus", "song.amr")) {
            assertEquals(name, TranscodePlan.Decision.AUDIO_TO_AAC_M4A, TranscodePlan.decide(name))
        }
    }

    @Test
    fun `the containers the competitor lists but the platform cannot demux are refused with a reason`() {
        for (name in listOf("movie.avi", "movie.wmv", "movie.asf", "movie.flv", "movie.vob", "movie.mpg", "movie.mpeg")) {
            assertEquals(name, TranscodePlan.Decision.NO_DEMUXER, TranscodePlan.decide(name))
            // Offered anyway, so the user reads the reason from the app instead of guessing - and a
            // device whose vendor added a demuxer is allowed to succeed.
            assertTrue(name, TranscodePlan.isOffered(name))
        }
    }

    @Test
    fun `a file with no media container is not offered conversion at all`() {
        for (name in listOf("report.pdf", "sheet.xls", "notes.txt", "archive.zip", "photo.jpg", "page.html")) {
            assertEquals(name, TranscodePlan.Decision.NOT_APPLICABLE, TranscodePlan.decide(name))
            assertFalse(name, TranscodePlan.isOffered(name))
        }
    }

    @Test
    fun `the extension is read from a path, a query string or a name`() {
        assertEquals(TranscodePlan.Decision.VIDEO_TO_H264_MP4, TranscodePlan.decide("clip.MKV"))
        assertEquals(TranscodePlan.Decision.VIDEO_TO_H264_MP4, TranscodePlan.decide("clip.mkv?token=1"))
        assertEquals(TranscodePlan.Decision.VIDEO_TO_H264_MP4, TranscodePlan.decide("/storage/emulated/0/Download/clip.mkv"))
        assertNull(TranscodePlan.extensionOf("noextension"))
        assertNull(TranscodePlan.extensionOf("trailingdot."))
        assertNull(TranscodePlan.extensionOf(null))
    }

    @Test
    fun `the converted file gets its own name and never overwrites the original`() {
        assertEquals("clip (converted).mp4", TranscodePlan.outputNameFor("clip.mkv"))
        assertEquals("movie (converted).mp4", TranscodePlan.outputNameFor("movie.avi"))
        assertEquals("song (converted).m4a", TranscodePlan.outputNameFor("song.mp3"))
        assertEquals("clip (converted).mp4", TranscodePlan.outputNameFor("clip"))
        // The name is derived, so the original path can never be the output path.
        assertFalse(TranscodePlan.outputNameFor("clip.mp4") == "clip.mp4")
    }
}
