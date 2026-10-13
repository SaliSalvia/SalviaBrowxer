package com.salvia.salviabrowxer.data.datastore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The tab list as it survives a process death.
 *
 * The decoder is written to be total — anything unreadable becomes "no tabs" — because the alternative
 * is a browser that refuses to start because a file it wrote itself was truncated by the system. These
 * tests pin that promise down, along with the two things that must never be restored: a private tab,
 * and a page that is not a page.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SessionCodecTest {

    @Test
    fun `tabs survive a round trip`() {
        val tabs = listOf(
            SessionTab(id = "a", url = "https://example.com/watch?v=1", title = "Watch"),
            SessionTab(id = "b", url = "https://news.example.org/", title = "News")
        )

        assertEquals(tabs, SessionCodec.decode(SessionCodec.encode(tabs)))
    }

    @Test
    fun `anything unreadable is no tabs rather than a failure`() {
        assertEquals(emptyList<SessionTab>(), SessionCodec.decode(null))
        assertEquals(emptyList<SessionTab>(), SessionCodec.decode(""))
        assertEquals(emptyList<SessionTab>(), SessionCodec.decode("   "))
        assertEquals(emptyList<SessionTab>(), SessionCodec.decode("not json"))
        assertEquals(emptyList<SessionTab>(), SessionCodec.decode("[{\"url\":"))
        assertEquals(emptyList<SessionTab>(), SessionCodec.decode("{\"url\":\"https://example.com\"}"))
        assertEquals(emptyList<SessionTab>(), SessionCodec.decode("[1,2,3]"))
    }

    @Test
    fun `a half-written entry costs that entry and nothing else`() {
        val raw = "[{\"id\":\"a\",\"url\":\"https://example.com\",\"title\":\"A\"},7,{\"id\":\"c\"}]"

        val decoded = SessionCodec.decode(raw)

        assertEquals(listOf("a"), decoded.map { it.id })
    }

    @Test
    fun `only pages are restorable`() {
        assertTrue(SessionCodec.isRestorable("https://example.com/x"))
        assertTrue(SessionCodec.isRestorable("HTTP://example.com"))
        assertFalse(SessionCodec.isRestorable("about:blank"))
        assertFalse(SessionCodec.isRestorable("file:///sdcard/clip.mp4"))
        assertFalse(SessionCodec.isRestorable("data:text/html,hi"))
        assertFalse(SessionCodec.isRestorable("chrome://settings"))
        assertFalse(SessionCodec.isRestorable(""))
    }

    @Test
    fun `a blob or data tab is dropped instead of coming back empty`() {
        val raw = SessionCodec.encode(
            listOf(
                SessionTab(id = "a", url = "blob:https://example.com/1234", title = "Blob"),
                SessionTab(id = "b", url = "https://example.com", title = "")
            )
        )

        val decoded = SessionCodec.decode(raw)

        assertEquals(listOf("b"), decoded.map { it.id })
        assertEquals("an empty title falls back to the url by the caller", "", decoded.single().title)
    }

    @Test
    fun `the list cannot grow without bound`() {
        val many = (1..100).map { SessionTab(id = "t$it", url = "https://example.com/$it", title = "T$it") }

        val decoded = SessionCodec.decode(SessionCodec.encode(many))

        assertEquals(SessionCodec.MAX_TABS, decoded.size)
        assertEquals("the first tabs are the ones kept", "https://example.com/1", decoded.first().url)
    }

    @Test
    fun `a missing id is still a usable tab`() {
        val decoded = SessionCodec.decode("[{\"url\":\"https://example.com\",\"title\":\"X\"}]")

        assertEquals(1, decoded.size)
        assertTrue("a synthesised id must be non-blank", decoded.single().id.isNotBlank())
        assertEquals("https://example.com", decoded.single().url)
    }
}
