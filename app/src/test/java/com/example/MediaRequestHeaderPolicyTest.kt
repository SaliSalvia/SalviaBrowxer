package com.example

import com.example.engine.MediaRequestHeaderPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaRequestHeaderPolicyTest {
    @Test
    fun `only non credential request metadata is retained`() {
        val safe = MediaRequestHeaderPolicy.sanitize(
            mapOf(
                "Accept" to "video/*",
                "Referer" to "https://example.test/watch",
                "Cookie" to "session=secret",
                "Authorization" to "Bearer secret",
                "Proxy-Authorization" to "Basic secret",
            ),
        )

        assertEquals("video/*", safe["accept"])
        assertEquals("https://example.test/watch", safe["referer"])
        assertFalse(safe.containsKey("cookie"))
        assertFalse(safe.containsKey("authorization"))
        assertFalse(safe.containsKey("proxy-authorization"))
    }

    @Test
    fun `unsafe values with control characters or excessive length are dropped`() {
        val safe = MediaRequestHeaderPolicy.sanitize(
            mapOf(
                "Origin" to "https://example.test\r\nX-Evil: true",
                "Accept-Language" to "x".repeat(1025),
            ),
        )
        assertTrue(safe.isEmpty())
    }
}
