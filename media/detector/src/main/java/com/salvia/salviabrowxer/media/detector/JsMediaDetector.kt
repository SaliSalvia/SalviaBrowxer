package com.salvia.salviabrowxer.media.detector

import com.salvia.salviabrowxer.core.model.MediaCandidate

class JsMediaDetector : MediaDetector {
    override suspend fun detect(pageUrl: String, html: String?): List<MediaCandidate> {
        return emptyList()
    }
}