package com.salvia.salviabrowxer.media.detector

import com.salvia.salviabrowxer.core.model.MediaCandidate
import com.salvia.salviabrowxer.core.model.MediaCandidate.MediaSource
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements

class DomMediaDetector : MediaDetector {

    private val mediaExtensions = listOf(
        "mp4", "webm", "mov", "avi", "3gp", "m4v", "mkv", "flv",
        "m3u8", "mpd", "ts",
        "mp3", "m4a", "aac", "wav"
    )

    private val videoMimeTypes = listOf(
        "video/mp4", "video/webm", "video/quicktime", "video/3gpp",
        "application/vnd.apple.mpegurl", "application/x-mpegURL", "application/dash+xml"
    )

    private val audioMimeTypes = listOf(
        "audio/mpeg", "audio/mp4", "audio/aac", "audio/wav"
    )

    override suspend fun detect(pageUrl: String, html: String): List<MediaCandidate> {
        val candidates = mutableListOf<MediaCandidate>()
        val doc: Document = Jsoup.parse(html, pageUrl)

        detectVideoElements(doc, pageUrl, candidates)
        detectAudioElements(doc, pageUrl, candidates)
        detectSourceElements(doc, pageUrl, candidates)
        detectMediaLinks(doc, pageUrl, candidates)

        return candidates
    }

    private fun detectVideoElements(doc: Document, pageUrl: String, candidates: MutableList<MediaCandidate>) {
        val videoElements: Elements = doc.select("video")
        videoElements.forEach { element ->
            val src = element.attr("src")
            if (src.isNotEmpty()) {
                val absoluteUrl = makeAbsoluteUrl(pageUrl, src)
                candidates.add(
                    MediaCandidate(
                        pageUrl = pageUrl,
                        mediaUrl = absoluteUrl,
                        title = element.attr("title").ifEmpty { null },
                        mimeType = element.attr("type").ifEmpty { null },
                        source = MediaSource.DOM,
                        confidence = 0.9f,
                        isLive = element.hasAttr("live")
                    )
                )
            }

            val sources: Elements = element.select("source")
            sources.forEach { source ->
                val src = source.attr("src")
                if (src.isNotEmpty()) {
                    val absoluteUrl = makeAbsoluteUrl(pageUrl, src)
                    candidates.add(
                        MediaCandidate(
                            pageUrl = pageUrl,
                            mediaUrl = absoluteUrl,
                            title = source.attr("title").ifEmpty { null },
                            mimeType = source.attr("type").ifEmpty { null },
                            source = MediaSource.DOM,
                            confidence = 0.8f
                        )
                    )
                }
            }
        }
    }

    private fun detectAudioElements(doc: Document, pageUrl: String, candidates: MutableList<MediaCandidate>) {
        val audioElements: Elements = doc.select("audio")
        audioElements.forEach { element ->
            val src = element.attr("src")
            if (src.isNotEmpty()) {
                val absoluteUrl = makeAbsoluteUrl(pageUrl, src)
                candidates.add(
                    MediaCandidate(
                        pageUrl = pageUrl,
                        mediaUrl = absoluteUrl,
                        title = element.attr("title").ifEmpty { null },
                        mimeType = element.attr("type").ifEmpty { null },
                        source = MediaSource.DOM,
                        confidence = 0.9f
                    )
                )
            }

            val sources: Elements = element.select("source")
            sources.forEach { source ->
                val src = source.attr("src")
                if (src.isNotEmpty()) {
                    val absoluteUrl = makeAbsoluteUrl(pageUrl, src)
                    candidates.add(
                        MediaCandidate(
                            pageUrl = pageUrl,
                            mediaUrl = absoluteUrl,
                            title = source.attr("title").ifEmpty { null },
                            mimeType = source.attr("type").ifEmpty { null },
                            source = MediaSource.DOM,
                            confidence = 0.8f
                        )
                    )
                }
            }
        }
    }

    private fun detectSourceElements(doc: Document, pageUrl: String, candidates: MutableList<MediaCandidate>) {
        val sourceElements: Elements = doc.select("source")
        sourceElements.forEach { element ->
            val src = element.attr("src")
            if (src.isNotEmpty()) {
                val absoluteUrl = makeAbsoluteUrl(pageUrl, src)
                val mimeType = element.attr("type")
                val isMedia = videoMimeTypes.contains(mimeType) ||
                        audioMimeTypes.contains(mimeType) ||
                        mediaExtensions.any { ext -> absoluteUrl.endsWith(ext, ignoreCase = true) }

                if (isMedia) {
                    candidates.add(
                        MediaCandidate(
                            pageUrl = pageUrl,
                            mediaUrl = absoluteUrl,
                            title = element.attr("title").ifEmpty { null },
                            mimeType = mimeType.ifEmpty { null },
                            extension = getExtension(absoluteUrl),
                            source = MediaSource.DOM,
                            confidence = 0.7f
                        )
                    )
                }
            }
        }
    }

    private fun detectMediaLinks(doc: Document, pageUrl: String, candidates: MutableList<MediaCandidate>) {
        val links: Elements = doc.select("a[href]")
        links.forEach { element ->
            val href = element.attr("href")
            if (href.isNotEmpty() && isMediaUrl(href)) {
                val absoluteUrl = makeAbsoluteUrl(pageUrl, href)
                candidates.add(
                    MediaCandidate(
                        pageUrl = pageUrl,
                        mediaUrl = absoluteUrl,
                        title = element.text().ifEmpty { null },
                        mimeType = null,
                        extension = getExtension(absoluteUrl),
                        source = MediaSource.DOM,
                        confidence = 0.6f
                    )
                )
            }
        }
    }

    private fun isMediaUrl(url: String): Boolean {
        return mediaExtensions.any { ext -> url.endsWith(ext, ignoreCase = true) }
    }

    private fun getExtension(url: String): String? {
        val lastDotIndex = url.lastIndexOf('.')
        val lastSlashIndex = url.lastIndexOf('/')
        return if (lastDotIndex > lastSlashIndex && lastDotIndex < url.length - 1) {
            url.substring(lastDotIndex + 1).lowercase()
        } else {
            null
        }
    }

    private fun makeAbsoluteUrl(baseUrl: String, relativeUrl: String): String {
        return if (relativeUrl.startsWith("http://") || relativeUrl.startsWith("https://")) {
            relativeUrl
        } else if (relativeUrl.startsWith("//")) {
            "https:$relativeUrl"
        } else if (relativeUrl.startsWith("/")) {
            val baseUri = java.net.URI(baseUrl)
            "${baseUri.scheme}://${baseUri.host}${if (baseUri.port != -1) ":${baseUri.port}" else ""}$relativeUrl"
        } else {
            val baseUri = java.net.URI(baseUrl)
            val basePath = baseUri.path
            val lastSlashIndex = basePath.lastIndexOf('/')
            val parentPath = if (lastSlashIndex >= 0) basePath.substring(0, lastSlashIndex) else basePath
            "${baseUri.scheme}://${baseUri.host}${if (baseUri.port != -1) ":${baseUri.port}" else ""}$parentPath/$relativeUrl"
        }
    }
}