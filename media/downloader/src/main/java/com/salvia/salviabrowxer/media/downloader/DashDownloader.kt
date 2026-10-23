package com.salvia.salviabrowxer.media.downloader

import com.salvia.salviabrowxer.core.model.DashManifest
import com.salvia.salviabrowxer.core.model.DashRepresentation
import com.salvia.salviabrowxer.media.resolver.DashManifestParser
import com.salvia.salviabrowxer.media.resolver.SegmentIndexBox
import java.io.File
import java.io.IOException
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Downloads one MPEG-DASH representation by fetching its initialisation segment and every media
 * segment, assembling them into a single fragmented-MP4 file.
 *
 * The manifest is re-fetched here rather than stored in the queue: a parsed manifest is derived
 * data that can list thousands of URLs, so the queue keeps the manifest URL and a rendition id and
 * this class re-resolves them at transfer time. The manifest was already validated as clear, static
 * and single-period when the quality sheet offered it; a manifest that no longer satisfies that is
 * refused with an [IOException] instead of saving undecodable bytes.
 *
 * Segments are fetched with the shared [SegmentedFetcher], so a DASH transfer is also **parallel**
 * (several segments in flight) and **resumable** — a paused or process-killed transfer continues
 * from the parts it already staged instead of restarting from the first segment.
 */
