package com.salvia.salviabrowxer.media.downloader

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * One part of a segmented transfer: an absolute URL, an optional byte range (`EXT-X-BYTERANGE`) and
 * an optional AES-128 key that has to be applied to the payload before it is written.
 */
internal class SegmentPart(
    val url: String,
    val range: LongRange? = null,
    val keyUri: String? = null,
    val iv: ByteArray? = null
)

/**
 * Fetches the parts of a segmented stream (HLS media segments, and later DASH media segments) into a
 * staging directory with bounded parallelism, then concatenates them in order into the output file.
 *
 * Parallelism is what makes this downloader competitive: a playlist with a few thousand small
 * segments finishes far faster when four are in flight. Order is preserved because each part is
 * written to its own numbered file and the final pass reads them back in index order.
 *
 * **Resume.** A fully written part file stays in the staging directory and is skipped on the next
 * attempt, so a paused or process-killed transfer continues where it stopped instead of restarting
 * from segment one. Half-written parts carry a `.tmp` suffix and are deleted first, so a part file
 * only ever exists when it is complete.
 *
 * **Encryption.** A part carrying [SegmentPart.keyUri] is fetched whole, decrypted with AES-128-CBC
 * (the standard HLS `EXT-X-KEY` scheme — this is *not* DRM) and only then staged. The key is fetched
 * once per URI and cached for the transfer. `SAMPLE-AES` is refused by the caller, not here.
 */
internal class SegmentedFetcher(private val okHttpClient: OkHttpClient) {

    /**
     * Downloads [parts] into [partFile], reusing whatever is already staged in [stagingDir].
     * Throws [DownloadAbortedException] when [shouldAbort] returns a reason; a CANCELLED abort also
     * discards the staging directory, while a PAUSED one keeps it for the resume.
     */
    suspend fun fetch(
        parts: List<SegmentPart>,
        stagingDir: File,
        partFile: File,
        userAgent: String,
        referer: String?,
        progressEveryMillis: Long,
        onProgress: suspend (DownloadSnapshot) -> Unit,
        shouldAbort: suspend () -> AbortReason?
    ) {
        if (parts.isEmpty()) throw IOException("Segmented stream has no parts")
        if (!stagingDir.exists() && !stagingDir.mkdirs()) {
            throw IOException("Unable to create staging directory: ${stagingDir.absolutePath}")
        }

        // Discard half-written parts; a file without the `.tmp` suffix is complete and kept.
        stagingDir.listFiles()?.forEach { if (it.name.endsWith(TMP_SUFFIX)) it.delete() }
        // A leftover part indexed past this stream belongs to a different manifest: start over.
        val hasStale = stagingDir.listFiles().orEmpty().any { file ->
            indexOf(file.name)?.let { it >= parts.size } == true
        }
        if (hasStale) stagingDir.listFiles()?.forEach { it.delete() }

        val keyCache = ConcurrentHashMap<String, ByteArray>()
        val completedBytes = AtomicLong(0L)
        val completedCount = AtomicInteger(0)
        for (index in parts.indices) {
            val staged = stagedFile(stagingDir, index)
            if (staged.exists() && staged.length() > 0L) {
                completedBytes.addAndGet(staged.length())
                completedCount.incrementAndGet()
            }
        }

        var lastReport = 0L
        var lastReportBytes = completedBytes.get()
        var lastReportTime = System.currentTimeMillis()
        val reportMutex = Mutex()

        suspend fun report(force: Boolean) {
            val now = System.currentTimeMillis()
            reportMutex.withLock {
                if (!force && now - lastReport < progressEveryMillis) return
                val written = completedBytes.get()
                val done = completedCount.get()
                val elapsed = (now - lastReportTime).coerceAtLeast(1L)
                val speed = (written - lastReportBytes) * 1000L / elapsed
                lastReport = now
                lastReportBytes = written
                lastReportTime = now
                // Total size is unknown up front; the finished-part fraction gives a usable estimate.
                val estimatedTotal = if (done > 0) (written * parts.size / done).takeIf { it > 0L } else null
                onProgress(DownloadSnapshot(downloadedBytes = written, totalBytes = estimatedTotal, bytesPerSecond = speed))
            }
        }

        val semaphore = Semaphore(PARALLELISM)
        try {
            coroutineScope {
                parts.indices.map { index ->
                    async(Dispatchers.IO) {
                        if (isStaged(stagingDir, index)) return@async
                        shouldAbort()?.let { throw DownloadAbortedException(it) }
                        semaphore.withPermit {
                            if (isStaged(stagingDir, index)) return@withPermit
                            shouldAbort()?.let { throw DownloadAbortedException(it) }
                            fetchPart(parts[index], stagingDir, index, userAgent, referer, keyCache, shouldAbort)
                            val staged = stagedFile(stagingDir, index)
                            completedBytes.addAndGet(staged.length())
                            completedCount.incrementAndGet()
                            report(false)
                        }
                    }
                }.awaitAll()
            }
        } catch (aborted: DownloadAbortedException) {
            // A cancelled transfer is gone for good; a paused one keeps its staged parts to resume.
            if (aborted.reason == AbortReason.CANCELLED) stagingDir.deleteRecursively()
            throw aborted
        } catch (cancelled: CancellationException) {
            throw cancelled
        }

        FileOutputStream(partFile).use { output ->
            for (index in parts.indices) {
                val staged = stagedFile(stagingDir, index)
                if (!staged.exists()) throw IOException("Segmented transfer is missing part $index")
                staged.inputStream().use { it.copyTo(output, BUFFER_SIZE) }
            }
            output.flush()
        }
        stagingDir.deleteRecursively()
        report(force = true)
    }

