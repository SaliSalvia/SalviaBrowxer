package com.example.engine

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.net.URI
import kotlin.math.ceil

/**
 * Parses only finite, non-DRM manifests. Unsupported constructs fail closed instead of
 * silently creating corrupt media files.
 */
internal object ManifestSegmentPlanner {
    data class ByteRange(val length: Long, val offset: Long)
    data class HlsKey(val method: String, val uri: String = "", val ivHex: String = "")
    data class HlsSegment(val index: Int, val sequence: Long, val uri: String, val range: ByteRange?, val key: HlsKey?)
    data class HlsPlan(val mediaPlaylistUrl: String, val initUri: String?, val initRange: ByteRange?,
                       val segments: List<HlsSegment>, val isEndList: Boolean)
    data class DashSegment(val index: Int, val uri: String, val range: ByteRange? = null, val initialization: Boolean = false)
    data class DashRepresentation(val id: String, val mimeType: String, val codecs: String,
                                 val height: Int, val bandwidth: Long, val segments: List<DashSegment>)
    data class DashPlan(val durationSeconds: Double, val representations: List<DashRepresentation>)

    fun parseHls(url: String, text: String): HlsPlan {
        requireHttp(url)
        val lines = text.lineSequence().map(String::trim).toList()
        require(lines.firstOrNull() == "#EXTM3U") { "Invalid HLS playlist" }
        require(lines.none { it.startsWith("#EXT-X-STREAM-INF") }) { "Resolve HLS master playlist first" }
        require(lines.none { it.startsWith("#EXT-X-KEY:") && it.contains("SAMPLE-AES", true) }) {
            "SAMPLE-AES/DRM HLS is unsupported"
        }
        var seq = lines.firstOrNull { it.startsWith("#EXT-X-MEDIA-SEQUENCE:") }
            ?.substringAfter(':')?.toLongOrNull() ?: 0L
        var key: HlsKey? = null
        var init: String? = null
        var initRange: ByteRange? = null
        var rangeText: String? = null
        var previousRangeUri = ""
        var nextRangeOffset = 0L
        val segments = mutableListOf<HlsSegment>()
        for (line in lines) {
            when {
                line.startsWith("#EXT-X-KEY:") -> {
                    val a = attrs(line.substringAfter(':'))
                    key = when (a["METHOD"]?.uppercase()) {
                        "NONE" -> null
                        "AES-128" -> {
                            val keyUri = a["URI"] ?: error("HLS AES-128 key URI missing")
                            val resolved = resolve(url, keyUri)
                            requireHttp(resolved)
                            HlsKey("AES-128", resolved, a["IV"].orEmpty().removePrefix("0x"))
                        }
                        else -> error("Unsupported HLS encryption method")
                    }
                }
                line.startsWith("#EXT-X-MAP:") -> {
                    val a = attrs(line.substringAfter(':'))
                    init = a["URI"]?.let { resolve(url, it) }
                    initRange = a["BYTERANGE"]?.let { parseRange(it, 0L) }
                }
                line.startsWith("#EXT-X-BYTERANGE:") -> rangeText = line.substringAfter(':')
                line.isNotEmpty() && !line.startsWith("#") -> {
                    val segmentUrl = resolve(url, line)
                    requireHttp(segmentUrl)
                    val range = rangeText?.let { raw ->
                        val p = raw.split('@', limit = 2)
                        val length = p[0].toLongOrNull() ?: error("Invalid HLS byte range")
                        val offset = if (p.size == 2) p[1].toLongOrNull() ?: error("Invalid HLS byte-range offset")
                            else if (previousRangeUri == segmentUrl) nextRangeOffset else 0L
                        ByteRange(length, offset).also {
                            require(it.length > 0L && it.offset >= 0L)
                            previousRangeUri = segmentUrl
                            nextRangeOffset = it.offset + it.length
                        }
                    }
                    if (range == null) {
                        previousRangeUri = ""
                        nextRangeOffset = 0L
                    }
                    segments.add(HlsSegment(segments.size, seq++, segmentUrl, range, key))
                    rangeText = null
                }
            }
        }
        require(segments.isNotEmpty()) { "HLS playlist has no segments" }
        return HlsPlan(url, init, initRange, segments, lines.any { it == "#EXT-X-ENDLIST" })
    }

