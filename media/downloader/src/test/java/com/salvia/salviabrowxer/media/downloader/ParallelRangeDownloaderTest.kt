package com.salvia.salviabrowxer.media.downloader

import java.io.File
import java.nio.file.Files
import java.util.Collections
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Drives the multi-connection fetcher against a real HTTP server that honours `Range` the way a CDN
 * does: 206 with a `Content-Range`, one slice per request.
 *
 * What matters here is that the parts are *joined*, not merely fetched: a plan executed out of order,
 * or resumed from the wrong offset, produces a file that looks finished and is not the file at all.
 */
class ParallelRangeDownloaderTest {

    private lateinit var server: MockWebServer

    /** Every `Range` header the server was asked for, in whatever order the parts arrived. */
    private val requestedRanges = Collections.synchronizedList(mutableListOf<String>())

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun tempDir(): File = Files.createTempDirectory("salvia-parallel").toFile()

    private fun payload(size: Int): ByteArray = ByteArray(size) { index -> (index % 251).toByte() }

    private fun serveRanges(body: ByteArray) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val header = request.getHeader("Range")
                if (header == null) {
                    return MockResponse().setResponseCode(200).setBody(Buffer().write(body))
                }
                requestedRanges += header
                val bounds = header.removePrefix("bytes=").split('-')
                val start = bounds[0].toLongOrNull() ?: return MockResponse().setResponseCode(416)
                val end = bounds.getOrNull(1)?.takeIf { it.isNotBlank() }?.toLongOrNull()
                    ?: (body.size - 1L)
                if (start >= body.size || start > end) return MockResponse().setResponseCode(416)
                val slice = body.copyOfRange(start.toInt(), (minOf(end, body.size - 1L) + 1).toInt())
                return MockResponse()
                    .setResponseCode(206)
                    .setHeader("Content-Range", "bytes $start-$end/${body.size}")
                    .setHeader("Content-Type", "video/mp4")
                    .setBody(Buffer().write(slice))
            }
        }
    }

    private fun download(url: String, dir: File) = FileDownload(
        id = "test-download",
        url = url,
        directory = dir.absolutePath,
        filename = "clip.mp4"
    )

    @Test
    fun `a file is fetched over several ranges and joined byte for byte`() = runTest {
        val body = payload(4 * 1024 * 1024)
        serveRanges(body)
        val dir = tempDir()
        val plan = RangePlanner.plan(body.size.toLong(), 4)!!

        val result = ParallelRangeDownloader(OkHttpClient(), 4).download(
            download = download(server.url("/clip.mp4").toString(), dir),
            plan = plan,
            targetDir = dir,
            progressEveryMillis = 1L,
            onProgress = {},
            shouldAbort = { null }
        )

        assertArrayEquals(body, result.file.readBytes())
        assertEquals(body.size.toLong(), result.bytesWritten)
        assertEquals(body.size.toLong(), result.totalBytes)
        assertEquals("video/mp4", result.contentType)
        assertEquals("one request per part", plan.connections, requestedRanges.size)
        assertEquals(
            "the ranges the plan asked for, and nothing else",
            plan.ranges.map { "bytes=${it.first}-${it.last}" }.toSet(),
            requestedRanges.toSet()
        )
        assertTrue(
            "no part file outlives the download",
            dir.listFiles().orEmpty().none { it.name.contains(DownloadManager.PART_SUFFIX) }
        )
    }

    @Test
    fun `a part already on disk is resumed instead of fetched again`() = runTest {
        val body = payload(4 * 1024 * 1024)
        serveRanges(body)
        val dir = tempDir()
        val plan = RangePlanner.plan(body.size.toLong(), 4)!!
        val firstPart = plan.ranges.first()
        val alreadyHave = 100_000L
        File(dir, "clip.mp4.${DownloadManager.PART_SUFFIX}0").writeBytes(body.copyOf(alreadyHave.toInt()))
        val snapshots = mutableListOf<DownloadSnapshot>()

        val result = ParallelRangeDownloader(OkHttpClient(), 4).download(
            download = download(server.url("/clip.mp4").toString(), dir),
            plan = plan,
            targetDir = dir,
            progressEveryMillis = 1L,
            onProgress = { snapshots += it },
            shouldAbort = { null }
        )

        assertArrayEquals("the joined file is the file, resumed bytes included", body, result.file.readBytes())
        assertTrue(
            "the resumed part asked for the bytes it did not have",
            requestedRanges.any { it == "bytes=${firstPart.first + alreadyHave}-${firstPart.last}" }
        )
        assertTrue(
            "progress counts what was already on disk",
            snapshots.first().downloadedBytes >= alreadyHave
        )
        assertEquals(body.size.toLong(), snapshots.last().downloadedBytes)
    }

    @Test
    fun `a server that ignores the range is reported rather than joined wrongly`() = runTest {
        val body = payload(4 * 1024 * 1024)
        // Every request, ranged or not, is answered with the whole file — exactly what a server that
        // advertises ranges and then ignores them does.
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                MockResponse().setResponseCode(200).setBody(Buffer().write(body))
        }
        val dir = tempDir()
        val plan = RangePlanner.plan(body.size.toLong(), 4)!!

        val outcome = runCatching {
            ParallelRangeDownloader(OkHttpClient(), 4).download(
                download = download(server.url("/clip.mp4").toString(), dir),
                plan = plan,
                targetDir = dir,
                progressEveryMillis = 1L,
                onProgress = {},
                shouldAbort = { null }
            )
        }.exceptionOrNull()

        assertTrue("expected a range refusal, got $outcome", outcome is RangeNotHonouredException)
        // The parts stay behind for the retry the queue will run; a finished file must not appear.
        assertTrue(
            "a refusal must not leave something that looks like a finished download",
            dir.listFiles().orEmpty().none { it.name == "clip.mp4" }
        )
    }

    @Test
    fun `an abort stops every part`() = runTest {
        val body = payload(4 * 1024 * 1024)
        serveRanges(body)
        val dir = tempDir()
        val plan = RangePlanner.plan(body.size.toLong(), 4)!!
        var aborted = false

        val outcome = runCatching {
            ParallelRangeDownloader(OkHttpClient(), 4).download(
                download = download(server.url("/clip.mp4").toString(), dir),
                plan = plan,
                targetDir = dir,
                // Report on the very first read, so the abort is checked deterministically.
                progressEveryMillis = 0L,
                onProgress = { aborted = true },
                shouldAbort = { if (aborted) AbortReason.PAUSED else null }
            )
        }.exceptionOrNull()

        assertTrue("expected an abort, got $outcome", outcome is DownloadAbortedException)
        assertEquals(AbortReason.PAUSED, (outcome as DownloadAbortedException).reason)
        assertTrue(
            "the parts stay for the resume",
            dir.listFiles().orEmpty().any { it.name.contains(DownloadManager.PART_SUFFIX) }
        )
    }
}