    private suspend fun fetchPart(
        part: SegmentPart,
        stagingDir: File,
        index: Int,
        userAgent: String,
        referer: String?,
        keyCache: ConcurrentHashMap<String, ByteArray>,
        shouldAbort: suspend () -> AbortReason?
    ) {
        val request = Request.Builder().url(part.url)
            .header("User-Agent", userAgent)
            .header("Accept", "*/*")
            .apply { part.range?.let { header("Range", "bytes=${it.first}-${it.last}") } }
            .apply { referer?.takeIf { it.isNotBlank() }?.let { header("Referer", it) } }
            .get().build()
        val tmp = File(stagingDir, "${partName(index)}.$TMP_SUFFIX")
        try {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Segment failed HTTP ${response.code} for ${part.url}")
                val body = response.body ?: throw IOException("Empty segment body for ${part.url}")
                if (part.keyUri == null) {
                    body.byteStream().use { input ->
                        FileOutputStream(tmp).use { output ->
                            val buffer = ByteArray(BUFFER_SIZE)
                            while (true) {
                                shouldAbort()?.let { throw DownloadAbortedException(it) }
                                val read = input.read(buffer)
                                if (read == -1) break
                                output.write(buffer, 0, read)
                            }
                            output.flush()
                        }
                    }
                } else {
                    val key = keyCache[part.keyUri] ?: fetchKey(part.keyUri, userAgent, referer).also { keyCache[part.keyUri] = it }
                    val decrypted = decryptAes128(body.bytes(), key, part.iv ?: ZERO_IV)
                    tmp.writeBytes(decrypted)
                }
            }
            if (!tmp.renameTo(stagedFile(stagingDir, index))) throw IOException("Unable to finalise segment $index")
        } finally {
            if (tmp.exists()) tmp.delete()
        }
    }

    private fun fetchKey(url: String, userAgent: String, referer: String?): ByteArray {
        val request = Request.Builder().url(url)
            .header("User-Agent", userAgent)
            .header("Accept", "*/*")
            .apply { referer?.takeIf { it.isNotBlank() }?.let { header("Referer", it) } }
            .get().build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HLS key fetch failed HTTP ${response.code}")
            val bytes = response.body?.bytes() ?: throw IOException("Empty HLS key response")
            if (bytes.size < AES_KEY_SIZE) throw IOException("HLS key is shorter than 128 bits")
            return bytes.copyOf(AES_KEY_SIZE)
        }
    }

    private fun decryptAes128(data: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        return cipher.doFinal(data)
    }

    companion object {
        /** Four segments in flight: fast on a real connection, still gentle to a phone. */
        const val PARALLELISM = 4
        private const val BUFFER_SIZE = 64 * 1024
        private const val TMP_SUFFIX = "tmp"
        private const val AES_KEY_SIZE = 16
        private val ZERO_IV = ByteArray(16)

        fun stagingDirFor(parent: File, name: String): File = File(parent, "$name.parts")

        private fun partName(index: Int): String = "part-%05d".format(index)

        private fun stagedFile(stagingDir: File, index: Int): File = File(stagingDir, partName(index))

        private fun isStaged(stagingDir: File, index: Int): Boolean =
            stagedFile(stagingDir, index).let { it.exists() && it.length() > 0L }

        private fun indexOf(name: String): Int? {
            if (!name.startsWith("part-")) return null
            return name.substringAfter("part-").substringBefore('.').toIntOrNull()
        }
    }
}
