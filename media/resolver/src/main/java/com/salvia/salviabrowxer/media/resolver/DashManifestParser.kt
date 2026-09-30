package com.salvia.salviabrowxer.media.resolver

import com.salvia.salviabrowxer.core.model.DashManifest
import com.salvia.salviabrowxer.core.model.DashRepresentation
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource

/**
 * A bounded MPEG-DASH manifest reader for the one shape this app can honestly save.
 *
 * Supported: a **static** (finite) manifest with a **single Period**, no `ContentProtection`, and
 * representations described by `SegmentTemplate` (fixed `duration` or a `SegmentTimeline`),
 * `SegmentList`, or a single-file `SegmentBase`. Everything else is refused by returning `null`,
 * so the UI keeps its honest "cannot be saved" message instead of starting a transfer that would
 * fail. Dynamic/live manifestos, multi-period (ad-inserted) ones and DRM are all out of scope on
 * purpose — the app does not defeat protection.
 *
 * This is deliberately a pure XML reader with no Android or network dependency, so it is unit
 * tested directly on the JVM.
 */
object DashManifestParser {

    /** A hard bound on the URLs one representation may contribute, to keep a hostile manifest small. */
    private const val MAX_SEGMENTS_PER_REPRESENTATION = 6000

    fun parse(manifestUrl: String, xml: String): DashManifest? {
        val root = try {
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = false
                isExpandEntityReferences = false
                setFeatureQuietly("http://apache.org/xml/features/disallow-doctype-decl", true)
                setFeatureQuietly("http://xml.org/sax/features/external-general-entities", false)
                setFeatureQuietly("http://xml.org/sax/features/external-parameter-entities", false)
            }
            val builder = factory.newDocumentBuilder()
            // Refuse every external entity: the manifest is fetched from an untrusted CDN.
            builder.setEntityResolver { _, _ -> InputSource(StringReader("")) }
            builder.parse(InputSource(StringReader(xml))).documentElement
        } catch (_: Exception) {
            return null
        } ?: return null

        if (localName(root) != "MPD") return null
        val type = root.getAttribute("type").takeIf { it.isNotBlank() } ?: "static"
        if (!type.equals("static", ignoreCase = true)) return null
        if (containsContentProtection(root)) return null

        val durationSeconds = parseIsoDuration(root.getAttribute("mediaPresentationDuration"))
        val mpdBase = resolve(manifestUrl, directChildText(root, "BaseURL"))
        val periods = directChildren(root, "Period")
        // A multi-period presentation is usually ad insertion; one period keeps segment math honest.
        if (periods.size != 1) return null
        val period = periods.first()
        val periodBase = resolve(mpdBase, directChildText(period, "BaseURL"))

