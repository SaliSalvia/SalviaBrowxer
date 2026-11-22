package com.example.engine

import android.content.pm.ServiceInfo
import android.os.Build
import android.webkit.CookieManager
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.example.data.local.DownloadEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * Compatibility executor for API 24–33. API 34+ user-initiated transfers are
 * scheduled by DownloadUidtJobService instead, avoiding Android 16 job quotas
 * applied to long-running foreground workers.
 */
class DownloadWorker(
    appContext: android.content.Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val downloadId = inputData.getLong(KEY_DOWNLOAD_ID, -1L)
        if (downloadId <= 0L) return Result.failure()

        val repository = DownloadRuntime.repository(applicationContext)
        val manager = DownloadRuntime.manager(applicationContext)
        val download = repository.getDownloadById(downloadId) ?: return Result.failure()

        if (download.isIncognito) {
            // Private transfers are intentionally never persisted in WorkManager.
            return Result.failure()
        }

        when (download.status) {
            "COMPLETED" -> return Result.success()
            "CANCELLED", "FAILED", "PAUSED" -> return Result.success()
        }

        try {
            setForeground(createForegroundInfo(download))
            val userAgent = WebViewConfigurator.resolveUserAgent(
                applicationContext,
                desktopMode = download.isDesktopMode,
            )
            val result = manager.executePersistedDownload(
                downloadId = download.id,
                userAgent = userAgent,
                sessionCookieManager = CookieManager.getInstance(),
                allowGlobalCookieFallback = true,
            )
            return when (result?.status) {
                "COMPLETED" -> Result.success()
                "FAILED", "CANCELLED" -> Result.failure()
                "PAUSED" -> Result.success()
                else -> Result.retry()
            }
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable + Dispatchers.IO) {
                manager.stopForSystemRetry(downloadId)
            }
            throw cancelled
        } catch (_: Exception) {
            val latest = repository.getDownloadById(downloadId)
            return if (latest?.status in setOf("COMPLETED", "FAILED", "CANCELLED", "PAUSED")) {
                if (latest?.status == "COMPLETED") Result.success() else Result.failure()
            } else {
                Result.retry()
            }
        }
    }

    private fun createForegroundInfo(download: DownloadEntity): ForegroundInfo {
        val notification = DownloadNotificationHelper.build(
            applicationContext,
            download.id,
            download.fileName,
        )
        val id = DownloadNotificationHelper.notificationId(download.id)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(id, notification)
        }
    }

    companion object {
        const val KEY_DOWNLOAD_ID = "salvia_download_id"
        fun uniqueWorkName(downloadId: Long) = "salvia-download-$downloadId"
    }
}
