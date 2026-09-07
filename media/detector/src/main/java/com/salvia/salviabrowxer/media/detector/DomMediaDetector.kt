package com.salvia.salviabrowxer.media.detector

import com.salvia.salviabrowxer.core.model.MediaCandidate
import com.salvia.salviabrowxer.core.model.MediaCandidate.MediaSource
import com.salvia.salviabrowxer.core.model.MediaFileTypes
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements

class DomMediaDetector : MediaDetector {

    private val mediaExtensions = MediaFileTypes.PLAYLIST_EXTENSIONS +
        MediaFileTypes.VIDEO_EXTENSIONS + MediaFileTypes.AUDIO_EXTENSIONS

    private val videoMimeTypes = MediaFileTypes.VIDEO_MIME_TYPES +
        MediaFileTypes.PLAYLIST_MIME_TYPES

    private val audioMimeTypes = MediaFileTypes.AUDIO_MIME_TYPES

    override suspend fun detect(pageUrl: String, html: String?): List<MediaCandidate> {
        if (html.isNullOrBlank()) return emptyList()
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

    private fun isMediaUrl(url: String): Boolean = MediaFileTypes.isMediaUrl(url)

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