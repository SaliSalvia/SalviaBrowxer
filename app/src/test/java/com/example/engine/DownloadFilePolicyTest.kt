package com.example.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException

class DownloadFilePolicyTest {

    @Test
    fun `part count adapts to file size and is deterministic`() {
        assertEquals(1, DownloadFilePolicy.partCountFor(-1L))
        assertEquals(1, DownloadFilePolicy.partCountFor(1_000_000L))
        assertEquals(4, DownloadFilePolicy.partCountFor(10L * 1024 * 1024))
        assertEquals(6, DownloadFilePolicy.partCountFor(32L * 1024 * 1024))
        assertEquals(DownloadFilePolicy.MAX_PARTS, DownloadFilePolicy.partCountFor(2L * 1024 * 1024 * 1024))
        assertEquals(DownloadFilePolicy.partCountFor(50_000_000L), DownloadFilePolicy.partCountFor(50_000_000L))
    }

    @Test
    fun `part ranges cover every byte exactly once`() {
        val ranges = DownloadFilePolicy.partRanges(10L, 4)
        assertEquals(listOf(0L..1L, 2L..3L, 4L..5L, 6L..9L), ranges)
        val big = DownloadFilePolicy.partRanges(123_456_789L, 8)
        assertEquals(8, big.size)
        assertEquals(0L, big.first().first)
        assertEquals(123_456_788L, big.last().last)
        big.zipWithNext().forEach { (a, b) -> assertEquals(a.last + 1L, b.first) }
        assertEquals(123_456_789L, big.sumOf { it.last - it.first + 1L })
        // Never more parts than bytes.
        assertEquals(listOf(0L..0L, 1L..1L, 2L..2L), DownloadFilePolicy.partRanges(3L, 8))
    }

    @Test
    fun `layout marker distinguishes splits`() {
        assertEquals("v2:100:4", DownloadFilePolicy.layoutMarker(100L, 4))
        assertFalse(DownloadFilePolicy.layoutMarker(100L, 4) == DownloadFilePolicy.layoutMarker(100L, 8))
    }

    @Test
    fun `retry delay backs off exponentially with a ceiling and bounded jitter`() {
        assertEquals(600L, DownloadFilePolicy.retryDelayMs(0))
        assertEquals(1_200L, DownloadFilePolicy.retryDelayMs(1))
        assertEquals(4_800L, DownloadFilePolicy.retryDelayMs(3))
        assertEquals(8_000L, DownloadFilePolicy.retryDelayMs(4))
        assertEquals(8_000L, DownloadFilePolicy.retryDelayMs(20))
        assertEquals(1_000L, DownloadFilePolicy.retryDelayMs(0, jitterMs = 10_000L))
    }

    @Test
    fun `only transient failures are retried`() {
        assertTrue(DownloadFilePolicy.isRetryable(HttpStatusException(503)))
        assertTrue(DownloadFilePolicy.isRetryable(HttpStatusException(429)))
        assertTrue(DownloadFilePolicy.isRetryable(HttpStatusException(408)))
        assertFalse(DownloadFilePolicy.isRetryable(HttpStatusException(404)))
        assertFalse(DownloadFilePolicy.isRetryable(HttpStatusException(403)))
        assertTrue(DownloadFilePolicy.isRetryable(SocketTimeoutException("slow")))
        assertTrue(DownloadFilePolicy.isRetryable(IOException("reset")))
        assertFalse(DownloadFilePolicy.isRetryable(NonRetryableDownloadException("bad range")))
        assertFalse(DownloadFilePolicy.isRetryable(InsufficientStorageException(10L, 1L)))
        assertFalse(DownloadFilePolicy.isRetryable(IllegalStateException("bug")))
    }

