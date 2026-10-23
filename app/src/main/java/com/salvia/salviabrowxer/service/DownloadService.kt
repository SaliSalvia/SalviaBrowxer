package com.salvia.salviabrowxer.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaScannerConnection
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.salvia.salviabrowxer.MainActivity
import com.salvia.salviabrowxer.R
import com.salvia.salviabrowxer.core.concurrent.SlotLimiter
import com.salvia.salviabrowxer.core.database.entities.DownloadEntity
import com.salvia.salviabrowxer.core.model.DashRenditionId
import com.salvia.salviabrowxer.core.model.DashRepresentation
import com.salvia.salviabrowxer.core.model.DashSelection
import com.salvia.salviabrowxer.core.model.DownloadProgress
import com.salvia.salviabrowxer.core.model.DownloadState
import com.salvia.salviabrowxer.core.model.HlsRenditionId
import com.salvia.salviabrowxer.core.model.HlsSelection
import com.salvia.salviabrowxer.core.model.MediaUrlRules
import com.salvia.salviabrowxer.core.model.SessionReferer
import com.salvia.salviabrowxer.core.storage.MediaStoreExporter
import com.salvia.salviabrowxer.core.storage.SafDocumentExporter
import com.salvia.salviabrowxer.data.datastore.SettingsDataStore
import com.salvia.salviabrowxer.data.diagnostics.DiagnosticsLog
import com.salvia.salviabrowxer.data.diagnostics.hostOf
import com.salvia.salviabrowxer.data.download.DownloadArtifactCleaner
import com.salvia.salviabrowxer.data.repository.DownloadRepository
import com.salvia.salviabrowxer.data.storage.DestinationPolicy
import com.salvia.salviabrowxer.data.storage.SafDestinationStore
import com.salvia.salviabrowxer.service.queue.DATA_SYNC_TIMEOUT_MARKER
import com.salvia.salviabrowxer.service.queue.DownloadWorkQueue
import com.salvia.salviabrowxer.service.queue.SegmentedRetryPolicy
import com.salvia.salviabrowxer.service.queue.TimeoutHold
import com.salvia.salviabrowxer.service.queue.TransferKind
import com.salvia.salviabrowxer.service.queue.TransferPlan
import com.salvia.salviabrowxer.service.queue.WifiHold
import com.salvia.salviabrowxer.service.queue.WifiHoldAction
import com.salvia.salviabrowxer.service.queue.WifiHoldGate
import com.salvia.salviabrowxer.media.downloader.AbortReason
import com.salvia.salviabrowxer.media.downloader.DownloadAbortedException
import com.salvia.salviabrowxer.media.downloader.DownloadManager
import com.salvia.salviabrowxer.media.downloader.FileDownload
import com.salvia.salviabrowxer.media.downloader.DownloadSnapshot
import com.salvia.salviabrowxer.media.downloader.DashDownloader
import com.salvia.salviabrowxer.media.downloader.DownloadResult
import com.salvia.salviabrowxer.media.downloader.HlsDownloader
import com.salvia.salviabrowxer.media.downloader.MediaMetadataReader
import com.salvia.salviabrowxer.media.downloader.MediaRemuxer
import com.salvia.salviabrowxer.media.downloader.TrackMerger
import com.salvia.salviabrowxer.ui.utils.formatFileSize
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import java.io.IOException
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@AndroidEntryPoint
class DownloadService : Service() {
    @Inject lateinit var downloadRepository: DownloadRepository
    @Inject lateinit var downloadManager: DownloadManager
    @Inject lateinit var hlsDownloader: HlsDownloader
    @Inject lateinit var dashDownloader: DashDownloader
    @Inject lateinit var mediaRemuxer: MediaRemuxer
    @Inject lateinit var trackMerger: TrackMerger
    @Inject lateinit var mediaMetadataReader: MediaMetadataReader
    @Inject lateinit var mediaStoreExporter: MediaStoreExporter
    @Inject lateinit var settingsDataStore: SettingsDataStore
    @Inject lateinit var safDestinationStore: SafDestinationStore
    @Inject lateinit var safDocumentExporter: SafDocumentExporter
    @Inject lateinit var artifactCleaner: DownloadArtifactCleaner
    @Inject lateinit var diagnostics: DiagnosticsLog

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val notificationId = 1
    private val channelId = "download_channel"
    private var lastSummaryTime = 0L
    private var pendingSummaryRefresh = false
    // Per-download, so three transfers running at once cannot cancel each other's progress writes.
    private val progressThrottle by lazy { ProgressPersistThrottle(scope) }
    // One limiter for the service lifetime: retuning it never strands in-flight permits.
    private val limiter = SlotLimiter(3)
    private lateinit var connectivityGate: ConnectivityGate
    private var wifiOnly = false

    /**
     * The queue, the concurrency gate and the idle condition, in one testable object.
     *
     * Everything that made this service hard to test lives in `service/queue` now: the ordering of
     * "claim the id, then suspend", the slot limiter, the retry schedule and the Wi-Fi hold. What
     * stays here is what genuinely needs the Android service: notifications, the foreground
     * promotion and the Room rows.
     */
    private val workQueue by lazy {
        DownloadWorkQueue(
            limiter = limiter,
            process = { downloadId ->
                try { processDownload(downloadId) } finally { progressThrottle.forget(downloadId) }
            },
            onIdle = { stopWhenIdle() }
        )
    }

    /** Holds or releases work for the Wi-Fi-only setting; the transitions are unit-tested. */
    private val wifiHoldGate = WifiHoldGate()
    private val retryPolicy = SegmentedRetryPolicy()

    /** Range connections one direct file may use; retuned live from settings like the limiter. */
    @Volatile private var parallelConnections = 1

