package com.example.engine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.app.NotificationManager

/**
 * Notification actions are processed outside the UI lifecycle so they still
 * work when BrowserActivity is not visible.
 */
class DownloadActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val downloadId = intent.getLongExtra(EXTRA_DOWNLOAD_ID, -1L)
        if (downloadId <= 0L || action !in setOf(ACTION_PAUSE, ACTION_CANCEL)) return

        val pendingResult = goAsync()
        DownloadRuntime.launchMaintenance {
            try {
                val repository = DownloadRuntime.repository(context)
                val download = repository.getDownloadById(downloadId) ?: return@launchMaintenance
                val coordinator = DownloadCoordinator(context)
                when (action) {
                    ACTION_PAUSE -> coordinator.pause(download)
                    ACTION_CANCEL -> coordinator.cancel(download)
                }
            } finally {
                (context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager)
                    ?.cancel(DownloadNotificationHelper.notificationId(downloadId))
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_PAUSE = "com.example.action.PAUSE_DOWNLOAD"
        const val ACTION_CANCEL = "com.example.action.CANCEL_DOWNLOAD"
        const val EXTRA_DOWNLOAD_ID = "download_id"
    }
}
