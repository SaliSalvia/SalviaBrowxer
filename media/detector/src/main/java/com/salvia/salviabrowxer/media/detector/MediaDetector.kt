package com.salvia.salviabrowxer.media.detector

import com.salvia.salviabrowxer.core.model.MediaCandidate

interface MediaDetector {
    suspend fun detect(pageUrl: String, html: String? = null): List<MediaCandidate>
}

class MediaDetector : MediaDetector {
    private val domDetector = DomMediaDetector()
    private val jsDetector = JsMediaDetector()

    override suspend fun detect(pageUrl: String, html: String?): List<MediaCandidate> {
        val candidates = mutableListOf<MediaCandidate>()
        html?.let { candidates.addAll(domDetector.detect(pageUrl, it)) }
        candidates.addAll(jsDetector.detect(pageUrl, html))
        return candidates.distinctBy { it.mediaUrl }
    }
}