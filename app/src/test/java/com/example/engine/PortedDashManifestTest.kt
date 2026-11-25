package com.example.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DashManifestTest {

    // '@' stands in for '$' so the XML can be written without Kotlin string templates.
    private fun xml(text: String): String = text.trimIndent().replace('@', '$')

    private val base = "https://cdn.example.com/stream/manifest.mpd"

    @Test
    fun timelineTemplateExpandsInitAndSegments() {
        val manifest = DashManifestParser.parse(
            xml(
                """
                <MPD type="static" mediaPresentationDuration="PT20S">
                  <Period>
                    <BaseURL>https://cdn.example.com/v/</BaseURL>
                    <AdaptationSet mimeType="video/mp4" codecs="avc1.64001f">
                      <SegmentTemplate timescale="1000" initialization="init-@RepresentationID@.mp4"
                          media="seg-@RepresentationID@-@Number%03d@.m4s" startNumber="1">
                        <SegmentTimeline><S t="0" d="10000" r="1"/></SegmentTimeline>
                      </SegmentTemplate>
                      <Representation id="v720" bandwidth="2000000" width="1280" height="720"/>
                      <Representation id="v360" bandwidth="800000" width="640" height="360"/>
                    </AdaptationSet>
                    <AdaptationSet mimeType="audio/mp4" codecs="mp4a.40.2">
                      <Representation id="a1" bandwidth="128000"><BaseURL>audio.m4a</BaseURL></Representation>
                    </AdaptationSet>
                  </Period>
                </MPD>
                """
            ),
            base
        )
        assertNull(manifest.unsupported)

        val video = manifest.pickVideo(null)
        assertNotNull(video)
        assertEquals("v720", video!!.id)
        assertFalse(video.isSingleFile)
        assertEquals(
            listOf(
                "https://cdn.example.com/v/init-v720.mp4",
                "https://cdn.example.com/v/seg-v720-001.m4s",
                "https://cdn.example.com/v/seg-v720-002.m4s"
            ),
            video.urls
        )

        assertEquals("v360", manifest.pickVideo("v360")!!.id)

        val audio = manifest.pickAudio(null)
        assertNotNull(audio)
        assertTrue(audio!!.isSingleFile)
        assertEquals(listOf("https://cdn.example.com/v/audio.m4a"), audio.urls)
    }

    @Test
    fun fixedDurationTemplateRoundsSegmentCountUp() {
        val manifest = DashManifestParser.parse(
            xml(
                """
                <MPD type="static" mediaPresentationDuration="PT10S">
                  <Period>
                    <AdaptationSet mimeType="video/mp4">
                      <SegmentTemplate timescale="1" duration="4" media="s@Number@.m4s" startNumber="1"/>
                      <Representation id="r1" bandwidth="500000" width="640" height="360"/>
                    </AdaptationSet>
                  </Period>
                </MPD>
                """
            ),
            base
        )
        val video = manifest.pickVideo(null)
        assertNotNull(video)
        assertEquals(
            listOf(
                "https://cdn.example.com/stream/s1.m4s",
                "https://cdn.example.com/stream/s2.m4s",
                "https://cdn.example.com/stream/s3.m4s"
            ),
            video!!.urls
        )
    }

    @Test
    fun segmentListIsResolvedAgainstManifest() {
        val manifest = DashManifestParser.parse(
            xml(
                """
                <MPD type="static" mediaPresentationDuration="PT8S">
                  <Period>
                    <AdaptationSet mimeType="video/mp4">
                      <Representation id="r1" bandwidth="500000" height="360">
                        <SegmentList duration="4">
                          <Initialization sourceURL="init.mp4"/>
                          <SegmentURL media="a.m4s"/>
                          <SegmentURL media="b.m4s"/>
                        </SegmentList>
                      </Representation>
                    </AdaptationSet>
                  </Period>
                </MPD>
                """
            ),
            base
        )
        assertEquals(
            listOf(
                "https://cdn.example.com/stream/init.mp4",
                "https://cdn.example.com/stream/a.m4s",
                "https://cdn.example.com/stream/b.m4s"
            ),
            manifest.pickVideo(null)!!.urls
        )
    }

    @Test
    fun liveAndDrmAreReportedUnsupported() {
        assertEquals("LIVE", DashManifestParser.parse("<MPD type=\"dynamic\"><Period/></MPD>", base).unsupported)
        assertEquals(
            "DRM",
            DashManifestParser.parse(
                "<MPD type=\"static\"><Period><AdaptationSet><ContentProtection/></AdaptationSet></Period></MPD>",
                base
            ).unsupported
        )
        assertEquals("INVALID", DashManifestParser.parse("not xml", base).unsupported)
    }

    @Test
    fun multiPeriodRepresentationsAreMergedInOrder() {
        val manifest = DashManifestParser.parse(
            xml(
                """
                <MPD type="static" mediaPresentationDuration="PT20S">
                  <Period>
                    <AdaptationSet mimeType="video/mp4">
                      <SegmentTemplate timescale="1" duration="10" media="p1-@Number@.m4s" startNumber="1" initialization="p1-init.mp4"/>
                      <Representation id="v1" bandwidth="900000" height="360"/>
                    </AdaptationSet>
                  </Period>
                  <Period>
                    <AdaptationSet mimeType="video/mp4">
                      <SegmentTemplate timescale="1" duration="10" media="p2-@Number@.m4s" startNumber="1" initialization="p2-init.mp4"/>
                      <Representation id="v1" bandwidth="900000" height="360"/>
                    </AdaptationSet>
                  </Period>
                </MPD>
                """
            ),
            base
        )
        assertNull(manifest.unsupported)
        val video = manifest.pickVideo(null)
        assertNotNull(video)
        // Period 1 init + segments, then period 2 init + segments, in order.
        assertEquals(
            listOf(
                "https://cdn.example.com/stream/p1-init.mp4",
                "https://cdn.example.com/stream/p1-1.m4s",
                "https://cdn.example.com/stream/p1-2.m4s",
                "https://cdn.example.com/stream/p2-init.mp4",
                "https://cdn.example.com/stream/p2-1.m4s",
                "https://cdn.example.com/stream/p2-2.m4s"
            ),
            video!!.urls
        )
    }

    @Test
    fun segmentBaseIsExposedForSidxExpansion() {
        val manifest = DashManifestParser.parse(
            xml(
                """
                <MPD type="static" mediaPresentationDuration="PT40S">
                  <Period>
                    <AdaptationSet mimeType="video/mp4">
                      <Representation id="v1" bandwidth="1400000" height="720">
                        <BaseURL>https://cdn.example.com/videofile.mp4</BaseURL>
                        <SegmentBase indexRange="862-1405">
                          <Initialization range="0-861"/>
                        </SegmentBase>
                      </Representation>
                    </AdaptationSet>
                  </Period>
                </MPD>
                """
            ),
            base
        )
        assertNull(manifest.unsupported)
        val video = manifest.pickVideo(null)
        assertNotNull(video)
        assertEquals("https://cdn.example.com/videofile.mp4", video!!.sidxUrl)
        assertEquals(862L..1405L, video.sidxRange)
        assertEquals(0L..861L, video.initRange)
    }

    @Test
    fun templateVariablesAreFilled() {
        assertEquals(
            "x/5/00007-A.m4s",
            DashManifestParser.fillTemplate("x/@Time@/@Number%05d@-@RepresentationID@.m4s".replace('@', '$'), "A", 128000L, 7L, 5L)
        )
        assertEquals("a\$b", DashManifestParser.fillTemplate("a\$\$b", "A", 1L, 1L, 1L))
    }
}
