package com.salvia.salviabrowxer.media.downloader

import com.salvia.salviabrowxer.core.model.DashManifest
import com.salvia.salviabrowxer.core.model.DashRepresentation
import com.salvia.salviabrowxer.media.resolver.DashManifestParser
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
}
