package com.example.engine

import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.net.URI
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.ceil

/** One downloadable stream (a video or audio Representation) with every URL needed for it. */
data class DashRepresentation(
    val id: String,
    val isVideo: Boolean,
    val bandwidth: Long,
    val width: Int,
    val height: Int,
    val codecs: String,
    /** Initialization segment first (if any), then media segments; or a single whole-file URL. */
    val urls: List<String>,
    val isSingleFile: Boolean,
    /**
     * Parallel to [urls]: the byte range of that URL that makes up the segment, or null when the
     * whole response is the segment. Used by SegmentBase (sidx) representations.
     */
    val byteRanges: List<LongRange?> = emptyList(),
    /**
     * When set, [urls]/[byteRanges] must be filled by fetching this sidx box first (the parser
     * cannot do network I/O). [sidxRange] are the bytes of the index inside [sidxUrl].
     */
    val sidxUrl: String? = null,
    val sidxRange: LongRange? = null,
    /** Byte range of the initialization segment inside [sidxUrl] (SegmentBase). */
    val initRange: LongRange? = null
)

data class DashManifest(
    val representations: List<DashRepresentation>,
    /** null when usable; otherwise LIVE, DRM, INVALID or NO_STREAMS. */
    val unsupported: String?
) {
    fun pickVideo(preferredId: String?): DashRepresentation? {
        val videos = representations.filter { it.isVideo }
        if (videos.isEmpty()) return null
        if (!preferredId.isNullOrBlank()) {
            val exact = videos.firstOrNull { it.id == preferredId }
            if (exact != null) return exact
        }
        // MediaMuxer handles H.264/H.265 in MP4 everywhere; prefer them over VP9/AV1.
        val avc = videos.filter { it.codecs.startsWith("avc", ignoreCase = true) || it.codecs.startsWith("hvc", ignoreCase = true) || it.codecs.startsWith("hev", ignoreCase = true) }
        val pool = if (avc.isNotEmpty()) avc else videos
        return pool.maxWithOrNull(compareBy<DashRepresentation>({ it.height }, { it.bandwidth }))
    }

    fun pickAudio(preferredId: String?): DashRepresentation? {
        val audios = representations.filter { !it.isVideo }
        if (audios.isEmpty()) return null
        if (!preferredId.isNullOrBlank()) {
            val exact = audios.firstOrNull { it.id == preferredId }
            if (exact != null) return exact
        }
        val aac = audios.filter { it.codecs.startsWith("mp4a", ignoreCase = true) }
        val pool = if (aac.isNotEmpty()) aac else audios
        return pool.maxByOrNull { it.bandwidth }
    }
}

/**
 * Static-VOD DASH reader with no DRM support. Handles multiple periods (representations are
 * merged across periods in order), BaseURL (single file), SegmentTemplate (SegmentTimeline or
 * fixed duration), SegmentList, and SegmentBase with an indexRange (the sidx box is parsed by
 * the downloader, which can do network I/O). Pure JVM so it is unit tested without Android.
 */
object DashManifestParser {

    private const val MAX_SEGMENTS = 20000

    private val templateVariable = Regex("[\$]([A-Za-z]*)(?:%0(\\d+)d)?[\$]")
    private val isoDuration =
        Regex("""^P(?:(\d+)D)?(?:T(?:(\d+)H)?(?:(\d+)M)?(?:(\d+(?:\.\d+)?)S)?)?$""")

