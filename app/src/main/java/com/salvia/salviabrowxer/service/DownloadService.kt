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
import com.salvia.salviabrowxer.core.database.entities.DownloadEntity
import com.salvia.salviabrowxer.core.model.DownloadProgress
import com.salvia.salviabrowxer.core.model.DownloadState
import com.salvia.salviabrowxer.data.repository.DownloadRepository
import com.salvia.salviabrowxer.media.downloader.AbortReason
import com.salvia.salviabrowxer.media.downloader.DownloadAbortedException
import com.salvia.salviabrowxer.media.downloader.DownloadManager
import com.salvia.salviabrowxer.media.downloader.FileDownload
import com.salvia.salviabrowxer.ui.utils.formatFileSize
import dagger.hilt.android.AndroidEntryPoint
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

/**
 * Foreground service that owns the download queue. Each entry is transferred with OkHttp by
 * [DownloadManager]; progress, pause/cancel state and the final path are stored in Room, so the
 * UI and the service share a single source of truth instead of racing on an in-memory copy.
 */
@AndroidEntryPoint
class DownloadService : Service() {

    @Inject
    lateinit var downloadRepository: DownloadRepository

    @Inject
    lateinit var downloadManager: DownloadManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val queue = Channel<String>(Channel.UNLIMITED)
    private val inFlight = AtomicInteger(0)
    private var processorJob: Job? = null

    private val notificationId = 1
    private val channelId = "download_channel"

