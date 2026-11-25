package com.example

import com.example.engine.MediaRequestHeaderStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaRequestHeaderStoreTest {
    @Test
    fun `capture is promoted only after media evidence and stays tab scoped`() {
        var now = 1_000L
        val store = MediaRequestHeaderStore(clock = { now })
        val mediaUrl = "https://cdn.example.test/video?id=5"

        store.capture(
            tabId = "normal-1",
            url = mediaUrl,
            method = "GET",
            isMainFrame = false,
            headers = mapOf("Accept" to "video/*", "Cookie" to "private=secret"),
            mediaEvidence = false,
        )
        assertTrue(store.snapshot("normal-1", mediaUrl).isEmpty())

        store.markMedia("normal-1", mediaUrl)
        assertEquals(
            mapOf("https://cdn.example.test" to mapOf("accept" to "video/*")),
            store.snapshot("normal-1", mediaUrl),
        )
        assertTrue(store.snapshot("private-1", mediaUrl).isEmpty())

        now += 60_000L
        store.clearTab("normal-1")
        assertTrue(store.snapshot("normal-1", mediaUrl).isEmpty())
    }

    @Test
    fun `only media evidenced origins are included and expired entries are purged`() {
        var now = 1_000L
        val store = MediaRequestHeaderStore(clock = { now })
        val main = "https://media.example.test/master.m3u8"
        val segment = "https://segments.example.test/part.m4s"
        val image = "https://images.example.test/poster.webp"

        store.capture("tab", main, "GET", false, mapOf("Accept" to "application/vnd.apple.mpegurl"), true)
        store.capture("tab", segment, "GET", false, mapOf("Accept" to "video/mp4"), true)
        store.capture("tab", image, "GET", false, mapOf("Accept" to "image/webp"), false)

        val snapshot = store.snapshot("tab", main)
        assertTrue(snapshot.containsKey("https://media.example.test"))
        assertTrue(snapshot.containsKey("https://segments.example.test"))
        assertTrue(!snapshot.containsKey("https://images.example.test"))

        now += 11L * 60L * 1000L
        assertTrue(store.snapshot("tab", main).isEmpty())
    }

    @Test
    fun `non get and main frame requests are not retained`() {
        val store = MediaRequestHeaderStore()
        val headers = mapOf("Accept" to "video/*")
        store.capture("tab", "https://cdn.example.test/a.mp4", "POST", false, headers, true)
        store.capture("tab", "https://cdn.example.test/b.mp4", "GET", true, headers, true)

        assertTrue(store.snapshot("tab", "https://cdn.example.test/a.mp4").isEmpty())
        assertTrue(store.snapshot("tab", "https://cdn.example.test/b.mp4").isEmpty())
    }
}
