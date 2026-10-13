package com.salvia.salviabrowxer.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The referer rule the transfer follows, which is the browser's own default:
 * `strict-origin-when-cross-origin`, with the referrer stripped on a downgrade.
 */
class SessionRefererTest {

    @Test
    fun `same site keeps the whole page url`() {
        assertEquals(
            "https://video.example.com/watch?v=42",
            SessionReferer.headerFor("https://video.example.com/watch?v=42", "https://video.example.com/media/clip.mp4")
        )
    }

    @Test
    fun `a media subdomain of the page counts as the same site`() {
        assertEquals(
            "https://www.example.com/watch",
            SessionReferer.headerFor("https://www.example.com/watch", "https://cdn.example.com/clip.mp4")
        )
    }

    @Test
    fun `another site gets only the origin`() {
        assertEquals(
            "https://news.example/",
            SessionReferer.headerFor("https://news.example/story/1?private=token", "https://cdn.other.example/clip.mp4")
        )
    }

    @Test
    fun `two unrelated sites under a shared public suffix are not one site`() {
        // Without a public-suffix list `bbc.co.uk` and `itv.co.uk` would share the "site" `co.uk`,
        // and each would be sent the other's whole page URL.
        assertEquals(
            "https://bbc.co.uk/",
            SessionReferer.headerFor("https://bbc.co.uk/iplayer/episode/1", "https://itv.co.uk/clip.mp4")
        )
    }

    @Test
    fun `sibling subdomains under a public suffix are one site`() {
        assertEquals(
            "https://www.example.co.uk/watch",
            SessionReferer.headerFor("https://www.example.co.uk/watch", "https://cdn.example.co.uk/clip.mp4")
        )
    }

    @Test
    fun `an https page never refers an http target`() {
        assertNull(SessionReferer.headerFor("https://example.com/watch", "http://cdn.example.com/clip.mp4"))
    }

    @Test
    fun `an http page may refer an https target`() {
        assertEquals(
            "http://example.com/watch",
            SessionReferer.headerFor("http://example.com/watch", "https://example.com/clip.mp4")
        )
    }

    @Test
    fun `a page that cannot refer sends nothing`() {
        assertNull(SessionReferer.headerFor(null, "https://example.com/clip.mp4"))
        assertNull(SessionReferer.headerFor("   ", "https://example.com/clip.mp4"))
        assertNull(SessionReferer.headerFor("about:blank", "https://example.com/clip.mp4"))
        assertNull(SessionReferer.headerFor("data:text/html,<p>hi", "https://example.com/clip.mp4"))
        assertNull(SessionReferer.headerFor("blob:https://example.com/1a2b", "https://example.com/clip.mp4"))
    }

    @Test
    fun `a pasted direct link never refers itself`() {
        val file = "https://cdn.example.com/clip.mp4"

        assertNull(SessionReferer.headerFor(file, file))
    }

    @Test
    fun `a directly opened playlist still refers its own segments`() {
        // Opening a playlist URL puts it in the address bar; the segments it names are separate
        // requests, and the browser does send the playlist as their referrer.
        assertEquals(
            "https://cdn.example.com/master.m3u8",
            SessionReferer.headerFor("https://cdn.example.com/master.m3u8", "https://cdn.example.com/seg/1.ts")
        )
    }

    @Test
    fun `a port is part of the origin and not of the host comparison`() {
        assertEquals(
            "https://example.com:8443/",
            SessionReferer.headerFor("https://example.com:8443/watch", "https://cdn.other.example/clip.mp4")
        )
    }

    @Test
    fun `two different ip literals are different sites`() {
        assertEquals(
            "http://10.0.0.5/",
            SessionReferer.headerFor("http://10.0.0.5/watch", "http://10.0.0.9/clip.mp4")
        )
    }
}
