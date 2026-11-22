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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
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
        .followRedirects(true)
        .build()

    fun startDownload(
        fileName: String,
        quality: MediaQualityOption,
        sourcePageUrl: String,
        userAgent: String,
        sessionCookieManager: CookieManager? = null,
        isIncognito: Boolean = false,
        allowGlobalCookieFallback: Boolean = !isIncognito,
    ) {
        val safeFileName = sanitizeFileName(fileName, quality)
        scope.launch(Dispatchers.IO) {
            val downloadId = repository.insertDownload(
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
                ),
            )
            launchJobForEntity(
                downloadId = downloadId,
                fileName = safeFileName,
                url = quality.url,
                audioUrl = quality.audioUrl,
                referer = sourcePageUrl,
                userAgent = userAgent,
                mimeType = quality.mimeType,
                isHls = quality.isHls || quality.url.lowercase().contains(".m3u8"),
                isDash = quality.isDash,
                sessionCookieManager = sessionCookieManager,
                allowGlobalCookieFallback = allowGlobalCookieFallback && !isIncognito,
            )
        }
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
        sessionCookieManager: CookieManager?,
        allowGlobalCookieFallback: Boolean,
    ) {
        val job = scope.launch(Dispatchers.IO) {
            when {
                isHls -> downloadHlsStream(downloadId, fileName, url, audioUrl, referer, userAgent, sessionCookieManager, allowGlobalCookieFallback)
                isDash && audioUrl.isNotBlank() -> downloadDashAndMux(downloadId, fileName, url, audioUrl, referer, userAgent, sessionCookieManager, allowGlobalCookieFallback)
                else -> downloadDirectMultiPart(downloadId, fileName, url, referer, userAgent, mimeType, sessionCookieManager, allowGlobalCookieFallback)
            }
        }
        activeJobs[downloadId] = job
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
        try {
            repository.updateDownloadProgress(downloadId, 0L, -1L, 0L, "DOWNLOADING", supportsResume = true)
            val cookie = resolveCookieHeader(url, sessionCookieManager, allowGlobalCookieFallback)
            val probe = probeRangeSupport(url, referer, userAgent, cookie)
            val totalBytes = probe.first
            val supportsRanges = probe.second

            val tempDir = chunkDir(downloadId).apply { if (!exists()) mkdirs() }

            if (supportsRanges && totalBytes >= 1_500_000L) {
                val threadCount = 4
                val partSize = totalBytes / threadCount
                val downloadedCounter = AtomicLong(0L)
                var lastTickTime = System.currentTimeMillis()
                var lastTickBytes = 0L

                val partFiles = (0 until threadCount).map { idx ->
                    val f = File(tempDir, "part_$idx.bin")
                    if (f.exists()) downloadedCounter.addAndGet(f.length())
                    f
                }

                val latestProgressJob = java.util.concurrent.atomic.AtomicReference<Job?>(null)
                coroutineScope {
                    val tasks = (0 until threadCount).map { idx ->
                        async(Dispatchers.IO) {
                            val start = idx * partSize
                            val end = if (idx == threadCount - 1) totalBytes - 1L else ((idx + 1) * partSize) - 1L
                            val partFile = partFiles[idx]
                            val alreadyHave = if (partFile.exists()) partFile.length() else 0L
                            val reqStart = start + alreadyHave
                            if (reqStart > end) return@async

                            val req = Request.Builder()
                                .url(url)
                                .header("User-Agent", userAgent)
                                .header("Range", "bytes=$reqStart-$end")
                                .apply {
                                    if (referer.isNotBlank()) header("Referer", referer)
                                    if (cookie.isNotBlank()) header("Cookie", cookie)
                                }
                                .build()

                            client.newCall(req).execute().use { resp ->
                                if (!resp.isSuccessful && resp.code != 206) {
                                    throw IllegalStateException("Range HTTP ${resp.code}")
                                }
                                val stream = resp.body?.byteStream() ?: throw IllegalStateException("Empty body")
                                FileOutputStream(partFile, alreadyHave > 0L).use { out ->
                                    val buffer = ByteArray(16 * 1024)
                                    while (true) {
                                        val read = stream.read(buffer)
                                        if (read == -1) break
                                        out.write(buffer, 0, read)
                                        val totalNow = downloadedCounter.addAndGet(read.toLong())
                                        val now = System.currentTimeMillis()
                                        if (now - lastTickTime >= 500L) {
                                            synchronized(this@MediaDownloadManager) {
                                                val dt2 = System.currentTimeMillis() - lastTickTime
                                                if (dt2 >= 500L) {
                                                    val speed = ((totalNow - lastTickBytes) * 1000L) / dt2.coerceAtLeast(1L)
                                                    lastTickTime = System.currentTimeMillis()
                                                    lastTickBytes = totalNow
                                                    latestProgressJob.set(
                                                        launch(Dispatchers.IO) {
                                                            repository.updateDownloadProgress(
                                                                downloadId,
                                                                totalNow,
                                                                totalBytes,
                                                                speed.coerceAtLeast(0L),
                                                                "DOWNLOADING",
                                                                supportsResume = true,
                                                            )
                                                        },
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    tasks.awaitAll()
                    latestProgressJob.get()?.join()
                }

                // Merge chunks into final destination
                val (destStream, destUri) = openOutputDestination(fileName, mimeType)
                destStream.use { out ->
                    val buf = ByteArray(32 * 1024)
                    partFiles.forEach { part ->
                        FileInputStream(part).use { input ->
                            while (true) {
                                val r = input.read(buf)
                                if (r == -1) break
                                out.write(buf, 0, r)
                            }
                        }
                    }
                }
                tempDir.deleteRecursively()
                repository.updateDownloadProgress(downloadId, totalBytes, totalBytes, 0L, "COMPLETED", destUri, true)
                return
            }

            // Single-stream fallback (also supports single-file resume if Accept-Ranges is true)
            val singleFile = File(tempDir, "single.bin")
            val existingLen = if (supportsRanges && singleFile.exists()) singleFile.length() else 0L
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .apply {
                    if (referer.isNotBlank()) header("Referer", referer)
                    if (cookie.isNotBlank()) header("Cookie", cookie)
                    if (existingLen > 0L) header("Range", "bytes=$existingLen-")
                }
                .build()

            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful && resp.code != 206) {
                    repository.updateDownloadStatus(downloadId, "FAILED")
                    return
                }
                val body = resp.body ?: run {
                    repository.updateDownloadStatus(downloadId, "FAILED")
                    return
                }
                val respLen = body.contentLength()
                val resolvedTotal = if (totalBytes > 0) totalBytes else if (respLen > 0) existingLen + respLen else -1L
                var downloaded = existingLen
                var lastReport = System.currentTimeMillis()
                var lastBytes = downloaded

                FileOutputStream(singleFile, existingLen > 0L).use { out ->
                    val buffer = ByteArray(16 * 1024)
                    body.byteStream().use { input ->
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            out.write(buffer, 0, read)
                            downloaded += read
                            val now = System.currentTimeMillis()
                            val dt = now - lastReport
                            if (dt >= 450L) {
                                val speed = ((downloaded - lastBytes) * 1000L) / dt.coerceAtLeast(1L)
                                lastReport = now
                                lastBytes = downloaded
                                repository.updateDownloadProgress(
                                    downloadId,
                                    downloaded,
                                    resolvedTotal,
                                    speed.coerceAtLeast(0L),
                                    "DOWNLOADING",
                                    supportsResume = supportsRanges,
                                )
                            }
                        }
                    }
                }

                val (destStream, destUri) = openOutputDestination(fileName, mimeType)
                destStream.use { out ->
                    FileInputStream(singleFile).use { it.copyTo(out) }
                }
                tempDir.deleteRecursively()
                repository.updateDownloadProgress(downloadId, downloaded, downloaded, 0L, "COMPLETED", destUri, supportsRanges)
            }
        } catch (_: CancellationException) {
            if (pausedIds.remove(downloadId)) {
                repository.updateDownloadStatus(downloadId, "PAUSED")
            } else {
                chunkDir(downloadId).deleteRecursively()
                repository.updateDownloadStatus(downloadId, "CANCELLED")
            }
        } catch (_: Exception) {
            repository.updateDownloadStatus(downloadId, "FAILED")
        } finally {
            activeJobs.remove(downloadId)
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
            repository.updateDownloadProgress(downloadId, 0L, -1L, 0L, "DOWNLOADING", supportsResume = false)

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
            repository.updateDownloadProgress(downloadId, finalSize, finalSize, 0L, "COMPLETED", finalUri, false)
        } catch (_: CancellationException) {
            tempDir.deleteRecursively()
            repository.updateDownloadStatus(downloadId, "CANCELLED")
        } catch (_: Exception) {
            tempDir.deleteRecursively()
            repository.updateDownloadStatus(downloadId, "FAILED")
        } finally {
            activeJobs.remove(downloadId)
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
            tempDir.deleteRecursively()
            repository.updateDownloadStatus(downloadId, "CANCELLED")
        } catch (_: Exception) {
            tempDir.deleteRecursively()
            repository.updateDownloadStatus(downloadId, "FAILED")
        } finally {
            activeJobs.remove(downloadId)
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
        var mediaPlaylistUrl = playlistUrl
        var playlistText = fetchText(mediaPlaylistUrl, referer, userAgent, sessionCookieManager, allowGlobalCookieFallback) ?: return 0L
        if (playlistText.contains("#EXT-X-STREAM-INF")) {
            val firstVariant = extractHighestVariantUrl(mediaPlaylistUrl, playlistText)
            if (firstVariant != null) {
                mediaPlaylistUrl = firstVariant
                playlistText = fetchText(mediaPlaylistUrl, referer, userAgent, sessionCookieManager, allowGlobalCookieFallback) ?: playlistText
            }
        }

        val baseUri = Uri.parse(mediaPlaylistUrl)
        val lines = playlistText.lines().map { it.trim() }

        var aesKeyBytes: ByteArray? = null
        var explicitIv: ByteArray? = null
        var mediaSequence = 0L
        var mapInitUrl: String? = null
        val segments = mutableListOf<String>()

        for (line in lines) {
            when {
                line.startsWith("#EXT-X-MEDIA-SEQUENCE:") -> {
                    mediaSequence = line.removePrefix("#EXT-X-MEDIA-SEQUENCE:").toLongOrNull() ?: 0L
                }
                line.startsWith("#EXT-X-MAP:") -> {
                    val uri = Regex("URI=\"([^\"]+)\"").find(line)?.groupValues?.getOrNull(1)
                    if (!uri.isNullOrBlank()) mapInitUrl = resolveUrl(baseUri, uri)
                }
                line.startsWith("#EXT-X-KEY:") -> {
                    if (line.contains("METHOD=AES-128", ignoreCase = true)) {
                        val keyUri = Regex("URI=\"([^\"]+)\"").find(line)?.groupValues?.getOrNull(1)
                        val ivHex = Regex("IV=0x([0-9a-fA-F]+)").find(line)?.groupValues?.getOrNull(1)
                        if (!keyUri.isNullOrBlank()) {
                            aesKeyBytes = fetchBytes(resolveUrl(baseUri, keyUri), referer, userAgent, sessionCookieManager, allowGlobalCookieFallback)
                        }
                        if (!ivHex.isNullOrBlank()) {
                            explicitIv = hexToBytes(ivHex)
                        }
                    } else if (line.contains("METHOD=NONE", ignoreCase = true)) {
                        aesKeyBytes = null
                    }
                }
                line.isNotBlank() && !line.startsWith("#") -> {
                    segments.add(resolveUrl(baseUri, line))
                }
            }
        }

        if (segments.isEmpty()) return 0L
        var totalWritten = 0L
        var lastTick = System.currentTimeMillis()
        var lastBytes = 0L

        FileOutputStream(targetFile).use { out ->
            if (!mapInitUrl.isNullOrBlank()) {
                val initBytes = fetchBytes(mapInitUrl, referer, userAgent, sessionCookieManager, allowGlobalCookieFallback)
                if (initBytes != null) {
                    out.write(initBytes)
                    totalWritten += initBytes.size
                }
            }

            segments.forEachIndexed { idx, segUrl ->
                val rawBytes = fetchBytes(segUrl, referer, userAgent, sessionCookieManager, allowGlobalCookieFallback)
                if (rawBytes != null) {
                    val dataToWrite = if (aesKeyBytes != null) {
                        val iv = explicitIv ?: sequenceIv(mediaSequence + idx)
                        decryptAes128(rawBytes, aesKeyBytes, iv) ?: rawBytes
                    } else {
                        rawBytes
                    }
                    out.write(dataToWrite)
                    totalWritten += dataToWrite.size
                }

                val now = System.currentTimeMillis()
                val dt = now - lastTick
                if (dt >= 400L || idx == segments.lastIndex) {
                    val speed = if (dt > 0) ((totalWritten - lastBytes) * 1000L) / dt else 0L
                    lastTick = now
                    lastBytes = totalWritten
                    val estimatedTotal = (totalWritten / (idx + 1).coerceAtLeast(1)) * segments.size
                    repository.updateDownloadProgress(
                        downloadId,
                        totalWritten,
                        estimatedTotal,
                        speed.coerceAtLeast(0L),
                        "DOWNLOADING",
                        supportsResume = false,
                    )
                }
            }
        }
        return totalWritten
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
        cookie: String,
    ): Pair<Long, Boolean> {
        return runCatching {
            val req = Request.Builder()
                .url(url)
                .head()
                .header("User-Agent", userAgent)
                .apply {
                    if (referer.isNotBlank()) header("Referer", referer)
                    if (cookie.isNotBlank()) header("Cookie", cookie)
                }
                .build()
            client.newCall(req).execute().use { resp ->
                val len = resp.header("Content-Length")?.toLongOrNull() ?: -1L
                val acceptRanges = resp.header("Accept-Ranges").orEmpty().equals("bytes", ignoreCase = true)
                len to acceptRanges
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
        val cookie = resolveCookieHeader(url, sessionCookieManager, allowGlobalCookieFallback)
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .apply {
                if (referer.isNotBlank()) header("Referer", referer)
                if (cookie.isNotBlank()) header("Cookie", cookie)
            }
            .build()
        client.newCall(req).execute().use { resp ->
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
            client.newCall(req).execute().use { resp ->
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
            client.newCall(req).execute().use { resp ->
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

    private fun resolveCookieHeader(
        url: String,
        sessionCookieManager: CookieManager?,
        allowGlobalFallback: Boolean,
    ): String {
        val manager = sessionCookieManager ?: if (allowGlobalFallback) CookieManager.getInstance() else return ""
        return runCatching { manager.getCookie(url).orEmpty() }.getOrDefault("")
    }

    private fun chunkDir(downloadId: Long): File =
        File(context.cacheDir, "salvia_chunks/$downloadId")

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
