package com.salvia.salviabrowxer.ui.utils

import com.salvia.salviabrowxer.core.model.PastedLinkKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeLinkResolverTest {

    @Test
    fun `home accepts a bare website and adds https`() {
        assertEquals("https://example.com/path", HomeLinkResolver.resolve(" example.com/path "))
        assertEquals(PastedLinkKind.WEB_PAGE, HomeLinkResolver.classify("example.com"))
    }

    @Test
    fun `bare direct media link is classified consistently with submission`() {
        assertEquals("https://cdn.example.com/clip.mp4", HomeLinkResolver.resolve("cdn.example.com/clip.mp4"))
        assertEquals(PastedLinkKind.MEDIA_FILE, HomeLinkResolver.classify("cdn.example.com/clip.mp4"))
    }

    @Test
    fun `home still rejects prose and non-web schemes`() {
        assertNull(HomeLinkResolver.resolve("a page about example.com"))
        assertNull(HomeLinkResolver.resolve("ftp://example.com/file.mp4"))
        assertNull(HomeLinkResolver.resolve("javascript:alert(1)"))
    }

    @Test
    fun `saved homepage resolves bare host and refuses script URLs`() {
        assertEquals("https://example.com", HomepageResolver.resolve("example.com"))
        assertEquals(Constants.DEFAULT_HOMEPAGE, HomepageResolver.resolve("javascript:alert(1)"))
    }
}