    fun parse(xmlText: String, manifestUrl: String): DashManifest {
        val mpd: Element = try {
            val factory = DocumentBuilderFactory.newInstance()
            factory.isNamespaceAware = false
            try {
                factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            } catch (ignored: Exception) {
            // Some Android XML providers do not support this optional hardening feature.
            }
            val doc = factory.newDocumentBuilder().parse(ByteArrayInputStream(xmlText.toByteArray(Charsets.UTF_8)))
            doc.documentElement
        } catch (ignored: Exception) {
            return DashManifest(emptyList(), "INVALID")
        }
        if (!nameOf(mpd).equals("MPD", ignoreCase = true)) return DashManifest(emptyList(), "INVALID")

        if (attr(mpd, "type").equals("dynamic", ignoreCase = true)) return DashManifest(emptyList(), "LIVE")
        if (mpd.getElementsByTagName("ContentProtection").length > 0) return DashManifest(emptyList(), "DRM")

        val periods = childElements(mpd, "Period")
        if (periods.isEmpty()) return DashManifest(emptyList(), "INVALID")

        val mpdBase = applyBaseUrl(manifestUrl, mpd)
        val mpdSeconds = parseIsoDuration(attr(mpd, "mediaPresentationDuration"))

        // Representations are keyed so the same stream continued in another Period merges into
        // one downloadable representation with the periods' segments in order.
        val merged = LinkedHashMap<String, DashRepresentation>()

        for (period in periods) {
            val periodBase = applyBaseUrl(mpdBase, period)
            val periodSeconds = parseIsoDuration(attr(period, "duration")) ?: mpdSeconds
            parsePeriod(period, periodBase, periodSeconds, merged)
        }

        val reps = merged.values.toList()
        return DashManifest(reps, if (reps.isEmpty()) "NO_STREAMS" else null)
    }

    private fun parsePeriod(
        period: Element,
        periodBase: String,
        periodSeconds: Double?,
        merged: LinkedHashMap<String, DashRepresentation>
    ) {
        for (set in childElements(period, "AdaptationSet")) {
            val setBase = applyBaseUrl(periodBase, set)
            val setMime = (attr(set, "mimeType") ?: "").lowercase()
            val setType = (attr(set, "contentType") ?: "").lowercase()

            // SegmentBase / SegmentTemplate / SegmentList may live on the AdaptationSet.
            val setTemplate = childElements(set, "SegmentTemplate").firstOrNull()
            val setList = childElements(set, "SegmentList").firstOrNull()
            val setSegmentBase = childElements(set, "SegmentBase").firstOrNull()

            for ((index, rep) in childElements(set, "Representation").withIndex()) {
                val mime = (attr(rep, "mimeType") ?: setMime).lowercase()
                val width = (attr(rep, "width") ?: attr(set, "width"))?.toIntOrNull() ?: 0
                val height = (attr(rep, "height") ?: attr(set, "height"))?.toIntOrNull() ?: 0
                val isVideo = mime.startsWith("video") || setType == "video" ||
                    (mime.isEmpty() && setType.isEmpty() && height > 0)
                val isAudio = mime.startsWith("audio") || setType == "audio"
                if (!isVideo && !isAudio) continue

                val id = attr(rep, "id") ?: "rep$index"
                val bandwidth = attr(rep, "bandwidth")?.toLongOrNull() ?: 0L
                val codecs = attr(rep, "codecs") ?: attr(set, "codecs") ?: ""
                val repBase = applyBaseUrl(setBase, rep)

                val template = childElements(rep, "SegmentTemplate").firstOrNull() ?: setTemplate
                val list = childElements(rep, "SegmentList").firstOrNull() ?: setList
                val segmentBase = childElements(rep, "SegmentBase").firstOrNull() ?: setSegmentBase
                val hasBase = childElements(rep, "BaseURL").isNotEmpty() || childElements(set, "BaseURL").isNotEmpty()

                var urls: List<String>? = null
                var single = false
                var byteRanges: List<LongRange?> = emptyList()
                var sidxUrl: String? = null
                var sidxRange: LongRange? = null
                var initRange: LongRange? = null

                if (template != null) {
                    urls = templateUrls(template, repBase, id, bandwidth, periodSeconds)
                } else if (list != null) {
                    urls = listUrls(list, repBase)
                } else if (segmentBase != null) {
                    // Single file indexed by a sidx box; the service fetches and expands it.
                    val rangeText = attr(segmentBase, "indexRange")
                    val indexRange = parseRange(rangeText)
                    val initEl = childElements(segmentBase, "Initialization").firstOrNull()
                    initRange = parseRange(attr(initEl, "range"))
                    if (indexRange != null) {
                        sidxUrl = repBase
                        sidxRange = indexRange
                        urls = listOf(repBase) // placeholder; expanded after sidx is fetched
                        single = true
                    }
                }
                if (urls == null && sidxUrl == null && hasBase) {
                    urls = listOf(repBase)
                    single = true
                }
                if ((urls == null || urls.isEmpty()) && sidxUrl == null) continue

                val rep2 = DashRepresentation(
                    id = id,
                    isVideo = isVideo,
                    bandwidth = bandwidth,
                    width = width,
                    height = height,
                    codecs = codecs,
                    urls = urls ?: emptyList(),
                    isSingleFile = single,
                    byteRanges = byteRanges,
                    sidxUrl = sidxUrl,
                    sidxRange = sidxRange,
                    initRange = initRange
                )

                val previous = merged[id]
                merged[id] = if (previous == null) {
                    rep2
                } else {
                    mergeRepresentations(previous, rep2)
                }
            }
        }
    }

