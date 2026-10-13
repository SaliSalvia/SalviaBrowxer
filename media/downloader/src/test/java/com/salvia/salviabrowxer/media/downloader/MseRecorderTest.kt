package com.salvia.salviabrowxer.media.downloader

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The recorder that keeps what a page handed to its MediaSource: byte-exact, bounded, and refusing the
 * one case where keeping the bytes would produce a file nobody can play.
 */
class MseRecorderTest {

    private fun dir(): File = Files.createTempDirectory("salvia-mse").toFile().also { created = it }

    private var created: File? = null

    private fun encryptedInitSegment(): ByteArray =
        byteArrayOf(0, 0, 0, 32) + "pssh".toByteArray() + ByteArray(24)

    private fun plainInitSegment(): ByteArray =
        byteArrayOf(0, 0, 0, 16) + "ftypisom".toByteArray() + byteArrayOf(0, 0, 0, 8) + "moov".toByteArray()

    @Test
    fun `segments are written in the order the page appended them`() {
        val recorder = MseRecorder(dir())

        assertTrue(recorder.start("clip", "video/mp4"))
        assertEquals(MseRecorder.Append.CONTINUED, recorder.append(plainInitSegment()))
        assertEquals(MseRecorder.Append.CONTINUED, recorder.append(byteArrayOf(1, 2, 3)))
        assertEquals(MseRecorder.Append.CONTINUED, recorder.append(byteArrayOf(4, 5)))
        val recording = recorder.finish()

        val expected = plainInitSegment() + byteArrayOf(1, 2, 3) + byteArrayOf(4, 5)
        assertArrayEquals(expected, recording!!.file.readBytes())
        assertEquals("video/mp4", recording.mimeType)
        assertEquals(expected.size.toLong(), recording.bytes)
        assertFalse(recording.capped)
    }

    @Test
    fun `a stream that declares encryption is abandoned, not kept`() {
        val recorder = MseRecorder(dir())
        recorder.start("clip", "video/mp4")

        assertEquals(MseRecorder.Append.STOPPED_ENCRYPTED, recorder.append(encryptedInitSegment()))

        assertNull("nothing may be kept from an encrypted stream", recorder.finish())
        assertEquals(0L, recorder.bytesWritten)
        assertFalse(recorder.isRecording)
    }

    @Test
    fun `encryption is looked for in the first segment only`() {
        val recorder = MseRecorder(dir())
        recorder.start("clip", "video/mp4")
        recorder.append(plainInitSegment())

        // A later fragment that happens to spell a box name is not evidence of anything.
        assertEquals(MseRecorder.Append.CONTINUED, recorder.append("pssh".toByteArray()))

        assertTrue(recorder.finish()!!.bytes > 0L)
    }

    @Test
    fun `the cap stops the recording and reports what was kept`() {
        val recorder = MseRecorder(dir(), maxBytes = 8L)
        recorder.start("clip", "video/mp4")
        recorder.append(plainInitSegment())

        assertEquals(MseRecorder.Append.STOPPED_CAP, recorder.append(ByteArray(64)))

        val recording = recorder.finish()
        assertTrue(recording!!.capped)
        assertEquals(plainInitSegment().size.toLong(), recording.bytes)
    }

    @Test
    fun `a device without room never starts, and never writes a partial file`() {
        val recorder = MseRecorder(dir(), minFreeBytes = Long.MAX_VALUE)

        assertFalse(recorder.start("clip", "video/mp4"))
        assertFalse(recorder.isRecording)
        assertNull(recorder.finish())
    }

    @Test
    fun `nothing written means nothing kept`() {
        val recorder = MseRecorder(dir())
        recorder.start("clip", "video/mp4")

        assertNull(recorder.finish())
    }

    @Test
    fun `aborting removes the file`() {
        val recorder = MseRecorder(dir())
        recorder.start("clip", "video/mp4")
        recorder.append(plainInitSegment())
        assertTrue(recorder.bytesWritten > 0L)

        recorder.abort()

        assertEquals(0L, recorder.bytesWritten)
        assertFalse(recorder.isRecording)
        assertEquals("the aborted recording must leave nothing behind", 0, created?.listFiles()?.size ?: 0)
    }

    @Test
    fun `the extension a recording claims is the container it really is`() {
        assertEquals("mp4", MseRecorder.containerExtensionFor("video/mp4"))
        assertEquals("mp4", MseRecorder.containerExtensionFor("audio/mp4; codecs=\"mp4a.40.2\""))
        assertEquals("m4s", MseRecorder.containerExtensionFor("video/iso.segment"))
        assertEquals("webm", MseRecorder.containerExtensionFor("video/webm; codecs=\"vp9\""))
        assertEquals("mp3", MseRecorder.containerExtensionFor("audio/mpeg"))
        assertEquals("ts", MseRecorder.containerExtensionFor("video/mp2t"))
        // A stream whose type we cannot name is not renamed into a lie.
        assertEquals("bin", MseRecorder.containerExtensionFor("application/octet-stream"))
    }
}
