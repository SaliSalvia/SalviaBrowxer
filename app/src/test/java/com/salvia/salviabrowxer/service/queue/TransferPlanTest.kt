package com.salvia.salviabrowxer.service.queue

import com.salvia.salviabrowxer.core.model.DashRenditionId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The decision that picks a transfer path, pinned down without a device: which row becomes a DASH
 * reuse, which becomes an HLS assembly, and when a plain file may use several connections.
 */
class TransferPlanTest {

    @Test
    fun `a row with a dash rendition id is a dash transfer`() {
        val plan = TransferPlan.of(DashRenditionId.video("v1"), "https://cdn.example/master.mpd", "application/dash+xml", 4)

        assertEquals(TransferKind.DASH, plan.kind)
        assertTrue("a dash transfer stages segments, so it can resume", plan.retryable)
        assertFalse("the segmented fetcher is already parallel", plan.parallelRange)
    }

    @Test
    fun `an hls final url is recognised by the playlist suffix`() {
        val plan = TransferPlan.of(null, "https://cdn.example/hls/index.m3u8?token=1", "application/octet-stream", 1)

        assertEquals(TransferKind.HLS, plan.kind)
        assertTrue(plan.retryable)
        assertFalse(plan.parallelRange)
    }

    @Test
    fun `an hls content type is recognised even without the suffix`() {
        val plan = TransferPlan.of(null, "https://cdn.example/stream/1280", "application/vnd.apple.mpegurl", 1)

        assertEquals(TransferKind.HLS, plan.kind)
    }

    @Test
    fun `a web app manifest is not an hls stream`() {
        val plan = TransferPlan.of(null, "https://site.example/site.webmanifest", "application/manifest+json", 4)

        assertEquals("the old signal was the substring \"manifest\", which is a PWA, not a stream", TransferKind.DIRECT, plan.kind)
    }

    @Test
    fun `a plain file uses several connections only when the setting allows it`() {
        val parallel = TransferPlan.of(null, "https://cdn.example/movie.mp4", "video/mp4", 4)
        val single = TransferPlan.of(null, "https://cdn.example/movie.mp4", "video/mp4", 1)

        assertEquals(TransferKind.DIRECT, parallel.kind)
        assertTrue(parallel.parallelRange)
        assertFalse(single.parallelRange)
        assertFalse("a plain file has nothing staged to resume from, so a retry re-downloads", parallel.retryable)
    }

    @Test
    fun `an unknown rendition id does not turn a file into a dash transfer`() {
        val plan = TransferPlan.of("garbage", "https://cdn.example/movie.mp4", "video/mp4", 1)

        assertEquals(TransferKind.DIRECT, plan.kind)
    }
}
