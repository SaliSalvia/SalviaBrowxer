package com.salvia.salviabrowxer.service

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Rate-limits progress writes **per download**.
 *
 * This used to be one shared timestamp and one shared timer job for the whole service. With the
 * concurrency limit at three, that meant a progress tick from download A cancelled the write that
 * download B had already scheduled (and the next tick from B cancelled A's), so the queue rows
 * jumped between percentages, rates and ETAs that belonged to different transfers. State is keyed by
 * download id now: every transfer has its own window and its own pending job.
 *
 * [submit] writes inline when the window has elapsed, so the caller's ordering is preserved, and
 * schedules a single trailing write otherwise. A later [submit] for the same download supersedes the
 * pending one instead of letting a queue of stale snapshots build up.
 *
 * The clock is injected so the behaviour is testable without waiting for real time.
 */
class ProgressPersistThrottle(
    private val scope: CoroutineScope,
    private val intervalMillis: Long = DEFAULT_INTERVAL_MILLIS,
    private val clock: () -> Long = System::currentTimeMillis
) {

    private val lastWriteAt = ConcurrentHashMap<String, Long>()
    private val pending = ConcurrentHashMap<String, Job>()

    /** Writes now if this download's window has elapsed, otherwise schedules one trailing write. */
    suspend fun submit(downloadId: String, persist: suspend () -> Unit) {
        val now = clock()
        val last = lastWriteAt[downloadId] ?: 0L
        if (now - last >= intervalMillis) {
            lastWriteAt[downloadId] = now
            pending.remove(downloadId)?.cancel()
            persist()
            return
        }
        pending.remove(downloadId)?.cancel()
        pending[downloadId] = scope.launch {
            delay(intervalMillis)
            lastWriteAt[downloadId] = clock()
            persist()
        }
    }

    /**
     * Drops a download's window and cancels its pending write. Called when a transfer ends so a
     * finished row cannot receive one more late progress update, and so the maps stay bounded by the
     * number of transfers actually in flight.
     */
    fun forget(downloadId: String) {
        pending.remove(downloadId)?.cancel()
        lastWriteAt.remove(downloadId)
    }

    companion object {
        /** How often a download's progress is allowed to reach Room. */
        const val DEFAULT_INTERVAL_MILLIS = 800L
    }
}
