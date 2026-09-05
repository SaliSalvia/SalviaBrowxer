package com.salvia.salviabrowxer.core.model

enum class DownloadState {
    QUEUED,
    RESOLVING,
    PREPARING,
    DOWNLOADING,
    PAUSED,
    RETRYING,
    PROCESSING,
    COMPLETED,
    FAILED,
    CANCELLED
}

data class DownloadProgress(
    val downloadedBytes: Long,
    val totalBytes: Long? = null,
    val percentage: Float = 0f,
    val speed: Long = 0,
    val eta: Long? = null
)