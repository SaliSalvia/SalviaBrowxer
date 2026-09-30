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
        // Case-insensitive: the script text is ASCII and case is deterministic, but this avoids any
        // CI-only surprise from locale/classpath string handling.
        assertTrue(script.contains("getResponseHeader", ignoreCase = true))
        assertTrue(script.contains("addSourceBuffer", ignoreCase = true))
        assertTrue(script.contains("__salviaMseMime", ignoreCase = true))
    }

    @Test
    fun `the script installs itself once per document`() {
        assertTrue(script.contains("window.__salviaMediaHook"))
    }

    @Test
    fun `fragments are recognised by the range the page asked for`() {
        assertTrue(script.contains("isPartialRange"))
        assertTrue(script.contains("setRequestHeader"))
    }
}