    fun parseDash(url: String, xml: String): DashPlan {
        requireHttp(url)
        val p = Xml.newPullParser()
        p.setInput(StringReader(xml))
        var isStatic = false
        var totalDuration = 0.0
        var rootBase = url
        var periodBase = url
        var adaptationBase = url
        var adaptationMime = ""
        var adaptationCodecs = ""
        var adaptationTemplate: Template? = null
        var current: Rep? = null
        var timelineTarget: Template? = null
        var timelineTime = 0L
        val reps = mutableListOf<DashRepresentation>()
        var event = p.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (p.name) {
                    "MPD" -> {
                        isStatic = p.getAttributeValue(null, "type").equals("static", true)
                        totalDuration = duration(p.getAttributeValue(null, "mediaPresentationDuration").orEmpty())
                    }
                    "Period" -> { periodBase = rootBase; adaptationBase = rootBase }
                    "AdaptationSet" -> {
                        adaptationMime = p.getAttributeValue(null, "mimeType").orEmpty()
                        adaptationCodecs = p.getAttributeValue(null, "codecs").orEmpty()
                        adaptationBase = periodBase
                        adaptationTemplate = null
                    }
                    "BaseURL" -> {
                        val value = p.nextText().trim()
                        if (value.isNotEmpty()) {
                            val parent = current?.base ?: if (adaptationMime.isNotEmpty()) adaptationBase else periodBase
                            val resolved = resolve(parent, value)
                            if (current != null) current!!.base = resolved
                            else if (adaptationMime.isNotEmpty()) adaptationBase = resolved
                            else { rootBase = resolved; periodBase = resolved }
                        }
                    }
                    "SegmentTemplate" -> {
                        val t = Template(
                            initialization = p.getAttributeValue(null, "initialization").orEmpty(),
                            media = p.getAttributeValue(null, "media").orEmpty(),
                            timescale = p.getAttributeValue(null, "timescale")?.toLongOrNull()?.coerceAtLeast(1L) ?: 1L,
                            duration = p.getAttributeValue(null, "duration")?.toLongOrNull() ?: 0L,
                            startNumber = p.getAttributeValue(null, "startNumber")?.toLongOrNull() ?: 1L,
                            timeline = mutableListOf()
                        )
                        require(t.media.isNotBlank()) { "DASH SegmentTemplate media missing" }
                        adaptationTemplate = t
                        current?.template = t
                    }
                    "SegmentTimeline" -> { timelineTarget = current?.template ?: adaptationTemplate; timelineTime = 0L }
                    "S" -> {
                        val t = timelineTarget
                        if (t != null) {
                            val start = p.getAttributeValue(null, "t")?.toLongOrNull() ?: timelineTime
                            val d = p.getAttributeValue(null, "d")?.toLongOrNull() ?: error("DASH S@d missing")
                            val r = p.getAttributeValue(null, "r")?.toIntOrNull() ?: 0
                            require(d > 0L && r >= 0) { "Negative DASH timeline repeat is not supported" }
                            t.timeline.add(TimelineEntry(start, d, r))
                            timelineTime = start + d * (r + 1L)
                        }
                    }
                    "Representation" -> current = Rep(
                        id = p.getAttributeValue(null, "id").orEmpty(),
                        mime = p.getAttributeValue(null, "mimeType").orEmpty().ifBlank { adaptationMime },
                        codecs = p.getAttributeValue(null, "codecs").orEmpty().ifBlank { adaptationCodecs },
                        height = p.getAttributeValue(null, "height")?.toIntOrNull() ?: 0,
                        bandwidth = p.getAttributeValue(null, "bandwidth")?.toLongOrNull() ?: 0L,
                        base = adaptationBase,
                        template = adaptationTemplate
                    )
                }
                XmlPullParser.END_TAG -> when (p.name) {
                    "SegmentTimeline" -> timelineTarget = null
                    "Representation" -> {
                        val r = current
                        if (r != null && isStatic) {
                            val segments = buildSegments(r, totalDuration)
                            if (segments.isNotEmpty()) reps.add(DashRepresentation(r.id, r.mime, r.codecs, r.height, r.bandwidth, segments))
                        }
                        current = null
                    }
                    "AdaptationSet" -> { adaptationMime = ""; adaptationCodecs = ""; adaptationTemplate = null }
                }
            }
            event = p.next()
        }
        require(isStatic) { "Live/Dynamic DASH is not eligible for offline download" }
        require(reps.isNotEmpty()) { "No supported DASH SegmentTemplate representation" }
        return DashPlan(totalDuration, reps)
    }

    private data class Template(val initialization: String, val media: String, val timescale: Long,
                               val duration: Long, val startNumber: Long, val timeline: MutableList<TimelineEntry>)
    private data class TimelineEntry(val start: Long, val duration: Long, val repeat: Int)
    private data class Rep(val id: String, val mime: String, val codecs: String, val height: Int,
                           val bandwidth: Long, var base: String, var template: Template?)

    private fun buildSegments(rep: Rep, totalDuration: Double): List<DashSegment> {
        val t = rep.template ?: return emptyList()
        val result = mutableListOf<DashSegment>()
        if (t.initialization.isNotBlank()) result.add(
            DashSegment(-1, templateUrl(rep, t.initialization, t.startNumber, 0L), initialization = true))
        if (t.timeline.isNotEmpty()) {
            var index = 0
            for (entry in t.timeline) repeat(entry.repeat + 1) { n ->
                require(index < 100_000) { "DASH timeline exceeds safety limit" }
                val time = entry.start + entry.duration * n
                result.add(DashSegment(index, templateUrl(rep, t.media, t.startNumber + index, time)))
                index++
            }
        } else {
            require(t.duration > 0L && totalDuration > 0.0) { "DASH template requires finite duration or SegmentTimeline" }
            val count = ceil(totalDuration * t.timescale / t.duration).toInt().coerceIn(1, 100_000)
            repeat(count) { i -> result.add(DashSegment(i, templateUrl(rep, t.media, t.startNumber + i, i * t.duration))) }
        }
        return result
    }

    private fun templateUrl(rep: Rep, raw: String, number: Long, time: Long): String {
        val dollar = "$"
        var s = raw.replace(dollar + dollar, "\u0000")
        s = s.replace(dollar + "RepresentationID" + dollar, rep.id)
        s = s.replace(dollar + "Bandwidth" + dollar, rep.bandwidth.toString())
        s = Regex(Regex.escape(dollar) + "Number(?:%0(\\d+)d)?" + Regex.escape(dollar)).replace(s) { m ->
            val width = m.groupValues.getOrNull(1)?.toIntOrNull() ?: 0
            if (width > 0) number.toString().padStart(width, '0') else number.toString()
        }
        s = s.replace(dollar + "Time" + dollar, time.toString()).replace("\u0000", dollar)
        require(!s.contains(dollar + "RepresentationID" + dollar) &&
                !s.contains(dollar + "Bandwidth" + dollar) &&
                !s.contains(dollar + "Number" + dollar) &&
                !s.contains(dollar + "Time" + dollar)) { "Unsupported DASH template token" }
        return resolve(rep.base, s)
    }

    private fun duration(s: String): Double {
        val m = Regex("PT(?:(\\d+(?:\\.\\d+)?)H)?(?:(\\d+(?:\\.\\d+)?)M)?(?:(\\d+(?:\\.\\d+)?)S)?")
            .matchEntire(s) ?: return 0.0
        return (m.groupValues[1].toDoubleOrNull() ?: 0.0) * 3600 +
            (m.groupValues[2].toDoubleOrNull() ?: 0.0) * 60 +
            (m.groupValues[3].toDoubleOrNull() ?: 0.0)
    }

    private fun attrs(s: String): Map<String, String> =
        Regex("([A-Z0-9-]+)=\"([^\"]*)\"|([A-Z0-9-]+)=([^,]*)", RegexOption.IGNORE_CASE)
            .findAll(s).associate { it.groupValues[1].ifBlank { it.groupValues[3] } to
                it.groupValues[2].ifBlank { it.groupValues[4] }.trim() }

    private fun parseRange(s: String, defaultOffset: Long): ByteRange {
        val parts = s.split('@', limit = 2)
        val length = parts[0].toLongOrNull() ?: error("Invalid byte range")
        val offset = if (parts.size == 2) parts[1].toLongOrNull() ?: error("Invalid range offset") else defaultOffset
        require(length > 0L && offset >= 0L)
        return ByteRange(length, offset)
    }

    private fun resolve(base: String, relative: String): String = URI(base).resolve(URI(relative)).normalize().toString()
    private fun requireHttp(url: String) {
        val uri = URI(url)
        require((uri.scheme.equals("http", true) || uri.scheme.equals("https", true)) && !uri.host.isNullOrBlank())
    }
}
