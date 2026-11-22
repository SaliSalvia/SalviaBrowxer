package com.example

import com.example.engine.MediaCandidateRanker
import com.example.ui.DetectedMedia
import com.example.ui.MediaQualityOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaCandidateRankerTest {

    @Test
    fun `ad candidates are retained but ranked after normal media`() {
        val main = media(
            id = "main",
            title = "Main video",
            url = "https://cdn.example.com/watch/main.mp4",
            score = 180,
            height = 1080,
            area = 240_000L,
            isMain = true,
        )
        val ad = media(
            id = "ad",
            title = "Sponsored pre-roll",
            url = "https://ads.example.net/preroll/movie.mp4",
            score = 250,
            height = 720,
            area = 200_000L,
            isMain = true,
        )

        val ranked = MediaCandidateRanker.rankCandidates(listOf(ad, main))

        assertEquals(listOf("main", "ad"), ranked.map { it.media.id })
        assertEquals(MediaCandidateRanker.Role.AD, ranked.last().role)
        assertTrue(MediaCandidateRanker.prioritize(listOf(ad, main))[0].isMainVideo)
        assertFalse(MediaCandidateRanker.prioritize(listOf(ad))[0].isMainVideo)
    }

    @Test
    fun `related and preview candidates are demoted without being discarded`() {
        val main = media(
            id = "main",
            title = "Feature",
            url = "https://cdn.example.com/video/feature.mp4",
            score = 150,
            height = 720,
            area = 160_000L,
            isMain = true,
        )
        val suggested = media(
            id = "suggested",
            title = "Related video",
            url = "https://cdn.example.com/related/clip.mp4",
            score = 220,
            height = 1080,
            area = 300_000L,
            isMain = true,
        )

        val ranked = MediaCandidateRanker.rankCandidates(listOf(suggested, main))

        assertEquals(listOf("main", "suggested"), ranked.map { it.media.id })
        assertEquals(MediaCandidateRanker.Role.SUGGESTED, ranked.last().role)
    }

    @Test
    fun `audio remains behind video candidates even with a higher raw score`() {
        val video = media(
            id = "video",
            title = "Video",
            url = "https://cdn.example.com/video.mp4",
            score = 100,
            height = 720,
            area = 100_000L,
        )
        val audio = media(
            id = "audio",
            title = "Audio",
            url = "https://cdn.example.com/audio.m4a",
            score = 500,
            audioOnly = true,
        )

        val ranked = MediaCandidateRanker.rankCandidates(listOf(audio, video))

        assertEquals(listOf("video", "audio"), ranked.map { it.media.id })
        assertEquals(MediaCandidateRanker.Role.AUDIO, ranked.last().role)
        assertFalse(MediaCandidateRanker.prioritize(listOf(audio))[0].isMainVideo)
    }

    @Test
    fun `ranking preserves media metadata and legacy order for ordinary candidates`() {
        val first = media(
            id = "first",
            title = "First",
            url = "https://cdn.example.com/first.mp4",
            score = 130,
            height = 720,
            area = 90_000L,
        ).copy(
            qualities = listOf(
                MediaQualityOption(
                    label = "720p MP4",
                    resolutionHeight = 720,
                    url = "https://cdn.example.com/first.mp4",
                    format = "MP4",
                    mimeType = "video/mp4",
                ),
            ),
        )
        val second = media(
            id = "second",
            title = "Second",
            url = "https://cdn.example.com/second.mp4",
            score = 100,
            height = 480,
            area = 70_000L,
        )

        val ranked = MediaCandidateRanker.prioritize(listOf(first, second))

        assertEquals(listOf("first", "second"), ranked.map { it.id })
        assertEquals(first.primaryUrl, ranked[0].primaryUrl)
        assertEquals(first.qualities, ranked[0].qualities)
        assertEquals(first.title, ranked[0].title)
        assertTrue(ranked[0].isMainVideo)
        assertFalse(ranked[1].isMainVideo)
    }

    private fun media(
        id: String,
        title: String,
        url: String,
        score: Int,
        height: Int = 0,
        area: Long = 0L,
        isMain: Boolean = false,
        audioOnly: Boolean = false,
    ) = DetectedMedia(
        id = id,
        title = title,
        sourcePageUrl = "https://example.com/watch",
        primaryUrl = url,
        height = height,
        domAreaPx = area,
        priorityScore = score,
        isMainVideo = isMain,
        isAudioOnly = audioOnly,
    )
}
