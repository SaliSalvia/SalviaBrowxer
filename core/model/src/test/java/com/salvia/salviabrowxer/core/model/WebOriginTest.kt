package com.salvia.salviabrowxer.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The identity every origin gate in the app is built on: an origin is `scheme://host[:port]` and
 * nothing else, and a URL that cannot be understood is never a match.
 */
class WebOriginTest {

    @Test
    fun `an origin is the scheme, the host and the port`() {
        assertEquals("https://example.com", WebOrigin.of("https://example.com/watch?v=1#t=10"))
        assertEquals("https://example.com", WebOrigin.of("HTTPS://Example.COM/Path"))
        assertEquals("https://example.com:8443", WebOrigin.of("https://example.com:8443/a/b"))
        assertEquals("http://192.168.1.5:8080", WebOrigin.of("http://192.168.1.5:8080/clip.mp4"))
    }

    @Test
    fun `credentials are not part of an origin`() {
        assertEquals("https://example.com", WebOrigin.of("https://user:secret@example.com/x"))
    }

    @Test
    fun `anything that is not an http url has no origin`() {
        assertNull(WebOrigin.of(null))
        assertNull(WebOrigin.of(""))
        assertNull(WebOrigin.of("   "))
        assertNull(WebOrigin.of("data:text/html,hi"))
        assertNull(WebOrigin.of("file:///sdcard/a.mp4"))
        assertNull(WebOrigin.of("not a url"))
        assertNull(WebOrigin.of("https:///nohost"))
        assertNull(WebOrigin.of("blob:https://example.com/1234"))
    }

    @Test
    fun `same origin is decided on the origin only`() {
        assertTrue(WebOrigin.same("https://example.com/a", "https://example.com/b?c=1"))
        assertFalse(WebOrigin.same("https://example.com/a", "https://example.com:8443/a"))
        assertFalse(WebOrigin.same("https://example.com/a", "https://cdn.example.com/a"))
        assertFalse(WebOrigin.same("https://example.com/a", "http://example.com/a"))
    }

    @Test
    fun `an unparsable url never matches, not even itself`() {
        assertFalse(WebOrigin.same(null, null))
        assertFalse(WebOrigin.same("blob:https://example.com/x", "blob:https://example.com/x"))
        assertFalse(WebOrigin.same("https://example.com/a", null))
    }

    @Test
    fun `a frame may speak for the page when it is not cross-origin`() {
        assertTrue(WebOrigin.matchesPage("https://example.com/watch", "https://example.com"))
        assertTrue(WebOrigin.matchesPage("https://example.com/watch", "https://example.com/embed"))
        // A frame that reported no origin at all is not treated as a different site.
        assertTrue(WebOrigin.matchesPage("https://example.com/watch", null))
        assertFalse(WebOrigin.matchesPage("https://example.com/watch", "https://ads.example"))
        assertFalse(WebOrigin.matchesPage("https://example.com/watch", "http://example.com"))
    }
}
