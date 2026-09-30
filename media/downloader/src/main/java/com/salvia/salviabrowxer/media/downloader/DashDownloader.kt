package com.salvia.salviabrowxer.media.downloader

import com.salvia.salviabrowxer.core.model.DashManifest
import com.salvia.salviabrowxer.core.model.DashRepresentation
import com.salvia.salviabrowxer.media.resolver.DashManifestParser
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Downloads one MPEG-DASH representation by fetching its initialisation segment and every media
 * segment in order, concatenating them into a single fragmented-MP4 file.
 *
 * The manifest is re-fetched here rather than stored in the queue: a parsed manifest is derived
 * data that can list thousands of URLs, so the queue keeps the manifest URL and a rendition id and
 * this class re-resolves them at transfer time. The manifest was already validated as clear, static
 * and single-period when the quality sheet offered it; a manifest that no longer satisfies that is
 * refused with an [IOException] instead of saving undecodable bytes.
 *
 * Resume is intentionally not supported: a stale `.part` from a segmented transfer has no meaning,
 * so it is dropped and the transfer restarts from the first segment.
 */
class DashDownloader(
    private val okHttpClient: OkHttpClient
) {

    fun fetchManifest(url: String, userAgent: String, referer: String?): DashManifest? {
        val xml = runCatching {
            val request = Request.Builder().url(url)
                .header("User-Agent", userAgent)
                .header("Accept", "*/*")
                .apply { referer?.takeIf { it.isNotBlank() }?.let { header("Referer", it) } }
                .get().build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val bytes = response.body?.bytes() ?: return null
                (if (bytes.size > 1_048_576) bytes.copyOf(1_048_576) else bytes).toString(Charsets.UTF_8)
            }
        }.getOrNull() ?: return null
        return DashManifestParser.parse(url, xml)
    }

    @Throws(IOException::class)
    suspend fun downloadRepresentation(
        representation: DashRepresentation,
        target: File,
        userAgent: String,
        referer: String?,
        progressEveryMillis: Long = DownloadManager.PROGRESS_THROTTLE_MILLIS,
        onProgress: suspend (DownloadSnapshot) -> Unit = {},
        shouldAbort: suspend () -> AbortReason? = { null }
    ): DownloadResult {
        if (representation.segmentUrls.isEmpty()) throw IOException("DASH representation has no segments")
        val partFile = File(target.parentFile, "${target.name}.${DownloadManager.PART_SUFFIX}")
        if (partFile.exists()) partFile.delete()

        var written = 0L
        var lastReport = System.currentTimeMillis()
        var lastReportBytes = 0L
        var lastReportTime = lastReport

        FileOutputStream(partFile).use { output ->
            representation.initUrl?.let { downloadTo(it, output, userAgent, referer, shouldAbort) }
            representation.segmentUrls.forEachIndexed { index, segmentUrl ->
                shouldAbort()?.let { reason -> output.flush(); throw DownloadAbortedException(reason) }
                downloadTo(segmentUrl, output, userAgent, referer, shouldAbort)
                written = partFile.length()
                val now = System.currentTimeMillis()
                if (now - lastReport >= progressEveryMillis || index == representation.segmentUrls.lastIndex) {
                    val elapsed = (now - lastReportTime).coerceAtLeast(1L)
                    val speed = (written - lastReportBytes) * 1000L / elapsed
                    lastReport = now; lastReportBytes = written; lastReportTime = now
                    // Total size is unknown for a segmented stream; progress is real, the estimate is not claimed.
                    onProgress(DownloadSnapshot(downloadedBytes = written, totalBytes = null, bytesPerSecond = speed))
                }
            }
            output.flush()
        }

        if (written <= 0L || !partFile.exists() || partFile.length() == 0L) {
            partFile.delete()
            throw IOException("DASH download produced an empty file")
        }
        if (!partFile.renameTo(target)) {
            partFile.copyTo(target, overwrite = true)
            partFile.delete()
        }
        val size = target.length()
        onProgress(DownloadSnapshot(downloadedBytes = size, totalBytes = size, bytesPerSecond = 0L))
        return DownloadResult(file = target, bytesWritten = size, totalBytes = size, contentType = representation.mimeType)
    }

    @Throws(IOException::class)
    private suspend fun downloadTo(
        url: String,
        output: FileOutputStream,
        userAgent: String,
        referer: String?,
        shouldAbort: suspend () -> AbortReason?
    ) {
        val request = Request.Builder().url(url)
            .header("User-Agent", userAgent)
            .header("Accept", "*/*")
            .apply { referer?.takeIf { it.isNotBlank() }?.let { header("Referer", it) } }
            .get().build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("DASH segment failed HTTP ${response.code} for $url")
            val body = response.body ?: throw IOException("Empty DASH segment body for $url")
            val input = body.byteStream()
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                output.write(buffer, 0, read)
                shouldAbort()?.let { reason -> throw DownloadAbortedException(reason) }
            }
        }
    }
}
