package com.example.engine

import java.io.IOException
import java.io.InterruptedIOException
import java.net.ProtocolException
import java.net.SocketException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * Pure, unit-testable policy decisions used by [MediaDownloadManager].
 *
 * Nothing here performs I/O; it only decides names, part layouts, retry timing, and storage
 * budgets so the transfer code stays small and the rules stay verifiable.
 */
object DownloadFilePolicy {

    /** Files smaller than this are fetched with a single connection. */
    const val MIN_MULTIPART_BYTES = 1_500_000L

    /** Hard ceiling for parallel range connections per file. */
    const val MAX_PARTS = 8

    /** Parallel segment fetches for HLS/DASH manifests. */
    const val SEGMENT_CONCURRENCY = 6

    /** Max attempts for one segment/part before the whole download is marked FAILED. */
    const val MAX_ATTEMPTS = 5

    /** Extra free space kept on the device on top of the transfer budget. */
    const val STORAGE_SAFETY_MARGIN_BYTES = 32L * 1024L * 1024L

    private val knownExtensions = setOf(
        "mp4", "m4v", "mov", "mkv", "webm", "3gp", "ts", "m4a", "mp3", "aac", "ogg", "opus",
        "flac", "wav", "pdf", "zip", "rar", "7z", "apk", "jpg", "jpeg", "png", "gif", "webp",
        "svg", "txt", "csv", "json", "xml", "html", "htm", "doc", "docx", "xls", "xlsx", "ppt",
        "pptx", "epub", "srt", "vtt", "torrent", "exe", "dmg", "iso", "bin",
    )

    /**
     * Deterministic, size-adaptive connection count. The same total size always yields the
     * same layout so persisted part files can be resumed safely.
     */
    fun partCountFor(totalBytes: Long): Int = when {
        totalBytes < MIN_MULTIPART_BYTES -> 1
        totalBytes < 16L * 1024 * 1024 -> 4
        totalBytes < 64L * 1024 * 1024 -> 6
        else -> MAX_PARTS
    }

    /** Inclusive byte ranges for each part; the last part absorbs the remainder. */
    fun partRanges(totalBytes: Long, parts: Int): List<LongRange> {
        require(totalBytes > 0L && parts > 0)
        val count = if (totalBytes < parts.toLong()) totalBytes.toInt() else parts
        val partSize = totalBytes / count
        return (0 until count).map { index ->
            val start = index * partSize
            val end = if (index == count - 1) totalBytes - 1L else (index + 1) * partSize - 1L
            start..end
        }
    }

    /** Layout marker so a resume never mixes part files from a different split. */
    fun layoutMarker(totalBytes: Long, parts: Int): String = "v2:$totalBytes:$parts"

    /** Exponential backoff with a ceiling; jitter is supplied by the caller for testability. */
    fun retryDelayMs(attempt: Int, jitterMs: Long = 0L): Long {
        val base = 600L shl attempt.coerceIn(0, 4)
        return (base.coerceAtMost(8_000L) + jitterMs.coerceIn(0L, 400L))
    }

    /** Transient network failures are retried; protocol/semantic failures are not. */
    fun isRetryable(error: Throwable): Boolean = when (error) {
        is NonRetryableDownloadException -> false
        is InsufficientStorageException -> false
        is HttpStatusException -> error.code == 408 || error.code == 425 || error.code == 429 || error.code >= 500
        is UnknownHostException -> true
        is InterruptedIOException -> true // includes SocketTimeoutException
        is SocketException -> true
        is SSLException -> true
        is ProtocolException -> false
        is IOException -> true
        else -> false
    }

    /**
     * Bytes needed for a transfer: the staging copy plus the published copy, because the
     * staging data is only removed after it has been published.
     */
    fun requiredBytesFor(totalBytes: Long, alreadyStaged: Long = 0L): Long {
        if (totalBytes <= 0L) return STORAGE_SAFETY_MARGIN_BYTES
        val remainingStage = (totalBytes - alreadyStaged).coerceAtLeast(0L)
        return remainingStage + totalBytes + STORAGE_SAFETY_MARGIN_BYTES
    }

