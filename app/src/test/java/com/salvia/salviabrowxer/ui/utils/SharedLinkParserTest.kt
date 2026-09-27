package com.salvia.salviabrowxer.ui.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SharedLinkParserTest {

    @Test
    fun `a link inside shared prose is found`() {
        assertEquals(
            "https://example.com/clip",
            SharedLinkParser.firstUrl("Look at this https://example.com/clip it is great")
        )
    }

    @Test
    fun `trailing punctuation is not part of the link`() {
        assertEquals("https://example.com/a", SharedLinkParser.firstUrl("see https://example.com/a."))
        assertEquals("https://example.com/b", SharedLinkParser.firstUrl("(https://example.com/b)"))
    }

    @Test
    fun `text without a web link yields nothing`() {
        assertNull(SharedLinkParser.firstUrl("just a thought"))
        assertNull(SharedLinkParser.firstUrl(null))
    }

    @Test
    fun `non-web schemes are ignored`() {
        assertNull(SharedLinkParser.firstUrl("ftp://example.com/file"))
        assertNull(SharedLinkParser.fromViewData("mailto:someone@example.com"))
        assertNull(SharedLinkParser.fromViewData("tel:+123456"))
        assertNull(SharedLinkParser.fromViewData("market://details?id=com.example"))
    }

    @Test
    fun `view data that is neither a url nor a host yields nothing`() {
        assertNull(SharedLinkParser.fromViewData("just some words"))
    }

    @Test
    fun `view data keeps an explicit http url`() {
        assertEquals("http://example.com/page", SharedLinkParser.fromViewData("http://example.com/page"))
        assertEquals("https://example.com", SharedLinkParser.fromViewData("https://example.com"))
    }

    @Test
    fun `view data with a bare host is normalised to https`() {
        assertEquals("https://example.com/path", SharedLinkParser.fromViewData("example.com/path"))
    }

    @Test
    fun `view data with nothing usable yields nothing`() {
        assertNull(SharedLinkParser.fromViewData(""))
        assertNull(SharedLinkParser.fromViewData(null))
    }
}
