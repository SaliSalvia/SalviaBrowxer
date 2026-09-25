package com.salvia.salviabrowxer.media.detector

import com.salvia.salviabrowxer.core.model.MediaCandidate
import com.salvia.salviabrowxer.core.model.MediaCandidate.MediaSource
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

class DomMediaDetector : MediaDetector {

    private val videoExtRegex = Regex("""\.(mp4|webm|mov|avi|3gp|m4v|mkv|flv|m3u8|mpd|ts)(\?|#|$)""", RegexOption.IGNORE_CASE)
    private val audioExtRegex = Regex("""\.(mp3|m4a|aac|wav|flac|ogg|wma)(\?|#|$)""", RegexOption.IGNORE_CASE)
    private val anyMediaExtRegex = Regex("""\.(mp4|webm|mov|avi|3gp|m4v|mkv|flv|m3u8|mpd|ts|mp3|m4a|aac|wav|flac|ogg|wma)(\?|#|$)""", RegexOption.IGNORE_CASE)

    // Captures any http(s) URL that contains a media extension (for JSON/script extraction)
    private val urlInTextRegex = Regex("""https?://[^\s"'<>]+\.(mp4|webm|mov|avi|3gp|m4v|mkv|flv|m3u8|mpd|ts|mp3|m4a|aac|wav|flac|ogg|wma)[^\s"'<>]*""", RegexOption.IGNORE_CASE)

    override suspend fun detect(pageUrl: String, html: String?): List<MediaCandidate> {
        if (html.isNullOrBlank()) return emptyList()
        // Hard cap: HTML already trimmed to 62k by JS, but defend against huge pasted values
        val safeHtml = if (html.length > 70000) html.take(70000) else html
        val candidates = mutableListOf<MediaCandidate>()
        val doc: Document = runCatching { Jsoup.parse(safeHtml, pageUrl) }.getOrNull() ?: return emptyList()

        detectVideoAudio(doc, pageUrl, candidates)
        detectSourceElements(doc, pageUrl, candidates)
        detectAnchors(doc, pageUrl, candidates)
        detectMetaTags(doc, pageUrl, candidates)
        detectJsonUrls(safeHtml, pageUrl, candidates)
        detectPlainUrls(safeHtml, pageUrl, candidates)

        // Final dedupe: exact mediaUrl
        return candidates.distinctBy { it.mediaUrl }.take(24)
    }

    private fun detectVideoAudio(doc: Document, pageUrl: String, out: MutableList<MediaCandidate>) {
        for (tag in arrayOf("video", "audio")) {
            doc.select(tag).forEach { el ->
                val src = el.attr("src").trim()
                if (src.isNotEmpty()) {
                    out += candidate(pageUrl, src, el.attr("title").ifEmpty { null }, el.attr("type").ifEmpty { null }, confidence = 0.92f)
                }
                // poster often is thumbnail
                val poster = if (tag == "video") el.attr("poster").trim().ifEmpty { null } else null
                el.select("source").forEach { srcEl ->
                    val s = srcEl.attr("src").trim()
                    if (s.isNotEmpty()) {
                        val c = candidate(pageUrl, s, srcEl.attr("title").ifEmpty { null }, srcEl.attr("type").ifEmpty { null }, confidence = 0.86f)
                        out += if (poster != null) c.copy(thumbnailUrl = makeAbsolute(pageUrl, poster)) else c
                    }
                }
            }
        }
    }

    private fun detectSourceElements(doc: Document, pageUrl: String, out: MutableList<MediaCandidate>) {
        // Standalone <source> outside video (e.g. picture/srcset not media, so filter)
        doc.select("source[src]").forEach { el ->
            val src = el.attr("src").trim()
            if (src.isEmpty()) return@forEach
            val type = el.attr("type").trim().ifEmpty { null }
            // Only keep if mime or extension suggests media
            val abs = makeAbsolute(pageUrl, src)
            if (isMediaUrl(abs, type)) {
                // Avoid double-adding those already under video/audio (distinctBy covers it)
                out += candidate(pageUrl, src, null, type, confidence = 0.72f)
            }
        }
    }

