package com.salvia.salviabrowxer.media.resolver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DashManifestParserTest {

    private val manifestUrl = "https://cdn.example.org/vod/stream.mpd"

    @Test
    fun `fixed-duration SegmentTemplate expands every segment from the presentation duration`() {
        val xml = """
            <MPD xmlns="urn:mpeg:dash:schema:mpd:2011" type="static" mediaPresentationDuration="PT10S">
              <Period>
                <AdaptationSet mimeType="video/mp4" contentType="video">
                  <SegmentTemplate media="seg-${'$'}Number${'$'}.m4s" initialization="init.mp4"
                                   timescale="1" duration="4" startNumber="1"/>
                  <Representation id="v1" bandwidth="1500000" width="1920" height="1080" codecs="avc1.640028"/>
                </AdaptationSet>
              </Period>
            </MPD>
        """.trimIndent()

        val manifest = DashManifestParser.parse(manifestUrl, xml)
        assertNotNull(manifest)
        val video = manifest!!.videoRepresentations.single()
        assertEquals("v1", video.id)
        assertEquals(1080, video.height)
        assertEquals("https://cdn.example.org/vod/init.mp4", video.initUrl)
        // ceil(10 / 4) = 3 segments
        assertEquals(3, video.segmentUrls.size)
        assertEquals("https://cdn.example.org/vod/seg-1.m4s", video.segmentUrls.first())
        assertEquals("https://cdn.example.org/vod/seg-3.m4s", video.segmentUrls.last())
    }

    @Test
    fun `SegmentTimeline resolves Time based names and repeats`() {
        val xml = """
            <MPD type="static">
              <Period>
                <AdaptationSet mimeType="video/mp4">
                  <SegmentTemplate timescale="90000" media="chunk-${'$'}Time${'$'}.m4s" initialization="init.mp4" startNumber="1">
                    <SegmentTimeline>
                      <S t="0" d="90000" r="1"/>
                      <S d="45000"/>
                    </SegmentTimeline>
                  </SegmentTemplate>
                  <Representation id="v1" bandwidth="800000" height="720"/>
                </AdaptationSet>
              </Period>
            </MPD>
        """.trimIndent()

        val manifest = DashManifestParser.parse(manifestUrl, xml)
        assertNotNull(manifest)
        val video = manifest!!.videoRepresentations.single()
        // r="1" means the first S repeats once (2 segments), then one more S -> 3 total.
        assertEquals(
            listOf(
                "https://cdn.example.org/vod/chunk-0.m4s",
                "https://cdn.example.org/vod/chunk-90000.m4s",
                "https://cdn.example.org/vod/chunk-180000.m4s"
            ),
            video.segmentUrls
        )
    }

    @Test
    fun `SegmentList and separate audio are both surfaced`() {
        val xml = """
            <MPD type="static">
              <Period>
                <AdaptationSet mimeType="video/mp4">
                  <SegmentList>
                    <Initialization sourceURL="v/init.mp4"/>
                    <SegmentURL media="v/1.m4s"/>
                    <SegmentURL media="v/2.m4s"/>
                  </SegmentList>
                  <Representation id="v1" bandwidth="900000" height="480"/>
                </AdaptationSet>
                <AdaptationSet mimeType="audio/mp4" lang="en">
                  <SegmentList>
                    <Initialization sourceURL="a/init.mp4"/>
                    <SegmentURL media="a/1.m4s"/>
                  </SegmentList>
                  <Representation id="a1" bandwidth="128000"/>
                </AdaptationSet>
              </Period>
            </MPD>
        """.trimIndent()

        val manifest = DashManifestParser.parse(manifestUrl, xml)
        assertNotNull(manifest)
        assertEquals(1, manifest!!.videoRepresentations.size)
        assertEquals("https://cdn.example.org/vod/v/2.m4s", manifest.videoRepresentations.single().segmentUrls.last())
        val audio = manifest.audioRepresentations.single()
        assertEquals("a1", audio.id)
        assertEquals("en", audio.language)
        assertTrue(audio.isAudio)
    }

    @Test
    fun `a dynamic manifest is refused`() {
        val xml = """
            <MPD type="dynamic">
              <Period><AdaptationSet mimeType="video/mp4"><SegmentTemplate media="s-${'$'}Number${'$'}.m4s" duration="4"/><Representation id="v1" bandwidth="1"/></AdaptationSet></Period>
            </MPD>
        """.trimIndent()
        assertNull(DashManifestParser.parse(manifestUrl, xml))
    }

    @Test
    fun `a protected manifest is refused`() {
        val xml = """
            <MPD type="static">
              <Period>
                <AdaptationSet mimeType="video/mp4">
                  <ContentProtection schemeIdUri="urn:uuid:EDEF8BA9-79D6-4ACE-A3C8-27DCD51D21ED"/>
                  <SegmentTemplate media="s-${'$'}Number${'$'}.m4s" duration="4"/>
                  <Representation id="v1" bandwidth="1"/>
                </AdaptationSet>
              </Period>
            </MPD>
        """.trimIndent()
        assertNull(DashManifestParser.parse(manifestUrl, xml))
    }

    @Test
    fun `a multi-period manifest is refused`() {
        val xml = """
            <MPD type="static">
              <Period><AdaptationSet mimeType="video/mp4"><SegmentTemplate media="a-${'$'}Number${'$'}.m4s" duration="4"/><Representation id="v1" bandwidth="1"/></AdaptationSet></Period>
              <Period><AdaptationSet mimeType="video/mp4"><SegmentTemplate media="b-${'$'}Number${'$'}.m4s" duration="4"/><Representation id="v2" bandwidth="1"/></AdaptationSet></Period>
            </MPD>
        """.trimIndent()
        assertNull(DashManifestParser.parse(manifestUrl, xml))
    }

    @Test
    fun `a template using an unsupported placeholder is refused`() {
        val xml = """
            <MPD type="static">
              <Period>
                <AdaptationSet mimeType="video/mp4">
                  <SegmentTemplate media="s-${'$'}SubNumber${'$'}.m4s" duration="4"/>
                  <Representation id="v1" bandwidth="1"/>
                </AdaptationSet>
              </Period>
            </MPD>
        """.trimIndent()
        assertNull(DashManifestParser.parse(manifestUrl, xml))
    }

    @Test
    fun `malformed xml is refused rather than thrown`() {
        assertNull(DashManifestParser.parse(manifestUrl, "<MPD><Period>"))
        assertNull(DashManifestParser.parse(manifestUrl, "not xml at all"))
    }

    @Test
    fun `ISO 8601 durations are converted to seconds`() {
        assertEquals(10.0, DashManifestParser.parseIsoDuration("PT10S")!!, 0.001)
        assertEquals(3723.5, DashManifestParser.parseIsoDuration("PT1H2M3.5S")!!, 0.001)
        assertNull(DashManifestParser.parseIsoDuration(null))
        assertNull(DashManifestParser.parseIsoDuration("not a duration"))
    }
}