    private val notificationManager: NotificationManager
        get() = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        promoteToForeground(buildSummaryNotification(getString(R.string.downloads_title), 0L, 0L))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val downloadId = intent?.getStringExtra(EXTRA_DOWNLOAD_ID)
        when (intent?.action) {
            ACTION_ENQUEUE -> if (downloadId != null) queue.trySend(downloadId) else recoverQueue()
            ACTION_PAUSE -> if (downloadId != null) scope.launch { pauseRequested(downloadId) }
            ACTION_CANCEL -> if (downloadId != null) scope.launch { cancelRequested(downloadId) }
            ACTION_PROCESS_QUEUE -> recoverQueue()
            else -> if (downloadId != null) queue.trySend(downloadId) else recoverQueue()
        }
        startQueueProcessor()
        return START_STICKY
    }

    /** Re-queues everything that is still waiting, e.g. after the process was killed. */
    private fun recoverQueue() {
        scope.launch {
            val pending = runCatching {
                downloadRepository.getDownloadsByStates(
                    listOf(DownloadState.QUEUED, DownloadState.RETRYING, DownloadState.PREPARING)
                ).first()
            }.getOrNull().orEmpty()

            if (pending.isEmpty()) {
                // Nothing to do: never keep a foreground service alive for an empty queue.
                if (inFlight.get() == 0) stopWhenIdle()
                return@launch
            }
            pending.forEach { download -> queue.trySend(download.id) }
        }
    }

    private fun startQueueProcessor() {
        if (processorJob?.isActive == true) return
        processorJob = scope.launch {
            while (isActive) {
                val downloadId = queue.receive()
                try {
                    processDownload(downloadId)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (error: Exception) {
                    Log.w(TAG, "Download $downloadId failed", error)
                }
                if (inFlight.get() == 0 && queue.isEmpty) {
                    stopWhenIdle()
                    return@launch
                }
            }
        }
    }

    private suspend fun processDownload(downloadId: String) {
        val stored = downloadRepository.getDownloadById(downloadId) ?: return
        if (stored.status == DownloadState.COMPLETED || stored.status == DownloadState.CANCELLED) return

        inFlight.incrementAndGet()
        try {
            downloadRepository.updateDownloadState(downloadId, DownloadState.PREPARING)

            val finalUrl = runCatching { downloadManager.getFinalUrl(stored.url) }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?: stored.url

            val preparing = stored.copy(
                finalUrl = finalUrl,
                status = DownloadState.DOWNLOADING,
                error = null,
                temporaryPath = temporaryPathFor(stored).absolutePath,
                updatedAt = System.currentTimeMillis()
            )
            downloadRepository.updateDownload(preparing)
            refreshSummary()

            val result = downloadManager.download(
                download = FileDownload(
                    id = stored.id,
                    url = finalUrl,
                    directory = stored.destination,
                    filename = stored.filename
                ),
                onProgress = { snapshot ->
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
                    refreshSummary()
                },
                shouldAbort = {
                    when (downloadRepository.getDownloadById(downloadId)?.status) {
                        DownloadState.PAUSED -> AbortReason.PAUSED
                        DownloadState.CANCELLED -> AbortReason.CANCELLED
                        else -> null
                    }
                }
            )

            downloadRepository.updateDownloadResult(
                id = downloadId,
                status = DownloadState.COMPLETED,
                downloadedBytes = result.bytesWritten,
                totalBytes = result.totalBytes,
                finalPath = result.file.absolutePath,
                mimeType = result.contentType
            )
            temporaryPathFor(stored).delete()
            scanFile(result.file)
            notifyCompleted(stored, result.file)
        } catch (aborted: DownloadAbortedException) {
            if (aborted.reason == AbortReason.CANCELLED) {
                temporaryPathFor(stored).delete()
                downloadRepository.updateDownloadResult(
                    id = downloadId,
                    status = DownloadState.CANCELLED,
                    error = getString(R.string.download_cancel)
                )
            } else {
                downloadRepository.updateDownloadState(downloadId, DownloadState.PAUSED)
            }
        } catch (network: IOException) {
            downloadRepository.updateDownloadResult(
                id = downloadId,
                status = DownloadState.FAILED,
                error = network.localizedMessage ?: getString(R.string.error_connection_unavailable)
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } finally {
            inFlight.decrementAndGet()
            refreshSummary()
        }
    }

    private suspend fun pauseRequested(downloadId: String) {
        val current = downloadRepository.getDownloadById(downloadId) ?: return
        if (current.status == DownloadState.DOWNLOADING || current.status == DownloadState.PREPARING) {
            downloadRepository.updateDownloadState(downloadId, DownloadState.PAUSED)
        }
        downloadManager.cancel(downloadId)
        refreshSummary()
    }

    private suspend fun cancelRequested(downloadId: String) {
        val current = downloadRepository.getDownloadById(downloadId)
        downloadManager.cancel(downloadId)
        current?.let { temporaryPathFor(it).delete() }
        if (current != null &&
            current.status != DownloadState.COMPLETED &&
            current.status != DownloadState.CANCELLED
        ) {
            downloadRepository.updateDownloadResult(
                id = downloadId,
                status = DownloadState.CANCELLED,
                error = getString(R.string.download_cancel)
            )
        }
        refreshSummary()
        if (inFlight.get() == 0 && queue.isEmpty) stopWhenIdle()
    }

    private fun temporaryPathFor(download: DownloadEntity): File = File(
        download.destination,
        "${DownloadManager.sanitizeFilename(download.filename)}.${DownloadManager.PART_SUFFIX}"
    )

    private fun stopWhenIdle() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun refreshSummary() {
        scope.launch {
            val active = runCatching {
                downloadRepository.getDownloadsByStates(ACTIVE_STATES).first()
            }.getOrNull().orEmpty()

            val downloaded = active.sumOf { it.downloadedBytes }
            val total = active.sumOf { it.totalBytes ?: 0L }
            val title = when {
                active.isEmpty() -> getString(R.string.downloads_title)
                active.size == 1 -> active.first().let { it.mediaTitle ?: it.filename }
                else -> "${active.size} ${getString(R.string.downloads_title)}"
            }
            notificationManager.notify(notificationId, buildSummaryNotification(title, downloaded, total))
        }
    }

    private fun buildSummaryNotification(
        title: String,
        downloadedBytes: Long,
        totalBytes: Long
    ): Notification {
        val percent = if (totalBytes > 0L) {
            (downloadedBytes * 100 / totalBytes).toInt().coerceIn(0, 100)
        } else {
            0
        }

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle(title)
            .setContentText(if (totalBytes > 0L) "$percent%" else getString(R.string.download_queue))
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentIntent(openDownloadsPendingIntent(notificationId))
            .setProgress(if (totalBytes > 0L) 100 else 0, percent, totalBytes <= 0L)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun notifyCompleted(download: DownloadEntity, file: File) {
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(getString(R.string.download_completed))
            .setContentText(
                "${download.mediaTitle ?: download.filename} · ${formatFileSize(file.length())}"
            )
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentIntent(openDownloadsPendingIntent(NOTIFICATION_COMPLETED_BASE_ID + download.id.hashCode()))
            .setAutoCancel(true)
            .setOngoing(false)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        notificationManager.notify(
            NOTIFICATION_COMPLETED_BASE_ID + download.id.hashCode(),
            notification
        )
    }

    private fun openDownloadsPendingIntent(requestCode: Int): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_DOWNLOADS, true)
        }
        return PendingIntent.getActivity(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun scanFile(file: File) {
        runCatching {
            MediaScannerConnection.scanFile(this, arrayOf(file.absolutePath), null, null)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                getString(R.string.downloads_title),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.app_name)
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun promoteToForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(
                this,
                notificationId,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                notificationId,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(notificationId, notification)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        processorJob?.cancel()
        scope.cancel()
    }

    companion object {
        const val ACTION_ENQUEUE = "com.salvia.salviabrowxer.action.ENQUEUE_DOWNLOAD"
        const val ACTION_PAUSE = "com.salvia.salviabrowxer.action.PAUSE_DOWNLOAD"
        const val ACTION_CANCEL = "com.salvia.salviabrowxer.action.CANCEL_DOWNLOAD"
        const val ACTION_PROCESS_QUEUE = "com.salvia.salviabrowxer.action.PROCESS_QUEUE"
        const val EXTRA_DOWNLOAD_ID = "extra_download_id"

        private const val TAG = "DownloadService"
        private const val NOTIFICATION_COMPLETED_BASE_ID = 2000

        private val ACTIVE_STATES = listOf(
            DownloadState.QUEUED,
            DownloadState.RESOLVING,
            DownloadState.PREPARING,
            DownloadState.DOWNLOADING,
            DownloadState.RETRYING,
            DownloadState.PROCESSING,
            DownloadState.PAUSED
        )

        fun enqueueDownload(context: Context, downloadId: String) {
            start(context, ACTION_ENQUEUE, downloadId)
        }

        fun pauseDownload(context: Context, downloadId: String) {
            start(context, ACTION_PAUSE, downloadId)
        }

        fun cancelDownload(context: Context, downloadId: String) {
            start(context, ACTION_CANCEL, downloadId)
        }

        fun processQueue(context: Context) {
            start(context, ACTION_PROCESS_QUEUE, null)
        }

        private fun start(context: Context, action: String, downloadId: String?) {
            val intent = Intent(context, DownloadService::class.java).apply {
                this.action = action
                downloadId?.let { putExtra(EXTRA_DOWNLOAD_ID, it) }
            }
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