    fun hasEnoughSpace(availableBytes: Long, totalBytes: Long, alreadyStaged: Long = 0L): Boolean {
        if (availableBytes < 0L) return true // unknown: let the write fail naturally
        return availableBytes >= requiredBytesFor(totalBytes, alreadyStaged)
    }

    /**
     * Produces a safe display name. A trailing known extension is kept; otherwise the
     * extension derived from the media format is appended (titles such as "Mr. Bean" no
     * longer lose their extension because they contain a dot).
     */
    fun sanitizeFileName(
        rawName: String,
        defaultExtension: String,
        fallbackBase: String = "salvia_download",
        maxBaseLength: Int = 80,
    ): String {
        val cleaned = rawName
            .replace(Regex("[\\u0000-\\u001F\\u007F]"), "")
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .replace(Regex("[^\\p{L}\\p{N}\\p{M}._\\- ()\\[\\]]"), "_")
            .replace(Regex("\\s+"), " ")
            .replace(Regex("_+"), "_")
            .trim(' ', '.', '_')
        val currentExt = cleaned.substringAfterLast('.', "").lowercase()
        val hasKnownExt = cleaned.contains('.') && currentExt.isNotEmpty() &&
            (currentExt in knownExtensions || currentExt == defaultExtension.lowercase())
        val base = (if (hasKnownExt) cleaned.substringBeforeLast('.') else cleaned)
            .trim(' ', '.', '_')
            .ifBlank { fallbackBase }
            .take(maxBaseLength)
            .trim(' ', '.', '_')
            .ifBlank { fallbackBase }
        val ext = (if (hasKnownExt) currentExt else defaultExtension.lowercase())
            .ifBlank { "bin" }
        return "$base.$ext"
    }

    /** Replaces the extension of [fileName] with [extension]. */
    fun withExtension(fileName: String, extension: String): String {
        val base = if (fileName.contains('.')) fileName.substringBeforeLast('.') else fileName
        return "${base.ifBlank { "salvia_download" }}.$extension"
    }

    /**
     * Returns [fileName] when free, otherwise "name (1).ext", "name (2).ext", ... like the
     * system Downloads provider does, so finished downloads never overwrite each other.
     */
    fun uniqueFileName(fileName: String, exists: (String) -> Boolean): String {
        if (!exists(fileName)) return fileName
        val hasExt = fileName.contains('.')
        val base = if (hasExt) fileName.substringBeforeLast('.') else fileName
        val ext = if (hasExt) "." + fileName.substringAfterLast('.') else ""
        for (i in 1..9_999) {
            val candidate = "$base ($i)$ext"
            if (!exists(candidate)) return candidate
        }
        return "$base (${System.currentTimeMillis()})$ext"
    }

    /** Download speed in bytes/s from a byte delta over a millisecond interval. */
    fun speedBytesPerSecond(deltaBytes: Long, elapsedMs: Long): Long =
        if (elapsedMs <= 0L || deltaBytes <= 0L) 0L else deltaBytes * 1000L / elapsedMs

    /** Display MIME type for opening a finished file; manifests always become mp4/m4a. */
    fun outputMimeType(storedMimeType: String, fileName: String): String {
        val lower = storedMimeType.lowercase()
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return when {
            ext == "mp4" || ext == "m4v" -> "video/mp4"
            ext == "m4a" -> "audio/mp4"
            ext == "ts" -> "video/mp2t"
            ext == "webm" -> if (lower.startsWith("audio/")) "audio/webm" else "video/webm"
            ext == "mp3" -> "audio/mpeg"
            lower.contains("mpegurl") || lower.contains("dash+xml") -> "video/mp4"
            lower.isBlank() -> "*/*"
            else -> lower
        }
    }
}

/** An HTTP response whose status code makes the request unusable. */
class HttpStatusException(val code: Int, message: String = "HTTP $code") : IOException(message)

/** A semantic failure (bad Content-Range, DRM, corrupt key...) that retrying cannot fix. */
class NonRetryableDownloadException(message: String) : IOException(message)

/** Raised before any transfer when the device lacks the space to finish the download. */
class InsufficientStorageException(val requiredBytes: Long, val availableBytes: Long) :
    IOException("Insufficient storage: need $requiredBytes bytes, have $availableBytes")
