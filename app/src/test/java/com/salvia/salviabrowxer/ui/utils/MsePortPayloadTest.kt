package com.salvia.salviabrowxer.ui.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The decoder for what a page posts to the recording port.
 *
 * Every message here comes from a web page, so the only two acceptable outcomes are "understood" and
 * "ignored": an unreadable message must never throw inside the WebView listener, and it must never be
 * guessed at either.
 */
class MsePortPayloadTest {

    @Test
    fun `an open message carries the mime the page is about to feed`() {
        assertEquals(
            MsePortPayload.Open("video/mp4; codecs=\"avc1.4d401f\""),
            decodeMseControl("{\"t\":\"open\",\"mime\":\"video/mp4; codecs=\\\"avc1.4d401f\\\"\"}")
        )
    }

    @Test
    fun `an open message without a mime is still an open`() {
        assertEquals(MsePortPayload.Open(""), decodeMseControl("{\"t\":\"open\"}"))
    }

    @Test
    fun `a cap message is the page saying it stopped feeding`() {
        assertEquals(MsePortPayload.Cap, decodeMseControl("{\"t\":\"cap\"}"))
    }

    @Test
    fun `anything unrecognised is ignored rather than guessed at`() {
        assertEquals(MsePortPayload.Ignored, decodeMseControl(null))
        assertEquals(MsePortPayload.Ignored, decodeMseControl(""))
        assertEquals(MsePortPayload.Ignored, decodeMseControl("   "))
        assertEquals(MsePortPayload.Ignored, decodeMseControl("not json"))
        assertEquals(MsePortPayload.Ignored, decodeMseControl("[1,2,3]"))
        assertEquals(MsePortPayload.Ignored, decodeMseControl("{\"t\":\"segments\"}"))
        assertEquals(MsePortPayload.Ignored, decodeMseControl("{\"t\":\"open\""))
    }

    @Test
    fun `a frame announcement carries the URL the frame claims`() {
        assertEquals(
            MsePortPayload.Frame("https://site.example/embed/1"),
            decodeMseControl("{\"t\":\"frame\",\"u\":\"https://site.example/embed/1\",\"m\":false}")
        )
    }

    @Test
    fun `a frame announcement without a URL is still a frame, and registers nothing`() {
        assertEquals(MsePortPayload.Frame(""), decodeMseControl("{\"t\":\"frame\"}"))
        assertFalse(frameClaimAccepted("", "https://site.example"))
    }

    @Test
    fun `a claim counts only when it agrees with the origin WebKit reported`() {
        assertTrue(frameClaimAccepted("https://site.example/watch?v=1", "https://site.example"))
        assertTrue(frameClaimAccepted("https://site.example:8443/embed", "https://site.example:8443"))
    }

    @Test
    fun `a frame cannot claim to be another site`() {
        assertFalse(frameClaimAccepted("https://other.example/x", "https://site.example"))
        assertFalse(frameClaimAccepted("http://site.example/x", "https://site.example"))
    }

    @Test
    fun `an unparsable claim registers nothing`() {
        assertFalse(frameClaimAccepted("about:blank", "null"))
        assertFalse(frameClaimAccepted("data:text/html,hi", "null"))
        assertFalse(frameClaimAccepted("https://site.example/x", null))
    }

    @Test
    fun `an oversized message is refused before it is parsed`() {
        val huge = "{\"t\":\"open\",\"mime\":\"video/mp4\",\"pad\":\"" + "x".repeat(4096) + "\"}"
        assertTrue(huge.length > MSE_CONTROL_MAX_CHARS)
        assertEquals(MsePortPayload.Ignored, decodeMseControl(huge))
    }

    @Test
    fun `a mime longer than anything real is cut, not carried`() {
        val payload = decodeMseControl("{\"t\":\"open\",\"mime\":\"" + "v".repeat(400) + "\"}")
        assertEquals(MsePortPayload.Open("v".repeat(MSE_MIME_MAX_CHARS)), payload)
    }
}
