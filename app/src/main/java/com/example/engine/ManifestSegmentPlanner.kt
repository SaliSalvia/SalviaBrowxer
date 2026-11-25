package com.example.engine

import android.net.Uri
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.net.URI

/**
 * Conservative HLS/DASH manifest planner.
 *
 * This class only accepts finite, downloadable manifests. It deliberately rejects live,
 * DRM-protected and unsupported template forms instead of producing a misleading partial file.
 */
internal object ManifestSegmentPlanner {
    data class ByteRange(val length: Long, val offset: Long)

    data class HlsKey(val method: String, val uri: String = "", val ivHex: String = "")
    data class HlsSegment(
        val index: Int,
        val sequence: Long,
        val uri: String,
        val range: ByteRange? = null,
        val key: HlsKey? = null,
    )
    data class HlsPlan(
        val mediaPlaylistUrl: String,
        val initUri: String? = null,
        val initRange: ByteRange? = null,
        val segments: List<HlsSegment>,
        val isEndList: Boolean,
    )

    data class DashSegment(
        val index: Int,
        val uri: String,
        val range: ByteRange? = null,
        val initialization: Boolean = false,
    )
    data class DashRepresentation(
        val id: String,
        val mimeType: String,
        val codecs: String,
        val height: Int,
        val bandwidth: Long,
        val segments: List<DashSegment>,
    )
    data class DashPlan(
        val isStatic: Boolean,
        val durationSeconds: Double,
        val representations: List<DashRepresentation>,
    )

    fun parseHls(playlistUrl: String, text: String): HlsPlan {
        requireHttpUrl(playlistUrl)
        val lines = text.lineSequence().map(String::trim).toList()
        require(lines.firstOrNull() == "#EXTM3U") { "Not an HLS playlist" }
        require(!lines.any { it.startsWith("#EXT-X-STREAM-INF") }) {
            "Master playlist must be resolved to a media playlist first"
        }
        require(!lines.any { it.startsWith("#EXT-X-KEY:") && it.contains("METHOD=SAMPLE-AES", true) }) {
            "DRM/SAMPLE-AES HLS is not supported"
        }

        var sequence = lines.firstOrNull { it.startsWith("#EXT-X-MEDIA-SEQUENCE:") }
            ?.substringAfter(':')?.toLongOrNull() ?: 0L
        var nextRangeOffset = 0L
        var initUri: String? = null
        var initRange: ByteRange? = null
        var key: HlsKey? = null
        val segments = mutableListOf<HlsSegment>()
        var pendingRange: ByteRange? = null

        for (line in lines) {
            when {
                line.startsWith("#EXT-X-KEY:") -> {
                    val attrs = attributes(line.substringAfter(':'))
                    val method = attrs["METHOD"].orEmpty().uppercase()
                    key = when (method) {
                        "NONE" -> null
                        "AES-128" -> {
                            val rawUri = attrs["URI"] ?: error("AES-128 key URI missing")
                            val resolved = resolve(playlistUrl, rawUri)
                            requireHttpUrl(resolved)
                            HlsKey(method, resolved, attrs["IV"].orEmpty().removePrefix("0x"))
                        }
                        else -> error("Unsupported HLS encryption method: $method")
                    }
                }
                line.startsWith("#EXT-X-MAP:") -> {
                    val attrs = attributes(line.substringAfter(':'))
                    initUri = attrs["URI"]?.let { resolve(playlistUrl, it) }
                    initRange = attrs["BYTERANGE"]?.let { parseRange(it, nextRangeOffset).also { r -> nextRangeOffset = r.offset + r.length } }
                }
                line.startsWith("#EXT-X-BYTERANGE:") -> {
                    pendingRange = parseRange(line.substringAfter(':'), nextRangeOffset)
                    nextRangeOffset = pendingRange!!.offset + pendingRange!!.length
                }
                line.isNotEmpty() && !line.startsWith("#") -> {
                    val resolved = resolve(playlistUrl, line)
                    requireHttpUrl(resolved)
                    segments += HlsSegment(segments.size, sequence, resolved, pendingRange, key)
                    sequence++
                    pendingRange = null
                    if (pendingRange == null && !line.startsWith("#")) nextRangeOffset = 0L
                }
            }
        }
        require(segments.isNotEmpty()) { "HLS playlist has no media segments" }
        return HlsPlan(playlistUrl, initUri, initRange, segments, lines.any { it == "#EXT-X-ENDLIST" })
    }