    private fun detectAnchors(doc: Document, pageUrl: String, out: MutableList<MediaCandidate>) {
        doc.select("a[href]").forEach { el ->
            val href = el.attr("href").trim()
            if (href.isEmpty() || href.startsWith("javascript:") || href.startsWith("#")) return@forEach
            val abs = makeAbsolute(pageUrl, href)
            if (anyMediaExtRegex.containsMatchIn(abs)) {
                out += MediaCandidate(
                    pageUrl = pageUrl, mediaUrl = abs,
                    title = el.text().trim().ifEmpty { el.attr("download").trim().ifEmpty { null } },
                    extension = getExt(abs), source = MediaSource.DOM, confidence = 0.64f
                )
            }
        }
        // <link rel="preload" as="video"> etc
        doc.select("link[href]").forEach { el ->
            val asAttr = el.attr("as").lowercase()
            if (asAttr !in setOf("video", "audio")) return@forEach
            val href = el.attr("href").trim().ifEmpty { return@forEach }
            out += candidate(pageUrl, href, null, null, confidence = 0.7f)
        }
    }

    private fun detectMetaTags(doc: Document, pageUrl: String, out: MutableList<MediaCandidate>) {
        // og:video, og:audio, twitter:player:stream
        val metaProps = listOf("og:video", "og:video:url", "og:video:secure_url", "og:audio", "twitter:player:stream")
        for (prop in metaProps) {
            doc.select("meta[property=$prop], meta[name=$prop]").forEach { el ->
                val content = el.attr("content").trim().ifEmpty { return@forEach }
                val abs = makeAbsolute(pageUrl, content)
                if (abs.isNotBlank()) out += candidate(pageUrl, abs, null, null, confidence = 0.88f)
            }
        }
    }

    private fun detectJsonUrls(html: String, pageUrl: String, out: MutableList<MediaCandidate>) {
        // Scan raw HTML for any http(s) media URL hidden inside JSON/script
        // Limit matches to avoid regex disaster on huge pages
        var count = 0
        for (match in urlInTextRegex.findAll(html)) {
            if (count++ > 16) break
            val url = match.value.trim().trimEnd(',', ';')
            if (url.length > 500) continue
            out += MediaCandidate(pageUrl = pageUrl, mediaUrl = url, extension = getExt(url), source = MediaSource.DOM, confidence = 0.58f)
        }
    }

    private fun detectPlainUrls(html: String, pageUrl: String, out: MutableList<MediaCandidate>) {
        // Also catch protocol-relative //cdn.example.com/video.mp4
        val protoRel = Regex("""//[^\s"'<>]+\.(mp4|webm|mov|m3u8|mpd|mp3|m4a)[^\s"'<>]*""", RegexOption.IGNORE_CASE)
        var c = 0
        for (m in protoRel.findAll(html)) {
            if (c++ > 8) break
            val url = "https:${m.value}"
            out += MediaCandidate(pageUrl = pageUrl, mediaUrl = url, extension = getExt(url), source = MediaSource.DOM, confidence = 0.5f)
        }
    }

    private fun isMediaUrl(url: String, mime: String?): Boolean {
        if (mime != null && (mime.startsWith("video/") || mime.startsWith("audio/") || "mpegurl" in mime || "dash+xml" in mime)) return true
        return anyMediaExtRegex.containsMatchIn(url)
    }

    private fun candidate(pageUrl: String, rawUrl: String, title: String?, mime: String?, confidence: Float): MediaCandidate {
        val abs = makeAbsolute(pageUrl, rawUrl)
        return MediaCandidate(pageUrl = pageUrl, mediaUrl = abs, title = title, mimeType = mime?.ifEmpty { null }, extension = getExt(abs), source = MediaSource.DOM, confidence = confidence)
    }

    private fun getExt(url: String): String? {
        val path = url.substringBefore('#').substringBefore('?')
        val dot = path.lastIndexOf('.')
        val slash = path.lastIndexOf('/')
        return if (dot > slash && dot < path.length - 1) path.substring(dot + 1).lowercase().takeIf { it.length <= 5 && it.all(Char::isLetterOrDigit) } else null
    }

    private fun makeAbsolute(baseUrl: String, relativeUrl: String): String {
        val r = relativeUrl.trim()
        return when {
            r.startsWith("http://") || r.startsWith("https://") || r.startsWith("blob:") || r.startsWith("data:") -> r
            r.startsWith("//") -> "https:$r"
            r.startsWith("/") -> {
                runCatching {
                    val u = java.net.URI(baseUrl)
                    "${u.scheme}://${u.host}${if (u.port != -1) ":${u.port}" else ""}$r"
                }.getOrDefault(r)
            }
            else -> {
                runCatching {
                    val u = java.net.URI(baseUrl)
                    val path = u.path ?: "/"
                    val lastSlash = path.lastIndexOf('/')
                    val parent = if (lastSlash >= 0) path.substring(0, lastSlash) else path
                    "${u.scheme}://${u.host}${if (u.port != -1) ":${u.port}" else ""}$parent/$r"
                }.getOrDefault(r)
            }
        }
    }
}
