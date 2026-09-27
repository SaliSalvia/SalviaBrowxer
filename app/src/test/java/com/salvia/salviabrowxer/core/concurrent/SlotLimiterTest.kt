package com.salvia.salviabrowxer.core.concurrent

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SlotLimiterTest {

    @Test
    fun `granted permits never exceed the limit`() = runTest {
        val limiter = SlotLimiter(2)

        limiter.acquire()
        limiter.acquire()
        assertEquals(2, limiter.activeCount)

        var thirdAcquired = false
        val waiter = launch {
            limiter.acquire()
            thirdAcquired = true
        }
        testScheduler.advanceUntilIdle()
        assertTrue("the third acquire must wait for a free permit", thirdAcquired.not())

        limiter.release()
        testScheduler.advanceUntilIdle()
        assertTrue("releasing a permit hands it to the waiter", thirdAcquired)
        assertEquals(2, limiter.activeCount)

        waiter.cancel()
        runCatching { limiter.release() }
    }

    @Test
    fun `raising the limit admits queued work immediately`() = runTest {
        val limiter = SlotLimiter(1)
        limiter.acquire()

        var admitted = false
        launch {
            limiter.acquire()
            admitted = true
        }
        testScheduler.advanceUntilIdle()
        assertTrue(admitted.not())

        limiter.setLimit(3)
        testScheduler.advanceUntilIdle()
        assertTrue("a higher limit wakes the queue", admitted)
        assertEquals(2, limiter.activeCount)
    }

    @Test
    fun `lowering the limit does not drop in-flight permits`() = runTest {
        val limiter = SlotLimiter(3)
        limiter.acquire()
        limiter.acquire()
        limiter.acquire()
        assertEquals(3, limiter.activeCount)

        limiter.setLimit(1)
        assertEquals("running transfers keep their permits", 3, limiter.activeCount)

        // No new work may start until the in-flight count drops below the new limit.
        var admitted = false
        launch {
            limiter.acquire()
            admitted = true
        }
        testScheduler.advanceUntilIdle()
        assertTrue(admitted.not())

        limiter.release()
        testScheduler.advanceUntilIdle()
        assertTrue("2 running > limit 1, so nothing new starts", admitted.not())

        limiter.release()
        testScheduler.advanceUntilIdle()
        assertTrue(admitted.not())

        limiter.release()
        testScheduler.advanceUntilIdle()
        assertTrue(admitted)
        assertEquals(1, limiter.activeCount)
    }

    @Test
    fun `limit is clamped to the supported range`() = runTest {
        val limiter = SlotLimiter(99)
        assertEquals(SlotLimiter.MAX_LIMIT, limiter.currentLimit)

        limiter.setLimit(0)
        assertEquals(SlotLimiter.MIN_LIMIT, limiter.currentLimit)
    }

    @Test
    fun `queued work is admitted in fifo order`() = runTest {
        val limiter = SlotLimiter(1)
        limiter.acquire()

        val order = mutableListOf<String>()
        launch { limiter.acquire(); order += "first"; limiter.release() }
        testScheduler.advanceUntilIdle()
        launch { limiter.acquire(); order += "second"; limiter.release() }
        testScheduler.advanceUntilIdle()

        limiter.release()
        testScheduler.advanceUntilIdle()

        assertEquals(listOf("first", "second"), order)
    }
}
