package com.example.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ManifestSegmentPlannerTest {
    @Test
    fun parsesHlsSequenceKeysAndImplicitByteRanges() {
        val text = """
            #EXTM3U
            #EXT-X-MEDIA-SEQUENCE:42
            #EXT-X-KEY:METHOD=AES-128,URI="key.bin"
            #EXT-X-BYTERANGE:4@0
            media.ts
            #EXT-X-BYTERANGE:4
            media.ts
            #EXT-X-ENDLIST
        """.trimIndent()
        val plan = ManifestSegmentPlanner.parseHls("https://cdn.example/path/list.m3u8", text)
        assertEquals(42L, plan.segments[0].sequence)
        assertEquals(0L, plan.segments[0].range?.offset)
        assertEquals(4L, plan.segments[1].range?.offset)
        assertEquals("https://cdn.example/path/key.bin", plan.segments[0].key?.uri)
        assertTrue(plan.isEndList)
    }

    @Test
    fun parsesStaticDashSegmentTimelineAndNumberTemplate() {
        val tokenId = "$" + "RepresentationID" + "$"
        val tokenNumber = "$" + "Number%03d" + "$"
        val tokenTime = "$" + "Time" + "$"
        val xml = """
            <MPD type="static" mediaPresentationDuration="PT6S">
              <BaseURL>https://cdn.example/video/</BaseURL>
              <Period>
                <AdaptationSet mimeType="video/mp4">
                  <SegmentTemplate timescale="1" startNumber="7"
                    initialization="init-TOKEN_ID.mp4"
                    media="chunk-TOKEN_NUMBER-TOKEN_TIME.m4s">
                    <SegmentTimeline>
                      <S t="0" d="2" r="2"/>
                    </SegmentTimeline>
                  </SegmentTemplate>
                  <Representation id="v1" bandwidth="900000" height="720"/>
                </AdaptationSet>
              </Period>
            </MPD>
        """.trimIndent()
            .replace("TOKEN_ID", tokenId)
            .replace("TOKEN_NUMBER", tokenNumber)
            .replace("TOKEN_TIME", tokenTime)
        val plan = DashManifestParser.parse(xml, "https://cdn.example/master.mpd")
        assertNull(plan.unsupported)
        val rep = plan.representations.single()
        assertEquals(4, rep.urls.size)
        assertTrue(rep.urls.first().endsWith("init-v1.mp4"))
        assertEquals("https://cdn.example/video/chunk-007-0.m4s", rep.urls[1])
        assertEquals("https://cdn.example/video/chunk-009-4.m4s", rep.urls[3])
    }

    @Test
    fun rejectsDynamicDashRatherThanOfferingPartialOfflineDownload() {
        val xml = """
            <MPD type="dynamic">
              <Period><AdaptationSet mimeType="video/mp4">
                <Representation id="v1"><BaseURL>https://cdn.example/v.mp4</BaseURL></Representation>
              </AdaptationSet></Period>
            </MPD>
        """.trimIndent()
        val result = DashManifestParser.parse(xml, "https://cdn.example/live.mpd")
        assertEquals("LIVE", result.unsupported)
    }

    @Test
    fun rejectsSampleAesHls() {
        val text = """
            #EXTM3U
            #EXT-X-KEY:METHOD=SAMPLE-AES,URI="drm.key"
            #EXTINF:2,
            segment.ts
            #EXT-X-ENDLIST
        """.trimIndent()
        assertFalse(runCatching {
            ManifestSegmentPlanner.parseHls("https://cdn.example/list.m3u8", text)
        }.isSuccess)
    }
}