    /**
     * Concatenates two periods of the same representation. The second period's init segment (the
     * first URL when segmented) is kept so the remuxer can resynchronize after the period break.
     */
    private fun mergeRepresentations(first: DashRepresentation, second: DashRepresentation): DashRepresentation {
        val firstIsIndexed = first.sidxUrl != null
        val secondIsIndexed = second.sidxUrl != null
        if (firstIsIndexed || secondIsIndexed) {
            // Indexed single-file periods cannot be safely concatenated; keep the bigger one.
            return if (first.bandwidth >= second.bandwidth) first else second
        }
        val urls = first.urls + second.urls
        val ranges = if (first.byteRanges.isEmpty() && second.byteRanges.isEmpty()) {
            emptyList()
        } else {
            first.byteRanges.ifEmpty { List(first.urls.size) { null } } +
                second.byteRanges.ifEmpty { List(second.urls.size) { null } }
        }
        return first.copy(
            bandwidth = maxOf(first.bandwidth, second.bandwidth),
            width = maxOf(first.width, second.width),
            height = maxOf(first.height, second.height),
            urls = urls,
            isSingleFile = first.isSingleFile && second.isSingleFile && urls.size == 1,
            byteRanges = ranges
        )
    }

    private fun parseRange(raw: String?): LongRange? {
        if (raw.isNullOrBlank()) return null
        val parts = raw.split('-')
        if (parts.size != 2) return null
        val start = parts[0].trim().toLongOrNull() ?: return null
        val end = parts[1].trim().toLongOrNull() ?: return null
        if (start < 0L || end < start) return null
        return start..end
    }

    // ------------------------------------------------------------------ segment expansion

    private fun templateUrls(
        template: Element,
        base: String,
        repId: String,
        bandwidth: Long,
        periodSeconds: Double?
    ): List<String>? {
        val media = attr(template, "media") ?: return null
        val initialization = attr(template, "initialization")
        val timescale = attr(template, "timescale")?.toLongOrNull()?.takeIf { it > 0L } ?: 1L
        val startNumber = attr(template, "startNumber")?.toLongOrNull() ?: 1L

        val urls = ArrayList<String>()
        if (initialization != null) {
            urls.add(resolve(base, fillTemplate(initialization, repId, bandwidth, 0L, 0L)))
        }

        val timeline = childElements(template, "SegmentTimeline").firstOrNull()
        if (timeline != null) {
            var time = 0L
            var number = startNumber
            val totalTicks: Long? = periodSeconds?.let { (it * timescale).toLong() }
            for (s in childElements(timeline, "S")) {
                val t = attr(s, "t")?.toLongOrNull()
                if (t != null) time = t
                val d = attr(s, "d")?.toLongOrNull() ?: return null
                if (d <= 0L) return null
                var repeat = attr(s, "r")?.toLongOrNull() ?: 0L
                if (repeat < 0L) {
                    if (totalTicks == null) return null
                    repeat = ((totalTicks - time + d - 1L) / d) - 1L
                    if (repeat < 0L) repeat = 0L
                }
                var n = 0L
                while (n <= repeat) {
                    urls.add(resolve(base, fillTemplate(media, repId, bandwidth, number, time)))
                    time += d
                    number += 1L
                    n += 1L
                    if (urls.size > MAX_SEGMENTS) return null
                }
            }
        } else {
            val duration = attr(template, "duration")?.toDoubleOrNull() ?: return null
            val total = periodSeconds ?: return null
            val segmentSeconds = duration / timescale
            if (segmentSeconds <= 0.0) return null
            val count = ceil(total / segmentSeconds).toLong()
            if (count <= 0L || count > MAX_SEGMENTS) return null
            var i = 0L
            while (i < count) {
                val number = startNumber + i
                val time = (i * duration).toLong()
                urls.add(resolve(base, fillTemplate(media, repId, bandwidth, number, time)))
                i += 1L
            }
        }
        return if (urls.isEmpty()) null else urls
    }

