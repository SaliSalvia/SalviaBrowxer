package com.salvia.salviabrowxer.media.detector

import com.salvia.salviabrowxer.core.model.MediaCandidate

interface MediaDetector {
    suspend fun detect(pageUrl: String, html: String? = null): List<MediaCandidate>
}
