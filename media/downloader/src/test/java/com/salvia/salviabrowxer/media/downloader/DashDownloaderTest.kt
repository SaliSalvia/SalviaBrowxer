package com.salvia.salviabrowxer.media.downloader

import com.salvia.salviabrowxer.media.resolver.DashManifestParser
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
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
 * requested in order, the initialisation segment is written first, and the bytes on disk are exactly
 * what the server served. A device is still required for the container rewrite (`MediaRemuxer`),
 * which needs the platform codecs.
 */
class DashDownloaderTest {

    private lateinit var server: MockWebServer
    private lateinit var downloader: DashDownloader

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
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

    @Test
    fun `fetchManifest parses a served manifest over HTTP`() = runTest {
        server.enqueue(MockResponse().setBody(manifestBody()).setHeader("Content-Type", "application/dash+xml"))

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
        server.enqueue(MockResponse().setResponseCode(404))
        assertNull(downloader.fetchManifest(server.url(manifestPath()).toString(), "ua", null))
    }

    @Test
    fun `downloadRepresentation writes the initialisation segment first and every segment in order`() = runTest {
        val initBytes = ByteArray(16) { 1 }
        val segmentOne = ByteArray(32) { 2 }
        val segmentTwo = ByteArray(32) { 3 }
        val segmentThree = ByteArray(32) { 4 }
        server.enqueue(MockResponse().setBody(manifestBody()))
        server.enqueue(MockResponse().setBody(okio.Buffer().write(initBytes)))
        server.enqueue(MockResponse().setBody(okio.Buffer().write(segmentOne)))
        server.enqueue(MockResponse().setBody(okio.Buffer().write(segmentTwo)))
        server.enqueue(MockResponse().setBody(okio.Buffer().write(segmentThree)))

        val manifest = downloader.fetchManifest(server.url(manifestPath()).toString(), "ua", null)!!
        val representation = manifest.videoRepresentations.single()
        val target = File(tempDir(), "clip.dash.tmp")

        val result = downloader.downloadRepresentation(representation, target, "ua", null)

        assertEquals(target.absolutePath, result.file.absolutePath)
        assertArrayEquals(initBytes + segmentOne + segmentTwo + segmentThree, target.readBytes())
        assertEquals(target.length(), result.bytesWritten)
        // Requests: manifest, init, then the three segments — all under the manifest's directory.
        assertEquals(manifestPath(), server.takeRequest().path)
        assertEquals("/vod/init.mp4", server.takeRequest().path)
        assertEquals("/vod/seg-1.m4s", server.takeRequest().path)
        assertEquals("/vod/seg-2.m4s", server.takeRequest().path)
        assertEquals("/vod/seg-3.m4s", server.takeRequest().path)
    }

    @Test
    fun `a failing segment aborts the transfer and leaves no output`() = runTest {
        server.enqueue(MockResponse().setBody(manifestBody()))
        server.enqueue(MockResponse().setBody(okio.Buffer().write(ByteArray(8))))
        server.enqueue(MockResponse().setResponseCode(500))

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
        server.enqueue(MockResponse().setBody(manifestBody()))
        server.enqueue(MockResponse().setBody(okio.Buffer().write(ByteArray(8))))
        server.enqueue(MockResponse().setBody(okio.Buffer().write(ByteArray(8))))
        server.enqueue(MockResponse().setBody(okio.Buffer().write(ByteArray(8))))
        server.enqueue(MockResponse().setBody(okio.Buffer().write(ByteArray(8))))

        val manifest = downloader.fetchManifest(server.url(manifestPath()).toString(), "ua", null)!!
        val target = File(tempDir(), "clip.dash.tmp")
        var aborts = 0

        var reason: AbortReason? = null
        try {
            downloader.downloadRepresentation(
                manifest.videoRepresentations.single(), target, "ua", null,
                shouldAbort = { if (aborts++ >= 1) AbortReason.PAUSED else null }
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
