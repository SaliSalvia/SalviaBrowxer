package com.salvia.salviabrowxer.ui.utils

object Constants {
    const val APP_NAME = "SalviaBrowxer"
    const val PACKAGE_NAME = "com.salvia.salviabrowxer"

    const val DEFAULT_HOMEPAGE = "https://www.google.com"
    const val DEFAULT_USER_AGENT = "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/100.0.0.0 Mobile Safari/537.36"
    const val DESKTOP_USER_AGENT = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/100.0.0.0 Safari/537.36"

    const val DEFAULT_DOWNLOAD_DIRECTORY = "SalviaBrowxer"
    const val MAX_SIMULTANEOUS_DOWNLOADS = 3
    const val DOWNLOAD_TIMEOUT_SECONDS = 30L
    const val DOWNLOAD_RETRY_COUNT = 3
    const val CHUNK_SIZE = 8192

    val SUPPORTED_VIDEO_EXTENSIONS = listOf(
        "mp4", "webm", "mov", "avi", "3gp", "m4v", "mkv", "flv"
    )
    val SUPPORTED_AUDIO_EXTENSIONS = listOf(
        "mp3", "m4a", "aac", "wav", "flac", "ogg", "wma"
    )
    val SUPPORTED_IMAGE_EXTENSIONS = listOf(
        "jpg", "jpeg", "png", "gif", "webp", "bmp", "svg"
    )
    val SUPPORTED_PLAYLIST_EXTENSIONS = listOf(
        "m3u8", "mpd", "ts"
    )

    val SUPPORTED_VIDEO_MIME_TYPES = listOf(
        "video/mp4", "video/webm", "video/quicktime", "video/3gpp",
        "video/x-matroska", "video/x-flv", "video/mp2t", "video/x-msvideo"
    )
    val SUPPORTED_AUDIO_MIME_TYPES = listOf(
        "audio/mpeg", "audio/mp4", "audio/aac", "audio/wav",
        "audio/flac", "audio/ogg", "audio/x-ms-wma"
    )
    val SUPPORTED_PLAYLIST_MIME_TYPES = listOf(
        "application/vnd.apple.mpegurl", "application/x-mpegURL", "application/dash+xml"
    )

    const val CONNECT_TIMEOUT_SECONDS = 10L
    const val READ_TIMEOUT_SECONDS = 30L
    const val WRITE_TIMEOUT_SECONDS = 30L

    const val DATABASE_NAME = "salviabrowxer_db"
    const val DATABASE_VERSION = 1

    const val NOTIFICATION_CHANNEL_ID = "download_channel"
    const val NOTIFICATION_CHANNEL_NAME = "Downloads"
    const val NOTIFICATION_CHANNEL_DESCRIPTION = "Download notifications"
    const val NOTIFICATION_ID_DOWNLOAD = 1000

    val SEARCH_ENGINES = mapOf(
        "Google" to "https://www.google.com/search?q=%s",
        "DuckDuckGo" to "https://duckduckgo.com/?q=%s",
        "Bing" to "https://www.bing.com/search?q=%s",
        "Yahoo" to "https://search.yahoo.com/search?p=%s",
        "Ecosia" to "https://www.ecosia.org/search?q=%s"
    )
}