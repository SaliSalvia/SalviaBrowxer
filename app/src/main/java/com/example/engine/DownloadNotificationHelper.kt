package com.example.engine

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.local.DownloadEntity

object DownloadNotificationHelper {

    const val CHANNEL_ID = "salvia_downloads"

    fun notificationId(downloadId: Long): Int =
        10_000 + (downloadId % 1_000_000L).toInt()

    fun build(context: Context, downloadId: Long, fileName: String): Notification =
        build(context, downloadId, fileName, downloadedBytes = 0L, totalBytes = -1L)

    fun build(context: Context, download: DownloadEntity): Notification =
        build(
            context = context,
            downloadId = download.id,
            fileName = download.fileName,
            downloadedBytes = download.downloadedBytes,
            totalBytes = download.totalBytes,
        )

    private fun build(
        context: Context,
        downloadId: Long,
        fileName: String,
        downloadedBytes: Long,
        totalBytes: Long,
    ): Notification {
        ensureChannel(context)
        val openApp = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            notificationId(downloadId),
            openApp,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val hasKnownTotal = totalBytes > 0L
        val percent = if (hasKnownTotal) {
            (
                downloadedBytes.coerceAtLeast(0L).toDouble() /
                    totalBytes.toDouble() * 100.0
                ).toInt().coerceIn(0, 100)
        } else {
            0
        }
        val text = if (hasKnownTotal) {
            context.getString(R.string.download_notification_progress, fileName, percent)
        } else {
            context.getString(R.string.download_notification_text, fileName)
        }
        val pauseIntent = actionPendingIntent(context, downloadId, DownloadActionReceiver.ACTION_PAUSE)
        val cancelIntent = actionPendingIntent(context, downloadId, DownloadActionReceiver.ACTION_CANCEL)

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(context.getString(R.string.download_notification_title))
            .setContentText(text)
            .setContentIntent(contentIntent)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(if (hasKnownTotal) 100 else 0, percent, !hasKnownTotal)
            .addAction(
                NotificationCompat.Action.Builder(
                    android.R.drawable.ic_media_pause,
                    context.getString(R.string.download_notification_pause),
                    pauseIntent,
                ).build(),
            )
            .addAction(
                NotificationCompat.Action.Builder(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    context.getString(R.string.download_notification_cancel),
                    cancelIntent,
                ).build(),
            )
            .build()
    }

    private fun actionPendingIntent(context: Context, downloadId: Long, action: String): PendingIntent {
        val intent = Intent(context, DownloadActionReceiver::class.java).apply {
            this.action = action
            putExtra(DownloadActionReceiver.EXTRA_DOWNLOAD_ID, downloadId)
        }
        return PendingIntent.getBroadcast(
            context,
            notificationId(downloadId) + if (action == DownloadActionReceiver.ACTION_PAUSE) 1 else 2,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.download_notification_channel),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(R.string.download_notification_channel)
                setShowBadge(false)
            },
        )
    }
}
