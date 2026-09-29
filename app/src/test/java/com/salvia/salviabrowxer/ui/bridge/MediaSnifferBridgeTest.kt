package com.salvia.salviabrowxer.ui.bridge

import com.salvia.salviabrowxer.core.model.MediaCandidate
import org.junit.Assert.*
import org.junit.Test

class MediaSnifferBridgeTest {
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
