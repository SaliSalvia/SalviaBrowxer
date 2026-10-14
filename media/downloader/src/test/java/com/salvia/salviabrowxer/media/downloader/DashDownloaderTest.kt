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

    /** The manifest the range-aware dispatcher serves, and the media path it guards. */
    private var manifest: String = ""
    private var mediaPath: String = ""

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

    // ---- The single-file shape (`SegmentBase` + `indexRange`) ------------------------------------

    /**
     * The single-file shape: the representation's BaseURL *is* the media file, and the manifest names
     * the byte range of its index. Everything else about the file is inside that index.
     */
    private fun singleFileManifest(mediaPath: String, indexRange: String, initRange: String? = null): String {
        val initialization = if (initRange == null) "" else "<Initialization range=\"$initRange\"/>"
        return """
            <MPD type="static" mediaPresentationDuration="PT2S">
              <Period>
                <AdaptationSet mimeType="video/mp4">
                  <Representation id="v1" bandwidth="500000" width="640" height="360">
                    <BaseURL>$mediaPath</BaseURL>
                    <SegmentBase indexRange="$indexRange">$initialization</SegmentBase>
                  </Representation>
                </AdaptationSet>
              </Period>
            </MPD>
        """.trimIndent()
    }

    /** A version-0 `sidx` describing one media reference per entry, with 0 as the first offset. */
    private fun sidxBytes(segmentSizes: List<Int>, firstOffset: Long = 0L): ByteArray {
        val payload = mutableListOf<Byte>()
        fun u8(value: Int) = payload.add((value and 0xFF).toByte())
        fun u16(value: Int) { u8(value shr 8); u8(value) }
        fun u32(value: Long) {
            u8((value shr 24).toInt()); u8((value shr 16).toInt()); u8((value shr 8).toInt()); u8(value.toInt())
        }
        u8(0); u8(0); u8(0); u8(0) // version + flags
        u32(1) // reference_ID
        u32(1000) // timescale
        u32(0) // earliest_presentation_time
        u32(firstOffset)
        u16(0) // reserved
        u16(segmentSizes.size)
        segmentSizes.forEach { size ->
            u32(size.toLong()) // reference_type = 0 (media)
            u32(1000) // subsegment_duration
            u32(0x9000_0000L) // starts_with_SAP + SAP type 1
        }
        val body = payload.toByteArray()
        val size = body.size + 8
        val out = ByteArray(size)
        out[0] = (size ushr 24).toByte(); out[1] = (size ushr 16).toByte(); out[2] = (size ushr 8).toByte(); out[3] = size.toByte()
        "sidx".toByteArray(Charsets.ISO_8859_1).copyInto(out, 4)
        body.copyInto(out, 8)
        return out
    }

    /** A server that serves [file] at [mediaPath] and honours `Range` requests the way a CDN does. */
    private fun serveFile(file: ByteArray, mediaPath: String, honourRanges: Boolean = true) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path?.substringBefore('?') ?: ""
                if (path == manifestPath()) return MockResponse().setBody(manifest)
                if (path != mediaPath) return MockResponse().setResponseCode(404)
                val range = request.getHeader("Range")
                if (!honourRanges || range == null) {
                    return MockResponse().setBody(Buffer().write(file)).setHeader("Accept-Ranges", "bytes")
                }
                val spec = range.removePrefix("bytes=")
                val start = spec.substringBefore('-').toLongOrNull() ?: return MockResponse().setResponseCode(416)
                val end = spec.substringAfter('-').toLongOrNull() ?: (file.size - 1L)
                if (start > end || end > file.lastIndex) return MockResponse().setResponseCode(416)
                val slice = file.copyOfRange(start.toInt(), (end + 1).toInt())
                return MockResponse().setResponseCode(206)
                    .setHeader("Content-Range", "bytes $start-$end/${file.size}")
                    .setBody(Buffer().write(slice))
            }
        }
    }

    /** Everything the server was asked for, as `path` to `Range` header (null when not ranged). */
    private fun requestedRanges(): Map<String, List<String?>> {
        val out = mutableMapOf<String, MutableList<String?>>()
        while (true) {
            val request = server.takeRequest(200, TimeUnit.MILLISECONDS) ?: break
            val path = request.path?.substringBefore('?') ?: ""
            out.getOrPut(path) { mutableListOf() }.add(request.getHeader("Range"))
        }
        return out
    }

    @Test
    fun `a single-file representation is assembled from its index ranges`() = runTest {
        val prefix = ByteArray(32) { 1 } // stands in for ftyp/moov, everything before the index
        val segmentOne = ByteArray(32) { 7 }
        val segmentTwo = ByteArray(40) { 8 }
        val index = sidxBytes(listOf(segmentOne.size, segmentTwo.size))
        // The whole resource, exactly as a player would consume it.
        val file = prefix + index + segmentOne + segmentTwo
        mediaPath = "/vod/ondemand.mp4"
        manifest = singleFileManifest(mediaPath, "${prefix.size}-${prefix.size + index.size - 1}", "0-${prefix.size - 1}")
        serveFile(file, mediaPath)

        val parsed = downloader.fetchManifest(server.url(manifestPath()).toString(), "ua", null)!!
        val representation = parsed.videoRepresentations.single()
        assertEquals(
            "the single-file URL is carried, not guessed as a segment",
            server.url(mediaPath).toString(),
            representation.fileUrl
        )
        assertTrue("the representation must have no segment URLs", representation.segmentUrls.isEmpty())

        val target = File(tempDir(), "ondemand.dash.tmp")
        val result = downloader.downloadRepresentation(representation, target, "ua", null)

        assertArrayEquals("the ranges must tile the file end to end", file, target.readBytes())
        assertEquals(file.size.toLong(), result.bytesWritten)
        val ranges = requestedRanges()[mediaPath].orEmpty()
        assertTrue("the index must be fetched before planning", "bytes=${prefix.size}-${prefix.size + index.size - 1}" in ranges)
        assertTrue("the prefix must be fetched from byte zero", "bytes=0-${prefix.size + index.size - 1}" in ranges)
        assertTrue("each media range is fetched", "bytes=${prefix.size + index.size}-${prefix.size + index.size + segmentOne.size - 1}" in ranges)
        assertNull("no request may fetch the whole file when the plan holds", ranges.firstOrNull { it == null })
    }

    @Test
    fun `a server that ignores Range still produces the whole file`() = runTest {
        // Some CDNs answer 200 to a ranged request. The plan then cannot be verified, and the
        // downloader must fall back to one plain request instead of writing a truncated file.
        val prefix = ByteArray(32) { 1 }
        val media = ByteArray(48) { 9 }
        val index = sidxBytes(listOf(media.size))
        val file = prefix + index + media
        mediaPath = "/vod/range-less.mp4"
        manifest = singleFileManifest(mediaPath, "${prefix.size}-${prefix.size + index.size - 1}")
        serveFile(file, mediaPath, honourRanges = false)

        val representation = downloader.fetchManifest(server.url(manifestPath()).toString(), "ua", null)!!
            .videoRepresentations.single()
        val target = File(tempDir(), "rangeless.dash.tmp")

        downloader.downloadRepresentation(representation, target, "ua", null)

        assertArrayEquals(file, target.readBytes())
        val ranges = requestedRanges()[mediaPath].orEmpty()
        assertTrue("the fallback is one plain request", ranges.any { it == null })
    }

    @Test
    fun `an index that leaves a gap falls back to the whole file`() = runTest {
        // A wrong first_offset describes a file with a hole in it. Concatenating it would produce a
        // corrupt MP4 that every player reports as "cannot play", so the plan is refused.
        val prefix = ByteArray(32) { 1 }
        val media = ByteArray(48) { 5 }
        val index = sidxBytes(listOf(media.size), firstOffset = 4L)
        val file = prefix + index + media
        mediaPath = "/vod/gapped.mp4"
        manifest = singleFileManifest(mediaPath, "${prefix.size}-${prefix.size + index.size - 1}")
        serveFile(file, mediaPath)

        val representation = downloader.fetchManifest(server.url(manifestPath()).toString(), "ua", null)!!
            .videoRepresentations.single()
        val target = File(tempDir(), "gapped.dash.tmp")

        downloader.downloadRepresentation(representation, target, "ua", null)

        assertArrayEquals(file, target.readBytes())
        val ranges = requestedRanges()[mediaPath].orEmpty()
        assertTrue("a plan that does not tile the file is not used", ranges.any { it == null })
    }
}
