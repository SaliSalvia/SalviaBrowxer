package com.salvia.salviabrowxer.service.queue

import com.salvia.salviabrowxer.core.concurrent.SlotLimiter
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The queue behaviour that `DownloadService` used to own inline: how many transfers run at once,
 * when an id counts as in flight, that a failure cannot stall the line, and that the idle callback
 * fires exactly once per batch.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DownloadWorkQueueTest {

    private class Recorder {
        val started = mutableListOf<String>()
        val finished = mutableListOf<String>()
        val running = AtomicInteger(0)
        val maxRunning = AtomicInteger(0)
        val idleCalls = AtomicInteger(0)
    }

    @Test
    fun `the limiter caps how many transfers run at once`() = runTest {
        val recorder = Recorder()
        val gate = CompletableDeferred<Unit>()
        val limiter = SlotLimiter(2)
        val queue = DownloadWorkQueue(
            limiter = limiter,
            process = { id ->
                recorder.started += id
                recorder.maxRunning.updateAndGet { max -> maxOf(max, recorder.running.incrementAndGet()) }
                gate.await()
                recorder.running.decrementAndGet()
                recorder.finished += id
            },
            onIdle = { recorder.idleCalls.incrementAndGet() }
        )

        repeat(4) { index -> queue.enqueue("d$index") }
        queue.start(this)
        advanceUntilIdle()

        assertEquals("only the two permitted transfers may start", 2, recorder.started.size)
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(4, recorder.finished.size)
        assertEquals("four queued ids, never more than the limit running", 2, recorder.maxRunning.get())
        assertEquals("one idle notification for the batch, not one per transfer", 1, recorder.idleCalls.get())
        assertTrue(queue.isIdle)
    }

    @Test
    fun `an id counts as in flight before the transfer first suspends`() = runTest {
        val recorder = Recorder()
        val blocked = CompletableDeferred<Unit>()
        val queue = DownloadWorkQueue(
            limiter = SlotLimiter(1),
            process = { id ->
                recorder.started += id
                blocked.await()
                recorder.finished += id
            },
            onIdle = { recorder.idleCalls.incrementAndGet() }
        )

        queue.enqueue("a")
        queue.start(this)
        advanceUntilIdle()

        // The id left the channel and is running (suspended inside the transfer). A completion of
        // some other transfer must not be able to read this as "nothing left to do".
        assertEquals(1, queue.inFlightCount)
        assertFalse(queue.isIdle)
        assertEquals(0, recorder.idleCalls.get())

        blocked.complete(Unit)
        advanceUntilIdle()
        assertTrue(queue.isIdle)
        assertEquals(1, recorder.idleCalls.get())
    }

    @Test
    fun `a failing transfer releases its slot and does not stop the queue`() = runTest {
        val recorder = Recorder()
        val queue = DownloadWorkQueue(
            limiter = SlotLimiter(1),
            process = { id ->
                recorder.started += id
                if (id == "bad") throw IllegalStateException("boom")
                recorder.finished += id
            },
            onIdle = { recorder.idleCalls.incrementAndGet() }
        )

        queue.enqueue("bad")
        queue.enqueue("good")
        queue.start(this)
        advanceUntilIdle()

        assertEquals(listOf("bad", "good"), recorder.started)
        assertEquals(listOf("good"), recorder.finished)
        assertTrue("the failed transfer must not hold the only slot", queue.isIdle)
        assertEquals(1, recorder.idleCalls.get())
    }

    @Test
    fun `starting the processor twice does not double-process an id`() = runTest {
        val recorder = Recorder()
        val queue = DownloadWorkQueue(
            limiter = SlotLimiter(2),
            process = { id -> recorder.finished += id }
        )

        queue.enqueue("once")
        queue.start(this)
        queue.start(this)
        queue.start(this)
        advanceUntilIdle()

        assertEquals(listOf("once"), recorder.finished)
    }

    @Test
    fun `raising the limit admits queued work without a new enqueue`() = runTest {
        val recorder = Recorder()
        val limiter = SlotLimiter(1)
        val gate = CompletableDeferred<Unit>()
        val queue = DownloadWorkQueue(
            limiter = limiter,
            process = { id ->
                recorder.started += id
                if (id == "first") gate.await()
            }
        )

        queue.enqueue("first")
        queue.enqueue("second")
        queue.start(this)
        advanceUntilIdle()
        assertEquals(listOf("first"), recorder.started)

        // A waiting id is admitted when the limit is raised: this is the settings change, no new
        // command arrives with it.
        limiter.setLimit(2)
        advanceUntilIdle()
        assertEquals(listOf("first", "second"), recorder.started)

        gate.complete(Unit)
        advanceUntilIdle()
    }

    @Test
    fun `stop keeps queued ids for the next start`() = runTest {
        val recorder = Recorder()
        val queue = DownloadWorkQueue(limiter = SlotLimiter(2), process = { id -> recorder.finished += id })

        queue.stop()
        queue.enqueue("waiting")
        advanceUntilIdle()
        assertTrue("nothing runs while the processor is stopped", recorder.finished.isEmpty())

        queue.start(this)
        advanceUntilIdle()
        assertEquals(listOf("waiting"), recorder.finished)
    }

    @Test
    fun `a slow transfer does not lose progress writes from a later one`() = runTest {
        val order = mutableListOf<String>()
        val slow = CompletableDeferred<Unit>()
        val counter = AtomicLong(0)
        val queue = DownloadWorkQueue(
            limiter = SlotLimiter(2),
            process = { id ->
                counter.incrementAndGet()
                if (id == "slow") slow.await()
                order += id
            }
        )

        queue.enqueue("slow")
        queue.enqueue("fast")
        queue.start(this)
        advanceUntilIdle()
        assertEquals(2L, counter.get())
        assertEquals(listOf("fast"), order)

        slow.complete(Unit)
        advanceUntilIdle()
        assertTrue(order.containsAll(listOf("slow", "fast")))
    }

    @Test
    fun `enqueue never blocks even with a full run of pending work`() = runTest {
        val queue = DownloadWorkQueue(limiter = SlotLimiter(1), process = { })
        repeat(500) { index -> assertTrue(queue.enqueue("d$index")) }
        queue.start(this)
        advanceUntilIdle()
        assertTrue(queue.isIdle)
    }

    @Test
    fun `cancelling the scope does not report idle for abandoned work`() = runTest {
        val recorder = Recorder()
        val never = CompletableDeferred<Unit>()
        val queue = DownloadWorkQueue(
            limiter = SlotLimiter(1),
            process = { never.await() },
            onIdle = { recorder.idleCalls.incrementAndGet() }
        )

        queue.enqueue("a")
        val processor = launch { queue.start(this) }
        advanceUntilIdle()
        processor.cancel()
        advanceUntilIdle()

        assertEquals("a cancelled processor is not an idle queue", 0, recorder.idleCalls.get())
    }
}