class DashDownloader(
    private val okHttpClient: OkHttpClient
) {

    fun fetchManifest(url: String, userAgent: String, referer: String?, origin: String? = null): DashManifest? {
        val xml = runCatching {
            val request = Request.Builder().url(url)
                .header("User-Agent", userAgent)
                .header("Accept", "*/*")
                .apply { referer?.takeIf { it.isNotBlank() }?.let { header("Referer", it) } }
                .apply { origin?.takeIf { it.isNotBlank() }?.let { header("Origin", it) } }
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
        origin: String? = null,
        progressEveryMillis: Long = DownloadManager.PROGRESS_THROTTLE_MILLIS,
        onProgress: suspend (DownloadSnapshot) -> Unit = {},
        shouldAbort: suspend () -> AbortReason? = { null }
    ): DownloadResult {
        // The single-file shape (`SegmentBase`): one resource, sliced by byte ranges the manifest
        // named and its own `sidx` index describes.
        if (representation.isSingleFile) {
            return downloadSingleFile(representation, target, userAgent, referer, origin, progressEveryMillis, onProgress, shouldAbort)
        }
        if (representation.segmentUrls.isEmpty()) throw IOException("DASH representation has no segments")
        val parent = target.parentFile ?: throw IOException("DASH target has no parent directory")
        val partFile = File(parent, "${target.name}.${DownloadManager.PART_SUFFIX}")
        // A `.part` left by an interrupted concatenation is unusable; the staged segments are the
        // real resume state, so start the assembly output clean.
        if (partFile.exists()) partFile.delete()

        val parts = buildList {
            representation.initUrl?.let { add(SegmentPart(url = it)) }
            representation.segmentUrls.forEach { add(SegmentPart(url = it)) }
        }

        SegmentedFetcher(okHttpClient).fetch(
            parts = parts,
            stagingDir = SegmentedFetcher.stagingDirFor(parent, target.name),
            partFile = partFile,
            userAgent = userAgent,
            referer = referer,
            origin = origin,
            progressEveryMillis = progressEveryMillis,
            onProgress = onProgress,
            shouldAbort = shouldAbort
        )

        if (!partFile.exists() || partFile.length() == 0L) {
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

    /**
     * Downloads a `SegmentBase` representation: a file whose segments are byte ranges.
     *
     * The manifest gives the range of the index and (optionally) of the initialisation data; the
     * index gives the range of every other segment. The transfer is therefore planned as
     * `[0 .. end of index] + every referenced range`, which concatenates back into the original file
     * byte for byte and is fetched by the same parallel, resumable [SegmentedFetcher] every other
     * segmented transfer uses.
     *
     * The plan is only used when it provably covers the whole file: the parts have to start at zero,
     * be contiguous and add up to the length the server reports. Any mismatch — a missing index, a
     * range that overlaps or leaves a gap, a server that will not describe the file's length — falls
     * back to fetching the whole resource in one request, because a truncated MP4 that looks
     * finished is worse than a slower download.
     */
    private suspend fun downloadSingleFile(
        representation: DashRepresentation,
        target: File,
        userAgent: String,
        referer: String?,
        origin: String?,
        progressEveryMillis: Long,
        onProgress: suspend (DownloadSnapshot) -> Unit,
        shouldAbort: suspend () -> AbortReason?
    ): DownloadResult {
        val url = representation.fileUrl ?: throw IOException("DASH single-file representation has no URL")
        val parent = target.parentFile ?: throw IOException("DASH target has no parent directory")
        val partFile = File(parent, "${target.name}.${DownloadManager.PART_SUFFIX}")
        if (partFile.exists()) partFile.delete()

        val totalBytes = probeLength(url, userAgent, referer, origin)
        val parts = planSingleFileRanges(url, representation, totalBytes, userAgent, referer, origin)
            ?: listOf(SegmentPart(url = url))

        SegmentedFetcher(okHttpClient).fetch(
            parts = parts,
            stagingDir = SegmentedFetcher.stagingDirFor(parent, target.name),
            partFile = partFile,
            userAgent = userAgent,
            referer = referer,
            origin = origin,
            progressEveryMillis = progressEveryMillis,
            onProgress = onProgress,
            shouldAbort = shouldAbort
        )

        if (!partFile.exists() || partFile.length() == 0L) {
            partFile.delete()
            throw IOException("DASH download produced an empty file")
        }
        if (!partFile.renameTo(target)) {
            partFile.copyTo(target, overwrite = true)
            partFile.delete()
        }
        val size = target.length()
        onProgress(DownloadSnapshot(downloadedBytes = size, totalBytes = totalBytes ?: size, bytesPerSecond = 0L))
        return DownloadResult(file = target, bytesWritten = size, totalBytes = totalBytes ?: size, contentType = representation.mimeType)
    }

    /**
     * The range plan for a single-file representation, or null when it cannot be trusted.
     *
     * `rangeBytes` failures (a server that answers without the index, an index that does not parse)
     * are also a null: the caller then downloads the whole resource.
     */
    private fun planSingleFileRanges(
        url: String,
        representation: DashRepresentation,
        totalBytes: Long?,
        userAgent: String,
        referer: String?,
        origin: String?
    ): List<SegmentPart>? {
        val indexRange = representation.indexRange ?: return null
        val indexBytes = fetchRange(url, indexRange, userAgent, referer, origin) ?: return null
        val entries = SegmentIndexBox.parse(indexBytes, indexRange.first) ?: return null

        // Everything up to and including the index: the file type box, the initialisation data and
        // the index itself, which is exactly the prefix a player needs before the first segment.
        val ranges = mutableListOf(0L..indexRange.last)
        val initRange = representation.initRange
        if (initRange != null && (initRange.first < 0L || initRange.last > indexRange.last)) return null

        entries.forEach { entry ->
            if (entry.start <= indexRange.last) return null // overlapping the prefix: the plan is wrong
            ranges += entry.start..(entry.endExclusive - 1)
        }

        // Contiguity is what makes the concatenation byte-exact; without it the result would be a
        // file with a hole in it, which no player reports as an error.
        var expectedStart = 0L
        ranges.forEach { range ->
            if (range.first != expectedStart) return null
            expectedStart = range.last + 1
        }
        if (totalBytes != null && expectedStart != totalBytes) return null
        return ranges.map { range -> SegmentPart(url = url, range = range) }
    }

    /** `Content-Range` total for a one-byte ranged request, or null when the server will not say. */
    private fun probeLength(url: String, userAgent: String, referer: String?, origin: String?): Long? = runCatching {
        val request = Request.Builder().url(url)
            .header("Range", "bytes=0-0")
            .header("Accept", "*/*")
            .header("User-Agent", userAgent)
            .apply { referer?.takeIf { it.isNotBlank() }?.let { header("Referer", it) } }
            .apply { origin?.takeIf { it.isNotBlank() }?.let { header("Origin", it) } }
            .get().build()
        okHttpClient.newCall(request).execute().use { response ->
            if (response.code != 206) return@use null
            response.header("Content-Range")?.substringAfter('/', "")?.trim()?.toLongOrNull()?.takeIf { it > 0L }
        }
    }.getOrNull()

    /** The bytes of one range, or null when the request failed or the body is short. */
    private fun fetchRange(url: String, range: LongRange, userAgent: String, referer: String?, origin: String?): ByteArray? = runCatching {
        val request = Request.Builder().url(url)
            .header("Range", "bytes=${range.first}-${range.last}")
            .header("Accept", "*/*")
            .header("User-Agent", userAgent)
            .apply { referer?.takeIf { it.isNotBlank() }?.let { header("Referer", it) } }
            .apply { origin?.takeIf { it.isNotBlank() }?.let { header("Origin", it) } }
            .get().build()
        okHttpClient.newCall(request).execute().use { response ->
            if (response.code != 206) return@use null
            val bytes = response.body?.bytes() ?: return@use null
            if (bytes.isEmpty()) null else bytes
        }
    }.getOrNull()
}
