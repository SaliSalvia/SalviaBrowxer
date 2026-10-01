package com.salvia.salviabrowxer.media.downloader

import java.io.File
import java.io.IOException
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * HLS reassembler.
 *
 * Unlike the single-file [DownloadManager.transfer], this fetches the media playlist (`.m3u8`),
 * resolves every segment URL and assembles them into one file:
 *
 * - **AES-128 encrypted** playlists (`EXT-X-KEY:METHOD=AES-128`) are decrypted with the key the
 *   playlist names. This is the standard, non-DRM HLS encryption — the same scheme VLC and every
 *   browser handle — and its key is plainly referenced by the manifest, so refusing it only made the
 *   app weaker than the alternatives. `SAMPLE-AES` (the DRM-adjacent method) is still refused with an
 *   honest error because it cannot be decrypted from the manifest alone.
 * - **Byte ranges** (`EXT-X-BYTERANGE`) are honoured, so a single media file addressed by ranges is
 *   fetched correctly instead of concatenated whole.
 * - **Parallel** segment fetching (bounded by [SegmentedFetcher.PARALLELISM]) is what makes a
 *   thousand-segment stream finish quickly, and the staged parts make a paused transfer **resumable**
 *   across process death instead of restarting from the first segment.
 * - **Live** playlists (no `EXT-X-ENDLIST`) are refused — there is no end to them, so they cannot be
 *   saved as a finite file.
 */
