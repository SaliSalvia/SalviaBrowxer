package com.salvia.salviabrowxer.ui.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AddressBarResolverTest {

    // ── Explicit URLs pass through untouched ──
    @Test
    fun `full https url passes through`() {
        assertEquals("https://example.com/page?q=1", AddressBarResolver.resolve("https://example.com/page?q=1"))
    }

    @Test
    fun `full http url passes through`() {
        assertEquals("http://neverssl.com", AddressBarResolver.resolve("http://neverssl.com"))
    }

    @Test
    fun `uppercase scheme is accepted`() {
        assertEquals("HTTPS://Example.com", AddressBarResolver.resolve("HTTPS://Example.com"))
    }

    @Test
    fun `file and about schemes pass through`() {
        assertEquals("file:///sdcard/video.mp4", AddressBarResolver.resolve("file:///sdcard/video.mp4"))
        assertEquals("about:blank", AddressBarResolver.resolve("about:blank"))
    }

    // ── Host-like input gets https prefix ──
    @Test
    fun `dotted host becomes https url`() {
        assertEquals("https://example.com", AddressBarResolver.resolve("example.com"))
    }

    @Test
    fun `host with path becomes https url`() {
        assertEquals("https://en.wikipedia.org/wiki/Android", AddressBarResolver.resolve("en.wikipedia.org/wiki/Android"))
    }

    @Test
    fun `localhost becomes https url`() {
        assertEquals("https://localhost", AddressBarResolver.resolve("localhost"))
        assertEquals("https://localhost:8080", AddressBarResolver.resolve("localhost:8080"))
    }

    @Test
    fun `ip address becomes https url`() {
        assertEquals("https://192.168.1.1", AddressBarResolver.resolve("192.168.1.1"))
    }

    // ── Search fallback ──
    @Test
    fun `single word searches google`() {
        assertEquals("https://www.google.com/search?q=wikipedia", AddressBarResolver.resolve("wikipedia"))
    }

    @Test
    fun `multi word query is url encoded`() {
        assertEquals(
            "https://www.google.com/search?q=best+headphones+2026",
            AddressBarResolver.resolve("best headphones 2026")
        )
    }

    @Test
    fun `persian query searches google`() {
        assertEquals("https://www.google.com/search?q=%D8%B3%D8%A7%D9%84%D8%A7%D8%B1", AddressBarResolver.resolve("سالار"))
    }

    @Test
    fun `empty input returns null`() {
        assertNull(AddressBarResolver.resolve(""))
        assertNull(AddressBarResolver.resolve("   "))
    }

    @Test
    fun `malformed scheme without slashes searches`() {
        // "https:foo" is not a valid URL — should fall back to search
        assertEquals("https://www.google.com/search?q=https%3Afoo", AddressBarResolver.resolve("https:foo"))
    }

    @Test
    fun `domain in sentence still searches`() {
        assertEquals(
            "https://www.google.com/search?q=what+is+example.com",
            AddressBarResolver.resolve("what is example.com")
        )
    }

    // ── Custom search engine ──
    @Test
    fun `duckduckgo engine is honored`() {
        assertEquals("https://duckduckgo.com/?q=cats", AddressBarResolver.resolve("cats", "DuckDuckGo"))
    }

    @Test
    fun `unknown engine falls back to google`() {
        assertEquals("https://www.google.com/search?q=cats", AddressBarResolver.resolve("cats", "Ask Jeeves"))
    }

    // ── Predicates ──
    @Test
    fun `hasExplicitScheme detects schemes case-insensitively`() {
        assertTrue(AddressBarResolver.hasExplicitScheme("HTTP://a.com"))
        assertTrue(AddressBarResolver.hasExplicitScheme("about:blank"))
        assertFalse(AddressBarResolver.hasExplicitScheme("example.com"))
    }

    @Test
    fun `looksLikeHost rejects sentences`() {
        assertFalse(AddressBarResolver.looksLikeHost("how to bake bread"))
        assertTrue(AddressBarResolver.looksLikeHost("example.com"))
        assertTrue(AddressBarResolver.looksLikeHost("localhost:3000"))
    }
}
