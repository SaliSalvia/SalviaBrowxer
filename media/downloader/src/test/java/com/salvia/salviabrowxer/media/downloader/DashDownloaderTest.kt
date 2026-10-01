package com.salvia.salviabrowxer.media.downloader

import com.salvia.salviabrowxer.media.resolver.DashManifestParser
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Drives the real DASH downloader against a real HTTP server.
 *
 * This is the executable half of the DASH feature: the manifest is fetched, every segment is
 * requested, the initialisation segment is written first, and the bytes on disk are exactly what the
 * server served. Responses are routed by path because the downloader fetches segments in parallel —
 * an enqueue-order server would hand each response to whichever request arrived first. A device is
 * still required for the container rewrite (`MediaRemuxer`), which needs the platform codecs.
 */
class DashDownloaderTest {

    private lateinit var server: MockWebServer
    private lateinit var downloader: DashDownloader

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        downloader = DashDownloader(OkHttpClient())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun tempDir(): File = Files.createTempDirectory("salvia-dash").toFile()

    private fun manifestPath() = "/vod/stream.mpd"

    private fun manifestBody(mediaTemplate: String = "seg-\$Number\$.m4s", init: String = "init.mp4"): String = """
        <MPD type="static" mediaPresentationDuration="PT3S">
          <Period>
            <AdaptationSet mimeType="video/mp4">
              <SegmentTemplate media="$mediaTemplate" initialization="$init" timescale="1" duration="1" startNumber="1"/>
              <Representation id="v1" bandwidth="500000" width="640" height="360"/>
            </AdaptationSet>
          </Period>
        </MPD>
    """.trimIndent()

    private fun body(bytes: ByteArray): MockResponse = MockResponse().setBody(Buffer().write(bytes))

    private fun serve(handler: (String) -> MockResponse?) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                handler(request.path?.substringBefore('?') ?: "") ?: MockResponse().setResponseCode(404)
        }
    }

    private fun requestedPaths(): Set<String> {
        val out = mutableSetOf<String>()
        while (true) {
            val request = server.takeRequest(200, TimeUnit.MILLISECONDS) ?: break
            out += request.path?.substringBefore('?') ?: ""
        }
        return out
    }

    @Test
    fun `fetchManifest parses a served manifest over HTTP`() = runTest {
        serve { if (it == manifestPath()) MockResponse().setBody(manifestBody()).setHeader("Content-Type", "application/dash+xml") else null }

        val manifest = downloader.fetchManifest(server.url(manifestPath()).toString(), "ua", null)

        assertNotNull(manifest)
        val video = manifest!!.videoRepresentations.single()
        assertEquals("v1", video.id)
        assertEquals(360, video.height)
        assertEquals(3, video.segmentUrls.size)
        assertEquals(3.0, manifest.durationSeconds!!, 0.001)
    }

    @Test
    fun `a non-200 manifest response yields no manifest`() = runTest {
        serve { null }
        assertNull(downloader.fetchManifest(server.url(manifestPath()).toString(), "ua", null))
    }

    @Test
    fun `downloadRepresentation writes the initialisation segment first and every segment`() = runTest {
        val initBytes = ByteArray(16) { 1 }
        val segmentOne = ByteArray(32) { 2 }
        val segmentTwo = ByteArray(32) { 3 }
        val segmentThree = ByteArray(32) { 4 }
        serve { path ->
            when (path) {
                manifestPath() -> MockResponse().setBody(manifestBody())
                "/vod/init.mp4" -> body(initBytes)
                "/vod/seg-1.m4s" -> body(segmentOne)
                "/vod/seg-2.m4s" -> body(segmentTwo)
                "/vod/seg-3.m4s" -> body(segmentThree)
                else -> null
            }
        }

        val manifest = downloader.fetchManifest(server.url(manifestPath()).toString(), "ua", null)!!
        val representation = manifest.videoRepresentations.single()
        val target = File(tempDir(), "clip.dash.tmp")

        val result = downloader.downloadRepresentation(representation, target, "ua", null)

        assertEquals(target.absolutePath, result.file.absolutePath)
        assertArrayEquals(initBytes + segmentOne + segmentTwo + segmentThree, target.readBytes())
        assertEquals(target.length(), result.bytesWritten)
        // Every part is fetched (order is not asserted: they are in flight together), init included.
        assertEquals(
            setOf(manifestPath(), "/vod/init.mp4", "/vod/seg-1.m4s", "/vod/seg-2.m4s", "/vod/seg-3.m4s"),
            requestedPaths()
        )
    }

    @Test
    fun `a failing segment aborts the transfer and leaves no output`() = runTest {
        serve { path ->
            when (path) {
                manifestPath() -> MockResponse().setBody(manifestBody())
                "/vod/init.mp4" -> body(ByteArray(8))
                "/vod/seg-1.m4s" -> body(ByteArray(8))
                "/vod/seg-2.m4s" -> MockResponse().setResponseCode(500)
                "/vod/seg-3.m4s" -> body(ByteArray(8))
                else -> null
            }
        }

        val manifest = downloader.fetchManifest(server.url(manifestPath()).toString(), "ua", null)!!
        val target = File(tempDir(), "clip.dash.tmp")

        var failed = false
        try {
            downloader.downloadRepresentation(manifest.videoRepresentations.single(), target, "ua", null)
        } catch (io: java.io.IOException) {
            failed = true
        }

        assertTrue("a failed segment must surface as an IOException", failed)
        assertFalse("a half-downloaded representation must not be published", target.exists())
    }

    @Test
    fun `a cancel request stops the transfer with a PAUSED reason`() = runTest {
        serve { path ->
            when (path) {
                manifestPath() -> MockResponse().setBody(manifestBody())
                "/vod/init.mp4" -> body(ByteArray(8))
                "/vod/seg-1.m4s" -> body(ByteArray(8))
                "/vod/seg-2.m4s" -> body(ByteArray(8))
                "/vod/seg-3.m4s" -> body(ByteArray(8))
                else -> null
            }
        }

        val manifest = downloader.fetchManifest(server.url(manifestPath()).toString(), "ua", null)!!
        val target = File(tempDir(), "clip.dash.tmp")
        val aborts = AtomicInteger(0)

        var reason: AbortReason? = null
        try {
            downloader.downloadRepresentation(
                manifest.videoRepresentations.single(), target, "ua", null,
                shouldAbort = { if (aborts.getAndIncrement() >= 1) AbortReason.PAUSED else null }
            )
        } catch (aborted: DownloadAbortedException) {
            reason = aborted.reason
        }

        assertEquals(AbortReason.PAUSED, reason)
        assertFalse("a paused transfer keeps no published output", target.exists())
    }

    @Test
    fun `a representation with no segments is refused`() = runTest {
        val manifest = DashManifestParser.parse(
            "https://cdn.example.org/v/stream.mpd",
            """
            <MPD type="static"><Period><AdaptationSet mimeType="video/mp4">
              <SegmentTemplate media="s-${'$'}Number${'$'}.m4s" timescale="1" duration="1"/>
              <Representation id="v1" bandwidth="1"/>
            </AdaptationSet></Period></MPD>
            """.trimIndent()
        )
        // No mediaPresentationDuration -> the parser refuses the representation entirely.
        assertNull(manifest)
    }
}
