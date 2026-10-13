package com.salvia.salviabrowxer.ui.utils

import com.salvia.salviabrowxer.core.model.MediaCandidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The request observer both the document and a page's service worker go through.
 *
 * What is tested here is the part that decides *how much* the app hears: a whole-file GET for a media
 * URL is reported once per window, a partial read or a POST is not reported at all, and a page that
 * names endless URLs cannot grow the map without bound.
 */
class MediaRequestObserverTest {

    private val pageUrl = "https://site.example/watch"
    private val mediaUrl = "https://cdn.example.com/clip.mp4"

    private var clock = 1_000L
    private val seen = mutableListOf<MediaCandidate>()

    private fun observer() = MediaRequestObserver(
        pageUrlProvider = { pageUrl },
        onCandidate = { seen += it },
        now = { clock }
    )

    @Test
    fun `a whole-file GET for a media URL is reported once, with the page it came from`() {
        val observer = observer()

        observer.onRequest(mediaUrl, "GET", null)

        assertEquals(1, seen.size)
        assertEquals(mediaUrl, seen[0].mediaUrl)
        assertEquals(pageUrl, seen[0].pageUrl)
    }

    @Test
    fun `a whole-file range is still a whole file`() {
        val observer = observer()

        observer.onRequest(mediaUrl, "GET", "bytes=0-")

        assertEquals(1, seen.size)
    }

    @Test
    fun `the same URL inside the window is reported once, and again after it`() {
        val observer = observer()

        observer.onRequest(mediaUrl, "GET", null)
        clock += MediaRequestObserver.DEDUPE_WINDOW_MILLIS - 1
        observer.onRequest(mediaUrl, "GET", null)
        assertEquals("a player re-fetching the same URL is not a new candidate", 1, seen.size)

        clock += 2
        observer.onRequest(mediaUrl, "GET", null)
        assertEquals(2, seen.size)
    }

    @Test
    fun `a partial read is a piece of something the player already has`() {
        val observer = observer()

        observer.onRequest(mediaUrl, "GET", "bytes=100-")

        assertTrue(seen.isEmpty())
    }

    @Test
    fun `only a GET is a download`() {
        val observer = observer()

        observer.onRequest(mediaUrl, "POST", null)
        observer.onRequest(mediaUrl, null, null)

        assertTrue(seen.isEmpty())
    }

    @Test
    fun `a blob URL is not a file the app can fetch`() {
        val observer = observer()

        observer.onRequest("blob:https://site.example/1234", "GET", null)

        assertTrue(seen.isEmpty())
    }

    @Test
    fun `a new document clears the window, so the same URL is reported again`() {
        val observer = observer()
        observer.onRequest(mediaUrl, "GET", null)

        observer.reset()
        observer.onRequest(mediaUrl, "GET", null)

        assertEquals(2, seen.size)
    }

    @Test
    fun `endless distinct URLs cannot grow the window without bound`() {
        val observer = observer()

        repeat(300) { index ->
            clock += MediaRequestObserver.DEDUPE_WINDOW_MILLIS + 1
            observer.onRequest("https://cdn.example.com/clip-$index.mp4", "GET", null)
        }

        assertEquals(300, seen.size)
        assertTrue(
            "tracked ${observer.trackedUrlCount} URLs",
            observer.trackedUrlCount <= MediaRequestObserver.MAX_TRACKED_URLS
        )
    }
}
