package com.salvia.salviabrowxer.service

import com.salvia.salviabrowxer.core.model.MediaCandidate
import com.salvia.salviabrowxer.media.detector.DomMediaDetector
import com.salvia.salviabrowxer.media.detector.MediaDetector
import javax.inject.Inject

class MediaDetectionService @Inject constructor(
    private val mediaDetector: MediaDetector
) {
    suspend fun detectMedia(pageUrl: String, html: String? = null): List<MediaCandidate> {
        return mediaDetector.detect(pageUrl, html)
    }
}