class HlsDownloader(
    private val okHttpClient: OkHttpClient
) {

    fun looksLikeHls(url: String, contentType: String?): Boolean {
        if (url.contains(".m3u8", ignoreCase = true)) return true
        // Case-insensitive on purpose: the registered media type is `application/x-mpegURL` and
        // CDNs serve `application/x-mpegURL; charset=utf-8` — a case-sensitive match missed the
        // canonical spelling and only caught the all-lowercase variants.
        val mime = contentType?.lowercase() ?: return false
        return "mpegurl" in mime
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

        val safeName = DownloadManager.sanitizeFilename(download.filename)
        val partFile = File(download.directory, "$safeName.${DownloadManager.PART_SUFFIX}")

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

        // A live stream has no EXT-X-ENDLIST: it never ends, so it cannot be saved as a file.
        val isLive = "#EXT-X-ENDLIST" !in playlistText
        if (isLive) throw IOException("Live HLS streams cannot be downloaded")

        val parts = parseParts(playlistText, playlistUrl)
        if (parts.isEmpty()) throw IOException("HLS playlist contains no segments")

        val stagingDir = SegmentedFetcher.stagingDirFor(targetDir, safeName)
        SegmentedFetcher(okHttpClient).fetch(
            parts = parts,
            stagingDir = stagingDir,
            partFile = partFile,
            userAgent = userAgent,
            referer = referer,
            progressEveryMillis = progressEveryMillis,
            onProgress = onProgress,
            shouldAbort = shouldAbort
        )

        if (!partFile.exists() || partFile.length() == 0L) {
            partFile.delete()
            throw IOException("HLS download produced an empty file")
        }

        val finalFile = DownloadManager.nonConflicting(File(targetDir, safeName))
        if (!partFile.renameTo(finalFile)) {
            partFile.copyTo(finalFile, overwrite = true)
            partFile.delete()
        }
        val size = finalFile.length()
        onProgress(DownloadSnapshot(downloadedBytes = size, totalBytes = size, bytesPerSecond = 0L))
        return DownloadResult(file = finalFile, bytesWritten = size, totalBytes = size, contentType = "video/mp2t")
    }

    /**
     * Turns the media playlist into an ordered list of parts. The `EXT-X-MAP` initialisation segment
     * (when present) is part 0; every `EXTINF`/URI is a part after it. `EXT-X-KEY` applies to the
     * media segments that follow it and rotates whenever a new key tag appears.
     */
    private fun parseParts(text: String, playlistUrl: String): List<SegmentPart> {
        val baseUrl = playlistUrl.substringBeforeLast('/') + "/"
        val lines = text.lineSequence().map { it.trim() }.toList()

        var mediaSequence = 0L
        for (line in lines) {
            if (line.startsWith("#EXT-X-MEDIA-SEQUENCE:")) {
                mediaSequence = line.substringAfter(':').trim().toLongOrNull() ?: 0L
                break
            }
        }

        var currentKeyUri: String? = null
        var currentIv: ByteArray? = null
        var pendingRangeSpec: String? = null
        val lastRangeEnd = HashMap<String, Long>()

        var initPart: SegmentPart? = null
        val mediaParts = mutableListOf<SegmentPart>()
        var segmentIndex = 0

        for (line in lines) {
            if (line.isEmpty()) continue
            if (line.startsWith("#EXT-X-KEY:")) {
                val method = Regex("METHOD=([^,\"]+)").find(line)?.groupValues?.getOrNull(1)?.trim()?.uppercase()
                when (method) {
                    null, "NONE" -> {
                        currentKeyUri = null
                        currentIv = null
                    }
                    "AES-128" -> {
                        currentKeyUri = Regex("URI=\"([^\"]+)\"").find(line)?.groupValues?.getOrNull(1)
                            ?.let { absolutize(it.trim(), playlistUrl, baseUrl) }
                            ?: throw IOException("Encrypted HLS playlist has no key URI")
                        currentIv = Regex("IV=0x([0-9A-Fa-f]+)").find(line)?.groupValues?.getOrNull(1)?.let { hexToBytes(it) }
                    }
                    else -> throw IOException("Unsupported HLS encryption method: $method")
                }
                continue
            }
            if (line.startsWith("#EXT-X-MAP:")) {
                val uri = Regex("URI=\"([^\"]+)\"").find(line)?.groupValues?.getOrNull(1)
                // The init segment is left undecrypted: it is normally clear even when the media
                // segments are AES-128 encrypted, and decrypting a clear init segment would corrupt it.
                if (uri != null) initPart = SegmentPart(url = absolutize(uri.trim(), playlistUrl, baseUrl))
                continue
            }
            if (line.startsWith("#EXT-X-BYTERANGE:")) {
                pendingRangeSpec = line.substringAfter(':').trim().trim('"')
                continue
            }
            if (line.startsWith("#")) continue

            val url = absolutize(line, playlistUrl, baseUrl)
            val range = pendingRangeSpec?.let { resolveByteRange(it, url, lastRangeEnd) }
            pendingRangeSpec = null
            val iv = currentIv ?: sequenceIv(mediaSequence + segmentIndex)
            mediaParts += SegmentPart(url = url, range = range, keyUri = currentKeyUri, iv = iv)
            segmentIndex++
            if (mediaParts.size >= MAX_SEGMENTS) break
        }

        val parts = ArrayList<SegmentPart>(mediaParts.size + 1)
        initPart?.let { parts += it }
        parts += mediaParts
        return parts
    }

    /** `EXT-X-BYTERANGE:<length>[@<offset>]`; a missing offset continues the previous range. */
    private fun resolveByteRange(spec: String, url: String, lastRangeEnd: MutableMap<String, Long>): LongRange {
        val lengthPart = spec.substringBefore('@')
        val offsetPart = spec.substringAfter('@', "")
        val length = lengthPart.toLongOrNull()?.takeIf { it > 0L } ?: throw IOException("Invalid EXT-X-BYTERANGE length")
        val offset = offsetPart.toLongOrNull() ?: lastRangeEnd[url]?.plus(1) ?: 0L
        val end = offset + length - 1
        lastRangeEnd[url] = end
        return offset..end
    }

    private fun hexToBytes(hex: String): ByteArray {
        val padded = hex.removePrefix("0x").padStart(32, '0').takeLast(32)
        return ByteArray(16) { i ->
            ((Character.digit(padded[i * 2], 16) shl 4) or Character.digit(padded[i * 2 + 1], 16)).toByte()
        }
    }

    /** The default HLS IV: the segment's media sequence number as a 128-bit big-endian value. */
    private fun sequenceIv(sequence: Long): ByteArray {
        val iv = ByteArray(16)
        var value = sequence
        for (i in 15 downTo 0) {
            iv[i] = (value and 0xFF).toByte()
            value = value ushr 8
        }
        return iv
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
            // Cap master playlist at 256 KiB — prevents OOM on adversarial CDN responses
            if (bytes.size > 256 * 1024) return bytes.copyOf(256 * 1024).toString(Charsets.UTF_8)
            return bytes.toString(Charsets.UTF_8)
        }
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

    private companion object {
        /** A hard bound on the segments one playlist may contribute, to keep a hostile manifest small. */
        const val MAX_SEGMENTS = 2000
    }
}
