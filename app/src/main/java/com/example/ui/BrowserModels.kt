package com.example.ui

import java.util.UUID

data class MediaQualityOption(
    val id: String = UUID.randomUUID().toString(),
    val label: String,        // e.g. "1080p Full HD", "720p HD", "480p", "Audio Only (M4A)"
    val resolutionHeight: Int, // 1080, 720, 480, 360, 0 for audio
    val url: String,
    val audioUrl: String = "", // Optional separate audio stream URL (HLS/DASH) for muxing into MP4
    val format: String,        // "MP4", "HLS", "DASH", "WEBM", "M4A", "MP3"
    val mimeType: String,
    val sizeBytes: Long = -1L,
    val bandwidthBps: Long = 0L,
    val isHls: Boolean = false,
    val isDash: Boolean = false,
)

enum class MediaDetectionSource {
    NETWORK_HOOK,
    HTML5_ELEMENT,
    JSON_LD,
    OPEN_GRAPH,
    MSE_REFERENCE,
    UNKNOWN,
}

enum class MediaCaptureStatus {
    DOWNLOADABLE,
    BLOB_OR_MSE_REFERENCE_ONLY,
    LIVE_MEDIA_STREAM_REFERENCE_ONLY,
}

data class DetectedMedia(
    val id: String,
    val title: String,
    val sourcePageUrl: String,
    val primaryUrl: String,
    val thumbnailUrl: String = "",
    val durationSeconds: Double = 0.0,
    val width: Int = 0,
    val height: Int = 0,
    val domAreaPx: Long = 0L,
    val priorityScore: Int = 0,
    val isMainVideo: Boolean = false,
    val isAudioOnly: Boolean = false,
    val qualities: List<MediaQualityOption> = emptyList(),
    val detectionSource: MediaDetectionSource = MediaDetectionSource.UNKNOWN,
    val captureStatus: MediaCaptureStatus = MediaCaptureStatus.DOWNLOADABLE,
    val confidenceScore: Int = 50,
    val platformLabel: String = "Web",
    val mimeType: String = "",
    val detectedAt: Long = System.currentTimeMillis(),
) {
    val isDownloadable: Boolean
        get() = captureStatus == MediaCaptureStatus.DOWNLOADABLE &&
            qualities.any { quality ->
                val uri = runCatching { java.net.URI(quality.url) }.getOrNull()
                uri != null &&
                    uri.scheme?.lowercase() in setOf("http", "https") &&
                    !uri.host.isNullOrBlank()
            }
}

data class ConsoleLogEntry(
    val id: String = UUID.randomUUID().toString(),
    val level: String, // "LOG", "WARN", "ERROR", "INFO", "EVAL"
    val message: String,
    val source: String = "",
    val timestamp: Long = System.currentTimeMillis(),
)

data class NetworkRequestEntry(
    val id: String = UUID.randomUUID().toString(),
    val method: String,
    val url: String,
    val resourceType: String, // "DOC", "XHR", "SCRIPT", "MEDIA", "IMG", "CSS", "OTHER"
    val isBlocked: Boolean = false,
    val blockReason: String = "",
    val isMediaDetected: Boolean = false,
    val headersSummary: String = "",
    val timestamp: Long = System.currentTimeMillis(),
)

data class DomInspectionSnapshot(
    val url: String = "",
    val title: String = "",
    val htmlSource: String = "",
    val metaTags: List<Pair<String, String>> = emptyList(),
    val cookies: String = "",
    val localStorageJson: String = "{}",
    val sessionStorageJson: String = "{}",
    val userAgent: String = "",
    val viewport: String = "",
)

data class BrowserTab(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "New Tab",
    val url: String = "",
    val isStartPage: Boolean = true,
    val isIncognito: Boolean = false,
    val isDesktopMode: Boolean = false,
    val isLoading: Boolean = false,
    val progress: Int = 0,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val isSecureHttps: Boolean = true,
    val isHibernated: Boolean = false,
    val blockedAdsCount: Int = 0,
    val blockedTrackersCount: Int = 0,
    val detectedMedia: List<DetectedMedia> = emptyList(),
    val consoleLogs: List<ConsoleLogEntry> = emptyList(),
    val networkRequests: List<NetworkRequestEntry> = emptyList(),
    val domSnapshot: DomInspectionSnapshot = DomInspectionSnapshot(),
) {
    val hasMainMedia: Boolean
        get() = detectedMedia.isNotEmpty()

    val totalBlockedOnTab: Int
        get() = blockedAdsCount + blockedTrackersCount
}

enum class DevToolsTab(val label: String) {
    CONSOLE("Console"),
    NETWORK("Network"),
    DOM("DOM & HTML"),
    STORAGE("Storage"),
}

enum class ActiveSheet {
    NONE,
    TABS,
    MEDIA_DOWNLOADER,
    EXTENSIONS,
    DEVTOOLS,
    BOOKMARKS,
    HISTORY,
    DOWNLOADS,
    SETTINGS,
    SITE_INFO,
    FIND_IN_PAGE,
}
