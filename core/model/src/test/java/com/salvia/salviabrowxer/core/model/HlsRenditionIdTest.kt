package com.salvia.salviabrowxer.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HlsRenditionIdTest {

    @Test
    fun `a video-only rendition round-trips`() {
        val url = "https://cdn.example/hls/high/index.m3u8"
        assertEquals(HlsSelection.Video(url), HlsRenditionId.parse(HlsRenditionId.video(url)))
    }

    @Test
    fun `a merged rendition keeps both playlist urls`() {
        val video = "https://cdn.example/hls/high/index.m3u8"
        val audio = "https://cdn.example/hls/audio/en/index.m3u8"
        assertEquals(HlsSelection.Merged(video, audio), HlsRenditionId.parse(HlsRenditionId.merged(video, audio)))
    }

    @Test
    fun `an hls selection is never mistaken for a dash selection`() {
        assertNull(DashRenditionId.parse(HlsRenditionId.merged("https://cdn.example/v.m3u8", "https://cdn.example/a.m3u8")))
        assertNull(HlsRenditionId.parse(DashRenditionId.merged("v1", "a1")))
    }

    @Test
    fun `a plain playlist url is not a selection`() {
        assertNull(HlsRenditionId.parse(null))
        assertNull(HlsRenditionId.parse(""))
        assertNull(HlsRenditionId.parse("https://cdn.example/master.m3u8"))
        assertNull(HlsRenditionId.parse("hv|"))
        assertNull(HlsRenditionId.parse("hm|https://cdn.example/v.m3u8"))
    }
}
