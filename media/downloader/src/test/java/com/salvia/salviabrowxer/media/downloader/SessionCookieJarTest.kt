package com.salvia.salviabrowxer.media.downloader

import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The transfer must arrive with the cookie header the page's WebView already holds, and must never
 * write anything back into that store.
 */
class SessionCookieJarTest {

    private val url = "https://video.example.com/media/clip.mp4".toHttpUrl()

    @Test
    fun `splits the header the way a browser sends it`() {
        val jar = SessionCookieJar { target -> if (target.startsWith("https://video.example.com")) "session=abc; quality=hd" else null }

        val cookies = jar.loadForRequest(url)

        assertEquals(listOf("session", "quality"), cookies.map { it.name })
        assertEquals(listOf("abc", "hd"), cookies.map { it.value })
    }

    @Test
    fun `sends nothing when the store has no cookie for the url`() {
        val jar = SessionCookieJar { null }

        assertTrue(jar.loadForRequest(url).isEmpty())
    }

    @Test
    fun `a malformed or empty piece is dropped instead of throwing`() {
        val jar = SessionCookieJar { "; session=abc; ; not-a-cookie ;; " }

        val cookies = jar.loadForRequest(url)

        assertEquals(listOf("session"), cookies.map { it.name })
    }

    @Test
    fun `nothing is ever written back into the browser's store`() {
        var reads = 0
        val jar = SessionCookieJar { reads++; "session=abc" }
        val parsed = Cookie.parse(url, "server=1")!!

        jar.saveFromResponse(url, listOf(parsed))

        assertEquals("a response cookie must not trigger a read either", 0, reads)
    }

    @Test
    fun `the client really sends the cookie on the wire`() {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setBody("ok"))
            server.start()
            val client = OkHttpClient.Builder().cookieJar(SessionCookieJar { "session=abc; quality=hd" }).build()

            client.newCall(Request.Builder().url(server.url("/clip.mp4")).build()).execute().close()

            assertEquals("session=abc; quality=hd", server.takeRequest().getHeader("Cookie"))
        } finally {
            server.shutdown()
        }
    }
}
