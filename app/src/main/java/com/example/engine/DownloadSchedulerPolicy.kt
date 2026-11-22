package com.example.engine

internal enum class DownloadSchedulerBackend {
    USER_INITIATED_JOB,
    WORK_MANAGER,
}

internal object DownloadSchedulerPolicy {

    fun backendForSdk(apiLevel: Int): DownloadSchedulerBackend =
        if (apiLevel >= 34) {
            DownloadSchedulerBackend.USER_INITIATED_JOB
        } else {
            DownloadSchedulerBackend.WORK_MANAGER
        }

    fun shouldRescheduleQueuedWorkOnAppStart(apiLevel: Int): Boolean = apiLevel < 34

    fun jobIdFor(downloadId: Long): Int {
        require(downloadId > 0L) { "Download ID must be positive" }
        return (downloadId % 1_000_000_000L).toInt().coerceAtLeast(1)
    }

    fun uniqueWorkName(downloadId: Long): String {
        require(downloadId > 0L) { "Download ID must be positive" }
        return "salvia-download-$downloadId"
    }
}
