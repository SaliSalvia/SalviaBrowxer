package com.example.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class UrlInputNormalizerTest {

    private val searchTemplate = "https://search.example/?q=%s"

    @Test
    fun `scheme matching is case insensitive and upgrades non-local HTTP`() {
        assertEquals(
            "https://example.com/path",
            UrlInputNormalizer.normalize("HTTP://example.com/path", true, searchTemplate),
        )
        assertEquals(
            "https://example.com/path",
            UrlInputNormalizer.normalize("HTTPS://example.com/path", true, searchTemplate),
        )
    }

    @Test
    fun `local detection uses the parsed host instead of matching URL text`() {
        assertEquals(
            "https://notlocalhost.example/path",
            UrlInputNormalizer.normalize("http://notlocalhost.example/path", true, searchTemplate),
        )
        assertEquals(
            "https://example.com/path/localhost",
            UrlInputNormalizer.normalize("http://example.com/path/localhost", true, searchTemplate),
        )
        assertEquals(
            "https://notlocalhost.example/",
            UrlInputNormalizer.normalize("notlocalhost.example/", true, searchTemplate),
        )
    }

    @Test
    fun `loopback and private network hosts stay on HTTP`() {
        listOf(
            "localhost:8080/page",
            "router.localhost/admin",
            "127.0.0.1:5000",
            "10.20.30.40",
            "172.20.0.1",
            "192.168.1.1",
            "[::1]:8080",
        ).forEach { input ->
            assertEquals("http://$input", UrlInputNormalizer.normalize(input, true, searchTemplate))
        }
    }

    @Test
    fun `search input and the disabled upgrade setting are preserved`() {
        assertEquals(
            "https://search.example/?q=hello+world",
            UrlInputNormalizer.normalize("  hello world  ", true, searchTemplate),
        )
        assertEquals(
            "http://example.com/path",
            UrlInputNormalizer.normalize("HTTP://example.com/path", false, searchTemplate),
        )
        assertEquals("", UrlInputNormalizer.normalize("  ", true, searchTemplate))
    }
}
