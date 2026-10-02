package com.salvia.salviabrowxer.media.resolver

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Drives the real resolver against a real HTTP server, so the HLS/DASH expansion — including the
 * master-playlist fetch the resolver now overlaps with its metadata probe — is exercised end to end
 * rather than through mocks.
 */
class DirectMediaResolverHttpTest {

    private lateinit var server: MockWebServer
    private lateinit var resolver: DirectMediaResolver

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        resolver = DirectMediaResolver(OkHttpClient())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun serve(files: Map<String, Pair<String, ByteArray>>) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path?.substringBefore('?') ?: ""
                val (type, body) = files[path] ?: return MockResponse().setResponseCode(404)
                return MockResponse().setHeader("Content-Type", type).setBody(Buffer().write(body))
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

    @Test
    fun `an m3u8 master playlist expands into one format per variant, highest quality first`() = runBlocking {
        val master = """
            #EXTM3U
            #EXT-X-STREAM-INF:BANDWIDTH=400000,RESOLUTION=640x360
            low/index.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=5000000,RESOLUTION=1920x1080
            high/index.m3u8
        """.trimIndent()
        serve(mapOf("/hls/master.m3u8" to ("application/vnd.apple.mpegurl" to master.toByteArray())))

        val info = resolver.resolve(server.url("/hls/master.m3u8").toString())

        assertEquals("direct-hls", info.extractor)
        assertEquals(2, info.formats.size)
        assertEquals("1080p", info.formats[0].format)
        assertEquals(1920, info.formats[0].width ?: -1)
        assertEquals(1080, info.formats[0].height ?: -1)
        assertEquals(5000, info.formats[0].bitrate ?: -1)
        assertTrue("the variant URL must be absolutized", info.formats[0].url.endsWith("/hls/high/index.m3u8"))
        assertEquals("360p", info.formats[1].format)
        assertEquals(2, info.videoFormats.size)
    }

    @Test
    fun `a non-manifest URL still resolves to a single direct format`() = runBlocking {
        serve(mapOf("/media/clip.mp4" to ("video/mp4" to ByteArray(2048) { 1 })))

        val info = resolver.resolve(server.url("/media/clip.mp4").toString())

        assertEquals("direct", info.extractor)
        assertEquals(1, info.formats.size)
        assertEquals("mp4", info.formats[0].extension)
        assertEquals(2048L, info.formats[0].size)
        assertTrue(info.formats[0].isVideo)
        // A plain file is probed with a single HEAD: its bytes are never read during resolution.
        assertEquals(listOf("/media/clip.mp4"), requestedPaths())
    }
}
