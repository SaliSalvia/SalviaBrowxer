package com.salvia.salviabrowxer.core.model

import org.junit.Assert.*
import org.junit.Test

class MediaCandidateIndexTest {
    private fun candidate(n: Int, confidence: Float = 0.9f) = MediaCandidate(
        pageUrl = "https://example.org/feed", mediaUrl = "https://cdn.example.org/$n.mp4", confidence = confidence
    )

    @Test fun `new feed video is retained even after 24 stronger old detections`() {
        val old = (0..23).map { candidate(it) }
        val fresh = candidate(25, 0.7f)
        val merged = MediaCandidateIndex.merge(old, listOf(fresh), old[0].mediaUrl)
        assertEquals(24, merged.size)
        assertEquals(old[0], merged.first())
        assertTrue(merged.any { it.mediaUrl == fresh.mediaUrl })
    }

    @Test fun `stronger repeat retains row identity and explicit streaming provenance`() {
        val old = candidate(1, 0.7f).copy(isMediaSource = true)
        val later = candidate(1, 0.95f)
        val row = MediaCandidateIndex.merge(listOf(old), listOf(later), null).single()
        assertEquals(old.id, row.id)
        assertEquals(0.95f, row.confidence, 0.001f)
        assertTrue(row.isMediaSource)
    }

    @Test fun `lower confidence cannot erase file or live evidence`() {
        val old = candidate(1).copy(isBlobFile = true, isLive = true)
        val row = MediaCandidateIndex.merge(listOf(old), listOf(candidate(1, 0.5f)), null).single()
        assertTrue(row.isBlobFile)
        assertTrue(row.isLive)
        assertEquals(old.id, row.id)
    }
}
