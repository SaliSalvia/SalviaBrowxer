package com.salvia.salviabrowxer.ui.utils

import com.salvia.salviabrowxer.core.model.MediaUrlRules
import com.salvia.salviabrowxer.core.model.SniffOrigin
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The script is built by interpolation, so the two ways it can silently break are a placeholder
 * that was never substituted and an origin name Kotlin drops on the floor. Both are checkable
 * without a WebView.
 */
class MediaSnifferScriptTest {

    private val script = MediaSniffer.script

    @Test
    fun `every placeholder is substituted`() {
        assertFalse(script.contains("__MEDIA_EXT__"))
        assertFalse(script.contains("__NON_MEDIA_EXT__"))
        assertFalse(script.contains("__SEGMENT_EXT__"))
        assertTrue(script.contains(MediaUrlRules.EXTENSION_ALTERNATION))
    }

    @Test
    fun `the script reports on the bridge the store registers`() {
        assertTrue(script.contains(MediaSniffer.BRIDGE_NAME))
        assertTrue(script.contains("onMediaUrlFound"))
    }

    @Test
    fun `every origin Kotlin understands is one the script can send`() {
        SniffOrigin.entries.forEach { origin ->
            assertTrue("script never sends ${origin.wireName}", script.contains("'${origin.wireName}'"))
        }
    }

    @Test
    fun `the response-header hooks are present`() {
        // These three are what make extension-less CDN media detectable; without them the script is
        // back to guessing from the URL and the whole exercise is pointless.
        // One hook per loader: XHR, fetch, and MSE (which learns the codec from the SourceBuffer
        // it was handed). MSE mime is tracked per MediaSource now, so there is no page global
        // left to assert on.
        assertTrue(script.contains("getResponseHeader"))
        assertTrue(script.contains("headers.get"))
        assertTrue(script.contains("addSourceBuffer"))
    }

    @Test
    fun `the script installs itself once per document`() {
        assertTrue(script.contains("window.__salviaMediaHook"))
    }

    @Test
    fun `a media element reports the type its markup declared`() {
        // `<source type="video/mp4">` and lazy data-* players name the container in the markup.
        // Carrying it across the bridge is what lets an extension-less source URL be admitted on
        // the same evidence the DOM scanner already trusts, instead of being dropped here.
        assertTrue(script.contains("declaredType"))
        assertTrue(script.contains("getAttribute('type')"))
    }

    @Test
    fun `fragments are recognised by the range the page asked for`() {
        assertTrue(script.contains("isPartialRange"))
        assertTrue(script.contains("setRequestHeader"))
    }
}
