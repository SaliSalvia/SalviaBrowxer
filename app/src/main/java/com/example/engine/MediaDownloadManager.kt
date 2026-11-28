package com.example.engine

import android.content.ContentValues
import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import android.webkit.CookieManager
import com.example.data.local.BrowserRepository
import com.example.data.local.DownloadEntity
import com.example.ui.MediaQualityOption
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlin.random.Random
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.net.ProtocolException
import java.io.OutputStream
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class MediaDownloadManager(
    private val context: Context,
    private val repository: BrowserRepository,
    private val scope: CoroutineScope,
) {
    private val activeJobs = ConcurrentHashMap<Long, Job>()
    private val pausedIds = ConcurrentHashMap.newKeySet<Long>()

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(false)
        .build()

    fun startDownload(
        fileName: String,
        quality: MediaQualityOption,
        sourcePageUrl: String,
        userAgent: String,
        sessionCookieManager: CookieManager? = null,
        isIncognito: Boolean = false,
        isDesktopMode: Boolean = false,
        allowGlobalCookieFallback: Boolean = !isIncognito,
    ) {
        scope.launch(Dispatchers.IO) {
            val downloadId = createDownloadRecord(
                fileName = fileName,
                quality = quality,
                sourcePageUrl = sourcePageUrl,
                isIncognito = isIncognito,
                isDesktopMode = isDesktopMode,
            )
            executePersistedDownload(
                downloadId = downloadId,
                userAgent = userAgent,
                sessionCookieManager = sessionCookieManager,
                allowGlobalCookieFallback = allowGlobalCookieFallback && !isIncognito,
            )
        }
    }

    suspend fun createDownloadRecord(
        fileName: String,
        quality: MediaQualityOption,
        sourcePageUrl: String,
        isIncognito: Boolean = false,
        isDesktopMode: Boolean = false,
    ): Long {
        val safeFileName = sanitizeFileName(fileName, quality)
        return repository.insertDownload(
            DownloadEntity(
                fileName = safeFileName,
                url = quality.url,
                audioUrl = quality.audioUrl,
                sourcePageUrl = sourcePageUrl,
                qualityLabel = quality.label,
                mimeType = quality.mimeType,
                totalBytes = quality.sizeBytes,
                downloadedBytes = 0L,
                status = "QUEUED",
                isHlsStream = quality.isHls,
                isDashStream = quality.isDash,
                supportsResume = !quality.isHls && !quality.isDash,
                isIncognito = isIncognito,
                isDesktopMode = isDesktopMode,
                manifestRepresentationId = quality.manifestRepresentationId,
                manifestAudioRepresentationId = quality.manifestAudioRepresentationId,
            ),
        )
    }

    fun executePersistedDownloadAsync(
        downloadId: Long,
        userAgent: String,
        sessionCookieManager: CookieManager? = null,
        allowGlobalCookieFallback: Boolean = true,
        requestHeaderContextOverride: Map<String, Map<String, String>> = emptyMap(),
    ): Job = scope.launch(Dispatchers.IO) {
        executePersistedDownload(
            downloadId = downloadId,
            userAgent = userAgent,
            sessionCookieManager = sessionCookieManager,
            allowGlobalCookieFallback = allowGlobalCookieFallback,
            requestHeaderContextOverride = requestHeaderContextOverride,
        )
    }

    suspend fun markDownloadQueued(downloadId: Long) {
        repository.updateDownloadStatus(downloadId, "QUEUED")
    }

    suspend fun executePersistedDownload(
        downloadId: Long,
        userAgent: String,
        sessionCookieManager: CookieManager? = null,
        allowGlobalCookieFallback: Boolean = true,
        cancelTransferWhenCallerCancelled: Boolean = true,
        requestHeaderContextOverride: Map<String, Map<String, String>> = emptyMap(),
    ): DownloadEntity? {
        val download = repository.getDownloadById(downloadId) ?: return null
        if (download.status in setOf("COMPLETED", "CANCELLED")) return download

        // A fresh explicit resume must not inherit an old scheduler-stop marker.
        pausedIds.remove(downloadId)

        val job = launchJobForEntity(
            downloadId = download.id,
            fileName = download.fileName,
            url = download.url,
            audioUrl = download.audioUrl,
            referer = download.sourcePageUrl,
            userAgent = userAgent,
            mimeType = download.mimeType,
            isHls = download.isHlsStream || download.url.lowercase().contains(".m3u8"),
            isDash = download.isDashStream || download.url.lowercase().contains(".mpd"),
            manifestRepresentationId = download.manifestRepresentationId,
            manifestAudioRepresentationId = download.manifestAudioRepresentationId,
            sessionCookieManager = sessionCookieManager,
            allowGlobalCookieFallback = allowGlobalCookieFallback && !download.isIncognito,
            requestHeaderContextOverride = requestHeaderContextOverride,
            isIncognito = download.isIncognito,
        )
        val callerContext = currentCoroutineContext()
        try {
            job.join()
        } finally {
            // Stop the process-wide IO task when its owning Worker/JobService is cancelled.
            if (cancelTransferWhenCallerCancelled && !callerContext.isActive && job.isActive) {
                job.cancelAndJoin()
            }
        }
        return repository.getDownloadById(downloadId)
    }

    suspend fun stopForSystemRetry(downloadId: Long) {
        val before = repository.getDownloadById(downloadId) ?: return
        // Explicit user pause/cancel always wins over a concurrent scheduler stop.
        if (before.status in setOf("PAUSED", "CANCELLED", "COMPLETED", "FAILED")) return

        pausedIds.add(downloadId)
        activeJobs[downloadId]?.cancelAndJoin()
        val after = repository.getDownloadById(downloadId) ?: return
        if (after.status !in setOf("CANCELLED", "COMPLETED", "FAILED")) {
            repository.updateDownloadStatus(downloadId, "QUEUED")
        }
    }

    fun isDownloadExecuting(downloadId: Long): Boolean =
        activeJobs[downloadId]?.isActive == true

    suspend fun pauseDownloadAndWait(download: DownloadEntity) {
        pausedIds.add(download.id)
        activeJobs[download.id]?.cancelAndJoin()
        repository.updateDownloadStatus(download.id, "PAUSED")
    }

    suspend fun cancelDownloadAndWait(download: DownloadEntity) {
        pausedIds.remove(download.id)
        activeJobs[download.id]?.cancelAndJoin()
        chunkDir(download.id).deleteRecursively()
        repository.deleteMediaRequestContext(download.id)
        repository.updateDownloadStatus(download.id, "CANCELLED")
    }

    fun pauseDownload(download: DownloadEntity) {
        pausedIds.add(download.id)
        activeJobs.remove(download.id)?.cancel()
        scope.launch(Dispatchers.IO) {
            repository.updateDownloadStatus(download.id, "PAUSED")
        }
    }

    fun resumeDownload(
        download: DownloadEntity,
        userAgent: String,
        sessionCookieManager: CookieManager?,
        allowGlobalCookieFallback: Boolean,
        requestHeaderContextOverride: Map<String, Map<String, String>> = emptyMap(),
    ) {
        pausedIds.remove(download.id)
        scope.launch(Dispatchers.IO) {
            launchJobForEntity(
                downloadId = download.id,
                fileName = download.fileName,
                url = download.url,
                audioUrl = download.audioUrl,
                referer = download.sourcePageUrl,
                userAgent = userAgent,
                mimeType = download.mimeType,
                isHls = download.isHlsStream || download.url.lowercase().contains(".m3u8"),
                isDash = download.isDashStream,
                manifestRepresentationId = download.manifestRepresentationId,
                manifestAudioRepresentationId = download.manifestAudioRepresentationId,
                sessionCookieManager = sessionCookieManager,
                allowGlobalCookieFallback = allowGlobalCookieFallback && !download.isIncognito,
                requestHeaderContextOverride = requestHeaderContextOverride,
                isIncognito = download.isIncognito,
            )
        }
    }

    /**
     * Cancels all active private downloads, removes partial files, and deletes
     * their Room metadata before the dedicated private WebView profile is removed.
     * Completed files intentionally published to MediaStore are retained.
     */
    suspend fun clearIncognitoDownloads() {
        val privateDownloads = repository.getIncognitoDownloads()
        privateDownloads.forEach { download ->
            pausedIds.remove(download.id)
            activeJobs.remove(download.id)?.cancelAndJoin()
            chunkDir(download.id).deleteRecursively()
            repository.deleteDownload(download.id)
        }
        // Only records captured in this closing-session snapshot are removed.
        // A new private session started during cleanup keeps its own queue entries.
    }

    fun cancelDownload(download: DownloadEntity) {
        pausedIds.remove(download.id)
        activeJobs.remove(download.id)?.cancel()
        chunkDir(download.id).deleteRecursively()
        scope.launch(Dispatchers.IO) {
            repository.deleteMediaRequestContext(download.id)
            repository.updateDownloadStatus(download.id, "CANCELLED")
        }
    }

    private fun launchJobForEntity(
        downloadId: Long,
        fileName: String,
        url: String,
        audioUrl: String,
        referer: String,
        userAgent: String,
        mimeType: String,
        isHls: Boolean,
        isDash: Boolean,
        manifestRepresentationId: String,
        manifestAudioRepresentationId: String,
        sessionCookieManager: CookieManager?,
        allowGlobalCookieFallback: Boolean,
        requestHeaderContextOverride: Map<String, Map<String, String>> = emptyMap(),
        isIncognito: Boolean = false,
    ): Job = synchronized(activeJobs) {
        activeJobs[downloadId]?.takeIf { it.isActive }?.let { return@synchronized it }

        val job = scope.launch(context = Dispatchers.IO, start = CoroutineStart.LAZY) {
            val encodedOverride = if (requestHeaderContextOverride.isNotEmpty()) {
                MediaRequestHeaderPolicy.encodeContext(requestHeaderContextOverride)
            } else {
                ""
            }
            val persisted = if (encodedOverride.isBlank() && !isIncognito) {
                repository.cleanupExpiredMediaRequestContexts()
                repository.getMediaRequestContext(downloadId)?.contextData.orEmpty()
            } else {
                ""
            }
            val requestContexts = MediaRequestHeaderPolicy.decodeContext(
                encodedOverride.ifBlank { persisted },
            )
            withContext(MediaRequestHeaderContextElement(requestContexts)) {
                when {
                    isHls -> downloadHlsStream(downloadId, fileName, url, audioUrl, referer, userAgent, sessionCookieManager, allowGlobalCookieFallback)
                    isDash && manifestRepresentationId.isNotBlank() -> downloadDashManifestAndMux(downloadId, fileName, url, manifestRepresentationId, manifestAudioRepresentationId, referer, userAgent, sessionCookieManager, allowGlobalCookieFallback)
                    isDash && audioUrl.isNotBlank() -> downloadDashAndMux(downloadId, fileName, url, audioUrl, referer, userAgent, sessionCookieManager, allowGlobalCookieFallback)
                    else -> downloadDirectMultiPart(downloadId, fileName, url, referer, userAgent, mimeType, sessionCookieManager, allowGlobalCookieFallback)
                }
            }
            val finalStatus = repository.getDownloadById(downloadId)?.status
            if (finalStatus == "COMPLETED" || finalStatus == "CANCELLED") {
                repository.deleteMediaRequestContext(downloadId)
            }
        }
        activeJobs[downloadId] = job
        job.invokeOnCompletion { activeJobs.remove(downloadId, job) }
        job.start()
        job
    }

    /**
     * Adaptive multi-connection Range downloader (up to [DownloadFilePolicy.MAX_PARTS] parts)
     * with persistent part resume, per-part retries with backoff, a storage pre-check, a
     * shared smoothed speed meter, and a safe single-stream fallback when a server stops
     * honoring ranges mid-way.
     */
    private suspend fun downloadDirectMultiPart(
        downloadId: Long,
        fileName: String,
        url: String,
        referer: String,
        userAgent: String,
        mimeType: String,
        sessionCookieManager: CookieManager?,
        allowGlobalCookieFallback: Boolean,
    ) {
        val tempDir = chunkDir(downloadId).apply { if (!exists()) mkdirs() }

        try {
            repository.updateDownloadProgress(
                id = downloadId,
                downloaded = 0L,
                total = -1L,
                speedBytesPerSec = 0L,
                status = "DOWNLOADING",
                supportsResume = true,
            )

            val rangeProbe = probeRangeSupport(
                url = url,
                referer = referer,
                userAgent = userAgent,
                sessionCookieManager = sessionCookieManager,
                allowGlobalCookieFallback = allowGlobalCookieFallback,
            )
            val totalBytes = rangeProbe.first
            var supportsRanges = rangeProbe.second
            val partCount = if (supportsRanges) DownloadFilePolicy.partCountFor(totalBytes) else 1

            if (supportsRanges && partCount > 1) {
                val completed = runCatching {
                    downloadInParallelParts(
                        downloadId = downloadId,
                        fileName = fileName,
                        url = url,
                        totalBytes = totalBytes,
                        partCount = partCount,
                        tempDir = tempDir,
                        referer = referer,
                        userAgent = userAgent,
                        mimeType = mimeType,
                        sessionCookieManager = sessionCookieManager,
                        allowGlobalCookieFallback = allowGlobalCookieFallback,
                    )
                    true
                }.getOrElse { error ->
                    // A server that stops honoring ranges is downloaded with one connection
                    // instead of failing; every other error keeps the parts for a later resume.
                    if (error is NonRetryableDownloadException) {
                        tempDir.listFiles()?.forEach { it.delete() }
                        supportsRanges = false
                        false
                    } else {
                        throw error
                    }
                }
                if (completed) return
            }

            downloadSingleStream(
                downloadId = downloadId,
                fileName = fileName,
                url = url,
                knownTotalBytes = totalBytes,
                rangesUsable = supportsRanges,
                tempDir = tempDir,
                referer = referer,
                userAgent = userAgent,
                mimeType = mimeType,
                sessionCookieManager = sessionCookieManager,
                allowGlobalCookieFallback = allowGlobalCookieFallback,
            )
        } catch (_: CancellationException) {
            if (pausedIds.remove(downloadId)) {
                repository.updateDownloadStatus(downloadId, "PAUSED")
            } else {
                tempDir.deleteRecursively()
                repository.updateDownloadStatus(downloadId, "CANCELLED")
            }
        } catch (e: Exception) {
            android.util.Log.w("SalviaDownloads", "Download $downloadId failed: ${e.javaClass.simpleName}: ${e.message?.take(200)}")
            // Staged parts are kept so the user's retry resumes instead of restarting.
            repository.updateDownloadStatus(downloadId, "FAILED")
        } finally {
            // activeJobs is cleared by the central Job completion handler.
        }
    }

    private suspend fun downloadInParallelParts(
        downloadId: Long,
        fileName: String,
        url: String,
        totalBytes: Long,
        partCount: Int,
        tempDir: File,
        referer: String,
        userAgent: String,
        mimeType: String,
        sessionCookieManager: CookieManager?,
        allowGlobalCookieFallback: Boolean,
    ) {
        val layout = DownloadFilePolicy.layoutMarker(totalBytes, partCount)
        val marker = File(tempDir, "layout.txt")
        val markerMatches = marker.exists() && runCatching { marker.readText() }.getOrNull() == layout
        if (!markerMatches) {
            // Never append to part files produced by a different split (older versions used
            // a fixed 4-part layout) — that would silently corrupt the output.
            tempDir.listFiles()?.filter { it.name.startsWith("part_") || it.name == "single.bin" }
                ?.forEach { it.delete() }
            marker.writeText(layout)
        }

        val ranges = DownloadFilePolicy.partRanges(totalBytes, partCount)
        val partFiles = ranges.indices.map { index -> File(tempDir, "part_$index.bin") }
        partFiles.forEachIndexed { index, part ->
            val expected = ranges[index].last - ranges[index].first + 1L
            if (part.exists() && part.length() > expected) part.delete()
        }
        val staged = partFiles.sumOf { if (it.exists()) it.length() else 0L }
        ensureStorageFor(totalBytes, staged)

        val reporter = ProgressReporter(downloadId, staged, totalBytes, supportsResume = true)
        coroutineScope {
            ranges.mapIndexed { index, range ->
                async(Dispatchers.IO) {
                    downloadRangePart(
                        url = url,
                        range = range,
                        partFile = partFiles[index],
                        totalBytes = totalBytes,
                        referer = referer,
                        userAgent = userAgent,
                        sessionCookieManager = sessionCookieManager,
                        allowGlobalCookieFallback = allowGlobalCookieFallback,
                        reporter = reporter,
                    )
                }
            }.awaitAll()
        }

        val assembledBytes = partFiles.sumOf { part -> if (part.exists()) part.length() else 0L }
        if (assembledBytes != totalBytes) {
            throw IOException("Incomplete download: $assembledBytes of $totalBytes bytes")
        }

        val published = publishFile(fileName, mimeType) { output ->
            val buffer = ByteArray(64 * 1024)
            partFiles.forEach { part ->
                FileInputStream(part).use { input ->
                    while (true) {
                        val count = input.read(buffer)
                        if (count == -1) break
                        output.write(buffer, 0, count)
                    }
                }
            }
        }
        tempDir.deleteRecursively()
        completeDownload(downloadId, fileName, published, totalBytes, supportsResume = true)
    }

    private suspend fun downloadRangePart(
        url: String,
        range: LongRange,
        partFile: File,
        totalBytes: Long,
        referer: String,
        userAgent: String,
        sessionCookieManager: CookieManager?,
        allowGlobalCookieFallback: Boolean,
        reporter: ProgressReporter,
    ) {
        val expectedPartLength = range.last - range.first + 1L
        withRetries {
            var existingBytes = if (partFile.exists()) partFile.length() else 0L
            if (existingBytes > expectedPartLength) {
                reporter.add(-existingBytes)
                partFile.delete()
                existingBytes = 0L
            }
            if (existingBytes == expectedPartLength) return@withRetries

            val requestStart = range.first + existingBytes
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .header("Range", "bytes=$requestStart-${range.last}")
                .apply { if (referer.isNotBlank()) header("Referer", referer) }
                .build()

            executeWithProfileCookies(request, sessionCookieManager, allowGlobalCookieFallback).use { response ->
                if (response.code != 206) {
                    if (response.isSuccessful) {
                        throw NonRetryableDownloadException("Range request was not honored (HTTP ${response.code})")
                    }
                    throw HttpStatusException(response.code)
                }
                val rangeMatch = RangeResponseParser.parse(response.header("Content-Range"))
                    ?: throw NonRetryableDownloadException("Missing or invalid Content-Range")
                if (rangeMatch.start != requestStart ||
                    rangeMatch.end != range.last ||
                    rangeMatch.totalBytes != totalBytes
                ) {
                    throw NonRetryableDownloadException("Unexpected Content-Range")
                }
                val input = response.body?.byteStream() ?: throw IOException("Empty range response body")
                FileOutputStream(partFile, existingBytes > 0L).use { output ->
                    input.use { stream -> copyWithProgress(stream, output, reporter) }
                }
            }
            if (partFile.length() != expectedPartLength) {
                throw IOException("Incomplete range part: ${partFile.length()} of $expectedPartLength bytes")
            }
        }
    }

    /**
     * Single-connection transfer with automatic resume when the server supports ranges, and a
     * clean restart otherwise. Retries transient failures with exponential backoff.
     */
    private suspend fun downloadSingleStream(
        downloadId: Long,
        fileName: String,
        url: String,
        knownTotalBytes: Long,
        rangesUsable: Boolean,
        tempDir: File,
        referer: String,
        userAgent: String,
        mimeType: String,
        sessionCookieManager: CookieManager?,
        allowGlobalCookieFallback: Boolean,
    ) {
        val singleFile = File(tempDir, "single.bin")
        if (!rangesUsable && singleFile.exists()) singleFile.delete()
        val staged = if (singleFile.exists()) singleFile.length() else 0L
        ensureStorageFor(knownTotalBytes, staged)

        val reporter = ProgressReporter(downloadId, staged, knownTotalBytes, supportsResume = rangesUsable)
        var expectedTotal = knownTotalBytes
        withRetries {
            val existingBytes = if (rangesUsable && singleFile.exists()) singleFile.length() else 0L
            if (existingBytes == 0L && singleFile.exists()) singleFile.delete()
            if (existingBytes > 0L && expectedTotal > 0L && existingBytes == expectedTotal) return@withRetries
            reporter.reset(existingBytes)

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .apply {
                    if (referer.isNotBlank()) header("Referer", referer)
                    if (existingBytes > 0L) header("Range", "bytes=$existingBytes-")
                }
                .build()

            executeWithProfileCookies(request, sessionCookieManager, allowGlobalCookieFallback).use { response ->
                val append = existingBytes > 0L && response.code == 206
                if (existingBytes > 0L && response.code == 206) {
                    val contentRange = response.header("Content-Range").orEmpty()
                    if (!contentRange.startsWith("bytes $existingBytes-", ignoreCase = true)) {
                        throw NonRetryableDownloadException("Unexpected resume Content-Range")
                    }
                } else if (!response.isSuccessful) {
                    throw HttpStatusException(response.code)
                }
                if (!append) reporter.reset(0L)

                val body = response.body ?: throw IOException("Empty download response body")
                val responseLength = body.contentLength()
                expectedTotal = when {
                    knownTotalBytes > 0L -> knownTotalBytes
                    responseLength > 0L -> (if (append) existingBytes else 0L) + responseLength
                    else -> -1L
                }
                reporter.totalBytes = expectedTotal
                if (!append) ensureStorageFor(expectedTotal, 0L)

                FileOutputStream(singleFile, append).use { output ->
                    body.byteStream().use { input -> copyWithProgress(input, output, reporter) }
                }
            }
            if (expectedTotal > 0L && singleFile.length() != expectedTotal) {
                throw IOException("Incomplete download: ${singleFile.length()} of $expectedTotal bytes")
            }
        }

        val published = publishFile(fileName, mimeType) { output ->
            FileInputStream(singleFile).use { input -> input.copyTo(output, 64 * 1024) }
        }
        val completedBytes = singleFile.length()
        tempDir.deleteRecursively()
        completeDownload(downloadId, fileName, published, completedBytes, supportsResume = rangesUsable)
    }

    /**
     * Downloads HLS (.m3u8) with native AES-128 decryption (#EXT-X-KEY), #EXT-X-MAP init segment,
     * optional separate audio rendition, and remuxes into standard playable .mp4 via MediaMuxer.
     */
    private suspend fun downloadHlsStream(
        downloadId: Long,
        fileName: String,
        playlistUrl: String,
        audioPlaylistUrl: String,
        referer: String,
        userAgent: String,
        sessionCookieManager: CookieManager?,
        allowGlobalCookieFallback: Boolean,
    ) {
        val tempDir = chunkDir(downloadId).apply { if (!exists()) mkdirs() }
        try {
            repository.updateDownloadProgress(downloadId, 0L, -1L, 0L, "DOWNLOADING", supportsResume = true)

            val rawVideoFile = File(tempDir, "hls_video.raw")
            val totalWritten = downloadSingleHlsPlaylistToFile(
                downloadId = downloadId,
                playlistUrl = playlistUrl,
                targetFile = rawVideoFile,
                referer = referer,
                userAgent = userAgent,
                sessionCookieManager = sessionCookieManager,
                allowGlobalCookieFallback = allowGlobalCookieFallback,
            )
            if (totalWritten <= 0L) {
                repository.updateDownloadStatus(downloadId, "FAILED")
                return
            }

            var rawAudioFile: File? = null
            if (audioPlaylistUrl.isNotBlank()) {
                val audioTemp = File(tempDir, "hls_audio.raw")
                val audioBytes = downloadSingleHlsPlaylistToFile(
                    downloadId = downloadId,
                    playlistUrl = audioPlaylistUrl,
                    targetFile = audioTemp,
                    referer = referer,
                    userAgent = userAgent,
                    sessionCookieManager = sessionCookieManager,
                    allowGlobalCookieFallback = allowGlobalCookieFallback,
                )
                if (audioBytes > 0L) rawAudioFile = audioTemp
            }

            val mp4FileName = fileName.substringBeforeLast('.') + ".mp4"
            val remuxedTempMp4 = File(tempDir, "muxed_output.mp4")
            val remuxSuccess = remuxToMp4(rawVideoFile, rawAudioFile, remuxedTempMp4)

            val remuxed = remuxSuccess && remuxedTempMp4.exists() && remuxedTempMp4.length() > 0L
            // When MediaMuxer cannot remux (rare codecs), the untouched MPEG-TS stream is kept
            // with an honest .ts name/MIME instead of being mislabeled as MP4.
            val outputName = if (remuxed) mp4FileName else DownloadFilePolicy.withExtension(fileName, "ts")
            val published = if (remuxed) {
                publishFromFile(remuxedTempMp4, outputName, "video/mp4")
            } else {
                publishFromFile(rawVideoFile, outputName, "video/mp2t")
            }
            val finalSize = if (remuxed) remuxedTempMp4.length() else totalWritten
            tempDir.deleteRecursively()
            completeDownload(downloadId, fileName, published, finalSize, supportsResume = true)
        } catch (_: CancellationException) {
            if (pausedIds.remove(downloadId)) {
                repository.updateDownloadStatus(downloadId, "PAUSED")
            } else {
                tempDir.deleteRecursively()
                repository.updateDownloadStatus(downloadId, "CANCELLED")
            }
        } catch (e: Exception) {
            android.util.Log.w("SalviaDownloads", "Download $downloadId failed: ${e.javaClass.simpleName}: ${e.message?.take(200)}")
            // Preserve atomically completed HLS segment files for explicit retry/resume.
            repository.updateDownloadStatus(downloadId, "FAILED")
        } finally {
            // activeJobs is cleared by the central Job completion handler.
        }
    }

    private suspend fun downloadDashManifestAndMux(
        downloadId: Long,
        fileName: String,
        mpdUrl: String,
        videoRepresentationId: String,
        audioRepresentationId: String,
        referer: String,
        userAgent: String,
        sessionCookieManager: CookieManager?,
        allowGlobalCookieFallback: Boolean,
    ) {
        val tempDir = chunkDir(downloadId).apply { if (!exists()) mkdirs() }
        try {
            repository.updateDownloadProgress(downloadId, 0L, -1L, 0L, "DOWNLOADING", supportsResume = true)
            val xml = fetchText(mpdUrl, referer, userAgent, sessionCookieManager, allowGlobalCookieFallback)
                ?: throw IOException("Could not fetch DASH MPD")
            val manifest = DashManifestParser.parse(xml, mpdUrl)
            if (manifest.unsupported != null) throw IOException("DASH manifest unsupported: " + manifest.unsupported)
            val selected = manifest.representations.firstOrNull { it.id == videoRepresentationId }
                ?: throw IOException("Selected DASH representation is no longer available")
            if (!selected.isVideo) {
                val audioOnly = File(tempDir, "dash_audio_" + safeSegmentName(selected.id) + ".m4a")
                val audioResumeSupported = !selected.isSingleFile || selected.byteRanges.isNotEmpty() || selected.sidxUrl != null
                downloadDashRepresentation(selected, audioOnly, tempDir,
                    "audio_" + manifestFingerprint(xml) + "_" + safeSegmentName(selected.id), referer, userAgent,
                    sessionCookieManager, allowGlobalCookieFallback, downloadId)
                val outName = fileName.substringBeforeLast('.') + ".m4a"
                val published = publishFromFile(audioOnly, outName, "audio/mp4")
                val bytes = audioOnly.length()
                tempDir.deleteRecursively()
                completeDownload(downloadId, fileName, published, bytes, audioResumeSupported)
                return
            }

            val videoRep = selected
            val videoFile = File(tempDir, "dash_video_" + safeSegmentName(videoRep.id) + ".mp4")
            var supportsResume = !videoRep.isSingleFile || videoRep.byteRanges.isNotEmpty() || videoRep.sidxUrl != null
            downloadDashRepresentation(videoRep, videoFile, tempDir, "video_" + manifestFingerprint(xml) + "_" + safeSegmentName(videoRep.id), referer, userAgent,
                sessionCookieManager, allowGlobalCookieFallback, downloadId)

            var audioFile: File? = null
            if (audioRepresentationId.isNotBlank()) {
                val audioRep = manifest.representations.firstOrNull { !it.isVideo && it.id == audioRepresentationId }
                if (audioRep != null) {
                    audioFile = File(tempDir, "dash_audio_" + safeSegmentName(audioRep.id) + ".m4a")
                    supportsResume = supportsResume && (!audioRep.isSingleFile || audioRep.byteRanges.isNotEmpty() || audioRep.sidxUrl != null)
                    downloadDashRepresentation(audioRep, audioFile, tempDir, "audio_" + manifestFingerprint(xml) + "_" + safeSegmentName(audioRep.id), referer, userAgent,
                        sessionCookieManager, allowGlobalCookieFallback, downloadId)
                }
            }

            val muxed = File(tempDir, "dash_muxed.mp4")
            val muxOk = remuxToMp4(videoFile, audioFile, muxed)
            val source = if (muxOk && muxed.exists() && muxed.length() > 0L) muxed else videoFile
            val outName = fileName.substringBeforeLast('.') + ".mp4"
            val published = publishFromFile(source, outName, "video/mp4")
            val bytes = source.length()
            tempDir.deleteRecursively()
            completeDownload(downloadId, fileName, published, bytes, supportsResume)
        } catch (_: CancellationException) {
            if (pausedIds.remove(downloadId)) {
                repository.updateDownloadStatus(downloadId, "PAUSED")
            } else {
                tempDir.deleteRecursively()
                repository.updateDownloadStatus(downloadId, "CANCELLED")
            }
        } catch (e: Exception) {
            android.util.Log.w("SalviaDownloads", "Download $downloadId failed: ${e.javaClass.simpleName}: ${e.message?.take(200)}")
            // Keep completed segment files; a user retry can reuse them.
            repository.updateDownloadStatus(downloadId, "FAILED")
        }
    }

    private suspend fun downloadDashRepresentation(
        representation: DashRepresentation,
        target: File,
        staging: File,
        label: String,
        referer: String,
        userAgent: String,
        sessionCookieManager: CookieManager?,
        allowGlobalCookieFallback: Boolean,
        downloadId: Long,
    ) {
        var rep = representation
        if (rep.sidxUrl != null && rep.sidxRange != null) {
            val indexRange = rep.sidxRange
            val indexBytes = fetchHlsBytes(
                rep.sidxUrl,
                ManifestSegmentPlanner.ByteRange(indexRange.last - indexRange.first + 1L, indexRange.first),
                referer, userAgent, sessionCookieManager, allowGlobalCookieFallback,
            )
            val sidx = runCatching { SidxParser.parse(indexBytes) }.getOrNull()
                ?: throw IOException("Invalid DASH sidx index")
            require(sidx.entries.none { it.isSubsegment }) { "Nested DASH sidx references are not supported safely" }
            val urls = mutableListOf<String>()
            val ranges = mutableListOf<LongRange?>()
            if (rep.initRange != null) {
                urls.add(rep.sidxUrl)
                ranges.add(rep.initRange)
            } else if (indexRange.first > 0L) {
                urls.add(rep.sidxUrl)
                ranges.add(0L..(indexRange.first - 1L))
            }
            var cursor = indexRange.last + 1L + sidx.boxSize + sidx.firstOffset
            for (entry in sidx.entries) {
                urls.add(rep.sidxUrl)
                ranges.add(cursor..(cursor + entry.size - 1L))
                cursor += entry.size
            }
            rep = rep.copy(urls = urls, byteRanges = ranges, isSingleFile = false,
                sidxUrl = null, sidxRange = null, initRange = null)
        }

        if (rep.isSingleFile && rep.byteRanges.isEmpty()) {
            downloadUrlToFile(rep.urls.first(), target, referer, userAgent,
                sessionCookieManager, allowGlobalCookieFallback) { dl, total, speed ->
                repository.updateDownloadProgress(downloadId, dl, total, speed, "DOWNLOADING", supportsResume = true)
            }
            return
        }

        val plan = rep
        val ranges = plan.byteRanges.ifEmpty { List(plan.urls.size) { null } }
        val files = plan.urls.indices.map { File(staging, "dash_" + label + "_" + it + ".part") }
        fun isDone(index: Int): Boolean {
            val range = ranges.getOrNull(index)
            val file = files[index]
            return file.exists() && file.length() > 0L &&
                (range == null || file.length() == range.last - range.first + 1L)
        }
        val stagedBytes = files.indices.filter(::isDone).sumOf { files[it].length() }
        val reporter = ProgressReporter(downloadId, stagedBytes, -1L, supportsResume = true)
        val doneCount = java.util.concurrent.atomic.AtomicInteger(files.indices.count(::isDone))
        val doneBytes = AtomicLong(stagedBytes)
        val semaphore = Semaphore(DownloadFilePolicy.SEGMENT_CONCURRENCY)
        coroutineScope {
            plan.urls.indices.filterNot(::isDone).map { index ->
                async(Dispatchers.IO) {
                    semaphore.withPermit {
                        val range = ranges.getOrNull(index)
                        val file = files[index]
                        file.delete()
                        fetchToFile(
                            url = plan.urls[index],
                            range = range?.let { ManifestSegmentPlanner.ByteRange(it.last - it.first + 1L, it.first) },
                            target = file,
                            referer = referer,
                            userAgent = userAgent,
                            sessionCookieManager = sessionCookieManager,
                            allowGlobalCookieFallback = allowGlobalCookieFallback,
                            onBytes = { delta -> reporter.add(delta) },
                        )
                        val count = doneCount.incrementAndGet()
                        val bytes = doneBytes.addAndGet(file.length())
                        reporter.totalBytes = estimateTotal(bytes, count, files.size)
                            .coerceAtLeast(reporter.downloaded.get())
                    }
                }
            }.awaitAll()
        }
        reporter.flush()
        if (files.any { !it.exists() || it.length() == 0L }) throw IOException("DASH representation is incomplete")
        FileOutputStream(target, false).use { out ->
            files.forEach { file -> FileInputStream(file).use { it.copyTo(out) } }
        }
    }

    private fun manifestFingerprint(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
            .take(12).joinToString("") { byte ->
                (byte.toInt() and 0xff).toString(16).padStart(2, '0')
            }

    private fun safeSegmentName(value: String): String =
        value.replace(Regex("[^A-Za-z0-9_-]"), "_").take(48).ifBlank { "rep" }

    private suspend fun downloadDashAndMux(
        downloadId: Long,
        fileName: String,
        videoUrl: String,
        audioUrl: String,
        referer: String,
        userAgent: String,
        sessionCookieManager: CookieManager?,
        allowGlobalCookieFallback: Boolean,
    ) {
        val tempDir = chunkDir(downloadId).apply { if (!exists()) mkdirs() }
        try {
            repository.updateDownloadProgress(downloadId, 0L, -1L, 0L, "DOWNLOADING", supportsResume = true)
            val vFile = File(tempDir, "dash_video.mp4")
            val aFile = File(tempDir, "dash_audio.m4a")

            downloadUrlToFile(videoUrl, vFile, referer, userAgent, sessionCookieManager, allowGlobalCookieFallback) { dl, total, spd ->
                repository.updateDownloadProgress(downloadId, dl, total, spd, "DOWNLOADING", supportsResume = true)
            }
            downloadUrlToFile(audioUrl, aFile, referer, userAgent, sessionCookieManager, allowGlobalCookieFallback) { dl, total, spd ->
                repository.updateDownloadProgress(downloadId, dl, total, spd, "DOWNLOADING", supportsResume = true)
            }

            val mp4FileName = fileName.substringBeforeLast('.') + ".mp4"
            val muxedFile = File(tempDir, "dash_muxed.mp4")
            val muxed = remuxToMp4(vFile, aFile, muxedFile)
            val sourceOut = if (muxed && muxedFile.exists() && muxedFile.length() > 0L) muxedFile else vFile

            val published = publishFromFile(sourceOut, mp4FileName, "video/mp4")
            val len = sourceOut.length()
            tempDir.deleteRecursively()
            completeDownload(downloadId, fileName, published, len, supportsResume = true)
        } catch (_: CancellationException) {
            if (pausedIds.remove(downloadId)) {
                repository.updateDownloadStatus(downloadId, "PAUSED")
            } else {
                tempDir.deleteRecursively()
                repository.updateDownloadStatus(downloadId, "CANCELLED")
            }
        } catch (e: Exception) {
            android.util.Log.w("SalviaDownloads", "Download $downloadId failed: ${e.javaClass.simpleName}: ${e.message?.take(200)}")
            // Partial video/audio files are kept; a retry resumes them with HTTP Range.
            repository.updateDownloadStatus(downloadId, "FAILED")
        } finally {
            // activeJobs is cleared by the central Job completion handler.
        }
    }

    private suspend fun downloadSingleHlsPlaylistToFile(
        downloadId: Long,
        playlistUrl: String,
        targetFile: File,
        referer: String,
        userAgent: String,
        sessionCookieManager: CookieManager?,
        allowGlobalCookieFallback: Boolean,
    ): Long {
        val staging = chunkDir(downloadId).apply { if (!exists()) mkdirs() }
        var mediaUrl = playlistUrl
        var text = withRetries {
            fetchText(mediaUrl, referer, userAgent, sessionCookieManager, allowGlobalCookieFallback)
                ?: throw IOException("Could not fetch HLS playlist")
        }
        if (text.contains("#EXT-X-STREAM-INF")) {
            mediaUrl = extractHighestVariantUrl(mediaUrl, text)
                ?: throw IOException("Could not resolve HLS master playlist")
            text = withRetries {
                fetchText(mediaUrl, referer, userAgent, sessionCookieManager, allowGlobalCookieFallback)
                    ?: throw IOException("Could not fetch selected HLS media playlist")
            }
        }
        val plan = ManifestSegmentPlanner.parseHls(mediaUrl, text)
        val keyCache = ConcurrentHashMap<String, ByteArray>()
        val prefix = targetFile.nameWithoutExtension + "_" + manifestFingerprint(text)
        val initFile = File(staging, prefix + "_hls_init.bin")

        suspend fun keyBytesFor(key: ManifestSegmentPlanner.HlsKey): ByteArray {
            if (key.method != "AES-128") throw NonRetryableDownloadException("Unsupported HLS key method")
            return keyCache[key.uri] ?: withRetries {
                fetchBytes(key.uri, referer, userAgent, sessionCookieManager, allowGlobalCookieFallback)
                    ?: throw IOException("Could not fetch HLS AES-128 key")
            }.also {
                if (it.size != 16) throw NonRetryableDownloadException("Invalid AES-128 HLS key length")
                keyCache[key.uri] = it
            }
        }

        suspend fun keyFor(segment: ManifestSegmentPlanner.HlsSegment): Pair<ByteArray, ByteArray>? {
            val key = segment.key ?: return null
            val keyBytes = keyBytesFor(key)
            val iv = if (key.ivHex.isNotBlank()) hexToBytes(key.ivHex) else sequenceIv(segment.sequence)
            return keyBytes to iv
        }

        val initUri = plan.initUri
        if (initUri != null && (!initFile.exists() || initFile.length() == 0L)) {
            // RFC 8216 §4.3.2.5: an encrypted EXT-X-MAP section must carry an explicit IV.
            val initKey = plan.initKey
            val initDecryption = if (initKey != null) {
                if (initKey.ivHex.isBlank()) throw NonRetryableDownloadException("Encrypted HLS init section without IV")
                keyBytesFor(initKey) to hexToBytes(initKey.ivHex)
            } else {
                null
            }
            fetchToFile(
                url = initUri,
                range = plan.initRange,
                target = initFile,
                referer = referer,
                userAgent = userAgent,
                sessionCookieManager = sessionCookieManager,
                allowGlobalCookieFallback = allowGlobalCookieFallback,
                aesKey = initDecryption?.first,
                aesIv = initDecryption?.second,
            )
        }

        val segmentFiles = plan.segments.map { File(staging, prefix + "_hls_seg_" + it.index + ".bin") }
        fun isSegmentDone(index: Int): Boolean {
            val segment = plan.segments[index]
            val out = segmentFiles[index]
            val expectedRangeLength = segment.range?.length
            return out.exists() && out.length() > 0L &&
                (expectedRangeLength == null || segment.key != null || out.length() == expectedRangeLength)
        }

        val alreadyDone = plan.segments.indices.filter(::isSegmentDone)
        val stagedBytes = (if (initFile.exists()) initFile.length() else 0L) +
            alreadyDone.sumOf { segmentFiles[it].length() }
        ensureStorageFor(estimateTotal(stagedBytes, alreadyDone.size, plan.segments.size), stagedBytes)

        val reporter = ProgressReporter(downloadId, stagedBytes, -1L, supportsResume = true)
        val completedSegments = java.util.concurrent.atomic.AtomicInteger(alreadyDone.size)
        val completedSegmentBytes = AtomicLong(alreadyDone.sumOf { segmentFiles[it].length() })
        val semaphore = Semaphore(DownloadFilePolicy.SEGMENT_CONCURRENCY)

        // Continuous worker pool: a slow segment no longer stalls a whole batch.
        coroutineScope {
            plan.segments.indices.filterNot(::isSegmentDone).map { index ->
                async(Dispatchers.IO) {
                    semaphore.withPermit {
                        val segment = plan.segments[index]
                        val out = segmentFiles[index]
                        out.delete()
                        val decryption = keyFor(segment)
                        val written = fetchToFile(
                            url = segment.uri,
                            range = segment.range,
                            target = out,
                            referer = referer,
                            userAgent = userAgent,
                            sessionCookieManager = sessionCookieManager,
                            allowGlobalCookieFallback = allowGlobalCookieFallback,
                            aesKey = decryption?.first,
                            aesIv = decryption?.second,
                            onBytes = { delta -> reporter.add(delta) },
                        )
                        if (written <= 0L) throw IOException("Empty HLS segment")
                        val doneCount = completedSegments.incrementAndGet()
                        val doneBytes = completedSegmentBytes.addAndGet(out.length())
                        reporter.totalBytes = estimateTotal(doneBytes, doneCount, plan.segments.size)
                            .coerceAtLeast(reporter.downloaded.get())
                    }
                }
            }.awaitAll()
        }
        reporter.flush()

        if (segmentFiles.any { !it.exists() || it.length() == 0L }) {
            throw IOException("HLS download is incomplete")
        }
        FileOutputStream(targetFile, false).use { output ->
            if (initFile.exists()) FileInputStream(initFile).use { it.copyTo(output) }
            val buffer = ByteArray(64 * 1024)
            segmentFiles.forEach { file ->
                FileInputStream(file).use { input ->
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                    }
                }
            }
        }
        return targetFile.length()
    }

    /** Extrapolates a manifest's total size from the segments finished so far. */
    private fun estimateTotal(doneBytes: Long, doneSegments: Int, totalSegments: Int): Long =
        if (doneSegments < 2 || totalSegments <= 0) -1L else doneBytes / doneSegments * totalSegments

    /** Small manifest side resources only (sidx indexes); never used for media segments. */
    private fun fetchHlsBytes(
        url: String,
        range: ManifestSegmentPlanner.ByteRange?,
        referer: String,
        userAgent: String,
        sessionCookieManager: CookieManager?,
        allowGlobalCookieFallback: Boolean,
    ): ByteArray {
        val builder = Request.Builder().url(url).header("User-Agent", userAgent)
        if (referer.isNotBlank()) builder.header("Referer", referer)
        if (range != null) builder.header("Range", "bytes=" + range.offset + "-" + (range.offset + range.length - 1L))
        executeWithProfileCookies(builder.build(), sessionCookieManager, allowGlobalCookieFallback).use { response ->
            validateSegmentResponse(response, range)
            val body = response.body ?: throw IOException("Empty HLS response")
            val bytes = readCapped(body, MAX_INDEX_BYTES)
            if (range != null && bytes.size.toLong() != range.length) throw IOException("Truncated HLS byte range")
            return bytes
        }
    }

    private fun validateSegmentResponse(response: okhttp3.Response, range: ManifestSegmentPlanner.ByteRange?) {
        if (range == null) {
            if (response.code != 200) {
                if (response.isSuccessful) {
                    throw NonRetryableDownloadException("Expected complete HLS/DASH response, got HTTP " + response.code)
                }
                throw HttpStatusException(response.code)
            }
        } else {
            if (response.code != 206) {
                if (response.isSuccessful) throw NonRetryableDownloadException("HLS byte-range request was not honored")
                throw HttpStatusException(response.code)
            }
            val expected = "bytes " + range.offset + "-" + (range.offset + range.length - 1L)
            if (!response.header("Content-Range").orEmpty().startsWith(expected)) {
                throw NonRetryableDownloadException("Unexpected HLS Content-Range")
            }
        }
    }

    /**
     * Streams one segment/resource (optionally a byte range) to [target] through a temp file
     * that is atomically renamed. AES-128 segments are decrypted on the fly, so no segment is
     * ever held in memory as a whole. Transient failures are retried with backoff.
     */
    private suspend fun fetchToFile(
        url: String,
        range: ManifestSegmentPlanner.ByteRange?,
        target: File,
        referer: String,
        userAgent: String,
        sessionCookieManager: CookieManager?,
        allowGlobalCookieFallback: Boolean,
        aesKey: ByteArray? = null,
        aesIv: ByteArray? = null,
        onBytes: suspend (Long) -> Unit = {},
    ): Long = withRetries {
        val tmp = File(target.parentFile, target.name + ".tmp")
        tmp.delete()
        val builder = Request.Builder().url(url).header("User-Agent", userAgent)
        if (referer.isNotBlank()) builder.header("Referer", referer)
        if (range != null) builder.header("Range", "bytes=" + range.offset + "-" + (range.offset + range.length - 1L))
        var received = 0L
        try {
            executeWithProfileCookies(builder.build(), sessionCookieManager, allowGlobalCookieFallback).use { response ->
                validateSegmentResponse(response, range)
                val body = response.body ?: throw IOException("Empty segment response")
                val cipher = if (aesKey != null && aesIv != null) {
                    Cipher.getInstance("AES/CBC/PKCS5Padding").apply {
                        init(Cipher.DECRYPT_MODE, SecretKeySpec(aesKey, "AES"), IvParameterSpec(aesIv))
                    }
                } else {
                    null
                }
                FileOutputStream(tmp).use { output ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            if (cipher != null) {
                                cipher.update(buffer, 0, n)?.let { output.write(it) }
                            } else {
                                output.write(buffer, 0, n)
                            }
                            received += n
                            onBytes(n.toLong())
                            currentCoroutineContext().ensureActive()
                        }
                    }
                    if (cipher != null) {
                        val tail = try {
                            cipher.doFinal()
                        } catch (e: java.security.GeneralSecurityException) {
                            throw IOException("HLS AES-128 decryption failed; refusing to save encrypted bytes", e)
                        }
                        output.write(tail)
                    }
                }
            }
            if (range != null && received != range.length) throw IOException("Truncated segment byte range")
            if (!tmp.renameTo(target)) {
                tmp.delete()
                throw IOException("Could not atomically finalize segment")
            }
            received
        } catch (e: Throwable) {
            tmp.delete()
            if (received > 0L) runCatching { onBytes(-received) }
            throw e
        }
    }

    /**
     * Remuxes downloaded video (and optional separate audio) into a clean, universally playable MP4
     * using Android's native MediaExtractor + MediaMuxer without re-encoding.
     */
    private fun remuxToMp4(videoInput: File, audioInput: File?, outputMp4: File): Boolean {
        var muxer: MediaMuxer? = null
        val videoExtractor = MediaExtractor()
        val audioExtractor = if (audioInput != null && audioInput.exists()) MediaExtractor() else null
        return try {
            videoExtractor.setDataSource(videoInput.absolutePath)
            muxer = MediaMuxer(outputMp4.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            val trackMap = mutableListOf<Triple<MediaExtractor, Int, Int>>() // (extractor, srcTrack, dstTrack)
            var maxBufSize = 512 * 1024

            for (i in 0 until videoExtractor.trackCount) {
                val format = videoExtractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("video/") || (audioExtractor == null && mime.startsWith("audio/"))) {
                    videoExtractor.selectTrack(i)
                    val dstTrack = muxer.addTrack(format)
                    trackMap.add(Triple(videoExtractor, i, dstTrack))
                    if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                        maxBufSize = maxOf(maxBufSize, format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE))
                    }
                }
            }

            if (audioExtractor != null && audioInput != null) {
                audioExtractor.setDataSource(audioInput.absolutePath)
                for (i in 0 until audioExtractor.trackCount) {
                    val format = audioExtractor.getTrackFormat(i)
                    val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                    if (mime.startsWith("audio/")) {
                        audioExtractor.selectTrack(i)
                        val dstTrack = muxer.addTrack(format)
                        trackMap.add(Triple(audioExtractor, i, dstTrack))
                        if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                            maxBufSize = maxOf(maxBufSize, format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE))
                        }
                    }
                }
            }

            if (trackMap.isEmpty()) return false
            muxer.start()
            val buffer = ByteBuffer.allocate(maxBufSize)
            val bufferInfo = MediaCodec.BufferInfo()

            // Write video & audio samples interleaved by presentation timestamp for universal playback
            val videoDstBySrc = trackMap.filter { it.first === videoExtractor }.associate { it.second to it.third }
            if (audioExtractor != null) {
                val audioDstBySrc = trackMap.filter { it.first === audioExtractor }.associate { it.second to it.third }
                while (true) {
                    val vTrack = videoExtractor.sampleTrackIndex
                    val aTrack = audioExtractor.sampleTrackIndex
                    if (vTrack < 0 && aTrack < 0) break

                    val useVideo = when {
                        vTrack < 0 -> false
                        aTrack < 0 -> true
                        else -> videoExtractor.sampleTime <= audioExtractor.sampleTime
                    }
                    val activeExtractor = if (useVideo) videoExtractor else audioExtractor
                    val activeTrack = if (useVideo) vTrack else aTrack
                    val dstTrack = if (useVideo) videoDstBySrc[activeTrack] else audioDstBySrc[activeTrack]

                    if (dstTrack == null) {
                        activeExtractor.advance()
                        continue
                    }
                    buffer.clear()
                    val size = activeExtractor.readSampleData(buffer, 0)
                    if (size < 0) {
                        activeExtractor.advance()
                        continue
                    }
                    bufferInfo.offset = 0
                    bufferInfo.size = size
                    bufferInfo.presentationTimeUs = activeExtractor.sampleTime.coerceAtLeast(0L)
                    @Suppress("WrongConstant")
                    bufferInfo.flags = activeExtractor.sampleFlags
                    muxer.writeSampleData(dstTrack, buffer, bufferInfo)
                    activeExtractor.advance()
                }
            } else {
                pumpExtractorToMuxer(videoExtractor, videoDstBySrc, muxer, buffer, bufferInfo)
            }

            muxer.stop()
            true
        } catch (_: Exception) {
            false
        } finally {
            runCatching { muxer?.release() }
            runCatching { videoExtractor.release() }
            runCatching { audioExtractor?.release() }
        }
    }

    private fun pumpExtractorToMuxer(
        extractor: MediaExtractor,
        dstBySrc: Map<Int, Int>,
        muxer: MediaMuxer,
        buffer: ByteBuffer,
        info: MediaCodec.BufferInfo,
    ) {
        while (true) {
            val sampleTrack = extractor.sampleTrackIndex
            if (sampleTrack < 0) break
            val dstTrack = dstBySrc[sampleTrack]
            if (dstTrack == null) {
                extractor.advance()
                continue
            }
            buffer.clear()
            val size = extractor.readSampleData(buffer, 0)
            if (size < 0) break
            info.offset = 0
            info.size = size
            info.presentationTimeUs = extractor.sampleTime.coerceAtLeast(0L)
            @Suppress("WrongConstant")
            info.flags = extractor.sampleFlags
            muxer.writeSampleData(dstTrack, buffer, info)
            extractor.advance()
        }
    }

    private fun probeRangeSupport(
        url: String,
        referer: String,
        userAgent: String,
        sessionCookieManager: CookieManager?,
        allowGlobalCookieFallback: Boolean,
    ): Pair<Long, Boolean> {
        return runCatching {
            val req = Request.Builder()
                .url(url)
                .get()
                .header("User-Agent", userAgent)
                .header("Range", "bytes=0-0")
                .apply { if (referer.isNotBlank()) header("Referer", referer) }
                .build()
            executeWithProfileCookies(req, sessionCookieManager, allowGlobalCookieFallback).use { resp ->
                if (resp.code != 206) return@use -1L to false
                val match = Regex("^bytes\\s+0-0/(\\d+)$", RegexOption.IGNORE_CASE)
                    .matchEntire(resp.header("Content-Range").orEmpty()) ?: return@use -1L to false
                val total = match.groupValues[1].toLongOrNull() ?: return@use -1L to false
                val bodySize = resp.body?.let { readCapped(it, 1024L).size } ?: return@use -1L to false
                if (bodySize != 1) return@use -1L to false
                total to (total > 0L)
            }
        }.getOrDefault(-1L to false)
    }

    // --- Transfer infrastructure -------------------------------------------------------------

    /** Throttled, thread-safe progress publisher shared by every parallel connection. */
    private inner class ProgressReporter(
        private val downloadId: Long,
        initialBytes: Long,
        @Volatile var totalBytes: Long,
        private val supportsResume: Boolean,
    ) {
        val downloaded = AtomicLong(initialBytes)
        private val lastReportAt = AtomicLong(System.currentTimeMillis())
        private val lastReportBytes = AtomicLong(initialBytes)

        @Volatile
        private var smoothedSpeed = 0L

        fun reset(bytes: Long) {
            downloaded.set(bytes)
            lastReportBytes.set(bytes)
        }

        suspend fun add(delta: Long) {
            val current = downloaded.addAndGet(delta)
            val now = System.currentTimeMillis()
            val last = lastReportAt.get()
            if (now - last < PROGRESS_INTERVAL_MS || !lastReportAt.compareAndSet(last, now)) return
            val previous = lastReportBytes.getAndSet(current)
            val instant = DownloadFilePolicy.speedBytesPerSecond(current - previous, now - last)
            // Exponential smoothing keeps the displayed speed and ETA from jumping around.
            smoothedSpeed = if (smoothedSpeed == 0L) instant else (smoothedSpeed * 6L + instant * 4L) / 10L
            publish(current)
        }

        suspend fun flush() = publish(downloaded.get())

        private suspend fun publish(current: Long) {
            repository.updateDownloadProgress(
                id = downloadId,
                downloaded = current.coerceAtLeast(0L),
                total = totalBytes,
                speedBytesPerSec = smoothedSpeed.coerceAtLeast(0L),
                status = "DOWNLOADING",
                supportsResume = supportsResume,
            )
        }
    }

    /** Retries transient failures with exponential backoff and jitter; never retries cancellation. */
    private suspend fun <T> withRetries(block: suspend (attempt: Int) -> T): T {
        var attempt = 0
        while (true) {
            try {
                return block(attempt)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                attempt++
                if (attempt >= DownloadFilePolicy.MAX_ATTEMPTS || !DownloadFilePolicy.isRetryable(e)) throw e
                delay(DownloadFilePolicy.retryDelayMs(attempt - 1, Random.nextLong(0L, 400L)))
            }
        }
    }

    private suspend fun copyWithProgress(
        input: java.io.InputStream,
        output: OutputStream,
        reporter: ProgressReporter,
    ) {
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val count = input.read(buffer)
            if (count == -1) break
            output.write(buffer, 0, count)
            reporter.add(count.toLong())
            // Pause/cancel takes effect immediately instead of after the whole stream.
            currentCoroutineContext().ensureActive()
        }
    }

    /** Fails fast, before any transfer, when the device cannot hold the finished download. */
    private fun ensureStorageFor(totalBytes: Long, alreadyStaged: Long) {
        val available = runCatching { StatFs(context.filesDir.absolutePath).availableBytes }.getOrDefault(-1L)
        if (!DownloadFilePolicy.hasEnoughSpace(available, totalBytes, alreadyStaged)) {
            throw InsufficientStorageException(
                DownloadFilePolicy.requiredBytesFor(totalBytes, alreadyStaged),
                available,
            )
        }
    }

    private fun readCapped(body: okhttp3.ResponseBody, maxBytes: Long): ByteArray {
        val declared = body.contentLength()
        if (declared > maxBytes) throw NonRetryableDownloadException("Response exceeds $maxBytes bytes")
        body.byteStream().use { input ->
            val out = java.io.ByteArrayOutputStream(if (declared in 1..maxBytes) declared.toInt() else 8 * 1024)
            val buffer = ByteArray(16 * 1024)
            var total = 0L
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                total += n
                if (total > maxBytes) throw NonRetryableDownloadException("Response exceeds $maxBytes bytes")
                out.write(buffer, 0, n)
            }
            return out.toByteArray()
        }
    }

    private data class PublishedFile(val uri: String, val displayName: String)

    /**
     * Publishes a finished file. On Android 10+ it is written to the shared Downloads
     * collection with IS_PENDING so other apps never see a partial file, and a failed write
     * removes the pending row. Older versions write to app-specific storage with a unique
     * name and expose it through FileProvider (never a file:// URI).
     */
    private fun publishFile(fileName: String, mimeType: String, writer: (OutputStream) -> Unit): PublishedFile {
        val resolvedMime = DownloadFilePolicy.outputMimeType(mimeType, fileName).let {
            if (it == "*/*") "application/octet-stream" else it
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, resolvedMime)
                put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$DOWNLOAD_FOLDER")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = runCatching { resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) }.getOrNull()
            if (uri != null) {
                try {
                    val stream = resolver.openOutputStream(uri) ?: throw IOException("Cannot open MediaStore output")
                    stream.use(writer)
                    resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
                    val finalName = runCatching {
                        resolver.query(uri, arrayOf(MediaStore.Downloads.DISPLAY_NAME), null, null, null)?.use { cursor ->
                            if (cursor.moveToFirst()) cursor.getString(0) else null
                        }
                    }.getOrNull() ?: fileName
                    return PublishedFile(uri.toString(), finalName)
                } catch (e: Exception) {
                    runCatching { resolver.delete(uri, null, null) }
                    if (e is CancellationException) throw e
                    // Fall back to app-specific storage below.
                }
            }
        }
        val root = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: File(context.filesDir, "downloads")
        val dir = File(root, DOWNLOAD_FOLDER).apply { if (!exists()) mkdirs() }
        val finalName = DownloadFilePolicy.uniqueFileName(fileName) { File(dir, it).exists() }
        val file = File(dir, finalName)
        try {
            FileOutputStream(file).use(writer)
        } catch (e: Exception) {
            file.delete()
            throw e
        }
        return PublishedFile(DownloadFileAccess.shareableUriForFile(context, file).toString(), finalName)
    }

    private fun publishFromFile(source: File, fileName: String, mimeType: String): PublishedFile =
        publishFile(fileName, mimeType) { output ->
            FileInputStream(source).use { input -> input.copyTo(output, 64 * 1024) }
        }

    private suspend fun completeDownload(
        downloadId: Long,
        requestedName: String,
        published: PublishedFile,
        bytes: Long,
        supportsResume: Boolean,
    ) {
        if (published.displayName.isNotBlank() && published.displayName != requestedName) {
            repository.updateDownloadFileName(downloadId, published.displayName)
        }
        repository.updateDownloadProgress(
            id = downloadId,
            downloaded = bytes,
            total = bytes,
            speedBytesPerSec = 0L,
            status = "COMPLETED",
            localUri = published.uri,
            supportsResume = supportsResume,
        )
    }

    private suspend fun downloadUrlToFile(
        url: String,
        file: File,
        referer: String,
        userAgent: String,
        sessionCookieManager: CookieManager?,
        allowGlobalCookieFallback: Boolean,
        onProgress: suspend (Long, Long, Long) -> Unit,
    ) {
        withRetries {
            val existing = if (file.exists()) file.length() else 0L
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .apply {
                    if (referer.isNotBlank()) header("Referer", referer)
                    if (existing > 0L) header("Range", "bytes=$existing-")
                }
                .build()
            executeWithProfileCookies(req, sessionCookieManager, allowGlobalCookieFallback).use { resp ->
                if (existing > 0L && resp.code == 416) {
                    // Already complete when the server reports exactly the staged size.
                    val total = resp.header("Content-Range").orEmpty().substringAfter('/', "").toLongOrNull()
                    if (total != null && total == existing) return@withRetries
                    file.delete()
                    throw IOException("Stale partial file discarded")
                }
                val append = existing > 0L && resp.code == 206 &&
                    resp.header("Content-Range").orEmpty().startsWith("bytes $existing-", ignoreCase = true)
                if (!append && !resp.isSuccessful) throw HttpStatusException(resp.code)
                if (!append && resp.code == 206) throw NonRetryableDownloadException("Unexpected partial response")
                val body = resp.body ?: throw IOException("Empty response body")
                val contentLength = body.contentLength()
                val start = if (append) existing else 0L
                val total = if (contentLength > 0L) start + contentLength else -1L
                if (!append) ensureStorageFor(total, 0L)
                var dl = start
                var lastTime = System.currentTimeMillis()
                var lastBytes = dl
                FileOutputStream(file, append).use { out ->
                    val buf = ByteArray(64 * 1024)
                    body.byteStream().use { input ->
                        while (true) {
                            val r = input.read(buf)
                            if (r == -1) break
                            out.write(buf, 0, r)
                            dl += r
                            val now = System.currentTimeMillis()
                            val dt = now - lastTime
                            if (dt >= PROGRESS_INTERVAL_MS) {
                                val spd = DownloadFilePolicy.speedBytesPerSecond(dl - lastBytes, dt)
                                lastTime = now
                                lastBytes = dl
                                onProgress(dl, total, spd)
                            }
                            currentCoroutineContext().ensureActive()
                        }
                    }
                }
                if (total > 0L && file.length() != total) {
                    throw IOException("Incomplete transfer: ${file.length()} of $total bytes")
                }
            }
        }
    }

    private fun sequenceIv(seq: Long): ByteArray {
        val iv = ByteArray(16)
        var v = seq
        for (i in 15 downTo 8) {
            iv[i] = (v and 0xFF).toByte()
            v = v ushr 8
        }
        return iv
    }

    private fun hexToBytes(hex: String): ByteArray {
        val padded = hex.padStart(32, '0').takeLast(32)
        return ByteArray(16) { i ->
            padded.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }

    private fun fetchText(
        url: String,
        referer: String,
        userAgent: String,
        sessionCookieManager: CookieManager?,
        allowGlobalCookieFallback: Boolean,
    ): String? {
        return runCatching {
            val cookie = resolveCookieHeader(url, sessionCookieManager, allowGlobalCookieFallback)
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .apply {
                    if (referer.isNotBlank()) header("Referer", referer)
                    if (cookie.isNotBlank()) header("Cookie", cookie)
                }
                .build()
            executeWithProfileCookies(req, sessionCookieManager, allowGlobalCookieFallback).use { resp ->
                if (resp.isSuccessful) {
                    resp.body?.let { body ->
                        val charset = body.contentType()?.charset(Charsets.UTF_8) ?: Charsets.UTF_8
                        String(readCapped(body, MAX_MANIFEST_BYTES), charset)
                    }
                } else {
                    null
                }
            }
        }.getOrNull()
    }

    private fun fetchBytes(
        url: String,
        referer: String,
        userAgent: String,
        sessionCookieManager: CookieManager?,
        allowGlobalCookieFallback: Boolean,
    ): ByteArray? {
        return runCatching {
            val cookie = resolveCookieHeader(url, sessionCookieManager, allowGlobalCookieFallback)
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .apply {
                    if (referer.isNotBlank()) header("Referer", referer)
                    if (cookie.isNotBlank()) header("Cookie", cookie)
                }
                .build()
            executeWithProfileCookies(req, sessionCookieManager, allowGlobalCookieFallback).use { resp ->
                if (resp.isSuccessful) resp.body?.let { readCapped(it, MAX_KEY_BYTES) } else null
            }
        }.getOrNull()
    }

    /**
     * Picks the best HLS variant: highest resolution first, then highest bandwidth, so a
     * high-bitrate low-resolution rendition never wins over real HD.
     */
    private fun extractHighestVariantUrl(masterUrl: String, text: String): String? {
        val base = Uri.parse(masterUrl)
        val lines = text.lines().map { it.trim() }
        var bestHeight = -1
        var bestBw = -1L
        var bestUrl: String? = null
        var i = 0
        while (i < lines.size) {
            if (lines[i].startsWith("#EXT-X-STREAM-INF:")) {
                val attrs = lines[i]
                val bw = Regex("(?<![A-Z-])BANDWIDTH=(\\d+)").find(attrs)?.groupValues?.getOrNull(1)?.toLongOrNull() ?: 0L
                val height = Regex("RESOLUTION=\\d+x(\\d+)").find(attrs)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
                var j = i + 1
                while (j < lines.size && (lines[j].isBlank() || lines[j].startsWith("#"))) j++
                if (j < lines.size && (height > bestHeight || (height == bestHeight && bw >= bestBw))) {
                    bestHeight = height
                    bestBw = bw
                    bestUrl = resolveUrl(base, lines[j])
                }
                i = j
            } else {
                i++
            }
        }
        return bestUrl
    }

    private fun resolveUrl(base: Uri, relative: String): String {
        if (relative.startsWith("http://") || relative.startsWith("https://")) return relative
        return runCatching { java.net.URI(base.toString()).resolve(relative).toString() }.getOrDefault(relative)
    }

    /**
     * Resolve cookies from the selected browser profile for every redirect hop.
     * OkHttp does not consistently strip a manually supplied Cookie header on
     * cross-origin redirects, so redirects are followed explicitly here.
     */
    private fun executeWithProfileCookies(
        initialRequest: Request,
        sessionCookieManager: CookieManager?,
        allowGlobalCookieFallback: Boolean,
    ): okhttp3.Response {
        var request = initialRequest.newBuilder().removeHeader("Cookie").build()
        val redirectCodes = setOf(300, 301, 302, 303, 307, 308)

        var previouslyInjectedHeaderNames = emptySet<String>()
        repeat(10) { redirectCount ->
            val cookie = resolveCookieHeader(
                request.url.toString(),
                sessionCookieManager,
                allowGlobalCookieFallback,
            )
            val requestWithCookies = request.newBuilder()
                .removeHeader("Cookie")
                .apply { if (cookie.isNotBlank()) header("Cookie", cookie) }
                .build()
            val execution = MediaRequestHeaderExecution.execute(
                client = client,
                baseRequest = requestWithCookies,
                contextsByOrigin = MediaRequestHeaderContextElement.currentContexts(),
                previouslyInjectedHeaderNames = previouslyInjectedHeaderNames,
            )
            val response = execution.response
            val location = response.header("Location")
            if (response.code !in redirectCodes || location.isNullOrBlank()) return response

            val target = response.request.url.resolve(location)
            if (target == null || (target.scheme != "http" && target.scheme != "https")) return response

            val current = response.request.url
            if (current.isHttps && !target.isHttps) return response

            if (redirectCount == 9) {
                response.close()
                throw ProtocolException("Too many redirects")
            }

            val next = MediaRequestRedirectPolicy.nextRequest(
                response = response,
                target = target,
                resolveCookiePerHop = true,
            ) ?: return response

            val sameOrigin = MediaRequestHeaderPolicy.originForUrl(response.request.url.toString()) ==
                MediaRequestHeaderPolicy.originForUrl(target.toString())
            previouslyInjectedHeaderNames = if (sameOrigin) execution.injectedHeaderNames else emptySet()
            response.close()
            request = next
        }

        throw ProtocolException("Redirect limit exceeded")
    }

    private fun resolveCookieHeader(
        url: String,
        sessionCookieManager: CookieManager?,
        allowGlobalFallback: Boolean,
    ): String {
        val manager = sessionCookieManager ?: if (allowGlobalFallback) CookieManager.getInstance() else return ""
        return runCatching { manager.getCookie(url).orEmpty() }.getOrDefault("")
    }

    private fun chunkDir(downloadId: Long): File =
        File(context.filesDir, "salvia_download_staging/$downloadId")

    private fun sanitizeFileName(rawName: String, quality: MediaQualityOption): String {
        val ext = when {
            quality.isHls || quality.isDash ->
                if (quality.mimeType.startsWith("audio/") || quality.resolutionHeight == 0 &&
                    quality.label.contains("audio", ignoreCase = true)
                ) "m4a" else "mp4"
            quality.format.equals("WEBM", true) -> "webm"
            quality.format.equals("M4A", true) -> "m4a"
            quality.format.equals("MP3", true) -> "mp3"
            quality.format.equals("MP4", true) || quality.mimeType.startsWith("video/") -> "mp4"
            quality.format.isNotBlank() && quality.format.length <= 5 &&
                quality.format.all { it.isLetterOrDigit() } -> quality.format.lowercase()
            else -> "mp4"
        }
        return DownloadFilePolicy.sanitizeFileName(
            rawName = rawName,
            defaultExtension = ext,
            fallbackBase = "salvia_video_${System.currentTimeMillis()}",
        )
    }

    private companion object {
        const val DOWNLOAD_FOLDER = DownloadFileAccess.DOWNLOAD_FOLDER
        const val PROGRESS_INTERVAL_MS = 500L
        const val MAX_MANIFEST_BYTES = 16L * 1024L * 1024L
        const val MAX_INDEX_BYTES = 16L * 1024L * 1024L
        const val MAX_KEY_BYTES = 4L * 1024L
    }
}
