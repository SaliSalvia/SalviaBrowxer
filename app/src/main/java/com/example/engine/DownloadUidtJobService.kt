package com.example.engine

import android.app.job.JobParameters
import android.app.job.JobService
import android.os.Build
import android.webkit.CookieManager
import androidx.annotation.RequiresApi
import com.example.data.local.DownloadEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * API 34+ executor for downloads explicitly started by the user.
 *
 * The scheduler attaches a user-visible notification immediately in onStartJob.
 * Room remains authoritative; if the process is stopped, the system may reschedule
 * this UIDT job and the same DownloadEntity/staging files are reused.
 */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
class DownloadUidtJobService : JobService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val activeJobs = ConcurrentHashMap<Long, Job>()

    override fun onStartJob(params: JobParameters): Boolean {
        val downloadId = params.extras.getLong(EXTRA_DOWNLOAD_ID, -1L)
        if (downloadId <= 0L) return false

        activeJobs[downloadId]?.takeIf { it.isActive }?.let { return true }

        val fileName = params.extras.getString(EXTRA_FILE_NAME).orEmpty().ifBlank {
            "SalviaBrowxer download"
        }
        setNotification(
            params,
            DownloadNotificationHelper.notificationId(downloadId),
            DownloadNotificationHelper.build(applicationContext, downloadId, fileName),
            JOB_END_NOTIFICATION_POLICY_DETACH,
        )

        val job = serviceScope.launch {
            try {
                val repository = DownloadRuntime.repository(applicationContext)
                val download = withContext(Dispatchers.IO) {
                    repository.getDownloadById(downloadId)
                }
                if (download == null || download.isIncognito ||
                    download.status in setOf("COMPLETED", "CANCELLED", "FAILED", "PAUSED")
                ) {
                    jobFinished(params, false)
                    return@launch
                }

                val userAgent = WebViewConfigurator.resolveUserAgent(
                    applicationContext,
                    desktopMode = download.isDesktopMode,
                )
                withContext(Dispatchers.IO) {
                    DownloadRuntime.manager(applicationContext).executePersistedDownload(
                        downloadId = downloadId,
                        userAgent = userAgent,
                        sessionCookieManager = CookieManager.getInstance(),
                        allowGlobalCookieFallback = true,
                    )
                }
                jobFinished(params, false)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // The manager persists FAILED/QUEUED state; leave the OS job terminal.
                jobFinished(params, false)
            }
        }

        activeJobs[downloadId] = job
        job.invokeOnCompletion { activeJobs.remove(downloadId, job) }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        val downloadId = params.extras.getLong(EXTRA_DOWNLOAD_ID, -1L)
        if (downloadId > 0L) {
            activeJobs.remove(downloadId)?.cancel()
            serviceScope.launch(Dispatchers.IO) {
                DownloadRuntime.manager(applicationContext).stopForSystemRetry(downloadId)
            }
        }
        // Retry system interruptions. Explicit user pause/cancel is already
        // persisted in Room before the coordinator cancels the JobScheduler ID.
        return true
    }

    override fun onDestroy() {
        activeJobs.values.forEach(Job::cancel)
        activeJobs.clear()
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_DOWNLOAD_ID = "salvia_uidt_download_id"
        const val EXTRA_FILE_NAME = "salvia_uidt_file_name"
    }
}
