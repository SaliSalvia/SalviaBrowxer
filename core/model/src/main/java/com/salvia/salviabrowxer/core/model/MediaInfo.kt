package com.salvia.salviabrowxer.core.model

data class MediaInfo(
    val title: String,
    val thumbnail: String? = null,
    val duration: Long? = null,
    val formats: List<MediaFormat> = emptyList(),
    val audioFormats: List<MediaFormat> = emptyList(),
    val videoFormats: List<MediaFormat> = emptyList(),
    val combinedFormats: List<MediaFormat> = emptyList(),
    val source: String,
    val extractor: String? = null,
    val webpageUrl: String
)

data class MediaFormat(
    val id: String,
    val format: String,
    val url: String,
    val mimeType: String,
    val extension: String,
    val size: Long? = null,
    val width: Int? = null,
    val height: Int? = null,
    val frameRate: Float? = null,
    val bitrate: Int? = null,
    val audioBitrate: Int? = null,
    val isVideo: Boolean = false,
    val isAudio: Boolean = false,
    val isHls: Boolean = false,
    val isDash: Boolean = false
)