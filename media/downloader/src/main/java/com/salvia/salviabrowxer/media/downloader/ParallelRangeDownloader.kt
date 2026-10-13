package com.salvia.salviabrowxer.media.downloader

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request

/** The server answered a ranged request with the whole file: the plan cannot be used. */
internal class RangeNotHonouredException(message: String) : IOException(message)

/**
 * Fetches one file over several `Range` connections and joins the parts — what a download manager
 * does, and the reason it can be faster than a browser on the same link.
 *
 * A single stream is limited by the slowest hop between the two ends; several streams share that
 * limit instead of queueing behind it, and on a per-connection limited server they multiply it. This
 * only runs when the server itself stated the file's length and accepted a byte range, so nothing
 * here guesses.
 *
 * The properties that make it safe to interrupt:
 * - every part is written to its own file as it arrives, so a pause, a cancel or a process death
 *   leaves what was already fetched on disk;
 * - a part that already has bytes is resumed with `Range: bytes=start+have-end` instead of being
 *   fetched again;
 * - nothing is deleted until every part has its full length and the parts have been joined;
 * - the parts are joined in order, so the result is byte-identical to a plain download of the URL.
 *
 * It never returns a partial file: it either returns a [DownloadResult] whose file is complete, or
 * throws — leaving the parts behind for the retry the queue will run.
 */
internal class ParallelRangeDownloader(
    private val okHttpClient: OkHttpClient,
    private val connections: Int
) {

    /** What the parts share: one byte counter, one throttle, one lock. */
    private class Progress(initialBytes: Long) {
        val fetched = AtomicLong(initialBytes)
        val mutex = Mutex()
        var reportedBytes: Long = initialBytes
        var reportedAt: Long = System.currentTimeMillis()
    }

    suspend fun download(
        download: FileDownload,
        plan: RangePlan,
        targetDir: File,
        progressEveryMillis: Long,
        onProgress: suspend (DownloadSnapshot) -> Unit,
        shouldAbort: suspend () -> AbortReason?,
        registerCall: (Call) -> Unit = {}
    ): DownloadResult {
        if (!targetDir.exists() && !targetDir.mkdirs()) {
            throw IOException("Unable to create destination directory: ${targetDir.absolutePath}")
        }
        val base = DownloadManager.sanitizeFilename(download.filename)
        val parts = plan.ranges.indices.map { partFile(targetDir, base, it) }
        // Bytes already on disk from an earlier attempt are progress, not work to redo: the counter
        // starts where the parts say the transfer already is, so a resumed download shows the truth.
        val progress = Progress(parts.fold(0L) { sum, part -> sum + part.length() })
        val contentType = arrayOfNulls<String>(1)

        coroutineScope {
            plan.ranges.mapIndexed { index, range ->
                async(Dispatchers.IO) {
                    fetchPart(
                        download = download,
                        range = range,
                        index = index,
                        part = parts[index],
                        progress = progress,
                        expectedTotal = plan.totalBytes,
                        contentType = contentType,
                        progressEveryMillis = progressEveryMillis,
                        onProgress = onProgress,
                        shouldAbort = shouldAbort,
                        registerCall = registerCall
                    )
                }
            }.awaitAll()
        }

        val assembled = parts.fold(0L) { sum, part -> sum + part.length() }
        if (assembled != plan.totalBytes) {
            throw IOException("Parallel download is incomplete: $assembled of ${plan.totalBytes} bytes")
        }

        val joined = File(targetDir, "$base.${DownloadManager.PART_SUFFIX}")
        runCatching { joined.delete() }
        FileOutputStream(joined, false).use { output ->
            parts.forEach { part ->
                part.inputStream().use { input -> input.copyTo(output, DownloadManager.BUFFER_SIZE) }
            }
            output.flush()
        }
        parts.forEach { it.delete() }

        val finalFile = DownloadManager.nonConflicting(File(targetDir, base))
        if (!joined.renameTo(finalFile)) {
            joined.copyTo(finalFile, overwrite = true)
            joined.delete()
        }
        val size = finalFile.length()
        onProgress(DownloadSnapshot(downloadedBytes = size, totalBytes = plan.totalBytes, bytesPerSecond = 0L))
        return DownloadResult(file = finalFile, bytesWritten = size, totalBytes = plan.totalBytes, contentType = contentType[0])
    }

    private suspend fun fetchPart(
        download: FileDownload,
        range: LongRange,
        index: Int,
        part: File,
        progress: Progress,
        expectedTotal: Long,
        contentType: Array<String?>,
        progressEveryMillis: Long,
        onProgress: suspend (DownloadSnapshot) -> Unit,
        shouldAbort: suspend () -> AbortReason?,
        registerCall: (Call) -> Unit
    ) {
        val expected = range.last - range.first + 1L
        val have = if (part.exists()) part.length().coerceAtMost(expected) else 0L
        // A part that is already complete was counted into the progress seed before the parts started.
        if (have == expected) return

        val request = Request.Builder()
            .url(download.url)
            .header("Range", "bytes=${range.first + have}-${range.last}")
            .header("Accept", "*/*")
            .apply {
                download.userAgent?.takeIf { it.isNotBlank() }?.let { header("User-Agent", it) }
                download.referer?.takeIf { it.isNotBlank() }?.let { header("Referer", it) }
            }
            .build()

        val call = okHttpClient.newCall(request)
        registerCall(call)
        try {
            withContext(Dispatchers.IO) {
                call.execute().use { response ->
                    if (!response.isSuccessful) throw IOException("Unexpected HTTP ${response.code} for ${download.url}")
                    // The server accepted ranges during the probe and ignored this one. Falling back to
                    // a single connection gets the file; arguing with the server does not.
                    if (response.code != 206) {
                        throw RangeNotHonouredException("Range request answered with ${response.code} for ${download.url}")
                    }
                    contentType[0] = contentType[0] ?: response.header("Content-Type")?.substringBefore(';')?.trim()
                    val body = response.body ?: throw IOException("Empty response body")
                    FileOutputStream(part, have > 0L).use { output ->
                        val input = body.byteStream()
                        val buffer = ByteArray(DownloadManager.BUFFER_SIZE)
                        var reportAt = System.currentTimeMillis() + progressEveryMillis
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            val total = progress.fetched.addAndGet(read.toLong())
                            if (System.currentTimeMillis() >= reportAt) {
                                reportAt = System.currentTimeMillis() + progressEveryMillis
                                progress.mutex.withLock {
                                    val now = System.currentTimeMillis()
                                    val elapsed = (now - progress.reportedAt).coerceAtLeast(1L)
                                    val speed = (total - progress.reportedBytes) * 1000L / elapsed
                                    progress.reportedBytes = total
                                    progress.reportedAt = now
                                    onProgress(
                                        DownloadSnapshot(
                                            downloadedBytes = total,
                                            totalBytes = expectedTotal,
                                            bytesPerSecond = speed
                                        )
                                    )
                                }
                                shouldAbort()?.let { reason -> throw DownloadAbortedException(reason) }
                            }
                        }
                        output.flush()
                    }
                }
            }
        } catch (io: IOException) {
            // A part cancelled through the call is a user action, not a network failure.
            if (call.isCanceled()) throw DownloadAbortedException(AbortReason.CANCELLED)
            throw io
        }

        if (part.length() != expected) {
            throw IOException("Part ${index + 1} of $connections ended at ${part.length()} of $expected bytes")
        }
    }

    private fun partFile(dir: File, base: String, index: Int): File =
        File(dir, "$base.${DownloadManager.PART_SUFFIX}$index")
}
