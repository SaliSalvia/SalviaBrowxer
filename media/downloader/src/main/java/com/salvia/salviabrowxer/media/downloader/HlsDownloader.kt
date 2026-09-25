package com.salvia.salviabrowxer.media.downloader

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * InShot-style HLS downloader.
 * Unlike the single-file [DownloadManager.transfer], this fetches the media playlist
 * (`.m3u8`), resolves every TS segment URL and concatenates them into the final file.
 *
 * Limitations (identical to InShot's first-gen offline path):
 * - AES-128 encrypted playlists (EXT-X-KEY) are rejected with a clear IO error so the
 *   caller can fall back to a user-visible "DRM/encrypted" failure instead of saving
 *   undecryptable bytes.
 * - LIVE playlists (no EXT-X-ENDLIST) are rejected — same behaviour as InShot which
 *   refuses to archive endless streams.
 * - Resume is intentionally not supported for segmented downloads — a stale .part file
 *   is dropped and the transfer restarts; this matches InShot where HLS reassembly
 *   always starts from the first segment.
 */
class HlsDownloader(
    private val okHttpClient: OkHttpClient
) {

    fun looksLikeHls(url: String, contentType: String?): Boolean {
        if (url.contains(".m3u8", ignoreCase = true)) return true
        if (contentType != null && ("mpegurl" in contentType || "x-mpegurl" in contentType)) return true
        return false
    }

    @Throws(IOException::class)
    suspend fun download(
        download: FileDownload,
        progressEveryMillis: Long = DownloadManager.PROGRESS_THROTTLE_MILLIS,
        onProgress: suspend (DownloadSnapshot) -> Unit = {},
        shouldAbort: suspend () -> AbortReason? = { null }
    ): DownloadResult {
        val targetDir = File(download.directory)
        if (!targetDir.exists() && !targetDir.mkdirs()) throw IOException("Unable to create destination directory: ${targetDir.absolutePath}")

        // Drop any stale single-file .part — segmented streams cannot be resumed that way
        val partFile = File(download.directory, "${DownloadManager.sanitizeFilename(download.filename)}.${DownloadManager.PART_SUFFIX}")
        if (partFile.exists()) partFile.delete()

        val playlistUrl = download.url
        val userAgent = download.userAgent ?: FileDownload.DEFAULT_DOWNLOAD_USER_AGENT
        val referer = download.referer

        val playlistText = fetchText(playlistUrl, userAgent, referer)
            ?: throw IOException("Unable to fetch HLS playlist")

        if ("#EXTM3U" !in playlistText) throw IOException("Not a valid HLS playlist")

        // Master playlist → pick the best variant and recurse once (highest resolution)
        if ("#EXT-X-STREAM-INF" in playlistText) {
            val bestVariant = pickBestVariantUrl(playlistText, playlistUrl)
                ?: throw IOException("HLS master playlist has no variants")
            return download(
                download.copy(url = bestVariant),
                progressEveryMillis, onProgress, shouldAbort
            )
        }

        if ("#EXT-X-KEY" in playlistText && "METHOD=NONE" !in playlistText) {
            throw IOException("Encrypted HLS streams are not supported (AES-128)")
        }

        val baseUrl = playlistUrl.substringBeforeLast('/') + "/"
        val segments = parseSegments(playlistText, playlistUrl, baseUrl)
        if (segments.isEmpty()) throw IOException("HLS playlist contains no segments")

        // Optional init segment (EXT-X-MAP)
        val initSegment = parseInitSegment(playlistText, playlistUrl, baseUrl)

        // For LIVE streams InShot shows an error instead of saving infinite data
        val isLive = "#EXT-X-ENDLIST" !in playlistText
        if (isLive) throw IOException("Live HLS streams cannot be downloaded")

        var written = 0L
        var lastReport = System.currentTimeMillis()
        var lastReportBytes = 0L
        var lastReportTime = lastReport

        // Estimate total bytes if available is not — we use segment count as fallback for progress
        val estimatedTotal: Long? = null

        FileOutputStream(partFile).use { output ->
            // Write init segment first if present
            initSegment?.let { url ->
                downloadSegment(url, userAgent, referer, output, shouldAbort)
                // init segment size is small, don't count as progress denominator
            }

            for ((idx, segUrl) in segments.withIndex()) {
                shouldAbort()?.let { reason -> output.flush(); throw DownloadAbortedException(reason) }
                downloadSegment(segUrl, userAgent, referer, output, shouldAbort)
                written = partFile.length()

                val now = System.currentTimeMillis()
                if (now - lastReport >= progressEveryMillis || idx == segments.lastIndex) {
                    val elapsed = (now - lastReportTime).coerceAtLeast(1L)
                    val speed = (written - lastReportBytes) * 1000L / elapsed
                    lastReport = now; lastReportBytes = written; lastReportTime = now
                    val fraction = (idx + 1).toFloat() / segments.size
                    val snapTotal = estimatedTotal ?: if (idx == segments.lastIndex) written else (written / fraction.coerceAtLeast(0.01f)).toLong()
                    onProgress(DownloadSnapshot(downloadedBytes = written, totalBytes = snapTotal, bytesPerSecond = speed))
                }
            }
            output.flush()
        }

        if (written <= 0L || !partFile.exists() || partFile.length() == 0L) {
            partFile.delete()
            throw IOException("HLS download produced an empty file")
        }

        val finalFile = DownloadManager.nonConflicting(File(targetDir, DownloadManager.sanitizeFilename(download.filename)))
        if (!partFile.renameTo(finalFile)) {
            partFile.copyTo(finalFile, overwrite = true)
            partFile.delete()
        }
        val size = finalFile.length()
        onProgress(DownloadSnapshot(downloadedBytes = size, totalBytes = size, bytesPerSecond = 0L))
        return DownloadResult(file = finalFile, bytesWritten = size, totalBytes = size, contentType = "video/mp2t")
    }

    private fun fetchText(url: String, userAgent: String, referer: String?): String? {
        val req = Request.Builder().url(url)
            .header("User-Agent", userAgent)
            .header("Accept", "*/*")
            .apply { referer?.takeIf { it.isNotBlank() }?.let { header("Referer", it) } }
            .get().build()
        okHttpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val bytes = resp.body?.bytes() ?: return null
            // Cap master playlist at 256 KiB — prevents OOM on adversarial CF responses
            if (bytes.size > 256 * 1024) return bytes.copyOf(256 * 1024).toString(Charsets.UTF_8)
            return bytes.toString(Charsets.UTF_8)
        }
    }

    private fun parseSegments(text: String, playlistUrl: String, baseUrl: String): List<String> {
        val lines = text.lineSequence().map { it.trim() }.toList()
        val out = mutableListOf<String>()
        for (line in lines) {
            if (line.isEmpty() || line.startsWith("#")) continue
            // Skip URI lines that follow #EXT-X-STREAM-INF already handled — but media playlists
            // contain only segment URIs, so any bare line is a segment.
            out += absolutize(line, playlistUrl, baseUrl)
            if (out.size >= 2000) break // cap: InShot refuses >2000-segment playlists
        }
        return out
    }

    private fun parseInitSegment(text: String, playlistUrl: String, baseUrl: String): String? {
        val mapLine = text.lineSequence().firstOrNull { it.trim().startsWith("#EXT-X-MAP:") } ?: return null
        val uri = Regex("""URI="([^"]+)"""").find(mapLine)?.groupValues?.getOrNull(1) ?: return null
        return absolutize(uri.trim(), playlistUrl, baseUrl)
    }

    private fun pickBestVariantUrl(masterText: String, masterUrl: String): String? {
        val lines = masterText.lineSequence().map { it.trim() }.toList()
        val base = masterUrl.substringBeforeLast('/') + "/"
        var bestUrl: String? = null
        var bestScore = -1L
        for (i in lines.indices) {
            val line = lines[i]
            if (!line.startsWith("#EXT-X-STREAM-INF:")) continue
            val uri = lines.getOrNull(i + 1)?.takeIf { it.isNotEmpty() && !it.startsWith("#") } ?: continue
            val bw = Regex("BANDWIDTH=(\\d+)").find(line)?.groupValues?.getOrNull(1)?.toLongOrNull() ?: 0L
            val res = Regex("RESOLUTION=(\\d+)x(\\d+)").find(line)
            val h = res?.groupValues?.getOrNull(2)?.toIntOrNull() ?: 0
            val score = h * 100_000L + bw
            if (score > bestScore) {
                bestScore = score
                bestUrl = absolutize(uri, masterUrl, base)
            }
        }
        return bestUrl
    }

    private fun absolutize(url: String, playlistUrl: String, baseUrl: String): String = when {
        url.startsWith("http://") || url.startsWith("https://") -> url
        url.startsWith("//") -> "https:$url"
        url.startsWith("/") -> runCatching {
            val u = java.net.URI(playlistUrl)
            "${u.scheme}://${u.host}${if (u.port != -1) ":${u.port}" else ""}$url"
        }.getOrDefault(url)
        else -> baseUrl + url
    }

    @Throws(IOException::class)
    private suspend fun downloadSegment(
        url: String,
        userAgent: String,
        referer: String?,
        output: FileOutputStream,
        shouldAbort: suspend () -> AbortReason?
    ) {
        val req = Request.Builder().url(url)
            .header("User-Agent", userAgent)
            .header("Accept", "*/*")
            .apply { referer?.takeIf { it.isNotBlank() }?.let { header("Referer", it) } }
            .get().build()
        val call = okHttpClient.newCall(req)
        call.execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("Segment failed HTTP ${resp.code} for $url")
            val body = resp.body ?: throw IOException("Empty segment body for $url")
            val input = body.byteStream()
            val buf = ByteArray(64 * 1024)
            while (true) {
                val r = input.read(buf)
                if (r == -1) break
                output.write(buf, 0, r)
                shouldAbort()?.let { reason -> throw DownloadAbortedException(reason) }
            }
        }
    }
}