        val representations = mutableListOf<DashRepresentation>()
        for (adaptation in directChildren(period, "AdaptationSet")) {
            if (containsContentProtection(adaptation)) return null
            val adaptationBase = resolve(periodBase, directChildText(adaptation, "BaseURL"))
            for (representation in directChildren(adaptation, "Representation")) {
                if (containsContentProtection(representation)) return null
                val built = buildRepresentation(period, adaptation, representation, adaptationBase, durationSeconds) ?: continue
                representations += built
            }
        }
        if (representations.isEmpty()) return null
        return DashManifest(sourceUrl = manifestUrl, isStatic = true, durationSeconds = durationSeconds, representations = representations)
    }

    private fun buildRepresentation(
        period: Element,
        adaptation: Element,
        representation: Element,
        adaptationBase: String,
        durationSeconds: Double?
    ): DashRepresentation? {
        val mimeType = firstNonBlank(
            representation.getAttribute("mimeType"),
            adaptation.getAttribute("mimeType"),
            contentTypeToMime(adaptation.getAttribute("contentType"))
        ) ?: return null
        val isVideo = mimeType.startsWith("video/", ignoreCase = true)
        val isAudio = mimeType.startsWith("audio/", ignoreCase = true)
        if (!isVideo && !isAudio) return null

        val id = firstNonBlank(representation.getAttribute("id")) ?: return null
        val codecs = firstNonBlank(representation.getAttribute("codecs"), adaptation.getAttribute("codecs"))
        val bandwidth = representation.getAttribute("bandwidth").toLongOrNull()
        val width = representation.getAttribute("width").toIntOrNull()
        val height = representation.getAttribute("height").toIntOrNull()
        val language = firstNonBlank(adaptation.getAttribute("lang"))
        val repBase = resolve(adaptationBase, directChildText(representation, "BaseURL"))

        val template = directChild(representation, "SegmentTemplate")
            ?: directChild(adaptation, "SegmentTemplate")
            ?: directChild(period, "SegmentTemplate")
        if (template != null) {
            val segments = buildFromTemplate(template, repBase, durationSeconds, id, bandwidth) ?: return null
            return DashRepresentation(
                id = id, mimeType = mimeType, codecs = codecs, bandwidth = bandwidth,
                width = width, height = height, language = language,
                isVideo = isVideo, isAudio = isAudio,
                initUrl = segments.initUrl, segmentUrls = segments.segmentUrls
            )
        }

        val list = directChild(representation, "SegmentList") ?: directChild(adaptation, "SegmentList")
        if (list != null) {
            val init = directChild(list, "Initialization")?.getAttribute("sourceURL")?.takeIf { it.isNotBlank() }
            val urls = directChildren(list, "SegmentURL")
                .mapNotNull { it.getAttribute("media")?.takeIf { m -> m.isNotBlank() } }
                .map { resolve(repBase, it) }
                .take(MAX_SEGMENTS_PER_REPRESENTATION)
            if (urls.isEmpty()) return null
            return DashRepresentation(
                id = id, mimeType = mimeType, codecs = codecs, bandwidth = bandwidth,
                width = width, height = height, language = language,
                isVideo = isVideo, isAudio = isAudio,
                initUrl = init?.let { resolve(repBase, it) }, segmentUrls = urls
            )
        }

        // SegmentBase: the representation's BaseURL *is* the media file.
        val hasSegmentBase = directChild(representation, "SegmentBase") != null || directChild(adaptation, "SegmentBase") != null
        if (hasSegmentBase && repBase != adaptationBase && looksLikeFile(repBase)) {
            return DashRepresentation(
                id = id, mimeType = mimeType, codecs = codecs, bandwidth = bandwidth,
                width = width, height = height, language = language,
                isVideo = isVideo, isAudio = isAudio, initUrl = null, segmentUrls = listOf(repBase)
            )
        }
        return null
    }

    private data class TemplateSegments(val initUrl: String?, val segmentUrls: List<String>)

    private fun buildFromTemplate(
        template: Element,
        base: String,
        durationSeconds: Double?,
        representationId: String,
        bandwidth: Long?
    ): TemplateSegments? {
        val media = template.getAttribute("media")?.takeIf { it.isNotBlank() } ?: return null
        if ("\$SubNumber\$" in media) return null
        val initTemplate = template.getAttribute("initialization")?.takeIf { it.isNotBlank() }
        val startNumber = template.getAttribute("startNumber").toLongOrNull() ?: 1L
        val timescale = template.getAttribute("timescale").toLongOrNull()?.takeIf { it > 0 } ?: 1L

        val initUrl = initTemplate?.let {
            substitute(it, number = 0, time = 0, representationId = representationId, bandwidth = bandwidth)?.let { u -> resolve(base, u) }
        }

        val timeline = directChild(template, "SegmentTimeline")
        val urls = mutableListOf<String>()
        if (timeline != null) {
            var time = 0L
            var number = startNumber
            for (s in directChildren(timeline, "S")) {
                val start = s.getAttribute("t").toLongOrNull()
                val duration = s.getAttribute("d").toLongOrNull() ?: return null
                if (start != null) time = start
                val repeat = (s.getAttribute("r").toLongOrNull() ?: 0L).coerceIn(0L, MAX_SEGMENTS_PER_REPRESENTATION.toLong())
                var i = 0L
                while (i <= repeat) {
                    val url = substitute(media, number, time, representationId, bandwidth) ?: return null
                    urls += resolve(base, url)
                    if (urls.size >= MAX_SEGMENTS_PER_REPRESENTATION) break
                    time += duration
                    number += 1
                    i += 1
                }
                if (urls.size >= MAX_SEGMENTS_PER_REPRESENTATION) break
            }
        } else {
            val segmentDuration = template.getAttribute("duration").toLongOrNull() ?: return null
            val totalDuration = durationSeconds ?: return null
            if ("\$Time\$" in media) return null // time-based naming needs a timeline
            val count = kotlin.math.ceil(totalDuration * timescale / segmentDuration.toDouble()).toLong()
                .coerceIn(1L, MAX_SEGMENTS_PER_REPRESENTATION.toLong())
            var number = startNumber
            while (urls.size < count) {
                val url = substitute(media, number, 0L, representationId, bandwidth) ?: return null
                urls += resolve(base, url)
                number += 1
            }
        }
        if (urls.isEmpty()) return null
        return TemplateSegments(initUrl, urls)
    }

    private val placeholderRegex = Regex("""\$(RepresentationID|Bandwidth|Number|Time)(?:%0(\d+)d)?\$""")

    /**
     * Replaces `$RepresentationID$`, `$Bandwidth$`, `$Number$`/`$Time$` (optionally zero-padded).
     * Returns `null` when the template uses a placeholder this app does not support, so the caller
     * refuses the representation instead of producing a wrong URL.
     */
    private fun substitute(template: String, number: Long, time: Long, representationId: String, bandwidth: Long?): String? {
        var failed = false
        val result = placeholderRegex.replace(template) { match ->
            val name = match.groupValues[1]
            val pad = match.groupValues[2].toIntOrNull()
            val raw = when (name) {
                "RepresentationID" -> return@replace representationId
                "Bandwidth" -> return@replace (bandwidth ?: 0L).toString()
                "Number" -> number.toString()
                "Time" -> time.toString()
                else -> null
            }
            if (raw == null) {
                failed = true
                return@replace match.value
            }
            if (pad != null && pad > 0) raw.padStart(pad, '0') else raw
        }
        if (failed) return null
        // A leftover dollar placeholder means the template used something we do not understand.
        if ('$' in result) return null
        return result
    }

    private fun containsContentProtection(root: Element): Boolean =
        root.getElementsByTagName("ContentProtection").length > 0

    private fun localName(element: Element): String = element.tagName.substringAfter(':')

    private fun directChildren(parent: Element, name: String): List<Element> {
        val children = parent.childNodes
        val out = mutableListOf<Element>()
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node.nodeType == Node.ELEMENT_NODE && localName(node as Element) == name) out += node
        }
        return out
    }

    private fun directChild(parent: Element, name: String): Element? = directChildren(parent, name).firstOrNull()

    private fun directChildText(parent: Element, name: String): String? =
        directChild(parent, name)?.textContent?.trim()?.takeIf { it.isNotBlank() }

    private fun firstNonBlank(vararg values: String?): String? = values.firstOrNull { !it.isNullOrBlank() }

    private fun contentTypeToMime(contentType: String?): String? = when (contentType?.lowercase()) {
        "video" -> "video/mp4"
        "audio" -> "audio/mp4"
        else -> null
    }

    private fun looksLikeFile(url: String): Boolean {
        val path = url.substringBefore('#').substringBefore('?')
        val name = path.substringAfterLast('/')
        return '.' in name && name.substringAfterLast('.').length in 1..5
    }

    /** ISO-8601 duration (`PT1H2M3.5S`) to seconds; `null` when absent or malformed. */
    fun parseIsoDuration(raw: String?): Double? {
        val value = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val match = Regex("""P(?:(\d+)Y)?(?:(\d+)M)?(?:(\d+)D)?(?:T(?:(\d+(?:\.\d+)?)H)?(?:(\d+(?:\.\d+)?)M)?(?:(\d+(?:\.\d+)?)S)?)?""")
            .find(value) ?: return null
        var seconds = 0.0
        match.groupValues[1].toDoubleOrNull()?.let { seconds += it * 365 * 86_400 }
        match.groupValues[2].toDoubleOrNull()?.let { seconds += it * 30 * 86_400 }
        match.groupValues[3].toDoubleOrNull()?.let { seconds += it * 86_400 }
        match.groupValues[4].toDoubleOrNull()?.let { seconds += it * 3_600 }
        match.groupValues[5].toDoubleOrNull()?.let { seconds += it * 60 }
        match.groupValues[6].toDoubleOrNull()?.let { seconds += it }
        return seconds.takeIf { it > 0 }
    }

    private fun resolve(baseUrl: String, relative: String?): String {
        val value = relative?.trim().orEmpty()
        if (value.isEmpty()) return baseUrl
        return when {
            value.startsWith("http://") || value.startsWith("https://") -> value
            value.startsWith("//") -> "https:$value"
            value.startsWith("/") -> runCatching {
                val u = java.net.URI(baseUrl)
                "${u.scheme}://${u.host}${if (u.port != -1) ":${u.port}" else ""}$value"
            }.getOrDefault(value)
            else -> baseUrl.substringBeforeLast('/') + "/" + value
        }
    }

    private fun DocumentBuilderFactory.setFeatureQuietly(feature: String, value: Boolean) {
        runCatching { setFeature(feature, value) }
    }
}
