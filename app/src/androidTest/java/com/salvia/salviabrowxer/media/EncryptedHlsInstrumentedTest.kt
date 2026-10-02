package com.salvia.salviabrowxer.media

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.salvia.salviabrowxer.media.downloader.FileDownload
import com.salvia.salviabrowxer.media.downloader.HlsDownloader
import java.io.File
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.runBlocking
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
import org.junit.runner.RunWith

/**
 * On-device check of the AES-128 HLS path that the JVM unit tests can only approximate.
 *
 * Runs a real local HTTP server on the device and lets the real [HlsDownloader] fetch an encrypted
 * playlist, the key and the encrypted segments. Everything the JVM tests cannot cover — Android's
 * own JCE provider performing the AES-128-CBC decryption, OkHttp's connection handling on a device,
 * and the concatenation onto the app's real filesystem — is exercised here. Run with
 * `./gradlew :app:connectedDebugAndroidTest` on an emulator or a connected phone.
 */
@RunWith(AndroidJUnit4::class)
class EncryptedHlsInstrumentedTest {

    private lateinit var server: MockWebServer
    private lateinit var downloader: HlsDownloader
    private lateinit var workDir: File

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        downloader = HlsDownloader(OkHttpClient())
        workDir = File(context.cacheDir, "encrypted-hls-check").apply { deleteRecursively(); mkdirs() }
    }

    @After
    fun tearDown() {
        server.shutdown()
        workDir.deleteRecursively()
    }

    /** Routes every request by path, so a parallel segment fetch cannot be mis-served. */
    private fun serve(files: Map<String, ByteArray>) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path?.substringBefore('?') ?: ""
                val body = files[path] ?: return MockResponse().setResponseCode(404)
                return MockResponse().setBody(Buffer().write(body))
            }
        }
    }

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

    private fun mediaPlaylist(segments: List<String>, extra: String = ""): String = buildString {
        appendLine("#EXTM3U")
        appendLine("#EXT-X-VERSION:3")
        appendLine("#EXT-X-TARGETDURATION:2")
        if (extra.isNotEmpty()) extra.lineSequence().forEach { appendLine(it) }
        segments.forEach { appendLine("#EXTINF:2.0,"); appendLine(it) }
        appendLine("#EXT-X-ENDLIST")
    }

    private fun downloadOf(path: String, name: String = "clip.ts") = FileDownload(
        id = "instrumented",
        url = server.url(path).toString(),
        directory = workDir.absolutePath,
        filename = name
    )

    @Test
    fun an_aes128_hls_stream_is_decrypted_with_the_manifest_key() {
        val key = ByteArray(16) { (it * 7).toByte() }
        val first = "first encrypted segment, on a real device".toByteArray()
        val second = "second encrypted segment, on a real device".toByteArray()
        serve(
            mapOf(
                "/hls/enc.m3u8" to mediaPlaylist(
                    listOf("s0.ts", "s1.ts"),
                    extra = "#EXT-X-KEY:METHOD=AES-128,URI=\"key.bin\""
                ).toByteArray(),
                "/hls/key.bin" to key,
                "/hls/s0.ts" to encryptAes128(first, key, sequenceIv(0)),
                "/hls/s1.ts" to encryptAes128(second, key, sequenceIv(1))
            )
        )

        val result = runBlocking { downloader.download(downloadOf("/hls/enc.m3u8")) }

        assertArrayEquals(first + second, result.file.readBytes())
        assertEquals("clip.ts", result.file.name)
        assertTrue("the playlist, the key and both segments must be requested", requestedPaths().containsAll(listOf("/hls/enc.m3u8", "/hls/key.bin", "/hls/s0.ts", "/hls/s1.ts")))
    }

    @Test
    fun an_explicit_iv_and_a_rotated_key_are_both_applied_on_device() {
        val keyA = ByteArray(16) { 1 }
        val keyB = ByteArray(16) { 2 }
        val ivA = ByteArray(16) { 3 }
        val ivHex = ivA.joinToString("") { "%02x".format(it) }
        val first = "segment one payload".toByteArray()
        val second = "segment two payload".toByteArray()
        val playlist = buildString {
            appendLine("#EXTM3U")
            appendLine("#EXT-X-VERSION:3")
            appendLine("#EXT-X-TARGETDURATION:2")
            appendLine("#EXT-X-KEY:METHOD=AES-128,URI=\"keyA.bin\",IV=0x$ivHex")
            appendLine("#EXTINF:2.0,")
            appendLine("s0.ts")
            appendLine("#EXT-X-KEY:METHOD=AES-128,URI=\"keyB.bin\"")
            appendLine("#EXTINF:2.0,")
            appendLine("s1.ts")
            appendLine("#EXT-X-ENDLIST")
        }
        serve(
            mapOf(
                "/hls/rot.m3u8" to playlist.toByteArray(),
                "/hls/keyA.bin" to keyA,
                "/hls/keyB.bin" to keyB,
                "/hls/s0.ts" to encryptAes128(first, keyA, ivA),
                "/hls/s1.ts" to encryptAes128(second, keyB, sequenceIv(1))
            )
        )

        val result = runBlocking { downloader.download(downloadOf("/hls/rot.m3u8")) }

        assertArrayEquals(first + second, result.file.readBytes())
    }

    private fun requestedPaths(): List<String> {
        val out = mutableListOf<String>()
        while (true) {
            val request = server.takeRequest(200, java.util.concurrent.TimeUnit.MILLISECONDS) ?: break
            out += request.path?.substringBefore('?') ?: ""
        }
        return out
    }
}