    @Test
    fun `storage budget covers staging plus the published copy`() {
        val mb = 1024L * 1024L
        assertEquals(200 * mb + DownloadFilePolicy.STORAGE_SAFETY_MARGIN_BYTES, DownloadFilePolicy.requiredBytesFor(100 * mb))
        assertEquals(100 * mb + DownloadFilePolicy.STORAGE_SAFETY_MARGIN_BYTES, DownloadFilePolicy.requiredBytesFor(100 * mb, 100 * mb))
        assertFalse(DownloadFilePolicy.hasEnoughSpace(200 * mb, 100 * mb))
        assertTrue(DownloadFilePolicy.hasEnoughSpace(200 * mb, 100 * mb, alreadyStaged = 100 * mb))
        assertTrue(DownloadFilePolicy.hasEnoughSpace(-1L, 100 * mb)) // unknown free space
        assertTrue(DownloadFilePolicy.hasEnoughSpace(64 * mb, -1L)) // unknown size: margin only
    }

    @Test
    fun `file names keep dotted titles, known extensions and unicode`() {
        assertEquals("Mr. Bean.mp4", DownloadFilePolicy.sanitizeFileName("Mr. Bean", "mp4"))
        assertEquals("clip.mp4", DownloadFilePolicy.sanitizeFileName("clip.mp4", "mp4"))
        assertEquals("song.m4a", DownloadFilePolicy.sanitizeFileName("song.M4A", "mp4"))
        assertEquals("track.opus", DownloadFilePolicy.sanitizeFileName("track", "opus"))
        assertEquals("ویدیو تست.mp4", DownloadFilePolicy.sanitizeFileName("ویدیو تست", "mp4"))
        assertEquals("a_b_c_d.mp4", DownloadFilePolicy.sanitizeFileName("a/b:c*d?.mp4", "mp4"))
        assertEquals("fb.mp4", DownloadFilePolicy.sanitizeFileName("   ", "mp4", fallbackBase = "fb"))
        assertEquals("fb.mp4", DownloadFilePolicy.sanitizeFileName("...", "mp4", fallbackBase = "fb"))
        val long = DownloadFilePolicy.sanitizeFileName("a".repeat(300), "mp4")
        assertEquals(80 + ".mp4".length, long.length)
        assertFalse(DownloadFilePolicy.sanitizeFileName("x\u0000y\n.mp4", "mp4").contains('\u0000'))
    }

    @Test
    fun `extension replacement and unique names`() {
        assertEquals("video.ts", DownloadFilePolicy.withExtension("video.mp4", "ts"))
        assertEquals("noext.ts", DownloadFilePolicy.withExtension("noext", "ts"))
        val taken = setOf("a.mp4", "a (1).mp4")
        assertEquals("b.mp4", DownloadFilePolicy.uniqueFileName("b.mp4") { it in taken })
        assertEquals("a (2).mp4", DownloadFilePolicy.uniqueFileName("a.mp4") { it in taken })
        assertEquals("README (1)", DownloadFilePolicy.uniqueFileName("README") { it == "README" })
    }

    @Test
    fun `speed and output mime type`() {
        assertEquals(2_000L, DownloadFilePolicy.speedBytesPerSecond(1_000L, 500L))
        assertEquals(0L, DownloadFilePolicy.speedBytesPerSecond(1_000L, 0L))
        assertEquals(0L, DownloadFilePolicy.speedBytesPerSecond(-5L, 100L))

        assertEquals("video/mp4", DownloadFilePolicy.outputMimeType("application/x-mpegurl", "x.mp4"))
        assertEquals("video/mp4", DownloadFilePolicy.outputMimeType("application/dash+xml", "x"))
        assertEquals("audio/mp4", DownloadFilePolicy.outputMimeType("", "a.m4a"))
        assertEquals("video/mp2t", DownloadFilePolicy.outputMimeType("video/mp4", "a.ts"))
        assertEquals("audio/webm", DownloadFilePolicy.outputMimeType("audio/webm", "a.webm"))
        assertEquals("application/pdf", DownloadFilePolicy.outputMimeType("application/pdf", "a.pdf"))
        assertEquals("*/*", DownloadFilePolicy.outputMimeType("", "a.bin"))
    }
}
