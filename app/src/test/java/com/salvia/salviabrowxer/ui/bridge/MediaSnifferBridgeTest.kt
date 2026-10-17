package com.salvia.salviabrowxer.ui.bridge

import com.salvia.salviabrowxer.core.model.MediaCandidate
import org.junit.Assert.*
import org.junit.Test

class MediaSnifferBridgeTest {
    @Test fun `response filename reaches admission without becoming a URL guess`() {
        val results = mutableListOf<MediaCandidate>()
        val bridge = MediaSnifferBridge(onAdmitted = { results.add(it) })
        bridge.onMediaUrlFound(
            "https://example.org/post",
            "https://cdn.example.org/download/opaque-id",
            "application/octet-stream",
            "fetch",
            "",
            "attachment; filename=\"clip.mp4\""
        )
        assertEquals("mp4", results.single().extension)

        results.clear()
        bridge.onMediaUrlFound(
            "https://example.org/post",
            "https://cdn.example.org/download/opaque-id",
            "text/html",
            "fetch",
            "",
            "attachment; filename=\"clip.mp4\""
        )
        assertTrue(results.isEmpty())
    }

    @Test fun `object provenance needs a blob URL known kind and media MIME`() {
        val results = mutableListOf<MediaCandidate>()
        val bridge = MediaSnifferBridge(onAdmitted = { results.add(it) })
        val page = "https://example.org/feed"
        val blob = "blob:https://example.org/player"
        bridge.onMediaObjectUrl(page, blob, "video/mp4", "mse")
        assertTrue(results.single().isMediaSource)
        assertFalse(results.single().isBlobFile)
        results.clear()
        bridge.onMediaObjectUrl(page, blob, "video/mp4", "file")
        assertTrue(results.single().isBlobFile)
        results.clear()
        bridge.onMediaObjectUrl(page, "https://example.org/file", "video/mp4", "file")
        bridge.onMediaObjectUrl(page, blob, "text/html", "file")
        bridge.onMediaObjectUrl(page, blob, "video/mp4", "invented")
        assertTrue(results.isEmpty())
    }
}
