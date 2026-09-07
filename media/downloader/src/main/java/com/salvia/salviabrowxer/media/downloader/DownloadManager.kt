package com.salvia.salviabrowxer.media.downloader

import android.content.Context
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * Description of a file that has to be written to disk.
 */
data class FileDownload(
    val id: String,
    val url: String,
    val directory: String,
    val filename: String,
    val userAgent: String? = DEFAULT_DOWNLOAD_USER_AGENT,
    val referer: String? = null
) {
    companion object {
        const val DEFAULT_DOWNLOAD_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/100.0.0.0 Mobile Safari/537.36"
    }
}

/**
 * Snapshot of a running transfer, reported through [DownloadManager.download]'s progress hook.
 */
data class DownloadSnapshot(
    val downloadedBytes: Long,
    val totalBytes: Long? = null,
    val bytesPerSecond: Long = 0L
) {
    fun percentage(): Float {
        val total = totalBytes ?: return 0f
        return if (total > 0L) (downloadedBytes.toFloat() / total) * 100f else 0f
    }

    fun etaSeconds(): Long? {
        val total = totalBytes ?: return null
        if (bytesPerSecond <= 0L) return null
        return (total - downloadedBytes).coerceAtLeast(0L) / bytesPerSecond
    }
}

enum class AbortReason { PAUSED, CANCELLED }

/**
 * Raised when the caller asked for the transfer to stop. The `.part` file is intentionally kept
 * for a pause so that the download can resume with an HTTP Range request.
 */
class DownloadAbortedException(val reason: AbortReason) :
    IOException("Download aborted (${reason.name.lowercase()})")

/** Raised when the server rejects a resume attempt; the caller retries from byte zero. */
class ResumeRejectedException : IOException("Range resume not accepted by the server")

data class DownloadResult(
    val file: File,
    val bytesWritten: Long,
    val totalBytes: Long?,
    val contentType: String?
)

/**
 * OkHttp powered download engine: streams the response body into `<file>.part`, reports throttled
 * progress, honours pause/cancel requests and finally renames the temporary file into place.
 */
