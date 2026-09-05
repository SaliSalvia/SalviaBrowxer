package com.salvia.salviabrowxer.media.extractor

import com.salvia.salviabrowxer.core.model.MediaInfo

interface ExtractorEngine {
    suspend fun resolve(url: String): MediaInfo
    suspend fun getFormats(url: String): List<com.salvia.salviabrowxer.core.model.MediaFormat>
    suspend fun getMetadata(url: String): Map<String, String>
}