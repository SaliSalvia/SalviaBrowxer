package com.salvia.salviabrowxer.ui.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebViewSetupTest {

    private val webViewUserAgent =
        "Mozilla/5.0 (Linux; Android 15; Pixel 9) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.7049.38 Mobile Safari/537.36"

    @Test
    fun `mobile mode restores the exact provider user agent`() {
        assertEquals(webViewUserAgent, WebViewSetup.userAgentFor(webViewUserAgent, desktopMode = false))
    }

    @Test
    fun `desktop mode uses the installed WebView Chrome version`() {
        val desktop = WebViewSetup.userAgentFor(webViewUserAgent, desktopMode = true)

        assertEquals(
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.7049.38 Safari/537.36",
            desktop
        )
        assertFalse(desktop.contains("Android"))
        assertFalse(desktop.contains("Mobile"))
    }

    @Test
    fun `desktop mode falls back safely when the provider UA has no Chrome token`() {
        assertTrue(WebViewSetup.desktopUserAgentFor("Custom WebView").contains("Chrome/122.0.0.0"))
    }
}
