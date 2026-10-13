package com.salvia.salviabrowxer.media.downloader

import com.salvia.salviabrowxer.core.model.SessionReferer
import java.nio.file.Files
import java.util.concurrent.TimeUnit
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
 * The acceptance gate for the session work: a server that guards its media the way a hotlink guard
 * does — nothing without the page's cookie and its `Referer` — has to serve the whole transfer,
 * playlist and segments included.
 *
 * The guard is deliberately strict (both headers on every single request) because that is exactly the
 * case the old code failed: it sent neither, so a URL the page was playing answered 403 the moment
 * the downloader asked for it.
 */
class SessionTransportTest {

    // The page is http on purpose: the local server is http too, and a browser strips the referrer on
    // a downgrade, so an https page would legitimately send none. See SessionReferer.
    private val pageUrl = "http://video.example.com/watch?v=42"
    private val expectedReferer = "http://video.example.com/"
    private val cookieHeader = "session=abc; quality=hd"
    private val userAgent = "SalviaTest/1 (Android)"

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private val guard = object : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse {
            val path = request.path?.substringBefore('?') ?: ""
            if (request.getHeader("Cookie") != cookieHeader) return MockResponse().setResponseCode(403)
            if (request.getHeader("Referer") != expectedReferer) return MockResponse().setResponseCode(403)
            return when (path) {
                "/hls/index.m3u8" -> MockResponse().setBody(
                    "#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-TARGETDURATION:2\n#EXTINF:2.0,\ns1.ts\n#EXTINF:2.0,\ns2.ts\n#EXT-X-ENDLIST\n"
                )
                "/hls/s1.ts" -> MockResponse().setBody(Buffer().write(ByteArray(8) { 1 }))
                "/hls/s2.ts" -> MockResponse().setBody(Buffer().write(ByteArray(8) { 2 }))
                else -> MockResponse().setResponseCode(404)
            }
        }
    }

    private fun client(): OkHttpClient =
        OkHttpClient.Builder().cookieJar(SessionCookieJar { cookieHeader }).build()

    private fun guardedDownload(name: String, withSession: Boolean): FileDownload {
        val url = server.url("/hls/index.m3u8").toString()
        return FileDownload(
            id = "1",
            url = url,
            directory = Files.createTempDirectory("salvia-session-$name").toFile().absolutePath,
            filename = "clip.ts",
            userAgent = if (withSession) userAgent else null,
            referer = if (withSession) SessionReferer.headerFor(pageUrl, url) else null
        )
    }

    @Test
    fun `a guarded playlist and its segments are served when the session travels with the transfer`() = runTest {
        server.dispatcher = guard

        val result = HlsDownloader(client()).download(guardedDownload("ok", withSession = true))

        assertArrayEquals(ByteArray(8) { 1 } + ByteArray(8) { 2 }, result.file.readBytes())
    }

    @Test
    fun `without the session the same transfer is refused`() = runTest {
        server.dispatcher = guard

        val failure = runCatching { HlsDownloader(OkHttpClient()).download(guardedDownload("none", withSession = false)) }

        assertTrue("a guarded playlist must not be served to a bare client", failure.isFailure)
    }

    @Test
    fun `the session reaches every request, not only the first`() = runTest {
        server.dispatcher = guard

        HlsDownloader(client()).download(guardedDownload("all", withSession = true))

        val seen = mutableListOf<String>()
        while (true) {
            val request = server.takeRequest(200, TimeUnit.MILLISECONDS) ?: break
            val path = request.path?.substringBefore('?') ?: ""
            assertEquals("cookie missing on $path", cookieHeader, request.getHeader("Cookie"))
            assertEquals("referer missing on $path", expectedReferer, request.getHeader("Referer"))
            assertEquals("user agent missing on $path", userAgent, request.getHeader("User-Agent"))
            seen += path
        }
        assertEquals("the playlist is fetched first", "/hls/index.m3u8", seen.firstOrNull())
        // Segments are fetched in parallel, so only their set is deterministic.
        assertEquals(setOf("/hls/s1.ts", "/hls/s2.ts"), seen.drop(1).toSet())
    }

    @Test
    fun `a page on the same site keeps its whole url, another site gets only the origin`() {
        val page = "https://cdn.example.com/watch/9"

        assertEquals(page, SessionReferer.headerFor(page, "https://cdn.example.com/clip.mp4"))
        assertEquals("https://video.example.com/", SessionReferer.headerFor(pageUrl, "https://other.example.net/clip.mp4"))
    }
}
