package com.salvia.salviabrowxer.ui.utils

import java.io.File
import java.text.DecimalFormat

fun File.safeDelete(): Boolean {
    return if (exists()) {
        delete()
    } else {
        false
    }
}

fun File.ensureParentDirectory(): Boolean {
    parentFile?.let { parent ->
        if (!parent.exists()) {
            return parent.mkdirs()
        }
    }
    return true
}

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, 4)
    return DecimalFormat("#,##0.#").format(bytes / Math.pow(1024.0, digitGroups.toDouble())) + " " + units[digitGroups]
}

/**
 * A transfer rate for the task list: `4.2 MB/s`.
 *
 * The unit suffix is part of the string resource rather than this helper, because Persian does not
 * write the rate the same way. This returns the number and the size unit only.
 */
fun formatTransferRate(bytesPerSecond: Long): String = formatFileSize(bytesPerSecond)

/**
 * A duration for the task list: `1:23`, or `1:02:03` past an hour.
 *
 * Deliberately not localized prose ("about two minutes"): an estimate that changes every second
 * has to be short and comparable, and `m:ss` reads the same in both of the app's languages.
 */
fun formatDuration(seconds: Long): String {
    val total = seconds.coerceAtLeast(0L)
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    val remainder = total % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, remainder)
    else "%d:%02d".format(minutes, remainder)
}

fun sanitizeFilename(filename: String): String {
    return filename
        .replace("[^a-zA-Z0-9._-]".toRegex(), "_")
        .replace("__+".toRegex(), "_")
        .replace("^_+".toRegex(), "")
        .replace("_+$".toRegex(), "")
}

fun getFileExtension(url: String): String? {
    val lastDotIndex = url.lastIndexOf('.')
    val lastSlashIndex = url.lastIndexOf('/')
    return if (lastDotIndex > lastSlashIndex && lastDotIndex < url.length - 1) {
        url.substring(lastDotIndex + 1).lowercase()
    } else {
        null
    }
}

fun getMimeTypeFromExtension(extension: String?): String {
    return when (extension?.lowercase()) {
        "mp4", "webm", "mov", "avi", "3gp", "m4v" -> "video/*"
        "mp3", "m4a", "aac", "wav", "flac" -> "audio/*"
        "jpg", "jpeg", "png", "gif", "webp", "bmp" -> "image/*"
        "pdf" -> "application/pdf"
        "apk" -> "application/vnd.android.package-archive"
        "zip", "rar", "7z", "tar", "gz" -> "application/zip"
        "txt", "csv", "json", "xml", "html", "htm" -> "text/*"
        else -> "application/octet-stream"
    }
}

/**
 * The filename a `Content-Disposition` header asks for, or `null` when it does not name one.
 *
 * Only the name is taken — the browser must not act on `attachment`, let alone trust a path.
 * Both forms the header is allowed to use are handled: `filename*=UTF-8''…` (RFC 5987, already
 * percent-encoded) and the plain `filename="…"` quoted form, with the RFC 5987 form winning because
 * it is the one that survives non-ASCII names.
 */
fun filenameFromContentDisposition(header: String?): String? {
    val value = header?.takeIf { it.isNotBlank() } ?: return null
    val extended = Regex("""filename\*\s*=\s*([^'";]+)''([^;]+)""", RegexOption.IGNORE_CASE).find(value)
    if (extended != null) {
        val encoded = extended.groupValues[2].trim().trim('"')
        val decoded = runCatching { java.net.URLDecoder.decode(encoded, "UTF-8") }.getOrNull()
        return filenameOrNull(decoded)
    }
    val plain = Regex("""filename\s*=\s*(?:"([^"]*)"|([^;]+))""", RegexOption.IGNORE_CASE).find(value)
    if (plain != null) {
        val raw = plain.groupValues[1].ifBlank { plain.groupValues[2] }
        return filenameOrNull(raw)
    }
    return null
}

/** Keeps the last path segment only: a header is not allowed to point outside the download folder. */
private fun filenameOrNull(raw: String?): String? = raw
    ?.trim()
    ?.trim('"')
    ?.substringAfterLast('/')
    ?.substringAfterLast('\\')
    ?.takeIf { it.isNotBlank() && it != "." && it != ".." }
