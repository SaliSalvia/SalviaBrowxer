package com.salvia.salviabrowxer.media.downloader

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Drives the real HLS reassembler against a real HTTP server: master-playlist variant selection,
 * parallel segment ordering, the init segment, AES-128 decryption, byte ranges, resume, and the
 * streams the app deliberately refuses.
 *
 * Every test routes responses by path rather than by arrival order, because the downloader now keeps
 * several segment requests in flight at once — an enqueue-order server would hand each response to
 * whichever request happened to arrive first.
 */
class HlsDownloaderTest {

    private lateinit var server: MockWebServer
    private lateinit var downloader: HlsDownloader

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        downloader = HlsDownloader(OkHttpClient())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun tempDir(): File = Files.createTempDirectory("salvia-hls").toFile()

    private fun mediaPlaylist(segments: List<String>, extra: String = ""): String = buildString {
        appendLine("#EXTM3U")
        appendLine("#EXT-X-VERSION:3")
        appendLine("#EXT-X-TARGETDURATION:2")
        if (extra.isNotEmpty()) extra.lineSequence().forEach { appendLine(it) }
        segments.forEach { appendLine("#EXTINF:2.0,"); appendLine(it) }
        appendLine("#EXT-X-ENDLIST")
    }

    /** Routes every request by path; unknown paths 404. */
    private fun serve(files: Map<String, ByteArray>) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path?.substringBefore('?') ?: ""
                val body = files[path] ?: return MockResponse().setResponseCode(404)
                return MockResponse().setBody(Buffer().write(body))
            }
        }
    }

    /** Range-aware server, for the `EXT-X-BYTERANGE` case. */
    private fun serveWithRanges(files: Map<String, ByteArray>) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path?.substringBefore('?') ?: ""
                val full = files[path] ?: return MockResponse().setResponseCode(404)
                val match = request.getHeader("Range")?.let { Regex("bytes=(\\d+)-(\\d+)").find(it) }
                if (match != null) {
                    val start = match.groupValues[1].toInt().coerceAtMost(full.size)
                    val end = (match.groupValues[2].toInt() + 1).coerceAtMost(full.size)
                    return MockResponse().setResponseCode(206).setBody(Buffer().write(full.copyOfRange(start, end)))
                }
                return MockResponse().setBody(Buffer().write(full))
            }
        }
    }

    private fun requestedPaths(): List<String> {
        val out = mutableListOf<String>()
        while (true) {
            val request = server.takeRequest(200, TimeUnit.MILLISECONDS) ?: break
            out += request.path?.substringBefore('?') ?: ""
        }
        return out
    }

    private fun downloadOf(path: String, directory: File, name: String = "clip.ts") = FileDownload(
        id = "1",
        url = server.url(path).toString(),
        directory = directory.absolutePath,
        filename = name
    )

    private fun zeroIv(): ByteArray = ByteArray(16)

    private fun sequenceIv(sequence: Long): ByteArray {
        val iv = ByteArray(16)
        var value = sequence
        for (i in 15 downTo 0) {
            iv[i] = (value and 0xFF).toByte()
            value = value ushr 8
        }
        return iv
    }

    private fun encryptAes128(plain: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        return cipher.doFinal(plain)
    }

    @Test
    fun `segments are concatenated in playlist order though they are fetched in parallel`() = runTest {
        val one = ByteArray(16) { 1 }
        val two = ByteArray(16) { 2 }
        val three = ByteArray(16) { 3 }
        serve(
            mapOf(
                "/hls/index.m3u8" to mediaPlaylist(listOf("s1.ts", "s2.ts", "s3.ts")).toByteArray(),
                "/hls/s1.ts" to one,
                "/hls/s2.ts" to two,
                "/hls/s3.ts" to three
            )
        )

        val result = downloader.download(downloadOf("/hls/index.m3u8", tempDir()))

        assertArrayEquals(one + two + three, result.file.readBytes())
        assertEquals("clip.ts", result.file.name)
        val paths = requestedPaths()
        assertEquals(4, paths.size)
        assertEquals(setOf("/hls/index.m3u8", "/hls/s1.ts", "/hls/s2.ts", "/hls/s3.ts"), paths.toSet())
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
        val segment = ByteArray(8) { 9 }
        serve(
            mapOf(
                "/hls/master.m3u8" to master.toByteArray(),
                "/hls/high/index.m3u8" to mediaPlaylist(listOf("seg.ts")).toByteArray(),
                "/hls/high/seg.ts" to segment
            )
        )

        val result = downloader.download(downloadOf("/hls/master.m3u8", tempDir()))

        assertArrayEquals(segment, result.file.readBytes())
        val paths = requestedPaths()
        assertTrue("the 1080p variant must be fetched", "/hls/high/index.m3u8" in paths)
        assertTrue("/hls/high/seg.ts" in paths)
        assertFalse("the 360p variant must not be fetched", "/hls/low/index.m3u8" in paths)
    }

    @Test
    fun `an EXT-X-MAP initialisation segment is written before the media segments`() = runTest {
        val init = ByteArray(8) { 5 }
        val segment = ByteArray(8) { 6 }
        serve(
            mapOf(
                "/hls/fmp4/index.m3u8" to mediaPlaylist(listOf("seg.ts"), extra = "#EXT-X-MAP:URI=\"init.mp4\"").toByteArray(),
                "/hls/fmp4/init.mp4" to init,
                "/hls/fmp4/seg.ts" to segment
            )
        )

        val result = downloader.download(downloadOf("/hls/fmp4/index.m3u8", tempDir()))

        assertArrayEquals(init + segment, result.file.readBytes())
    }

    @Test
    fun `an AES-128 playlist is decrypted with the key the manifest names`() = runTest {
        val key = ByteArray(16) { it.toByte() }
        val plain = "this is a secret HLS segment payload".toByteArray()
        serve(
            mapOf(
                "/hls/enc.m3u8" to mediaPlaylist(listOf("s1.ts"), extra = "#EXT-X-KEY:METHOD=AES-128,URI=\"key.bin\"").toByteArray(),
                "/hls/key.bin" to key,
                "/hls/s1.ts" to encryptAes128(plain, key, zeroIv())
            )
        )

        val result = downloader.download(downloadOf("/hls/enc.m3u8", tempDir()))

        assertArrayEquals(plain, result.file.readBytes())
    }

    @Test
    fun `an explicit IV and a rotated key are both applied`() = runTest {
        val keyA = ByteArray(16) { 1 }
        val keyB = ByteArray(16) { 2 }
        val ivA = ByteArray(16) { 3 }
        val first = "first-segment-payload".toByteArray()
        val second = "second-segment-payload".toByteArray()
        val ivHex = ivA.joinToString("") { "%02x".format(it) }
        val playlist = buildString {
            appendLine("#EXTM3U")
            appendLine("#EXT-X-VERSION:3")
            appendLine("#EXT-X-TARGETDURATION:2")
            appendLine("#EXT-X-KEY:METHOD=AES-128,URI=\"keyA.bin\",IV=0x$ivHex")
            appendLine("#EXTINF:2.0,")
            appendLine("s1.ts")
            appendLine("#EXT-X-KEY:METHOD=AES-128,URI=\"keyB.bin\"")
            appendLine("#EXTINF:2.0,")
            appendLine("s2.ts")
            appendLine("#EXT-X-ENDLIST")
        }
        serve(
            mapOf(
                "/hls/rot.m3u8" to playlist.toByteArray(),
                "/hls/keyA.bin" to keyA,
                "/hls/keyB.bin" to keyB,
                "/hls/s1.ts" to encryptAes128(first, keyA, ivA),
                "/hls/s2.ts" to encryptAes128(second, keyB, sequenceIv(1))
            )
        )

        val result = downloader.download(downloadOf("/hls/rot.m3u8", tempDir()))

        assertArrayEquals(first + second, result.file.readBytes())
    }

    @Test
    fun `EXT-X-BYTERANGE segments are fetched as ranges of one file`() = runTest {
        val whole = ByteArray(8) { it.toByte() }
        val playlist = buildString {
            appendLine("#EXTM3U")
            appendLine("#EXT-X-VERSION:4")
            appendLine("#EXT-X-TARGETDURATION:2")
            appendLine("#EXT-X-BYTERANGE:4@0")
            appendLine("seg.ts")
            appendLine("#EXT-X-BYTERANGE:4")
            appendLine("seg.ts")
            appendLine("#EXT-X-ENDLIST")
        }
        serveWithRanges(mapOf("/hls/br.m3u8" to playlist.toByteArray(), "/hls/seg.ts" to whole))

        val result = downloader.download(downloadOf("/hls/br.m3u8", tempDir()))

        assertArrayEquals(whole, result.file.readBytes())
    }

    @Test
    fun `a staged part from a previous run is reused instead of re-downloaded`() = runTest {
        val one = ByteArray(16) { 1 }
        val two = ByteArray(16) { 2 }
        serve(
            mapOf(
                "/hls/index.m3u8" to mediaPlaylist(listOf("s1.ts", "s2.ts")).toByteArray(),
                "/hls/s1.ts" to one,
                "/hls/s2.ts" to two
            )
        )
        val directory = tempDir()
        val staging = File(directory, "clip.ts.parts").apply { mkdirs() }
        File(staging, "part-00000").writeBytes(one)

        val result = downloader.download(downloadOf("/hls/index.m3u8", directory))

        assertArrayEquals(one + two, result.file.readBytes())
        val paths = requestedPaths()
        assertFalse("a completed part must not be fetched again", "/hls/s1.ts" in paths)
        assertTrue("/hls/s2.ts" in paths)
        assertFalse("the staging directory is cleaned up after a successful transfer", staging.exists())
    }

    @Test
    fun `a SAMPLE-AES playlist is refused rather than saved`() = runTest {
        serve(
            mapOf(
                "/hls/index.m3u8" to mediaPlaylist(listOf("s1.ts"), extra = "#EXT-X-KEY:METHOD=SAMPLE-AES,URI=\"key.bin\"").toByteArray()
            )
        )

        var message: String? = null
        try {
            downloader.download(downloadOf("/hls/index.m3u8", tempDir()))
        } catch (io: IOException) {
            message = io.message
        }

        assertTrue("SAMPLE-AES must fail loudly, was: $message", message?.contains("SAMPLE-AES") == true)
    }

    @Test
    fun `a live playlist is refused`() = runTest {
        val live = """
            #EXTM3U
            #EXT-X-TARGETDURATION:2
            #EXTINF:2.0,
            s1.ts
        """.trimIndent()
        serve(mapOf("/hls/live.m3u8" to live.toByteArray()))

        val directory = tempDir()
        var message: String? = null
        try {
            downloader.download(downloadOf("/hls/live.m3u8", directory))
        } catch (io: IOException) {
            message = io.message
        }

        assertTrue("a live stream must fail loudly, was: $message", message?.contains("Live") == true)
        assertFalse(File(directory, "clip.ts").exists())
    }

    @Test
    fun `a response that is not a playlist is refused`() = runTest {
        serve(mapOf("/hls/index.m3u8" to "<html>not a playlist</html>".toByteArray()))

        var message: String? = null
        try {
            downloader.download(downloadOf("/hls/index.m3u8", tempDir()))
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