    /** True while the Wi-Fi-only setting is holding work, so the service stays alive to resume it. */
    @Volatile private var wifiHoldActive = false

    private val notificationManager: NotificationManager get() = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        connectivityGate = ConnectivityGate(this)
        workQueue.start(scope)
        createNotificationChannel()
        promoteToForeground(buildSummaryNotification(getString(R.string.downloads_title), 0L, 0L))
        scope.launch {
            settingsDataStore.maxSimultaneousDownloads.collect { count -> limiter.setLimit(count) }
        }
        scope.launch {
            settingsDataStore.parallelConnections.collect { count -> parallelConnections = count }
        }
        observeNetworkPolicy()
    }

    /**
     * Wi-Fi only: transfers never start on a non-Wi-Fi network, running ones are paused with an
     * explanatory error, and everything that was held is requeued when Wi-Fi returns.
     *
     * The decision of *which* of those two things to do is [WifiHoldGate]'s, so both inputs — the
     * setting and the network report — go through the same tested transition instead of two
     * hand-written `if`s that could disagree.
     */
    private fun observeNetworkPolicy() {
        scope.launch {
            settingsDataStore.isWifiOnly.collect { enabled ->
                wifiOnly = enabled
                when (wifiHoldGate.onWifiOnlyChanged(enabled, connectivityGate.isOnWifi())) {
                    WifiHoldAction.Hold -> holdEverythingForWifi()
                    WifiHoldAction.Release -> releaseWifiHolds()
                    WifiHoldAction.None -> Unit
                }
            }
        }
        scope.launch {
            connectivityGate.observeWifi().collect { onWifi ->
                if (!wifiOnly) return@collect
                when (wifiHoldGate.onConnectivityChanged(onWifi)) {
                    WifiHoldAction.Hold -> holdEverythingForWifi()
                    WifiHoldAction.Release -> releaseWifiHolds()
                    WifiHoldAction.None -> Unit
                }
            }
        }
    }

    private suspend fun holdEverythingForWifi() {
        val active = runCatching { downloadRepository.getDownloadsByStates(WifiHold.HOLDABLE_STATES).first() }
            .getOrNull().orEmpty()
        if (active.isEmpty()) return
        wifiHoldActive = true
        active.forEach { download ->
            downloadManager.cancel(download.id)
            downloadRepository.updateDownloadResult(id = download.id, status = DownloadState.PAUSED, error = WIFI_HOLD_MARKER)
        }
        refreshSummaryThrottled(force = true)
    }

    private suspend fun releaseWifiHolds() {
        wifiHoldActive = false
        val held = WifiHold.heldRows(
            runCatching { downloadRepository.getDownloadsByStates(listOf(DownloadState.PAUSED)).first() }
                .getOrNull().orEmpty()
        )
        if (held.isEmpty()) return
        held.forEach { download ->
            downloadRepository.updateDownloadResult(id = download.id, status = DownloadState.QUEUED, error = null)
            workQueue.enqueue(download.id)
        }
        workQueue.start(scope)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val downloadId = intent?.getStringExtra(EXTRA_DOWNLOAD_ID)
        when (intent?.action) {
            ACTION_ENQUEUE -> if (downloadId != null) workQueue.enqueue(downloadId) else recoverQueue()
            ACTION_PAUSE -> if (downloadId != null) scope.launch { pauseRequested(downloadId) }
            ACTION_CANCEL -> if (downloadId != null) scope.launch { cancelRequested(downloadId) }
            ACTION_PROCESS_QUEUE -> recoverQueue()
            else -> if (downloadId != null) workQueue.enqueue(downloadId) else recoverQueue()
        }
        workQueue.start(scope)
        return START_STICKY
    }

    private fun recoverQueue() {
        scope.launch {
            // DOWNLOADING is included so a process death mid-transfer resumes from the .part file
            // over a Range request. PAUSED is *not* requeued: only the user resumes it.
            releaseTimeoutHolds()
            val pending = runCatching { downloadRepository.getDownloadsByStates(WifiHold.RESUMABLE_STATES).first() }.getOrNull().orEmpty()
            if (pending.isEmpty()) { if (workQueue.isIdle) stopWhenIdle(); return@launch }
            pending.forEach { download -> workQueue.enqueue(download.id) }
        }
    }

    /**
     * Android 15+ caps a `dataSync` foreground service at six cumulative hours per 24-hour window.
     * When the cap is reached the system calls this, and the service has a few seconds to stop
     * itself before it is stopped *with a failure* — which for a downloader is the difference
     * between "paused, will resume" and a crash the user sees.
     *
     * The stop is honest on every axis: running transfers are paused with the stable
     * [DATA_SYNC_TIMEOUT_MARKER] (so a later release can tell them from a user's pause), a normal
     * notification explains what happened and what resumes it, and the service stops. The quota
     * is the system's to give back; resuming is the app's next real interaction — opening the app
     * runs `processQueue`, whose `recoverQueue` releases exactly these rows.
     */
    override fun onTimeout(startId: Int, fgsType: Int) {
        Log.w(TAG, "dataSync foreground service timed out (Android 15+ daily cap)")
        diagnostics.event(
            "data-sync-timeout",
            mapOf("startId" to startId.toString(), "fgsType" to fgsType.toString(), "sdkInt" to Build.VERSION.SDK_INT.toString())
        )
        // The Room writes are fast, but they must finish before stopSelf() tears the scope down, so
        // the stop waits on the same dispatcher the writes run on rather than racing them.
        scope.launch {
            runCatching { holdEverythingForTimeout() }
            runCatching { notifyTimeoutPaused() }
            ServiceCompat.stopForeground(this@DownloadService, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    /** The timeout's half of the Wi-Fi hold's pattern: pause what runs, marker first, ask nothing. */
    private suspend fun holdEverythingForTimeout() {
        val active = runCatching { downloadRepository.getDownloadsByStates(WifiHold.HOLDABLE_STATES).first() }
            .getOrNull().orEmpty()
        if (active.isEmpty()) return
        active.forEach { download ->
            downloadManager.cancel(download.id)
            downloadRepository.updateDownloadResult(id = download.id, status = DownloadState.PAUSED, error = DATA_SYNC_TIMEOUT_MARKER)
        }
        refreshSummaryThrottled(force = true)
    }

    /**
     * Requeues what the timeout paused. Called from [recoverQueue] — the app's own next start —
     * never from a timer, because a timer would try to spend the very quota the system removed.
     */
    private suspend fun releaseTimeoutHolds() {
        val held = TimeoutHold.heldRows(
            runCatching { downloadRepository.getDownloadsByStates(listOf(DownloadState.PAUSED)).first() }
                .getOrNull().orEmpty()
        )
        if (held.isEmpty()) return
        held.forEach { download ->
            downloadRepository.updateDownloadResult(id = download.id, status = DownloadState.QUEUED, error = null)
            workQueue.enqueue(download.id)
        }
        workQueue.start(scope)
    }

    /** A plain notification, not a foreground one: the service is on its way out when this fires. */
    private fun notifyTimeoutPaused() {
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(getString(R.string.download_timeout_title))
            .setContentText(getString(R.string.download_timeout_text))
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentIntent(openDownloadsPendingIntent(NOTIFICATION_TIMEOUT_ID))
            .setAutoCancel(true)
            .setOngoing(false)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        notificationManager.notify(NOTIFICATION_TIMEOUT_ID, notification)
    }

    private suspend fun processDownload(downloadId: String) {
        val stored = downloadRepository.getDownloadById(downloadId) ?: return
        if (stored.status == DownloadState.COMPLETED || stored.status == DownloadState.CANCELLED) return
        if (WifiOnlyPolicy.shouldHold(wifiOnly, connectivityGate.isOnWifi())) {
            wifiHoldActive = true
            downloadRepository.updateDownloadResult(id = downloadId, status = DownloadState.PAUSED, error = WIFI_HOLD_MARKER)
            refreshSummaryThrottled(force = true)
            return
        }
        try {
            downloadRepository.updateDownloadState(downloadId, DownloadState.PREPARING)
            val finalUrl = runCatching {
                downloadManager.getFinalUrl(stored.url, sessionUserAgent(stored), sessionReferer(stored, stored.url), sessionOrigin(stored, stored.url))
            }.getOrNull()?.takeIf { it.isNotBlank() } ?: stored.url
            val preparing = stored.copy(finalUrl = finalUrl, status = DownloadState.DOWNLOADING, error = null, temporaryPath = temporaryPathFor(stored).absolutePath, updatedAt = System.currentTimeMillis())
            downloadRepository.updateDownload(preparing)
            refreshSummaryThrottled(force = true)

            // Three real paths, chosen by what the row is — a decision made in TransferPlan so
            // it is unit-testable rather than three branches inside a network call.
            val plan = TransferPlan.of(stored.renditionId, finalUrl, stored.mimeType, parallelConnections)
            val dashSelection = DashRenditionId.parse(stored.renditionId)
            val hlsSelection = HlsRenditionId.parse(stored.renditionId)
            // A segmented transfer is retried after a transient failure. The parts it already
            // staged are kept, so the retry resumes instead of re-fetching from the first segment.
            val transfer = transferWithRetry(downloadId, segmented = plan.retryable) { attempt ->
                when (plan.kind) {
                    TransferKind.DASH -> downloadDashRendition(stored, downloadId, finalUrl, dashSelection!!, reuseCompleted = attempt > 0)
                    TransferKind.HLS -> downloadHlsStream(stored, downloadId, finalUrl, hlsSelection, reuseCompleted = attempt > 0)
                    TransferKind.DIRECT -> directTransfer(stored, downloadId, finalUrl)
                }
            }

            val finalized = applyDestinationPlan(finalizeTransfer(stored, transfer))
            downloadRepository.updateDownloadResult(id = downloadId, status = DownloadState.COMPLETED, downloadedBytes = finalized.sizeBytes, totalBytes = finalized.sizeBytes, finalPath = finalized.path, mimeType = finalized.mimeType)
            downloadRepository.updateDownloadArtifacts(
                id = downloadId,
                finalPath = finalized.path,
                filename = finalized.fileName,
                mimeType = finalized.mimeType,
                durationMs = finalized.durationMs,
                thumbnail = finalized.thumbnailPath,
                exportedUri = finalized.exportedUri
            )
            // The staged bytes are no longer needed once the result exists; the result itself is
            // protected by the cleaner (a DASH file that could not be remuxed keeps its staged name).
            artifactCleaner.cleanTransferStaging(stored)
            finalized.file?.let { file -> scanFile(file); notifyCompleted(stored, file, note = finalized.destinationNote) }
                ?: notifyCompleted(stored, null, finalized.sizeBytes, finalized.destinationNote)
        } catch (aborted: DownloadAbortedException) {
            if (aborted.reason == AbortReason.CANCELLED) { artifactCleaner.cleanTransferStaging(stored); downloadRepository.updateDownloadResult(id = downloadId, status = DownloadState.CANCELLED, error = getString(R.string.download_cancel)) }
            else downloadRepository.updateDownloadState(downloadId, DownloadState.PAUSED)
        } catch (network: IOException) {
            downloadRepository.updateDownloadResult(id = downloadId, status = DownloadState.FAILED, error = network.localizedMessage ?: getString(R.string.error_connection_unavailable))
            // The diagnostic carries the host, the kind and the reason — never the URL, whose query
            // string is where sites put the tokens a shareable file must not contain.
            diagnostics.event(
                "transfer-failed",
                mapOf(
                    "host" to hostOf(stored.finalUrl ?: stored.url),
                    "kind" to TransferPlan.of(stored.renditionId, stored.finalUrl ?: stored.url, stored.mimeType, parallelConnections).kind.name,
                    "reason" to (network.localizedMessage ?: network.javaClass.simpleName)
                )
            )
        } catch (cancellation: CancellationException) { throw cancellation }
        finally { refreshSummaryThrottled(force = true) }
    }

    /**
     * Runs a transfer, retrying a *segmented* one a bounded number of times after a transient
     * failure. A retry resumes rather than restarts: [SegmentedFetcher] keeps the parts it already
     * staged, and a DASH rendition that finished on an earlier attempt is not fetched again, so only
     * the missing segments are requested. The schedule itself lives in [SegmentedRetryPolicy] — three
     * tries in total with a short, growing backoff — and a pause or cancel during the wait stops the
     * retry instead of letting it finish.
     */
    private suspend fun transferWithRetry(
        downloadId: String,
        segmented: Boolean,
        block: suspend (attempt: Int) -> Transfer
    ): Transfer {
        var attempt = 0
        while (true) {
            abortReasonFor(downloadId)?.let { throw DownloadAbortedException(it) }
            try {
                return block(attempt)
            } catch (aborted: DownloadAbortedException) {
                throw aborted
            } catch (network: IOException) {
                // The schedule (three tries in total, growing backoff) is the retry policy's, so the
                // numbers are asserted in a unit test instead of counted here.
                val decision = if (segmented) retryPolicy.onFailure(attempt) else SegmentedRetryPolicy.Decision.GiveUp
                if (decision is SegmentedRetryPolicy.Decision.GiveUp) throw network
                decision as SegmentedRetryPolicy.Decision.Retry
                attempt = decision.attempt
                Log.i(TAG, "Segmented transfer $downloadId failed, retry $attempt/${SegmentedRetryPolicy.DEFAULT_MAX_RETRIES}", network)
                downloadRepository.updateDownloadState(downloadId, DownloadState.RETRYING)
                refreshSummaryThrottled(force = true)
                var waited = 0L
                while (waited < decision.backoffMillis) {
                    abortReasonFor(downloadId)?.let { throw DownloadAbortedException(it) }
                    kotlinx.coroutines.delay(RETRY_POLL_MILLIS)
                    waited += RETRY_POLL_MILLIS
                }
            }
        }
    }

    /** What a transfer produced, and whether a container rewrite is still owed on it. */
    private class Transfer(val file: File, val contentType: String?, val remuxToMp4: Boolean)

    /**
     * What a finished transfer produced.
     *
     * [path] is what the row stores: an absolute path in the normal case, and the document's
     * `content://` URI when the user has chosen a folder as the primary destination. [file] is null in
     * that case — the file is no longer reachable as a `File` — which is why every consumer takes the
     * path and the size instead of assuming a file.
     */
    private data class Finalized(
        val path: String,
        val fileName: String,
        val file: File?,
        val sizeBytes: Long,
        val mimeType: String?,
        val durationMs: Long?,
        val thumbnailPath: String?,
        val exportedUri: String?,
        /** Set when the file was asked to go to the user's folder and could not. Shown to the user. */
        val destinationNote: String? = null
    )

    private fun baseName(filename: String): String = filename.substringBeforeLast('.', filename).ifBlank { "download" }

    /**
     * The session a transfer belongs to: the page it was started from, sent as `Referer`, the
     * user agent that page was loaded with, and — cross-site only — the page origin as `Origin`.
     * All three are replayed on every request the transfer makes —
     * first byte, resumed byte, segment, key and manifest — so a server that guards its media by
     * origin or client sees the request it would have served to the page itself. Cookies are not part
     * of this: they live in the shared OkHttp jar (see `SessionCookieJar`) and are never copied around.
     */
    private fun sessionUserAgent(stored: DownloadEntity): String =
        stored.userAgent?.takeIf { it.isNotBlank() } ?: FileDownload.DEFAULT_DOWNLOAD_USER_AGENT

    /**
     * Derived per request URL, not copied: a browser sends the whole page to its own site and only the
     * origin to anyone else, and strips the header entirely on a downgrade. Same rule here, so a
     * hotlink check passes and no third party learns the path the user was reading.
     */
    private fun sessionReferer(stored: DownloadEntity, targetUrl: String): String? =
        SessionReferer.headerFor(stored.pageUrl, targetUrl)

    /**
     * The `Origin` the page itself would send for this target: present only cross-site, and only
     * the origin — a same-site fetch sends no `Origin` header, and neither does the transfer.
     */
    private fun sessionOrigin(stored: DownloadEntity, targetUrl: String): String? =
        SessionReferer.originHeaderFor(stored.pageUrl, targetUrl)

    private suspend fun directTransfer(stored: DownloadEntity, downloadId: String, finalUrl: String): Transfer {
        val result = downloadManager.download(
            download = FileDownload(
                id = stored.id,
                url = finalUrl,
                directory = stored.destination,
                filename = stored.filename,
                userAgent = sessionUserAgent(stored),
                referer = sessionReferer(stored, finalUrl),
                origin = sessionOrigin(stored, finalUrl)
            ),
            // A direct file may use several Range connections; a server that cannot serve them is
            // detected by the probe, not assumed, and the transfer falls back to one stream.
            parallelConnections = parallelConnections,
            onProgress = { snapshot ->
                handleProgress(downloadId, snapshot)
                refreshSummaryThrottled()
            },
            shouldAbort = { abortReasonFor(downloadId) }
        )
        return Transfer(result.file, result.contentType, remuxToMp4 = false)
    }

    private suspend fun downloadHlsStream(
        stored: DownloadEntity,
        downloadId: String,
        finalUrl: String,
        selection: HlsSelection?,
        reuseCompleted: Boolean
    ): Transfer {
        val base = baseName(stored.filename)
        val progress: suspend (DownloadSnapshot) -> Unit = { snapshot ->
            handleProgress(downloadId, snapshot)
            refreshSummaryThrottled()
        }
        val abort: suspend () -> AbortReason? = { abortReasonFor(downloadId) }
        return if (selection is HlsSelection.Merged) {
            // The audio URI came from the master playlist EXT-X-MEDIA tag and never went through
            // the redirect resolution the variant did, so that happens here, at transfer time,
            // with the same session headers the variant got.
            val audioUrl = runCatching {
                downloadManager.getFinalUrl(selection.audioUrl, sessionUserAgent(stored), sessionReferer(stored, selection.audioUrl), sessionOrigin(stored, selection.audioUrl))
            }.getOrNull()?.takeIf { it.isNotBlank() } ?: selection.audioUrl
            val directory = File(stored.destination).also { if (!it.exists()) it.mkdirs() }
            val videoRaw = File(directory, "$base.video.tmp")
            val audioRaw = File(directory, "$base.audio.tmp")
            fetchHlsPlaylist(videoRaw, stored, finalUrl, reuseCompleted, progress, abort)
            fetchHlsPlaylist(audioRaw, stored, audioUrl, reuseCompleted, progress, abort)
            val merged = DownloadManager.nonConflicting(File(directory, "$base.mp4"))
            if (trackMerger.merge(videoRaw, audioRaw, merged)) {
                videoRaw.delete(); audioRaw.delete()
                // MediaMuxer already wrote a plain MP4, so no further container rewrite is due.
                Transfer(merged, "video/mp4", remuxToMp4 = false)
            } else {
                // Honest fallback, the DASH merged path's rule: the picture survives and is
                // remuxed below as usual, and the sound that could not be joined to it is
                // dropped, rather than the whole download failing.
                audioRaw.delete()
                Transfer(videoRaw, "video/mp2t", remuxToMp4 = true)
            }
        } else {
            // The playlist is assembled as a raw transport stream; it becomes an MP4 in finalizeTransfer.
            val result = hlsDownloader.download(
                download = FileDownload(
                    id = stored.id,
                    url = finalUrl,
                    directory = stored.destination,
                    filename = "$base.hls.ts",
                    userAgent = sessionUserAgent(stored),
                    referer = sessionReferer(stored, finalUrl),
                origin = sessionOrigin(stored, finalUrl)
                ),
                onProgress = progress,
                shouldAbort = abort
            )
            Transfer(result.file, result.contentType ?: "video/mp2t", remuxToMp4 = true)
        }
    }

    /**
     * Downloads one HLS playlist unless a previous attempt of this same download already produced
     * the whole file — the HLS twin of [fetchDashRepresentation]'s reuse rule, for the same
     * reason: a finished playlist is the coarsest resume unit, so a retry does not fetch a whole
     * track again. On the first attempt any stale file is removed first, so a later retry can
     * trust whatever it finds.
     */
    private suspend fun fetchHlsPlaylist(
        target: File,
        stored: DownloadEntity,
        url: String,
        reuseCompleted: Boolean,
        onProgress: suspend (DownloadSnapshot) -> Unit,
        shouldAbort: suspend () -> AbortReason?
    ): DownloadResult {
        if (target.exists()) {
            if (reuseCompleted && target.length() > 0L) {
                return DownloadResult(file = target, bytesWritten = target.length(), totalBytes = target.length(), contentType = "video/mp2t")
            }
            target.delete()
        }
        return hlsDownloader.download(
            download = FileDownload(
                id = stored.id,
                url = url,
                directory = target.parentFile?.absolutePath ?: stored.destination,
                filename = target.name,
                userAgent = sessionUserAgent(stored),
                referer = sessionReferer(stored, url),
                origin = sessionOrigin(stored, url)
            ),
            onProgress = onProgress,
            shouldAbort = shouldAbort
        )
    }

    /**
     * Downloads one DASH representation unless a previous attempt of this same download already
     * produced the whole file. A finished representation is the coarsest resume unit: its staged
     * segments are deleted once it completes, so reusing the file is what keeps a retry from
     * fetching a whole video track again. On the first attempt any stale file is removed first, so a
     * later retry can trust whatever it finds.
     */
    private suspend fun fetchDashRepresentation(
        representation: DashRepresentation,
        target: File,
        userAgent: String,
        referer: String?,
        origin: String?,
        reuseCompleted: Boolean,
        onProgress: suspend (DownloadSnapshot) -> Unit,
        shouldAbort: suspend () -> AbortReason?
    ): DownloadResult {
        if (target.exists()) {
            if (reuseCompleted && target.length() > 0L) {
                return DownloadResult(file = target, bytesWritten = target.length(), totalBytes = target.length(), contentType = representation.mimeType)
            }
            target.delete()
        }
        return dashDownloader.downloadRepresentation(representation, target, userAgent, referer, origin, onProgress = onProgress, shouldAbort = shouldAbort)
    }

    /** Re-reads the manifest and downloads the chosen DASH representation(s). */
    private suspend fun downloadDashRendition(
        stored: DownloadEntity,
        downloadId: String,
        manifestUrl: String,
        selection: DashSelection,
        reuseCompleted: Boolean
    ): Transfer {
        val userAgent = sessionUserAgent(stored)
        // Segments and keys inherit the manifest's referer: they come from the same host as the
        // manifest that named them.
        val referer = sessionReferer(stored, manifestUrl)
        val origin = sessionOrigin(stored, manifestUrl)
        val manifest = dashDownloader.fetchManifest(manifestUrl, userAgent, referer, origin)
            ?: throw IOException(getString(R.string.error_dash_unsupported))
        val base = baseName(stored.filename)
        val directory = File(stored.destination).also { if (!it.exists()) it.mkdirs() }
        val progress: suspend (DownloadSnapshot) -> Unit = { snapshot ->
            handleProgress(downloadId, snapshot)
            refreshSummaryThrottled()
        }
        val abort: suspend () -> AbortReason? = { abortReasonFor(downloadId) }

        return when (selection) {
            is DashSelection.Video -> {
                val representation = manifest.representationById(selection.videoId)
                    ?: throw IOException("DASH video rendition is no longer in the manifest")
                val result = fetchDashRepresentation(representation, File(directory, "$base.dash.tmp"), userAgent, referer, origin, reuseCompleted, progress, abort)
                Transfer(result.file, representation.mimeType, remuxToMp4 = true)
            }
            is DashSelection.Audio -> {
                val representation = manifest.representationById(selection.audioId)
                    ?: throw IOException("DASH audio rendition is no longer in the manifest")
                val result = fetchDashRepresentation(representation, File(directory, "$base.dash.tmp"), userAgent, referer, origin, reuseCompleted, progress, abort)
                Transfer(result.file, representation.mimeType, remuxToMp4 = true)
            }
            is DashSelection.Merged -> {
                val videoRepresentation = manifest.representationById(selection.videoId)
                    ?: throw IOException("DASH video rendition is no longer in the manifest")
                val audioRepresentation = manifest.representationById(selection.audioId)
                    ?: throw IOException("DASH audio rendition is no longer in the manifest")
                val videoRaw = File(directory, "$base.video.tmp")
                val audioRaw = File(directory, "$base.audio.tmp")
                fetchDashRepresentation(videoRepresentation, videoRaw, userAgent, referer, origin, reuseCompleted, progress, abort)
                fetchDashRepresentation(audioRepresentation, audioRaw, userAgent, referer, origin, reuseCompleted, progress, abort)
                val merged = DownloadManager.nonConflicting(File(directory, "$base.mp4"))
                if (trackMerger.merge(videoRaw, audioRaw, merged)) {
                    videoRaw.delete(); audioRaw.delete()
                    // MediaMuxer already wrote a plain MP4, so no further container rewrite is due.
                    Transfer(merged, "video/mp4", remuxToMp4 = false)
                } else {
                    // Honest fallback: keep the picture (renamed to a real container) and drop the
                    // separate audio track rather than failing the whole download.
                    audioRaw.delete()
                    val videoOnly = DownloadManager.nonConflicting(File(directory, "$base.mp4"))
                    val moved = videoRaw.renameTo(videoOnly) || runCatching {
                        videoRaw.copyTo(videoOnly, overwrite = true)
                        videoRaw.delete()
                        true
                    }.getOrDefault(false)
                    Transfer(if (moved) videoOnly else videoRaw, videoRepresentation.mimeType, remuxToMp4 = false)
                }
            }
        }
    }

    /**
     * Turns a finished transfer into the file the user actually keeps: a segmented stream is remuxed
     * into MP4, the duration and a frame are read locally, and the result is published to the shared
     * media store when the user has that on. Every step is best-effort; none may lose the download.
     */
    private suspend fun finalizeTransfer(stored: DownloadEntity, transfer: Transfer): Finalized {
        val directory = File(stored.destination)
        val base = baseName(stored.filename)
        var file = transfer.file
        var mimeType = transfer.contentType

        if (transfer.remuxToMp4) {
            downloadRepository.updateDownloadState(stored.id, DownloadState.PROCESSING)
            refreshSummaryThrottled(force = true)
            val mp4 = DownloadManager.nonConflicting(File(directory, "$base.mp4"))
            if (mediaRemuxer.remux(file, mp4)) {
                runCatching { file.delete() }
                file = mp4
                mimeType = if (MediaUrlRules.isAudioMime(transfer.contentType)) "audio/mp4" else "video/mp4"
            }
        }

        val durationMs = mediaMetadataReader.durationMs(file)
        val thumbnailFile = File(cacheDir, "salvia_thumb_${stored.id}.jpg")
        val thumbnailPath = if (mediaMetadataReader.saveThumbnail(file, thumbnailFile)) android.net.Uri.fromFile(thumbnailFile).toString() else null

        // A locked file is published nowhere: a lock that leaks the file into a gallery or into a
        // folder anyone with the card can read is not a lock.
        var exportedUri: String? = null
        if (!stored.isLocked) {
            // The folder the user picked with the system picker comes first: it was an explicit
            // request, and the gallery export below is a setting. A tree that cannot be reached is
            // skipped rather than fatal — the download itself is already complete.
            val destination = runCatching { safDestinationStore.destination.first() }.getOrNull()
            val mode = runCatching { settingsDataStore.destinationMode.first() }
                .getOrDefault(SettingsDataStore.DESTINATION_INTERNAL)
            // In `tree` mode this file is about to *move* into that folder, so copying it here would
            // leave two identical files in the user's own folder.
            if (destination != null && mode != SettingsDataStore.DESTINATION_TREE) {
                exportedUri = safDocumentExporter.copyInto(destination.treeUri, file, mimeType, file.name)
            }
            // `tree` mode is the destination the user stated. Publishing a gallery copy *as well*
            // would mean two files where they asked for one folder; the per-file "Save to gallery"
            // action is still there for anyone who wants a copy in the gallery too.
            if (exportedUri == null && mode != SettingsDataStore.DESTINATION_TREE) {
                val exportEnabled = runCatching { settingsDataStore.isExportToGallery.first() }.getOrDefault(true)
                if (exportEnabled && mediaStoreExporter.isPermissionFree()) {
                    val isVideo = file.extension.lowercase() in MediaUrlRules.VIDEO_EXTENSIONS || MediaUrlRules.isVideoMime(mimeType)
                    exportedUri = mediaStoreExporter.export(file, file.name, mimeType, isVideo)
                }
            }
        }

        return Finalized(
            path = file.absolutePath,
            fileName = file.name,
            file = file,
            sizeBytes = file.length(),
            mimeType = mimeType,
            durationMs = durationMs,
            thumbnailPath = thumbnailPath,
            exportedUri = exportedUri
        )
    }

    /**
     * Moves a finished file into the folder the user chose, when that is the chosen destination.
     *
     * Everything above this point ran against the app's own storage on purpose: the staging, the
     * resumable parts and the remux all need a real file, and a provider can offer none of them. Only
     * the finished result moves — and if the folder cannot be reached (removed card, deleted folder,
     * revoked grant) the file simply stays where it is. A preference must never cost the user a
     * download.
     */
    private suspend fun applyDestinationPlan(finalized: Finalized): Finalized {
        val file = finalized.file ?: return finalized
        val mode = runCatching { settingsDataStore.destinationMode.first() }
            .getOrDefault(SettingsDataStore.DESTINATION_INTERNAL)
        if (mode != SettingsDataStore.DESTINATION_TREE) return finalized
        val tree = runCatching { safDestinationStore.destination.first() }.getOrNull()?.treeUri
        val plan = DestinationPolicy.decide(
            mode = mode,
            treeUri = tree,
            treeReachable = tree != null && safDocumentExporter.isReachable(tree)
        )
        if (plan !is DestinationPolicy.Plan.MoveToTree) {
            // The file stays in the app's own storage, and the user is told - a destination that
            // silently falls back is a user who cannot find their download.
            val unreachable = plan is DestinationPolicy.Plan.KeptPrivate &&
                plan.reason == DestinationPolicy.Reason.TREE_UNREACHABLE
            return if (unreachable) {
                finalized.copy(destinationNote = getString(R.string.download_folder_unreachable))
            } else {
                finalized
            }
        }
        val moved = runCatching {
            safDocumentExporter.moveInto(plan.treeUri, file, finalized.mimeType, file.name)
        }.getOrNull() ?: return finalized
        return Finalized(
            path = moved.uri,
            // The provider may have renamed the file on a collision; the row keeps the real name.
            fileName = moved.displayName,
            file = null,
            sizeBytes = finalized.sizeBytes,
            mimeType = finalized.mimeType,
            durationMs = finalized.durationMs,
            thumbnailPath = finalized.thumbnailPath,
            exportedUri = moved.uri
        )
    }

    private suspend fun abortReasonFor(downloadId: String): AbortReason? =
        when (downloadRepository.getDownloadById(downloadId)?.status) {
            DownloadState.PAUSED -> AbortReason.PAUSED
            DownloadState.CANCELLED -> AbortReason.CANCELLED
            else -> null
        }

    private suspend fun handleProgress(downloadId: String, snapshot: DownloadSnapshot) {
        progressThrottle.submit(downloadId) { persistProgress(downloadId, snapshot) }
    }

    private suspend fun pauseRequested(downloadId: String) {
        val current = downloadRepository.getDownloadById(downloadId) ?: return
        if (current.status == DownloadState.DOWNLOADING || current.status == DownloadState.PREPARING) downloadRepository.updateDownloadState(downloadId, DownloadState.PAUSED)
        downloadManager.cancel(downloadId)
        refreshSummaryThrottled(force = true)
    }

    private suspend fun cancelRequested(downloadId: String) {
        val current = downloadRepository.getDownloadById(downloadId)
        downloadManager.cancel(downloadId)
        current?.let { artifactCleaner.cleanTransferStaging(it) }
        if (current != null && current.status != DownloadState.COMPLETED && current.status != DownloadState.CANCELLED) {
            downloadRepository.updateDownloadResult(id = downloadId, status = DownloadState.CANCELLED, error = getString(R.string.download_cancel))
        }
        refreshSummaryThrottled(force = true)
        if (workQueue.settledNow()) stopWhenIdle()
    }

    private fun temporaryPathFor(download: DownloadEntity): File = File(download.destination, "${DownloadManager.sanitizeFilename(download.filename)}.${DownloadManager.PART_SUFFIX}")

    private suspend fun persistProgress(downloadId: String, snapshot: DownloadSnapshot) {
        runCatching {
            downloadRepository.updateDownloadProgress(
                downloadId,
                DownloadProgress(
                    downloadedBytes = snapshot.downloadedBytes,
                    totalBytes = snapshot.totalBytes,
                    percentage = snapshot.percentage(),
                    speed = snapshot.bytesPerSecond,
                    eta = snapshot.etaSeconds()
                )
            )
        }
    }

    private fun stopWhenIdle() {
        // A Wi-Fi hold keeps the service (and its notification) up so the transfer resumes by itself.
        if (wifiHoldActive) return
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun refreshSummaryThrottled(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastSummaryTime < 500) {
            if (!pendingSummaryRefresh) {
                pendingSummaryRefresh = true
                scope.launch {
                    kotlinx.coroutines.delay(500)
                    pendingSummaryRefresh = false
                    doRefreshSummary()
                }
            }
            return
        }
        lastSummaryTime = now
        scope.launch { doRefreshSummary() }
    }

    private suspend fun doRefreshSummary() {
        val active = runCatching { downloadRepository.getDownloadsByStates(ACTIVE_STATES).first() }.getOrNull().orEmpty()
        val downloaded = active.sumOf { it.downloadedBytes }
        val total = active.sumOf { it.totalBytes ?: 0L }
        val title = when { active.isEmpty() -> getString(R.string.downloads_title); active.size == 1 -> active.first().let { it.mediaTitle ?: it.filename }; else -> "${active.size} ${getString(R.string.downloads_title)}" }
        notificationManager.notify(notificationId, buildSummaryNotification(title, downloaded, total))
    }

    @Suppress("unused")
    private fun refreshSummary() { refreshSummaryThrottled(force = true) }

    private fun buildSummaryNotification(title: String, downloadedBytes: Long, totalBytes: Long): Notification {
        val percent = if (totalBytes > 0L) (downloadedBytes * 100 / totalBytes).toInt().coerceIn(0, 100) else 0
        return NotificationCompat.Builder(this, channelId).setContentTitle(title).setContentText(if (totalBytes > 0L) "$percent% · ${formatFileSize(downloadedBytes)} / ${formatFileSize(totalBytes)}" else getString(R.string.download_queue))
            .setSmallIcon(android.R.drawable.stat_sys_download_done).setContentIntent(openDownloadsPendingIntent(notificationId))
            .setProgress(if (totalBytes > 0L) 100 else 0, percent, totalBytes <= 0L).setOngoing(true).setOnlyAlertOnce(true).setPriority(NotificationCompat.PRIORITY_LOW).build()
    }

    private fun notifyCompleted(download: DownloadEntity, file: File?, sizeBytes: Long = 0L, note: String? = null) {
        val size = file?.length() ?: sizeBytes
        val detail = listOfNotNull("${download.mediaTitle ?: download.filename} · ${formatFileSize(size)}", note).joinToString("\n")
        val notification = NotificationCompat.Builder(this, channelId).setContentTitle(getString(R.string.download_completed)).setContentText(detail)
            .setSmallIcon(android.R.drawable.stat_sys_download_done).setContentIntent(openDownloadsPendingIntent(NOTIFICATION_COMPLETED_BASE_ID + download.id.hashCode())).setAutoCancel(true).setOngoing(false).setPriority(NotificationCompat.PRIORITY_DEFAULT).build()
        notificationManager.notify(NOTIFICATION_COMPLETED_BASE_ID + download.id.hashCode(), notification)
    }

    private fun openDownloadsPendingIntent(requestCode: Int): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP; putExtra(MainActivity.EXTRA_OPEN_DOWNLOADS, true) }
        return PendingIntent.getActivity(this, requestCode, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun scanFile(file: File) { runCatching { MediaScannerConnection.scanFile(this, arrayOf(file.absolutePath), null, null) } }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, getString(R.string.downloads_title), NotificationManager.IMPORTANCE_LOW).apply { description = getString(R.string.app_name); setShowBadge(false) }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun promoteToForeground(notification: Notification) {
        val promoted = runCatching {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> ServiceCompat.startForeground(this, notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> startForeground(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
                else -> startForeground(notificationId, notification)
            }
        }
        if (promoted.isFailure) { Log.w(TAG, "promoteToForeground failed, stopping service", promoted.exceptionOrNull()); stopSelf() }
    }

    override fun onDestroy() { super.onDestroy(); workQueue.stop(); scope.cancel() }

    companion object {
        const val ACTION_ENQUEUE = "com.salvia.salviabrowxer.action.ENQUEUE_DOWNLOAD"
        const val ACTION_PAUSE = "com.salvia.salviabrowxer.action.PAUSE_DOWNLOAD"
        const val ACTION_CANCEL = "com.salvia.salviabrowxer.action.CANCEL_DOWNLOAD"
        const val ACTION_PROCESS_QUEUE = "com.salvia.salviabrowxer.action.PROCESS_QUEUE"
        const val EXTRA_DOWNLOAD_ID = "extra_download_id"
        private const val TAG = "DownloadService"
        private const val RETRY_POLL_MILLIS = 200L
        private const val NOTIFICATION_COMPLETED_BASE_ID = 2000
        private const val NOTIFICATION_TIMEOUT_ID = 3000
        private val ACTIVE_STATES = listOf(DownloadState.QUEUED, DownloadState.RESOLVING, DownloadState.PREPARING, DownloadState.DOWNLOADING, DownloadState.RETRYING, DownloadState.PROCESSING, DownloadState.PAUSED)
        fun enqueueDownload(context: Context, downloadId: String) { start(context, ACTION_ENQUEUE, downloadId) }
        fun pauseDownload(context: Context, downloadId: String) { start(context, ACTION_PAUSE, downloadId) }
        fun cancelDownload(context: Context, downloadId: String) { start(context, ACTION_CANCEL, downloadId) }
        fun processQueue(context: Context) { start(context, ACTION_PROCESS_QUEUE, null) }
        private fun start(context: Context, action: String, downloadId: String?) {
            val intent = Intent(context, DownloadService::class.java).apply { this.action = action; downloadId?.let { putExtra(EXTRA_DOWNLOAD_ID, it) } }
            try { ContextCompat.startForegroundService(context, intent) } catch (error: IllegalStateException) { Log.w(TAG, "startForegroundService($action) blocked, falling back to startService", error); runCatching { context.startService(intent) } } catch (error: SecurityException) { Log.w(TAG, "startService($action) not permitted", error) }
        }
    }
}
