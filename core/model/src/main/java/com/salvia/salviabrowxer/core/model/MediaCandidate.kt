package com.salvia.salviabrowxer.core.model

import java.util.UUID

data class MediaCandidate(
    val id: String = UUID.randomUUID().toString(),
    val pageUrl: String,
    val mediaUrl: String,
    val title: String? = null,
    val thumbnailUrl: String? = null,
    val mimeType: String? = null,
    val extension: String? = null,
    val estimatedSize: Long? = null,
    val duration: Long? = null,
    val source: MediaSource = MediaSource.UNKNOWN,
    val confidence: Float = 0f,
    val isLive: Boolean = false,
    val requiresResolver: Boolean = false,
    val requiresAuthentication: Boolean = false
) {
    enum class MediaSource {
        DOM,
        JS,
        WEBVIEW,
        YT_DLP,
        UNKNOWN
    }
}