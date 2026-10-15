package com.salvia.salviabrowxer.ui.utils

import com.salvia.salviabrowxer.core.model.MediaCandidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MainContentSelectorTest {

    private val page = "https://example.org/watch"

    private fun video(
        url: String,
        pageUrl: String = page,
        durationMs: Long? = null,
        confidence: Float = 0.8f,
        sizeBytes: Long? = null
    ) = MediaCandidate(
        pageUrl = pageUrl,
        mediaUrl = url,
        mimeType = "video/mp4",
        extension = "mp4",
        duration = durationMs,
        estimatedSize = sizeBytes,
        confidence = confidence
    )

    @Test
    fun `the visible player's url wins over an equally strong unrelated candidate`() {
        val visible = video("https://cdn.example.org/main.mp4", durationMs = 300_000)
        val other = video("https://cdn.example.org/sidebar.mp4", durationMs = 300_000)
        val selected = MainContentSelector.select(listOf(other, visible), visibleUrl = visible.mediaUrl, pageUrl = page)
        assertEquals(visible.mediaUrl, selected?.mediaUrl)
    }

    @Test
    fun `a long video beats a short one from the same page`() {
        val long = video("https://cdn.example.org/feature.mp4", durationMs = 600_000)
        val short = video("https://cdn.example.org/teaser.mp4", durationMs = 6_000)
        val selected = MainContentSelector.select(listOf(short, long), visibleUrl = null, pageUrl = page)
        assertEquals(long.mediaUrl, selected?.mediaUrl)
    }

    @Test
    fun `a known ad host is never selected as the main video`() {
        val ad = video("https://static.doubleclick.net/creativemp4.mp4", durationMs = 300_000)
        val content = video("https://cdn.example.org/main.mp4", durationMs = 300_000)
        val onlyAd = MainContentSelector.select(listOf(ad), visibleUrl = ad.mediaUrl, pageUrl = page)
        assertNull("an ad must not be the one-tap file even when it is the only candidate", onlyAd)
        val selected = MainContentSelector.select(listOf(ad, content), visibleUrl = null, pageUrl = page)
        assertEquals(content.mediaUrl, selected?.mediaUrl)
    }

    @Test
    fun `an ad-shaped path is demoted below a plain one`() {
        val preroll = video("https://cdn.example.org/media/preroll/720p.mp4", durationMs = 300_000)
        val content = video("https://cdn.example.org/media/main/720p.mp4", durationMs = 300_000)
        val selected = MainContentSelector.select(listOf(preroll, content), visibleUrl = null, pageUrl = page)
        assertEquals(content.mediaUrl, selected?.mediaUrl)
    }

    @Test
    fun `a short pre-roll occupying the visible player can still lose to the real video`() {
        // A pre-roll carries two tell-tales at once — ad-shaped delivery (`preroll` in the path,
        // -45) and a six-second length (-28). Player evidence (+50) alone does not beat that
        // combination next to a real video's duration bonus (+12): the player is the strongest
        // *hint*, and a hint is all it is (a clean-URL short clip on the visible player can still
        // win, which is why the tray and its labels stay one long-press away).
        val preroll = video("https://cdn.example.org/preroll.mp4", durationMs = 6_000, confidence = 0.9f)
        val main = video("https://cdn.example.org/main.mp4", durationMs = 900_000, confidence = 0.9f)
        val selected = MainContentSelector.select(listOf(preroll, main), visibleUrl = preroll.mediaUrl, pageUrl = page)
        assertEquals(main.mediaUrl, selected?.mediaUrl)
    }

    @Test
    fun `video beats audio for the main slot`() {
        val audio = MediaCandidate(
            pageUrl = page, mediaUrl = "https://cdn.example.org/track.mp3",
            mimeType = "audio/mpeg", extension = "mp3", duration = 240_000, confidence = 0.9f
        )
        val clip = video("https://cdn.example.org/clip.mp4", durationMs = 240_000, confidence = 0.5f)
        val selected = MainContentSelector.select(listOf(audio, clip), visibleUrl = null, pageUrl = page)
        assertEquals(clip.mediaUrl, selected?.mediaUrl)
    }

    @Test
    fun `an offerable hls playlist can be the main file`() {
        val hls = MediaCandidate(
            pageUrl = page, mediaUrl = "https://cdn.example.org/master.m3u8",
            mimeType = "application/vnd.apple.mpegurl", extension = "m3u8",
            duration = 480_000, confidence = 0.8f
        )
        val selected = MainContentSelector.select(listOf(hls), visibleUrl = hls.mediaUrl, pageUrl = page)
        assertEquals(hls.mediaUrl, selected?.mediaUrl)
    }

    @Test
    fun `live and unproven blob streams are never selected`() {
        val live = video("https://cdn.example.org/live.m3u8").copy(isLive = true)
        val blobStream = video("blob:https://example.org/player").copy(isMediaSource = true)
        assertNull(MainContentSelector.select(listOf(live), visibleUrl = live.mediaUrl, pageUrl = page))
        assertNull(MainContentSelector.select(listOf(blobStream), visibleUrl = blobStream.mediaUrl, pageUrl = page))
    }

    @Test
    fun `a proven blob file can be selected`() {
        val blobFile = video("blob:https://example.org/data").copy(isBlobFile = true, estimatedSize = 40L * 1024 * 1024)
        val selected = MainContentSelector.select(listOf(blobFile), visibleUrl = blobFile.mediaUrl, pageUrl = page)
        assertEquals(blobFile.mediaUrl, selected?.mediaUrl)
    }

    @Test
    fun `a candidate the page itself carried outranks a frame's sighting`() {
        val mainFrame = video("https://cdn.example.org/main.mp4", pageUrl = page, durationMs = 300_000)
        val frame = video("https://cdn.example.org/embedded.mp4", pageUrl = "https://embed.example.net/frame", durationMs = 300_000)
        val selected = MainContentSelector.select(listOf(frame, mainFrame), visibleUrl = null, pageUrl = page)
        assertEquals(mainFrame.mediaUrl, selected?.mediaUrl)
    }

    @Test
    fun `a resolution named in the url is a tiebreaker not a verdict`() {
        val hd = video("https://cdn.example.org/media-1080p.mp4", durationMs = 300_000)
        val sd = video("https://cdn.example.org/media-360p.mp4", durationMs = 300_000)
        val selected = MainContentSelector.select(listOf(sd, hd), visibleUrl = null, pageUrl = page)
        assertEquals(hd.mediaUrl, selected?.mediaUrl)
    }

    @Test
    fun `nothing offerable means nothing selected`() {
        val unknown = MediaCandidate(
            pageUrl = page, mediaUrl = "https://cdn.example.org/file.bin",
            mimeType = "application/octet-stream", extension = "bin"
        )
        assertNull(MainContentSelector.select(listOf(unknown), visibleUrl = unknown.mediaUrl, pageUrl = page))
        assertNull(MainContentSelector.select(emptyList(), visibleUrl = null, pageUrl = page))
    }

    @Test
    fun `an empty page url does not disqualify anyone`() {
        val clip = video("https://cdn.example.org/clip.mp4", pageUrl = page, durationMs = 300_000)
        val selected = MainContentSelector.select(listOf(clip), visibleUrl = null, pageUrl = null)
        assertEquals(clip.mediaUrl, selected?.mediaUrl)
    }

    @Test
    fun `score never overflows on adversarial input`() {
        val hostile = MediaCandidate(
            pageUrl = page,
            mediaUrl = "https://static.doubleclick.net/ads/preroll/vast/2160p.mp4?doubleclick=1",
            mimeType = "video/mp4", extension = "mp4", duration = 1L, confidence = 1f
        )
        // Every demotion at once must stay a demotion — an Int.MIN_VALUE plus a penalty would wrap
        // around to a huge positive score if the no-kind early return were ever removed.
        val score = MainContentSelector.score(hostile, visibleUrl = hostile.mediaUrl, pageUrl = page)
        org.junit.Assert.assertTrue("hostile candidate must score below the threshold: $score", score < MainContentSelector.MINIMUM_SCORE)
        assertNull(MainContentSelector.select(listOf(hostile), visibleUrl = hostile.mediaUrl, pageUrl = page))
    }
}
