package com.example.engine

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import android.webkit.CookieManager
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.data.local.BrowserRepository
import com.example.data.local.DownloadEntity
import com.example.ui.MediaQualityOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Durable facade between the existing Download UI and the platform schedulers.
 *
 * Room is the source of truth. Normal downloads are executed by UIDT jobs on
 * API 34+ and WorkManager foreground workers on API 24–33. Private downloads
 * stay inside the live private WebView profile and are intentionally ephemeral.
 */
class DownloadCoordinator(context: Context) {

    private val appContext = context.applicationContext
    private val repository: BrowserRepository = DownloadRuntime.repository(appContext)
    private val manager: MediaDownloadManager = DownloadRuntime.manager(appContext)
    private val workManager: WorkManager = WorkManager.getInstance(appContext)
    private val jobScheduler: JobScheduler? =
        appContext.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler

    suspend fun enqueue(
        fileName: String,
        quality: MediaQualityOption,
        sourcePageUrl: String,
        userAgent: String,
        sessionCookieManager: CookieManager?,
        isIncognito: Boolean,
        isDesktopMode: Boolean,
    ): Long = withContext(Dispatchers.IO) {
        val id = manager.createDownloadRecord(
            fileName = fileName,
            quality = quality,
            sourcePageUrl = sourcePageUrl,
            isIncognito = isIncognito,
            isDesktopMode = isDesktopMode,
        )

        if (isIncognito) {
            // No persistent scheduler or cookie transfer for private sessions.
            manager.executePersistedDownloadAsync(
                downloadId = id,
                userAgent = userAgent,
                sessionCookieManager = sessionCookieManager,
                allowGlobalCookieFallback = false,
            )
        } else {
            scheduleNormalDownload(id, replaceExisting = false)
        }
        id
    }

    suspend fun pause(download: DownloadEntity) = withContext(Dispatchers.IO) {
        manager.pauseDownloadAndWait(download)
        cancelScheduledWork(download.id)
    }

    suspend fun resume(
        download: DownloadEntity,
        privateCookieManager: CookieManager? = null,
        privateUserAgent: String? = null,
    ) = withContext(Dispatchers.IO) {
        manager.markDownloadQueued(download.id)
        if (download.isIncognito) {
            if (privateCookieManager == null || privateUserAgent.isNullOrBlank()) {
                repository.updateDownloadStatus(download.id, "PAUSED")
                return@withContext
            }
            manager.resumeDownload(
                download = download,
                userAgent = privateUserAgent,
                sessionCookieManager = privateCookieManager,
                allowGlobalCookieFallback = false,
            )
        } else {
            scheduleNormalDownload(download.id, replaceExisting = true)
        }
    }

    suspend fun cancel(download: DownloadEntity) = withContext(Dispatchers.IO) {
        manager.cancelDownloadAndWait(download)
        cancelScheduledWork(download.id)
    }

    suspend fun clearIncognitoDownloads() {
        manager.clearIncognitoDownloads()
    }

    /**
     * Reattaches queued Room rows to the platform scheduler after app startup.
     * A stale DOWNLOADING row with no active executor is marked PAUSED rather
     * than silently restarted, so explicit user-stop/process-death states do
     * not surprise the user. The user can resume from the existing Downloads UI.
     */
    suspend fun recoverPendingDownloads() = withContext(Dispatchers.IO) {
        val pending = repository.getPendingNormalDownloads()
        pending.filter { it.status == "QUEUED" }.forEach { download ->
            scheduleNormalDownload(download.id, replaceExisting = false)
        }

        pending.filter { it.status == "DOWNLOADING" }.forEach { download ->
            delay(1_500L)
            if (manager.isDownloadExecuting(download.id)) return@forEach
            if (hasPlatformWork(download.id)) return@forEach
            repository.updateDownloadStatus(download.id, "PAUSED")
        }
    }

    private suspend fun scheduleNormalDownload(
        downloadId: Long,
        replaceExisting: Boolean,
    ) {
        val download = repository.getDownloadById(downloadId) ?: return
        if (download.isIncognito) return

        when (DownloadSchedulerPolicy.backendForSdk(Build.VERSION.SDK_INT)) {
            DownloadSchedulerBackend.USER_INITIATED_JOB ->
                scheduleUserInitiatedTransfer(download, replaceExisting)
            DownloadSchedulerBackend.WORK_MANAGER ->
                scheduleWorkManager(download, replaceExisting)
        }
    }

    private fun scheduleUserInitiatedTransfer(
        download: DownloadEntity,
        replaceExisting: Boolean,
    ) {
        val scheduler = jobScheduler ?: throw IllegalStateException("JobScheduler is unavailable")
        val jobId = jobIdFor(download.id)
        if (!replaceExisting && scheduler.getPendingJob(jobId) != null) return
        if (replaceExisting) scheduler.cancel(jobId)

        val extras = PersistableBundle().apply {
            putLong(DownloadUidtJobService.EXTRA_DOWNLOAD_ID, download.id)
            putString(DownloadUidtJobService.EXTRA_FILE_NAME, download.fileName)
        }
        val builder = JobInfo.Builder(
            jobId,
            ComponentName(appContext, DownloadUidtJobService::class.java),
        )
            .setUserInitiated(true)
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setExtras(extras)

        if (download.totalBytes > 0L) {
            builder.setEstimatedNetworkBytes(download.totalBytes, 0L)
        }

        val result = scheduler.schedule(builder.build())
        if (result != JobScheduler.RESULT_SUCCESS) {
            throw IllegalStateException("Android did not accept the user-initiated download job")
        }
    }

    private fun scheduleWorkManager(
        download: DownloadEntity,
        replaceExisting: Boolean,
    ) {
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(workDataOf(DownloadWorker.KEY_DOWNLOAD_ID to download.id))
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10L, TimeUnit.SECONDS)
            .build()

        workManager.enqueueUniqueWork(
            DownloadSchedulerPolicy.uniqueWorkName(download.id),
            if (replaceExisting) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
            request,
        )
    }

    private fun hasPlatformWork(downloadId: Long): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            jobScheduler?.getPendingJob(jobIdFor(downloadId)) != null
        } else {
            runCatching {
                workManager.getWorkInfosForUniqueWork(DownloadSchedulerPolicy.uniqueWorkName(downloadId))
                    .get(5, TimeUnit.SECONDS)
                    .any { !it.state.isFinished }
            }.getOrDefault(false)
        }
    }

    private fun cancelScheduledWork(downloadId: Long) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            jobScheduler?.cancel(jobIdFor(downloadId))
        } else {
            workManager.cancelUniqueWork(DownloadWorker.uniqueWorkName(downloadId))
        }
    }

    private fun jobIdFor(downloadId: Long): Int =
        DownloadSchedulerPolicy.jobIdFor(downloadId)
}
