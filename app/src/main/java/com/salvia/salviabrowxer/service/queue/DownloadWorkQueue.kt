package com.salvia.salviabrowxer.service.queue

import com.salvia.salviabrowxer.core.concurrent.SlotLimiter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * The download service's queue: one id at a time leaves it, [limiter] decides how many run at once,
 * and [onIdle] fires when the last transfer of the batch is finished and nothing else is waiting.
 *
 * This is the part of `DownloadService` that had already produced two real bugs, both of which are
 * about *when* an id counts as in flight:
 *
 *  - an id must count as in flight the moment it leaves the channel, before the first suspension
 *    (the Room read, the Wi-Fi check). It used to be claimed later, and in that window a finishing
 *    transfer saw an empty queue and an idle counter, called `stopSelf()`, and cancelled the
 *    transfer that had just started;
 *  - a transfer that fails must release its slot and still let the queue continue. A thrown
 *    exception inside the processing coroutine used to be able to skip both.
 *
 * It is deliberately free of Android types: the queue, the semaphore and the idle condition are all
 * testable on the JVM, which is where the two bugs above are now pinned down.
 */
class DownloadWorkQueue(
    private val limiter: SlotLimiter,
    private val process: suspend (String) -> Unit,
    private val onIdle: () -> Unit = {}
) {

    private val queue = Channel<String>(Channel.UNLIMITED)
    private val inFlight = AtomicInteger(0)
    private var processorJob: Job? = null

    /**
     * The ids a running transfer owns. A re-enqueued copy of an id that is already claimed is
     * skipped instead of started: the queue screen requeues every resumable row on open, so an
     * actively downloading row is handed to the channel again while its transfer is running, and a
     * double-tapped resume does the same. Two transfers against one row would write the same
     * `.part` file from two connections at once.
     */
    private val claimed = ConcurrentHashMap.newKeySet<String>()

    /** How many ids have been taken out of the queue and not yet finished. */
    val inFlightCount: Int get() = inFlight.get()

    /** Whether there is queued work waiting for a slot. */
    val hasQueued: Boolean get() = !queue.isEmpty

    /** Idle means nothing running *and* nothing waiting — the only state in which the service stops. */
    val isIdle: Boolean get() = inFlight.get() == 0 && queue.isEmpty

    /** Never blocks and never loses an id: the channel is unbounded on purpose. */
    fun enqueue(downloadId: String): Boolean = queue.trySend(downloadId).isSuccess

    /**
     * Starts the processor loop if it is not already running. Safe to call on every command — the
     * loop is single-instance, so two starts do not double-process the queue.
     */
    fun start(scope: CoroutineScope) {
        if (processorJob?.isActive == true) return
        processorJob = scope.launch {
            while (isActive) {
                val downloadId = queue.receive()
                inFlight.incrementAndGet()
                launch {
                    try {
                        // The claim happens the moment the id leaves the channel, before the limiter
                        // wait, so the window in which a re-enqueued copy could start a duplicate
                        // transfer does not exist. The copy that owns the claim owns the row.
                        if (claimed.add(downloadId)) {
                            try {
                                limiter.acquire()
                                try {
                                    process(downloadId)
                                } finally {
                                    limiter.release()
                                }
                            } finally {
                                claimed.remove(downloadId)
                            }
                        }
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (_: Exception) {
                        // A failed transfer is the row's business (it is marked FAILED there); it must
                        // not take the queue down with it.
                    } finally {
                        val idle = inFlight.decrementAndGet() == 0 && queue.isEmpty
                        // isActive, so a cancelled processor does not report an idle batch while the
                        // service is shutting down — that is what would make the callback run
                        // `stopSelf` on a service that is already stopping.
                        if (idle && isActive) onIdle()
                    }
                }
            }
        }
    }

    /** Stops the processor loop; queued ids stay in the channel for the next [start]. */
    fun stop() {
        processorJob?.cancel()
        processorJob = null
    }

    /** True when the queue has nothing to do *now*: used by cancel/pause paths to stop the service. */
    fun settledNow(): Boolean = isIdle
}
