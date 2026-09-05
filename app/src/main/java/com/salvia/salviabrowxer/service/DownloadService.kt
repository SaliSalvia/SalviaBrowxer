package com.salvia.salviabrowxer.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.salvia.salviabrowxer.MainActivity
import com.salvia.salviabrowxer.core.database.entities.DownloadEntity
import com.salvia.salviabrowxer.core.model.DownloadProgress
import com.salvia.salviabrowxer.core.model.DownloadState
import com.salvia.salviabrowxer.data.repository.DownloadRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class DownloadService : Service() {

    @Inject
    lateinit var downloadRepository: DownloadRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val notificationManager by lazy {
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    private val notificationId = 1
    private val channelId = "download_channel"

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        serviceScope.launch {
            downloadRepository.getDownloadsByStates(
                listOf(
                    DownloadState.QUEUED,
                    DownloadState.RESOLVING,
                    DownloadState.PREPARING,
                    DownloadState.DOWNLOADING,
                    DownloadState.PAUSED,
                    DownloadState.RETRYING,
                    DownloadState.PROCESSING
                )
            ).collectLatest { downloads ->
                downloads.forEach { download ->
                    handleDownload(download)
                }
            }
        }

        return START_STICKY
    }

    private fun handleDownload(download: DownloadEntity) {
        serviceScope.launch {
            when (download.status) {
                DownloadState.QUEUED -> {
                    downloadRepository.updateDownloadState(download.id, DownloadState.RESOLVING)
                    simulateDownload(download)
                }
                DownloadState.PAUSED -> {
                    // TODO: Resume download
                }
                DownloadState.RETRYING -> {
                    downloadRepository.updateDownloadState(download.id, DownloadState.QUEUED)
                }
                else -> {
                    // Already being processed
                }
            }
        }
    }

    private suspend fun simulateDownload(download: DownloadEntity) {
        val totalBytes = download.totalBytes ?: 1024L * 1024L * 10
        val downloadedBytes = download.downloadedBytes
        val startBytes = downloadedBytes

        downloadRepository.updateDownloadState(download.id, DownloadState.DOWNLOADING)

        repeat(10) { step ->
            val progressBytes = startBytes + (totalBytes * (step + 1) / 10)
            downloadRepository.updateDownloadProgress(
                download.id,
                DownloadProgress(
                    downloadedBytes = progressBytes,
                    totalBytes = totalBytes,
                    percentage = (progressBytes.toFloat() / totalBytes) * 100,
                    speed = (totalBytes / 10).toLong(),
                    eta = ((totalBytes - progressBytes) / (totalBytes / 10)) * 1000
                )
            )
            updateNotification(download, progressBytes, totalBytes)
            kotlin.runCatching { Thread.sleep(1000) }
        }

        downloadRepository.updateDownloadState(download.id, DownloadState.COMPLETED)
        updateNotification(download, totalBytes, totalBytes)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Download notifications"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun startForeground() {
        val notification = createNotification("Download Service", "Managing downloads", 0, 0)
        startForeground(notificationId, notification)
    }

    private fun updateNotification(download: DownloadEntity, downloadedBytes: Long, totalBytes: Long) {
        val progress = if (totalBytes > 0) (downloadedBytes * 100 / totalBytes).toInt() else 0
        val notification = createNotification(
            download.mediaTitle ?: download.filename,
            "$progress%",
            downloadedBytes,
            totalBytes
        )
        notificationManager.notify(download.id.hashCode(), notification)
    }

    private fun createNotification(
        title: String,
        message: String,
        downloadedBytes: Long,
        totalBytes: Long
    ): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentIntent(pendingIntent)
            .setProgress(totalBytes.toInt(), downloadedBytes.toInt(), false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}