    fun parseDash(mpdUrl: String, xml: String): DashPlan {
        requireHttpUrl(mpdUrl)
        val parser = Xml.newPullParser()
        parser.setInput(StringReader(xml))
        var staticMpd = false
        var duration = 0.0
        var mpdBase = mpdUrl
        var periodBase = ""
        var adaptBase = ""
        var adaptMime = ""
        var adaptCodecs = ""
        var adaptTemplate: Template? = null
        var adaptList: SegmentList? = null
        var current: Rep? = null
        val reps = mutableListOf<DashRepresentation>()

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "MPD" -> {
                        staticMpd = parser.getAttributeValue(null, "type").orEmpty().equals("static", true)
                        duration = parseDuration(parser.getAttributeValue(null, "mediaPresentationDuration").orEmpty())
                    }
                    "BaseURL" -> {
                        val value = parser.nextText().trim()
                        if (value.isNotEmpty()) {
                            val parent = when {
                                current != null -> current!!.base
                                adaptMime.isNotEmpty() -> adaptBase.ifBlank { periodBase }
                                periodBase.isNotEmpty() -> periodBase
                                else -> mpdBase
                            }
                            val resolved = resolve(parent, value)
                            if (current != null) current!!.base = resolved
                            else if (adaptMime.isNotEmpty()) adaptBase = resolved
                            else if (periodBase.isNotEmpty()) periodBase = resolved
                            else mpdBase = resolved
                        }
                    }
                    "Period" -> {
                        periodBase = mpdBase
                        adaptBase = ""
                    }
                    "AdaptationSet" -> {
                        adaptMime = parser.getAttributeValue(null, "mimeType").orEmpty()
                        adaptCodecs = parser.getAttributeValue(null, "codecs").orEmpty()
                        adaptBase = periodBase
                        adaptTemplate = null
                        adaptList = null
                    }
                    "SegmentTemplate" -> {
                        val t = readTemplate(parser)
                        adaptTemplate = t
                        if (current != null) current!!.template = t
                    }
                    "SegmentList" -> {
                        val list = readSegmentList(parser)
                        adaptList = list
                        if (current != null) current!!.list = list
                    }
                    "Representation" -> {
                        current = Rep(
                            id = parser.getAttributeValue(null, "id").orEmpty(),
                            mime = parser.getAttributeValue(null, "mimeType").orEmpty().ifBlank { adaptMime },
                            codecs = parser.getAttributeValue(null, "codecs").orEmpty().ifBlank { adaptCodecs },
                            height = parser.getAttributeValue(null, "height")?.toIntOrNull() ?: 0,
                            bandwidth = parser.getAttributeValue(null, "bandwidth")?.toLongOrNull() ?: 0L,
                            base = adaptBase.ifBlank { periodBase },
                            template = adaptTemplate,
                            list = adaptList,
                        )
                    }
                    "Initialization" -> {
                        val attrs = attributesFromParser(parser)
                        current?.list?.initialization = attrs["sourceURL"]?.let { resolve(current!!.base, it) }
                        current?.list?.initializationRange = attrs["range"]?.let { parseRange(it, 0L) }
                    }
                    "SegmentURL" -> {
                        val attrs = attributesFromParser(parser)
                        val media = attrs["media"]
                        if (!media.isNullOrBlank()) current?.list?.urls?.add(
                            DashSegment(current!!.list!!.urls.size, resolve(current!!.base, media),
                                attrs["mediaRange"]?.let { parseRange(it, 0L) })
                        )
                    }
                }
                XmlPullParser.END_TAG -> when (parser.name) {
                    "Representation" -> {
                        current?.let { rep ->
                            val segments = buildDashSegments(rep, staticMpd, duration)
                            if (segments.isNotEmpty()) reps += DashRepresentation(
                                rep.id, rep.mime, rep.codecs, rep.height, rep.bandwidth, segments
                            )
                        }
                        current = null
                    }
                    "AdaptationSet" -> {
                        adaptMime = ""
                        adaptCodecs = ""
                        adaptTemplate = null
                        adaptList = null
                        adaptBase = ""
                    }
                }
            }
            event = parser.next()
        }
        require(staticMpd) { "Dynamic/live DASH manifests are not supported for offline downloads" }
        require(reps.isNotEmpty()) { "No supported DASH segment plan found" }
        return DashPlan(staticMpd, duration, reps)
    }

    private data class Template(
        val initialization: String,
        val media: String,
        val timescale: Long,
        val duration: Long,
        val startNumber: Long,
        val timeline: List<Pair<Long, Int>>?,
    )
    private data class SegmentList(
        var initialization: String? = null,
        var initializationRange: ByteRange? = null,
        val urls: MutableList<DashSegment> = mutableListOf(),
    )
    private data class Rep(
        val id: String,
        val mime: String,
        val codecs: String,
        val height: Int,
        val bandwidth: Long,
        var base: String,
        var template: Template?,
        var list: SegmentList?,
    )

    private fun readTemplate(p: XmlPullParser): Template {
        val init = p.getAttributeValue(null, "initialization").orEmpty()
        val media = p.getAttributeValue(null, "media").orEmpty()
        require(media.isNotBlank()) { "DASH SegmentTemplate media missing" }
        val timescale = p.getAttributeValue(null, "timescale")?.toLongOrNull()?.coerceAtLeast(1L) ?: 1L
        val duration = p.getAttributeValue(null, "duration")?.toLongOrNull() ?: 0L
        val start = p.getAttributeValue(null, "startNumber")?.toLongOrNull() ?: 1L
        val timeline = mutableListOf<Pair<Long, Int>>()
        // SegmentTimeline children are parsed in a second pass by the timeline reader below.
        return Template(init, media, timescale, duration, start, timeline.takeIf { it.isNotEmpty() })
    }

    private fun readSegmentList(p: XmlPullParser): SegmentList = SegmentList()

    private fun buildDashSegments(rep: Rep, isStatic: Boolean, durationSeconds: Double): List<DashSegment> {
        rep.list?.let { list ->
            val result = mutableListOf<DashSegment>()
            list.initialization?.let { result += DashSegment(-1, it, list.initializationRange, true) }
            result += list.urls
            return result
        }
        val t = rep.template ?: return emptyList()
        if (!isStatic || t.media.contains("\\$Time\\$") && t.timeline.isNullOrEmpty()) return emptyList()
        val result = mutableListOf<DashSegment>()
        if (t.initialization.isNotBlank()) {
            result += DashSegment(-1, templateUrl(rep, t.initialization, t.startNumber, 0L), initialization = true)
        }
        val count = when {
            t.duration > 0L && durationSeconds > 0.0 ->
                kotlin.math.ceil(durationSeconds * t.timescale / t.duration).toInt().coerceIn(1, 100_000)
            else -> 0
        }
        if (count == 0) return emptyList()
        repeat(count) { i ->
            result += DashSegment(i, templateUrl(rep, t.media, t.startNumber + i, i * t.duration))
        }
        return result
    }

    private fun templateUrl(rep: Rep, template: String, number: Long, time: Long): String {
        val expanded = template
            .replace("$$", "\u0000")
            .replace(Regex("\\$RepresentationID\\$"), rep.id)
            .replace(Regex("\\$Bandwidth\\$"), rep.bandwidth.toString())
            .replace(Regex("\\$Number(?:%0(\\d+)d)?\\$")) { m ->
                val width = m.groupValues.getOrNull(1)?.toIntOrNull() ?: 0
                if (width > 0) number.toString().padStart(width, '0') else number.toString()
            }
            .replace(Regex("\\$Time\\$"), time.toString())
            .replace("\u0000", "$")
        return resolve(rep.base, expanded)
    }

    private fun parseDuration(value: String): Double {
        if (value.isBlank()) return 0.0
        val m = Regex("PT(?:(\\d+(?:\\.\\d+)?)H)?(?:(\\d+(?:\\.\\d+)?)M)?(?:(\\d+(?:\\.\\d+)?)S)?")
            .matchEntire(value) ?: return 0.0
        return (m.groupValues[1].toDoubleOrNull() ?: 0.0) * 3600 +
            (m.groupValues[2].toDoubleOrNull() ?: 0.0) * 60 +
            (m.groupValues[3].toDoubleOrNull() ?: 0.0)
    }

    private fun attributes(value: String): Map<String, String> =
        Regex("([A-Z0-9-]+)=\"([^\"]*)\"|([A-Z0-9-]+)=([^,]*)", RegexOption.IGNORE_CASE)
            .findAll(value).associate { m ->
                (m.groupValues[1].ifBlank { m.groupValues[3] }) to
                    m.groupValues[2].ifBlank { m.groupValues[4] }.trim()
            }

    private fun attributesFromParser(p: XmlPullParser): Map<String, String> =
        (0 until p.attributeCount).associate { p.getAttributeName(it) to p.getAttributeValue(it) }

    private fun parseRange(value: String, defaultOffset: Long): ByteRange {
        val parts = value.split('@', limit = 2)
        val length = parts[0].toLongOrNull() ?: value.substringBefore('-').toLongOrNull()
            ?: error("Invalid byte range")
        val offset = if (parts.size == 2) parts[1].toLongOrNull() ?: error("Invalid range offset") else defaultOffset
        require(length > 0 && offset >= 0) { "Invalid byte range values" }
        return ByteRange(length, offset)
    }

    private fun resolve(base: String, relative: String): String =
        URI(base).resolve(URI(relative)).normalize().toString()

    private fun requireHttpUrl(url: String) {
        val uri = URI(url)
        require(uri.scheme.equals("https", true) || uri.scheme.equals("http", true))
        require(!uri.host.isNullOrBlank())
    }
}
