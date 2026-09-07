package com.salvia.salviabrowxer.core.model

/**
 * Single source of truth for the extension and MIME lists used by media detection, filename
 * generation and file opening. Every consumer should read from here instead of maintaining its own
 * private list, so that HLS segments and regular media stay consistent across the app.
 */
object MediaFileTypes {

    val VIDEO_EXTENSIONS = listOf(
        "mp4", "webm", "mov", "avi", "3gp", "m4v", "mkv", "flv"
    )

    val AUDIO_EXTENSIONS = listOf(
        "mp3", "m4a", "aac", "wav", "flac", "ogg", "wma"
    )

    val IMAGE_EXTENSIONS = listOf(
        "jpg", "jpeg", "png", "gif", "webp", "bmp", "svg"
    )

    /**
     * Playlist / streaming descriptors. `.ts` is intentionally excluded: an HLS playlist can
     * reference thousands of transport-stream segments, and treating each one as an independent
     * "download candidate" floods the media list.
     */
    val PLAYLIST_EXTENSIONS = listOf(
        "m3u8", "mpd"
    )

    val DOCUMENT_EXTENSIONS = listOf(
        "pdf", "apk", "zip", "rar", "7z", "tar", "gz",
        "txt", "csv", "json", "xml", "html", "htm"
    )

    val ALL_EXTENSIONS: List<String> =
        VIDEO_EXTENSIONS + AUDIO_EXTENSIONS + IMAGE_EXTENSIONS +
            PLAYLIST_EXTENSIONS + DOCUMENT_EXTENSIONS

    val VIDEO_MIME_TYPES = listOf(
        "video/mp4", "video/webm", "video/quicktime", "video/3gpp",
        "video/x-matroska", "video/x-flv", "video/mp2t", "video/x-msvideo"
    )

    val AUDIO_MIME_TYPES = listOf(
        "audio/mpeg", "audio/mp4", "audio/aac", "audio/wav",
        "audio/flac", "audio/ogg", "audio/x-ms-wma"
    )

    val PLAYLIST_MIME_TYPES = listOf(
        "application/vnd.apple.mpegurl", "application/x-mpegURL", "application/dash+xml"
    )

    val ALL_MIME_TYPES: List<String> = VIDEO_MIME_TYPES + AUDIO_MIME_TYPES + PLAYLIST_MIME_TYPES

    fun extensionFromUrl(url: String): String? {
        val path = url.substringBefore('?').substringBefore('#')
        val lastDot = path.lastIndexOf('.')
        val lastSlash = path.lastIndexOf('/')
        if (lastDot <= lastSlash || lastDot == path.length - 1) return null
        val extension = path.substring(lastDot + 1).lowercase()
        return extension.takeIf { it.length <= 8 && it.all(Char::isLetterOrDigit) }
    }

    fun isKnownExtension(extension: String?): Boolean {
        val ext = extension?.lowercase() ?: return false
        return ext in ALL_EXTENSIONS
    }

    fun isKnownMimeType(mimeType: String?): Boolean {
        val mime = mimeType?.lowercase() ?: return false
        return ALL_MIME_TYPES.any { mime.contains(it.lowercase()) }
    }

    fun isMediaUrl(url: String): Boolean = isKnownExtension(extensionFromUrl(url))

    /**
     * Media request check used by WebView interceptors. Returns true for a known file extension
     * or for an Accept/Content-Type header that advertises a known media MIME type.
     */
    fun isMediaRequest(url: String, mimeType: String?): Boolean {
        return isMediaUrl(url) || isKnownMimeType(mimeType)
    }

    fun mimeTypeForExtension(extension: String?): String {
        val ext = extension?.lowercase() ?: return "application/octet-stream"
        return when (ext) {
            in VIDEO_EXTENSIONS -> "video/*"
            in AUDIO_EXTENSIONS -> "audio/*"
            in IMAGE_EXTENSIONS -> "image/*"
            "pdf" -> "application/pdf"
            "apk" -> "application/vnd.android.package-archive"
            in listOf("zip", "rar", "7z", "tar", "gz") -> "application/zip"
            in listOf("txt", "csv", "json", "xml", "html", "htm") -> "text/*"
            else -> "application/octet-stream"
        }
    }
}
