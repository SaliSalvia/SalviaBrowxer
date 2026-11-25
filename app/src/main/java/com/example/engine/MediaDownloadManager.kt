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
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
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
    ): Job = scope.launch(Dispatchers.IO) {
        executePersistedDownload(
            downloadId = downloadId,
            userAgent = userAgent,
            sessionCookieManager = sessionCookieManager,
            allowGlobalCookieFallback = allowGlobalCookieFallback,
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
    ): DownloadEntity? {
        val download = repository.getDownloadById(downloadId) ?: return null
        if (download.status in setOf("COMPLETED", "CANCELLED", "FAILED")) return download

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
    ): Job = synchronized(activeJobs) {
        activeJobs[downloadId]?.takeIf { it.isActive }?.let { return@synchronized it }

        val job = scope.launch(context = Dispatchers.IO, start = CoroutineStart.LAZY) {
            when {
                isHls -> downloadHlsStream(downloadId, fileName, url, audioUrl, referer, userAgent, sessionCookieManager, allowGlobalCookieFallback)
                isDash && manifestRepresentationId.isNotBlank() -> downloadDashManifestAndMux(downloadId, fileName, url, manifestRepresentationId, manifestAudioRepresentationId, referer, userAgent, sessionCookieManager, allowGlobalCookieFallback)
                isDash && audioUrl.isNotBlank() -> downloadDashAndMux(downloadId, fileName, url, audioUrl, referer, userAgent, sessionCookieManager, allowGlobalCookieFallback)
                else -> downloadDirectMultiPart(downloadId, fileName, url, referer, userAgent, mimeType, sessionCookieManager, allowGlobalCookieFallback)
            }
        }
        activeJobs[downloadId] = job
        job.invokeOnCompletion { activeJobs.remove(downloadId, job) }
        job.start()
        job
    }

    /**
     * Multi-threaded 4-part Range downloader with persistent chunk resume & real-time speed.
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
            val supportsRanges = rangeProbe.second

            if (supportsRanges && totalBytes >= 1_500_000L) {
                val threadCount = 4
                val partSize = totalBytes / threadCount
                val downloadedCounter = AtomicLong(0L)
                val partFiles = (0 until threadCount).map { index ->
                    File(tempDir, "part_$index.bin")
                }

                partFiles.forEach { part ->
                    if (part.exists()) {
                        downloadedCounter.addAndGet(part.length())
                    }
                }

                coroutineScope {
                    val tasks = (0 until threadCount).map { index ->
                        async(Dispatchers.IO) {
                            val rangeStart = index * partSize
                            val rangeEnd = if (index == threadCount - 1) {
                                totalBytes - 1L
                            } else {
                                ((index + 1) * partSize) - 1L
                            }
                            val partFile = partFiles[index]
                            val expectedPartLength = rangeEnd - rangeStart + 1L
                            var existingBytes = if (partFile.exists()) partFile.length() else 0L

                            if (existingBytes > expectedPartLength) {
                                downloadedCounter.addAndGet(-existingBytes)
                                partFile.delete()
                                existingBytes = 0L
                            }
                            if (existingBytes == expectedPartLength) {
                                return@async
                            }

                            val requestStart = rangeStart + existingBytes
                            val request = Request.Builder()
                                .url(url)
                                .header("User-Agent", userAgent)
                                .header("Range", "bytes=$requestStart-$rangeEnd")
                                .apply {
                                    if (referer.isNotBlank()) header("Referer", referer)
                                }
                                .build()

                            executeWithProfileCookies(
                                request,
                                sessionCookieManager,
                                allowGlobalCookieFallback,
                            ).use { response ->
                                if (response.code != 206) {
                                    throw IOException("Range request was not honored (HTTP ${response.code})")
                                }

                                val rangeMatch = Regex(
                                    "^bytes\\s+(\\d+)-(\\d+)/(\\d+)$",
                                    RegexOption.IGNORE_CASE,
                                ).matchEntire(response.header("Content-Range").orEmpty())
                                    ?: throw IOException("Missing or invalid Content-Range")

                                val actualStart = rangeMatch.groupValues[1].toLong()
                                val actualEnd = rangeMatch.groupValues[2].toLong()
                                val actualTotal = rangeMatch.groupValues[3].toLong()
                                if (actualStart != requestStart ||
                                    actualEnd != rangeEnd ||
                                    actualTotal != totalBytes
                                ) {
                                    throw IOException("Unexpected Content-Range")
                                }

                                val input = response.body?.byteStream()
                                    ?: throw IOException("Empty range response body")
                                var receivedBytes = 0L
                                var lastReportAt = System.currentTimeMillis()
                                var lastReportBytes = downloadedCounter.get()

                                FileOutputStream(partFile, existingBytes > 0L).use { output ->
                                    val buffer = ByteArray(16 * 1024)
                                    while (true) {
                                        val count = input.read(buffer)
                                        if (count == -1) break
                                        output.write(buffer, 0, count)
                                        receivedBytes += count.toLong()
                                        val overallDownloaded = downloadedCounter.addAndGet(count.toLong())
                                        val now = System.currentTimeMillis()
                                        val elapsed = now - lastReportAt

                                        if (elapsed >= 500L) {
                                            val speed = ((overallDownloaded - lastReportBytes) * 1000L) /
                                                elapsed.coerceAtLeast(1L)
                                            repository.updateDownloadProgress(
                                                id = downloadId,
                                                downloaded = overallDownloaded,
                                                total = totalBytes,
                                                speedBytesPerSec = speed.coerceAtLeast(0L),
                                                status = "DOWNLOADING",
                                                supportsResume = true,
                                            )
                                            lastReportAt = now
                                            lastReportBytes = overallDownloaded
                                        }
                                    }
                                }

                                val expectedResponseBytes = rangeEnd - requestStart + 1L
                                if (receivedBytes != expectedResponseBytes ||
                                    partFile.length() != expectedPartLength
                                ) {
                                    throw IOException(
                                        "Incomplete range part: ${partFile.length()} of ${expectedPartLength} bytes",
                                    )
                                }
                            }
                        }
                    }
                    tasks.awaitAll()
                }

                val assembledBytes = partFiles.sumOf { part ->
                    if (part.exists()) part.length() else 0L
                }
                if (assembledBytes != totalBytes) {
                    throw IOException("Incomplete download: ${assembledBytes} of ${totalBytes} bytes")
                }

                val (destination, destinationUri) = openOutputDestination(fileName, mimeType)
                destination.use { output ->
                    val buffer = ByteArray(32 * 1024)
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
                repository.updateDownloadProgress(
                    id = downloadId,
                    downloaded = totalBytes,
                    total = totalBytes,
                    speedBytesPerSec = 0L,
                    status = "COMPLETED",
                    localUri = destinationUri,
                    supportsResume = true,
                )
                return
            }

            // Single stream fallback. Resume is attempted only when the server
            // advertised byte ranges and an existing, verified staging file exists.
            val singleFile = File(tempDir, "single.bin")
            val existingBytes = if (supportsRanges && singleFile.exists()) singleFile.length() else 0L
            if (!supportsRanges && singleFile.exists()) singleFile.delete()

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .apply {
                    if (referer.isNotBlank()) header("Referer", referer)
                    if (existingBytes > 0L) header("Range", "bytes=$existingBytes-")
                }
                .build()

            executeWithProfileCookies(
                request,
                sessionCookieManager,
                allowGlobalCookieFallback,
            ).use { response ->
                val validResponse = if (existingBytes > 0L) {
                    response.code == 206
                } else {
                    response.isSuccessful
                }
                if (!validResponse) {
                    throw IOException("Download request failed (HTTP ${response.code})")
                }

                if (existingBytes > 0L) {
                    val contentRange = response.header("Content-Range").orEmpty()
                    if (!contentRange.startsWith("bytes $existingBytes-", ignoreCase = true)) {
                        throw IOException("Unexpected resume Content-Range")
                    }
                }

                val body = response.body ?: throw IOException("Empty download response body")
                val responseLength = body.contentLength()
                val expectedTotal = if (totalBytes > 0L) {
                    totalBytes
                } else if (responseLength > 0L) {
                    existingBytes + responseLength
                } else {
                    -1L
                }

                var downloaded = existingBytes
                var lastReportAt = System.currentTimeMillis()
                var lastReportBytes = downloaded

                FileOutputStream(singleFile, existingBytes > 0L).use { output ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(16 * 1024)
                        while (true) {
                            val count = input.read(buffer)
                            if (count == -1) break
                            output.write(buffer, 0, count)
                            downloaded += count.toLong()

                            val now = System.currentTimeMillis()
                            val elapsed = now - lastReportAt
                            if (elapsed >= 450L) {
                                val speed = ((downloaded - lastReportBytes) * 1000L) /
                                    elapsed.coerceAtLeast(1L)
                                repository.updateDownloadProgress(
                                    id = downloadId,
                                    downloaded = downloaded,
                                    total = expectedTotal,
                                    speedBytesPerSec = speed.coerceAtLeast(0L),
                                    status = "DOWNLOADING",
                                    supportsResume = supportsRanges,
                                )
                                lastReportAt = now
                                lastReportBytes = downloaded
                            }
                        }
                    }
                }

                if (expectedTotal > 0L && singleFile.length() != expectedTotal) {
                    throw IOException(
                        "Incomplete download: ${singleFile.length()} of ${expectedTotal} bytes",
                    )
                }

                val (destination, destinationUri) = openOutputDestination(fileName, mimeType)
                destination.use { output ->
                    FileInputStream(singleFile).use { input -> input.copyTo(output) }
                }
                val completedBytes = singleFile.length()
                tempDir.deleteRecursively()
                repository.updateDownloadProgress(
                    id = downloadId,
                    downloaded = completedBytes,
                    total = completedBytes,
                    speedBytesPerSec = 0L,
                    status = "COMPLETED",
                    localUri = destinationUri,
                    supportsResume = supportsRanges,
                )
            }
        } catch (_: CancellationException) {
            if (pausedIds.remove(downloadId)) {
                repository.updateDownloadStatus(downloadId, "PAUSED")
            } else {
                tempDir.deleteRecursively()
                repository.updateDownloadStatus(downloadId, "CANCELLED")
            }
        } catch (_: Exception) {
            repository.updateDownloadStatus(downloadId, "FAILED")
        } finally {
            // activeJobs is cleared by the central Job completion handler.
        }
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

            val (finalStream, finalUri) = if (remuxSuccess && remuxedTempMp4.exists() && remuxedTempMp4.length() > 0L) {
                val dest = openOutputDestination(mp4FileName, "video/mp4")
                dest.first.use { out -> FileInputStream(remuxedTempMp4).use { it.copyTo(out) } }
                dest
            } else {
                val fallbackName = fileName.substringBeforeLast('.') + ".ts"
                val dest = openOutputDestination(fallbackName, "video/mp2t")
                dest.first.use { out -> FileInputStream(rawVideoFile).use { it.copyTo(out) } }
                dest
            }
            val finalSize = if (remuxSuccess && remuxedTempMp4.exists()) remuxedTempMp4.length() else totalWritten
            tempDir.deleteRecursively()
            repository.updateDownloadProgress(downloadId, finalSize, finalSize, 0L, "COMPLETED", finalUri, true)
        } catch (_: CancellationException) {
            if (pausedIds.remove(downloadId)) {
                repository.updateDownloadStatus(downloadId, "PAUSED")
            } else {
                tempDir.deleteRecursively()
                repository.updateDownloadStatus(downloadId, "CANCELLED")
            }
        } catch (_: Exception) {
            tempDir.deleteRecursively()
            repository.updateDownloadStatus(downloadId, "FAILED")
        } finally {
            // activeJobs is cleared by the central Job completion handler.
        }
    }

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
            repository.updateDownloadProgress(downloadId, 0L, -1L, 0L, "DOWNLOADING", supportsResume = false)
            val vFile = File(tempDir, "dash_video.mp4")
            val aFile = File(tempDir, "dash_audio.m4a")

            downloadUrlToFile(videoUrl, vFile, referer, userAgent, sessionCookieManager, allowGlobalCookieFallback) { dl, total, spd ->
                repository.updateDownloadProgress(downloadId, dl, total, spd, "DOWNLOADING")
            }
            downloadUrlToFile(audioUrl, aFile, referer, userAgent, sessionCookieManager, allowGlobalCookieFallback) { _, _, _ -> }

            val mp4FileName = fileName.substringBeforeLast('.') + ".mp4"
            val muxedFile = File(tempDir, "dash_muxed.mp4")
            val muxed = remuxToMp4(vFile, aFile, muxedFile)
            val sourceOut = if (muxed && muxedFile.exists() && muxedFile.length() > 0L) muxedFile else vFile

            val (outStream, outUri) = openOutputDestination(mp4FileName, "video/mp4")
            outStream.use { out -> FileInputStream(sourceOut).use { it.copyTo(out) } }
            val len = sourceOut.length()
            tempDir.deleteRecursively()
            repository.updateDownloadProgress(downloadId, len, len, 0L, "COMPLETED", outUri, false)
        } catch (_: CancellationException) {
            if (pausedIds.remove(downloadId)) {
                repository.updateDownloadStatus(downloadId, "PAUSED")
            } else {
                tempDir.deleteRecursively()
                repository.updateDownloadStatus(downloadId, "CANCELLED")
            }
        } catch (_: Exception) {
            tempDir.deleteRecursively()
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
        var text = fetchText(mediaUrl, referer, userAgent, sessionCookieManager, allowGlobalCookieFallback)
            ?: throw IOException("Could not fetch HLS playlist")
        if (text.contains("#EXT-X-STREAM-INF")) {
            mediaUrl = extractHighestVariantUrl(mediaUrl, text)
                ?: throw IOException("Could not resolve HLS master playlist")
            text = fetchText(mediaUrl, referer, userAgent, sessionCookieManager, allowGlobalCookieFallback)
                ?: throw IOException("Could not fetch selected HLS media playlist")
        }
        val plan = ManifestSegmentPlanner.parseHls(mediaUrl, text)
        val keyCache = ConcurrentHashMap<String, ByteArray>()
        val initFile = File(staging, "hls_init.bin")
        if (plan.initUri != null && (!initFile.exists() || initFile.length() == 0L)) {
            val bytes = fetchHlsBytes(plan.initUri, plan.initRange, referer, userAgent,
                sessionCookieManager, allowGlobalCookieFallback)
                val tmp = File(staging, "hls_init.tmp")
                tmp.writeBytes(bytes)
                if (!tmp.renameTo(initFile)) {
                    tmp.copyTo(initFile, overwrite = true)
                    tmp.delete()
                }
        }

        suspend fun segmentBytes(segment: ManifestSegmentPlanner.HlsSegment): ByteArray {
            val raw = fetchHlsBytes(segment.uri, segment.range, referer, userAgent,
                sessionCookieManager, allowGlobalCookieFallback)
            val key = segment.key
            if (key == null) return raw
            if (key.method != "AES-128") throw IOException("Unsupported HLS key method")
            val keyBytes = keyCache[key.uri] ?: fetchBytes(key.uri, referer, userAgent,
                sessionCookieManager, allowGlobalCookieFallback)?.also {
                if (it.size != 16) throw IOException("Invalid AES-128 HLS key length")
                keyCache[key.uri] = it
            } ?: throw IOException("Could not fetch HLS AES-128 key")
            val iv = if (key.ivHex.isNotBlank()) hexToBytes(key.ivHex) else sequenceIv(segment.sequence)
            return decryptAes128(raw, keyBytes, iv)
                ?: throw IOException("HLS AES-128 decryption failed; refusing to save encrypted bytes")
        }

        var completedBytes = if (initFile.exists()) initFile.length() else 0L
        val segmentFiles = plan.segments.map { File(staging, "hls_seg_" + it.index + ".bin") }
        for (group in plan.segments.indices.toList().chunked(4)) {
            coroutineScope {
                group.map { index ->
                    async(Dispatchers.IO) {
                        val segment = plan.segments[index]
                        val out = segmentFiles[index]
                        val expectedRangeLength = segment.range?.length
                        if (out.exists() && out.length() > 0L &&
                            (expectedRangeLength == null || out.length() == expectedRangeLength)) {
                            return@async
                        }
                        out.delete()
                        val bytes = segmentBytes(segment)
                        val tmp = File(staging, out.name + ".tmp")
                        tmp.writeBytes(bytes)
                        if (!tmp.renameTo(out)) {
                            tmp.copyTo(out, overwrite = true)
                            tmp.delete()
                        }
                    }
                }.awaitAll()
            }
            completedBytes = (if (initFile.exists()) initFile.length() else 0L) +
                segmentFiles.sumOf { if (it.exists()) it.length() else 0L }
            repository.updateDownloadProgress(
                id = downloadId,
                downloaded = completedBytes,
                total = -1L,
                speedBytesPerSec = 0L,
                status = "DOWNLOADING",
                supportsResume = true,
            )
        }

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
            if (range == null) {
                if (!response.isSuccessful) throw IOException("HLS segment request failed: HTTP " + response.code)
            } else {
                if (response.code != 206) throw IOException("HLS byte-range request was not honored")
                val expected = "bytes " + range.offset + "-" + (range.offset + range.length - 1L)
                if (!response.header("Content-Range").orEmpty().startsWith(expected)) {
                    throw IOException("Unexpected HLS Content-Range")
                }
            }
            val bytes = response.body?.bytes() ?: throw IOException("Empty HLS response")
            if (range != null && bytes.size.toLong() != range.length) throw IOException("Truncated HLS byte range")
            return bytes
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
                val match = Regex("^bytes\\\\s+0-0/(\\\\d+)$", RegexOption.IGNORE_CASE)
                    .matchEntire(resp.header("Content-Range").orEmpty()) ?: return@use -1L to false
                val total = match.groupValues[1].toLongOrNull() ?: return@use -1L to false
                val bodySize = resp.body?.bytes()?.size ?: return@use -1L to false
                if (bodySize != 1) return@use -1L to false
                total to (total > 0L)
            }
        }.getOrDefault(-1L to false)
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
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .apply { if (referer.isNotBlank()) header("Referer", referer) }
            .build()
        executeWithProfileCookies(req, sessionCookieManager, allowGlobalCookieFallback).use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            val body = resp.body ?: return
            val total = body.contentLength()
            var dl = 0L
            var lastTime = System.currentTimeMillis()
            var lastBytes = 0L
            FileOutputStream(file).use { out ->
                val buf = ByteArray(16 * 1024)
                body.byteStream().use { input ->
                    while (true) {
                        val r = input.read(buf)
                        if (r == -1) break
                        out.write(buf, 0, r)
                        dl += r
                        val now = System.currentTimeMillis()
                        val dt = now - lastTime
                        if (dt >= 450L) {
                            val spd = ((dl - lastBytes) * 1000L) / dt.coerceAtLeast(1L)
                            lastTime = now
                            lastBytes = dl
                            onProgress(dl, total, spd.coerceAtLeast(0L))
                        }
                    }
                }
            }
        }
    }

    private fun decryptAes128(data: ByteArray, key: ByteArray, iv: ByteArray): ByteArray? {
        return runCatching {
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
            cipher.doFinal(data)
        }.getOrNull()
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

    private fun openOutputDestination(fileName: String, mimeType: String): Pair<OutputStream, String> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, mimeType.ifBlank { "video/mp4" })
                put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/SalviaBrowxser")
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                val stream = context.contentResolver.openOutputStream(uri)
                if (stream != null) return stream to uri.toString()
            }
        }
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "SalviaBrowxser")
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, fileName)
        return FileOutputStream(file) to Uri.fromFile(file).toString()
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
                if (resp.isSuccessful) resp.body?.string() else null
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
                if (resp.isSuccessful) resp.body?.bytes() else null
            }
        }.getOrNull()
    }

    private fun extractHighestVariantUrl(masterUrl: String, text: String): String? {
        val base = Uri.parse(masterUrl)
        val lines = text.lines().map { it.trim() }
        var bestBw = -1L
        var bestUrl: String? = null
        var i = 0
        while (i < lines.size) {
            if (lines[i].startsWith("#EXT-X-STREAM-INF:")) {
                val bw = Regex("BANDWIDTH=(\\d+)").find(lines[i])?.groupValues?.getOrNull(1)?.toLongOrNull() ?: 0L
                var j = i + 1
                while (j < lines.size && (lines[j].isBlank() || lines[j].startsWith("#"))) j++
                if (j < lines.size && bw >= bestBw) {
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

            val response = client.newCall(requestWithCookies).execute()
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

            val crossOrigin = current.scheme != target.scheme ||
                current.host != target.host ||
                current.port != target.port
            val next = response.request.newBuilder()
                .url(target)
                .removeHeader("Cookie")

            if (crossOrigin) {
                next.removeHeader("Authorization")
                next.removeHeader("Proxy-Authorization")
                val safeReferer = current.newBuilder()
                    .encodedPath("/")
                    .query(null)
                    .fragment(null)
                    .build()
                next.header("Referer", safeReferer.toString())
            }

            response.close()
            request = next.build()
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
        val base = rawName.replace(Regex("[^\\p{L}\\p{N}._\\- ]"), "_")
            .trim()
            .ifBlank { "salvia_video_${System.currentTimeMillis()}" }
            .take(55)
        val ext = when {
            quality.isHls || quality.isDash -> "mp4"
            quality.format.equals("WEBM", true) -> "webm"
            quality.format.equals("M4A", true) -> "m4a"
            quality.format.equals("MP3", true) -> "mp3"
            else -> "mp4"
        }
        return if (base.contains('.')) base else "$base.$ext"
    }
}
