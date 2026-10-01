package com.salvia.salviabrowxer.media.downloader

import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Drives the real HLS reassembler against a real HTTP server: master-playlist variant selection,
 * segment ordering, the init segment, and the two streams the app deliberately refuses.
 */
class HlsDownloaderTest {

    private lateinit var server: MockWebServer
    private lateinit var downloader: HlsDownloader

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        downloader = HlsDownloader(OkHttpClient())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun tempDir(): File = Files.createTempDirectory("salvia-hls").toFile()

    private fun body(bytes: ByteArray) = MockResponse().setBody(okio.Buffer().write(bytes))

    private fun mediaPlaylist(segments: List<String>, extra: String = ""): String = buildString {
        appendLine("#EXTM3U")
        appendLine("#EXT-X-VERSION:3")
        appendLine("#EXT-X-TARGETDURATION:2")
        if (extra.isNotEmpty()) appendLine(extra)
        segments.forEach { appendLine("#EXTINF:2.0,"); appendLine(it) }
        appendLine("#EXT-X-ENDLIST")
    }

    @Test
    fun `media playlist segments are concatenated into the published file`() = runTest {
        val one = ByteArray(16) { 1 }
        val two = ByteArray(16) { 2 }
        val three = ByteArray(16) { 3 }
        server.enqueue(MockResponse().setBody(mediaPlaylist(listOf("s1.ts", "s2.ts", "s3.ts"))))
        server.enqueue(body(one))
        server.enqueue(body(two))
        server.enqueue(body(three))

        val directory = tempDir()
        val result = downloader.download(
            FileDownload(id = "1", url = server.url("/hls/index.m3u8").toString(), directory = directory.absolutePath, filename = "clip.ts")
        )

        assertArrayEquals(one + two + three, result.file.readBytes())
        assertEquals("clip.ts", result.file.name)
        assertEquals("/hls/index.m3u8", server.takeRequest().path)
        assertEquals("/hls/s1.ts", server.takeRequest().path)
        assertEquals("/hls/s2.ts", server.takeRequest().path)
        assertEquals("/hls/s3.ts", server.takeRequest().path)
    }

    @Test
    fun `a master playlist picks the highest resolution variant`() = runTest {
        val master = """
            #EXTM3U
            #EXT-X-STREAM-INF:BANDWIDTH=400000,RESOLUTION=640x360
            low/index.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=5000000,RESOLUTION=1920x1080
            high/index.m3u8
        """.trimIndent()
        server.enqueue(MockResponse().setBody(master))
        server.enqueue(MockResponse().setBody(mediaPlaylist(listOf("seg.ts"))))
        server.enqueue(body(ByteArray(8) { 9 }))

        val directory = tempDir()
        downloader.download(
            FileDownload(id = "1", url = server.url("/hls/master.m3u8").toString(), directory = directory.absolutePath, filename = "clip.ts")
        )

        assertEquals("/hls/master.m3u8", server.takeRequest().path)
        assertEquals("the 1080p variant must be chosen", "/hls/high/index.m3u8", server.takeRequest().path)
        assertEquals("/hls/high/seg.ts", server.takeRequest().path)
    }

    @Test
    fun `an EXT-X-MAP initialisation segment is written before the media segments`() = runTest {
        val init = ByteArray(8) { 5 }
        val segment = ByteArray(8) { 6 }
        server.enqueue(MockResponse().setBody(mediaPlaylist(listOf("seg.ts"), extra = "#EXT-X-MAP:URI=\"init.mp4\"")))
        server.enqueue(body(init))
        server.enqueue(body(segment))

        val directory = tempDir()
        val result = downloader.download(
            FileDownload(id = "1", url = server.url("/hls/fmp4/index.m3u8").toString(), directory = directory.absolutePath, filename = "clip.ts")
        )

        assertArrayEquals(init + segment, result.file.readBytes())
    }

    @Test
    fun `an AES-128 encrypted playlist is refused rather than saved`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                mediaPlaylist(listOf("s1.ts"), extra = "#EXT-X-KEY:METHOD=AES-128,URI=\"key.bin\"")
            )
        )

        val directory = tempDir()
        var message: String? = null
        try {
            downloader.download(FileDownload(id = "1", url = server.url("/hls/enc.m3u8").toString(), directory = directory.absolutePath, filename = "clip.ts"))
        } catch (io: IOException) {
            message = io.message
        }

        assertTrue("encrypted HLS must fail loudly, was: $message", message?.contains("Encrypted") == true)
        assertTrue("no undecryptable bytes may be kept", directory.listFiles().orEmpty().none { it.name == "clip.ts" })
    }

    @Test
    fun `a live playlist is refused`() = runTest {
        val live = """
            #EXTM3U
            #EXT-X-TARGETDURATION:2
            #EXTINF:2.0,
            s1.ts
        """.trimIndent()
        server.enqueue(MockResponse().setBody(live))

        val directory = tempDir()
        var message: String? = null
        try {
            downloader.download(FileDownload(id = "1", url = server.url("/hls/live.m3u8").toString(), directory = directory.absolutePath, filename = "clip.ts"))
        } catch (io: IOException) {
            message = io.message
        }

        assertTrue("a live stream must fail loudly, was: $message", message?.contains("Live") == true)
        assertFalse(File(directory, "clip.ts").exists())
    }

    @Test
    fun `a response that is not a playlist is refused`() = runTest {
        server.enqueue(MockResponse().setBody("<html>not a playlist</html>"))

        var message: String? = null
        try {
            downloader.download(FileDownload(id = "1", url = server.url("/hls/index.m3u8").toString(), directory = tempDir().absolutePath, filename = "clip.ts"))
        } catch (io: IOException) {
            message = io.message
        }

        assertTrue("a non-playlist body must be refused, was: $message", message?.contains("valid HLS playlist") == true)
    }

    @Test
    fun `looksLikeHls recognises the extension and the content type`() {
        assertTrue(downloader.looksLikeHls("https://cdn/x/stream.m3u8?token=1", null))
        assertTrue(downloader.looksLikeHls("https://cdn/x/stream", "application/vnd.apple.mpegurl"))
        assertTrue(downloader.looksLikeHls("https://cdn/x/stream", "application/x-mpegURL; charset=utf-8"))
        assertFalse(downloader.looksLikeHls("https://cdn/x/clip.mp4", "video/mp4"))
    }
}