class DownloadManager(
    private val context: Context,
    private val okHttpClient: OkHttpClient
) {

    private val activeCalls = ConcurrentHashMap<String, Call>()

    val activeDownloadIds: Set<String>
        get() = HashSet(activeCalls.keys)

    fun isDownloading(id: String): Boolean = activeCalls.containsKey(id)

    /** Interrupts an in-flight transfer; the coroutine running it throws an [IOException]. */
    fun cancel(id: String) {
        activeCalls[id]?.cancel()
    }

    /** Follows redirects and returns the URL the server actually ended up serving. */
    fun getFinalUrl(url: String): String {
        val request = Request.Builder()
            .url(url)
            .head()
            .build()

        return okHttpClient.newCall(request).execute().use { response ->
            response.request.url.toString()
        }
    }

    fun getContentLength(url: String): Long {
        val request = Request.Builder()
            .url(url)
            .head()
            .build()

        return okHttpClient.newCall(request).execute().use { response ->
            response.body?.contentLength() ?: -1L
        }
    }

    /**
     * Downloads [download] end to end. Both hooks are suspend functions so the caller can persist
     * progress and expose pause/cancel state straight from the database.
     */
    @Throws(IOException::class)
    suspend fun download(
        download: FileDownload,
        progressEveryMillis: Long = PROGRESS_THROTTLE_MILLIS,
        onProgress: suspend (DownloadSnapshot) -> Unit = {},
        shouldAbort: suspend () -> AbortReason? = { null }
    ): DownloadResult {
        try {
            return transfer(download, progressEveryMillis, onProgress, shouldAbort)
        } catch (rejected: ResumeRejectedException) {
            // Drop the unusable partial file and start over from byte zero.
            partialFileFor(download).delete()
            return transfer(download, progressEveryMillis, onProgress, shouldAbort)
        }
    }

    private suspend fun transfer(
        download: FileDownload,
        progressEveryMillis: Long,
        onProgress: suspend (DownloadSnapshot) -> Unit,
        shouldAbort: suspend () -> AbortReason?
    ): DownloadResult {
        val targetDir = File(download.directory)
        if (!targetDir.exists() && !targetDir.mkdirs()) {
            throw IOException("Unable to create destination directory: ${targetDir.absolutePath}")
        }

        val partFile = partialFileFor(download)
        val resumeFrom = if (partFile.exists()) partFile.length().coerceAtLeast(0L) else 0L

        val requestBuilder = Request.Builder().url(download.url)
        download.userAgent?.takeIf { it.isNotBlank() }?.let { requestBuilder.header("User-Agent", it) }
        download.referer?.takeIf { it.isNotBlank() }?.let { requestBuilder.header("Referer", it) }
        if (resumeFrom > 0L) {
            requestBuilder.header("Range", "bytes=$resumeFrom-")
        }

        val call = okHttpClient.newCall(requestBuilder.build())
        activeCalls[download.id] = call

        var totalBytes: Long? = null
        var contentType: String? = null
        var written = resumeFrom

        try {
            call.execute().use { response ->
                if (response.code == 416) {
                    throw ResumeRejectedException()
                }
                if (!response.isSuccessful) {
                    throw IOException("Unexpected HTTP ${response.code} for ${download.url}")
                }

                val body = response.body ?: throw IOException("Empty response body")
                val declared = body.contentLength().takeIf { it > 0L }
                    ?: response.header("Content-Length")?.toLongOrNull()?.takeIf { it > 0L }
                val resumed = response.code == 206 && resumeFrom > 0L
                if (declared != null) {
                    totalBytes = if (resumed) declared + resumeFrom else declared
                }
                contentType = response.header("Content-Type")?.substringBefore(';')?.trim()
                if (!resumed) written = 0L

                FileOutputStream(partFile, resumed).use { output ->
                    val input = body.byteStream()
                    val buffer = ByteArray(BUFFER_SIZE)
                    var lastReport = System.currentTimeMillis()
                    var lastReportBytes = written
                    var lastReportMillis = lastReport

                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        written += read

                        val now = System.currentTimeMillis()
                        if (now - lastReport >= progressEveryMillis) {
                            val elapsed = (now - lastReportMillis).coerceAtLeast(1L)
                            val speed = (written - lastReportBytes) * 1000L / elapsed
                            lastReport = now
                            lastReportBytes = written
                            lastReportMillis = now

                            onProgress(
                                DownloadSnapshot(
                                    downloadedBytes = written,
                                    totalBytes = totalBytes,
                                    bytesPerSecond = speed
                                )
                            )

                            shouldAbort()?.let { reason ->
                                output.flush()
                                throw DownloadAbortedException(reason)
                            }
                        }
                    }
                    output.flush()
                }
            }
        } catch (aborted: DownloadAbortedException) {
            throw aborted
        } catch (rejected: ResumeRejectedException) {
            throw rejected
        } catch (io: IOException) {
            if (call.isCanceled()) {
                throw DownloadAbortedException(AbortReason.CANCELLED)
            }
            throw io
        } finally {
            activeCalls.remove(download.id)
        }

        if (written <= 0L) {
            partFile.delete()
            throw IOException("Server returned an empty payload")
        }

        val finalFile = nonConflicting(File(targetDir, sanitizeFilename(download.filename)))
        if (!partFile.renameTo(finalFile)) {
            partFile.copyTo(finalFile, overwrite = true)
            partFile.delete()
        }

        val size = finalFile.length()
        val total = totalBytes ?: size
        onProgress(DownloadSnapshot(downloadedBytes = size, totalBytes = total, bytesPerSecond = 0L))

        return DownloadResult(
            file = finalFile,
            bytesWritten = size,
            totalBytes = total,
            contentType = contentType
        )
    }

    private fun partialFileFor(download: FileDownload): File =
        File(download.directory, "${sanitizeFilename(download.filename)}.$PART_SUFFIX")

    companion object {
        const val PART_SUFFIX = "part"
        const val BUFFER_SIZE = 8 * 1024
        const val PROGRESS_THROTTLE_MILLIS = 300L

        fun sanitizeFilename(filename: String): String {
            val cleaned = filename
                .replace("\\", "_")
                .replace("/", "_")
                .replace(":", "_")
                .replace("*", "_")
                .replace("?", "_")
                .replace("\"", "'")
                .replace("<", "_")
                .replace(">", "_")
                .replace("|", "_")
                .trim()
                .take(180)
            return cleaned.ifBlank { "download" }
        }

        fun generateFilename(title: String?, extension: String?): String {
            val baseName = title?.takeIf { it.isNotBlank() } ?: "download"
            val sanitized = sanitizeFilename(baseName)
                .replace(Regex("[^A-Za-z0-9 ._()-]"), "_")
                .replace(Regex("\\s+"), "_")
                .replace(Regex("_+"), "_")
                .trim('_')
                .ifBlank { "download" }
            val ext = extension?.takeIf { it.isNotBlank() }?.lowercase()?.trim('.')
            return if (ext != null) "$sanitized.$ext" else sanitized
        }

        fun extensionFromUrl(url: String): String? {
            val path = url.substringBefore('?').substringBefore('#')
            val lastDotIndex = path.lastIndexOf('.')
            val lastSlashIndex = path.lastIndexOf('/')
            return if (lastDotIndex > lastSlashIndex && lastDotIndex in 0 until path.length - 1) {
                path.substring(lastDotIndex + 1)
                    .lowercase()
                    .takeIf { it.length <= 5 && it.all { char -> char.isLetterOrDigit() } }
            } else {
                null
            }
        }

        fun nameFromUrl(url: String): String {
            val tail = url.substringBefore('?').substringBefore('#').trimEnd('/')
            return tail.substringAfterLast('/').ifBlank { "download" }
        }

        /** Never overwrite an existing file: append ` (1)`, ` (2)`, ... instead. */
        internal fun nonConflicting(file: File): File {
            if (!file.exists()) return file
            val parent = file.parentFile ?: return file
            val base = file.nameWithoutExtension
            val extension = file.extension
            var index = 1
            while (index <= 999) {
                val candidateName = if (extension.isEmpty()) {
                    "$base ($index)"
                } else {
                    "$base ($index).$extension"
                }
                val candidate = File(parent, candidateName)
                if (!candidate.exists()) return candidate
                index++
            }
            return file
        }
    }
}