    private fun listUrls(list: Element, base: String): List<String>? {
        val urls = ArrayList<String>()
        val init = childElements(list, "Initialization").firstOrNull()
        val initUrl = if (init != null) attr(init, "sourceURL") else null
        if (initUrl != null) urls.add(resolve(base, initUrl))
        for (segment in childElements(list, "SegmentURL")) {
            val media = attr(segment, "media") ?: return null
            urls.add(resolve(base, media))
        }
        return if (urls.isEmpty()) null else urls
    }

    internal fun fillTemplate(template: String, repId: String, bandwidth: Long, number: Long, time: Long): String {
        return templateVariable.replace(template) { match ->
            val name = match.groupValues[1]
            val width = match.groupValues[2].toIntOrNull() ?: 0
            val value: String = when (name) {
                "" -> "\u0024"
                "RepresentationID" -> repId
                "Bandwidth" -> bandwidth.toString()
                "Number" -> number.toString()
                "Time" -> time.toString()
                else -> match.value
            }
            if (width > 0 && (name == "Number" || name == "Time" || name == "Bandwidth")) {
                value.padStart(width, '0')
            } else {
                value
            }
        }
    }

    // ------------------------------------------------------------------ helpers

    private fun nameOf(node: Node): String = node.nodeName.substringAfter(':')

    private fun childElements(parent: Element, name: String): List<Element> {
        val out = ArrayList<Element>()
        val nodes = parent.childNodes
        for (i in 0 until nodes.length) {
            val node = nodes.item(i)
            if (node.nodeType == Node.ELEMENT_NODE && nameOf(node).equals(name, ignoreCase = true)) {
                out.add(node as Element)
            }
        }
        return out
    }

    private fun attr(element: Element?, name: String): String? {
        if (element == null) return null
        val value = element.getAttribute(name)
        return if (value.isNullOrEmpty()) null else value
    }

    private fun applyBaseUrl(parentBase: String, element: Element): String {
        val node = childElements(element, "BaseURL").firstOrNull() ?: return parentBase
        val text = node.textContent?.trim()
        if (text.isNullOrEmpty()) return parentBase
        return resolve(parentBase, text)
    }

    private fun resolve(base: String, reference: String): String {
        val ref = reference.trim()
        return try {
            URI(base).resolve(ref).toString()
        } catch (ignored: Exception) {
            ref
        }
    }

    private fun parseIsoDuration(raw: String?): Double? {
        if (raw.isNullOrBlank()) return null
        val match = isoDuration.find(raw.trim()) ?: return null
        val days = match.groupValues[1].toDoubleOrNull() ?: 0.0
        val hours = match.groupValues[2].toDoubleOrNull() ?: 0.0
        val minutes = match.groupValues[3].toDoubleOrNull() ?: 0.0
        val seconds = match.groupValues[4].toDoubleOrNull() ?: 0.0
        val total = days * 86400.0 + hours * 3600.0 + minutes * 60.0 + seconds
        return if (total > 0.0) total else null
    }
}
