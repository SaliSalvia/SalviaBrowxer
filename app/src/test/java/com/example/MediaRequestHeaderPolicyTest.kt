package com.example

import com.example.engine.MediaRequestHeaderContextElement
import com.example.engine.MediaRequestHeaderPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaRequestHeaderPolicyTest {
    @Test
    fun `only low risk allowlisted headers are retained`() {
        val safe = MediaRequestHeaderPolicy.sanitize(
            mapOf(
                "Accept" to "video/*",
                "Accept-Language" to "en-US,en;q=0.9",
                "Origin" to "https://example.test",
                "Cookie" to "session=secret",
                "Authorization" to "Bearer secret",
                "Proxy-Authorization" to "Basic secret",
                "Referer" to "https://example.test/watch?token=secret",
                "User-Agent" to "Browser UA",
                "X-Custom-Auth" to "secret",
            ),
        )

        assertEquals("video/*", safe["accept"])
        assertEquals("en-US,en;q=0.9", safe["accept-language"])
        assertEquals("https://example.test", safe["origin"])
        assertFalse(safe.containsKey("cookie"))
        assertFalse(safe.containsKey("authorization"))
        assertFalse(safe.containsKey("proxy-authorization"))
        assertFalse(safe.containsKey("referer"))
        assertFalse(safe.containsKey("user-agent"))
        assertFalse(safe.containsKey("x-custom-auth"))
    }

    @Test
    fun `diagnostic summary remains available without making referer or user agent replayable`() {
        val headers = mapOf(
            "Referer" to "https://example.test/watch",
            "User-Agent" to "WebView UA",
        )
        val summary = MediaRequestHeaderPolicy.safeSummary(headers)
        assertTrue(summary.contains("referer:"))
        assertTrue(summary.contains("user-agent:"))
        assertTrue(MediaRequestHeaderPolicy.sanitize(headers).isEmpty())
    }

    @Test
    fun `control characters oversized values and non origin values are dropped`() {
        val safe = MediaRequestHeaderPolicy.sanitize(
            mapOf(
                "Accept" to "video/*\r\nX-Evil: true",
                "Accept-Language" to "x".repeat(257),
                "Origin" to "https://example.test/path?secret=1",
            ),
        )
        assertTrue(safe.isEmpty())
    }

    @Test
    fun `request context is replayed only to the exact canonical origin`() {
        val context = mapOf(
            "https://cdn.example.test" to mapOf(
                "Accept" to "video/*",
                "Accept-Language" to "en",
            ),
        )

        assertEquals(
            mapOf("accept" to "video/*", "accept-language" to "en"),
            MediaRequestHeaderPolicy.headersForUrl("https://cdn.example.test:443/clip?id=1", context),
        )
        assertTrue(
            MediaRequestHeaderPolicy.headersForUrl("https://other.example.test/clip", context).isEmpty(),
        )
        assertTrue(
            MediaRequestHeaderPolicy.headersForUrl("http://cdn.example.test/clip", context).isEmpty(),
        )
    }

    @Test
    fun `serialized contexts remain bounded sanitized and round trip`() {
        val context = mapOf(
            "https://cdn.example.test" to mapOf(
                "Accept" to "video/*",
                "Cookie" to "must-not-persist",
            ),
        )
        val decoded = MediaRequestHeaderPolicy.decodeContext(
            MediaRequestHeaderPolicy.encodeContext(context),
        )

        assertEquals(mapOf("accept" to "video/*"), decoded["https://cdn.example.test"])
        assertFalse(decoded.values.any { it.containsKey("cookie") })
    }

    @Test
    fun `request context is propagated across coroutine dispatchers and parallel work`() = runBlocking {
        val context = mapOf(
            "https://cdn.example.test" to mapOf("Accept" to "video/*"),
        )
        withContext(MediaRequestHeaderContextElement(context)) {
            val results = (1..4).map {
                async(Dispatchers.IO) {
                    MediaRequestHeaderContextElement.headersForUrl("https://cdn.example.test/seg$it")
                }
            }.awaitAll()
            assertEquals(4, results.size)
            assertTrue(results.all { it["accept"] == "video/*" })
            assertTrue(
                MediaRequestHeaderContextElement.headersForUrl("https://other.example.test/seg").isEmpty(),
            )
        }
    }
}
