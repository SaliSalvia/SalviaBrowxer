package com.salvia.salviabrowxer.service

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Covers the bug this class was extracted for: the service used to keep **one** progress timestamp
 * and **one** timer job for every transfer, so with three downloads running a tick from one cancelled
 * the scheduled write of another and the queue rows showed percentages, rates and ETAs that belonged
 * to different transfers. The clock is a lambda and the delays are virtual, so these run instantly.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProgressPersistThrottleTest {

    @Test
    fun `each download has its own window`() = runTest {
        var now = 1_000L
        val written = mutableListOf<String>()
        val throttle = ProgressPersistThrottle(scope = this, intervalMillis = 800, clock = { now })

        throttle.submit("a") { written += "a1" }
        throttle.submit("b") { written += "b1" } // another transfer, same instant: not throttled by a
        throttle.submit("a") { written += "a2" } // inside a's window: deferred, not written yet
        assertEquals(listOf("a1", "b1"), written)

        now += 800
        advanceTimeBy(801)
        runCurrent()
        assertEquals(listOf("a1", "b1", "a2"), written)
    }

    @Test
    fun `a newer tick supersedes the pending write instead of queueing snapshots`() = runTest {
        var now = 1_000L
        val written = mutableListOf<String>()
        val throttle = ProgressPersistThrottle(this, 800, { now })

        throttle.submit("a") { written += "first" } // window open: written inline, in order
        throttle.submit("a") { written += "stale" } // scheduled
        throttle.submit("a") { written += "newest" } // replaces the scheduled one
        assertEquals(listOf("first"), written)

        now += 800
        advanceTimeBy(801)
        runCurrent()
        assertEquals(listOf("first", "newest"), written)
    }

    @Test
    fun `one download's tick does not cancel another's pending write`() = runTest {
        var now = 1_000L
        val written = mutableListOf<String>()
        val throttle = ProgressPersistThrottle(this, 800, { now })

        throttle.submit("a") { written += "a-first" }
        throttle.submit("a") { written += "a-trailing" } // pending
        throttle.submit("b") { written += "b-first" } // used to cancel a's pending job
        assertEquals(listOf("a-first", "b-first"), written)

        now += 800
        advanceTimeBy(801)
        runCurrent()
        assertEquals(listOf("a-first", "b-first", "a-trailing"), written)
    }

    @Test
    fun `forget cancels the trailing write so a finished row keeps no late update`() = runTest {
        val now = 1_000L
        val written = mutableListOf<String>()
        val throttle = ProgressPersistThrottle(this, 800, { now })

        throttle.submit("a") { written += "first" }
        throttle.submit("a") { written += "trailing" }
        throttle.forget("a")

        advanceTimeBy(801)
        runCurrent()
        assertEquals(listOf("first"), written)
    }
}
