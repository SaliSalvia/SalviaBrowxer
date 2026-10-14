package com.salvia.salviabrowxer.service.queue

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The retry budget: three tries in total (the first attempt plus two retries) with a backoff that
 * grows per attempt and never restarts the count on a later failure.
 */
class SegmentedRetryPolicyTest {

    private val policy = SegmentedRetryPolicy()

    @Test
    fun `the first two failures schedule a retry and the third is final`() {
        val first = policy.onFailure(0)
        val second = policy.onFailure(1)
        val third = policy.onFailure(2)

        assertTrue(first is SegmentedRetryPolicy.Decision.Retry)
        assertEquals(1, (first as SegmentedRetryPolicy.Decision.Retry).attempt)
        assertTrue(second is SegmentedRetryPolicy.Decision.Retry)
        assertEquals(2, (second as SegmentedRetryPolicy.Decision.Retry).attempt)
        assertEquals("three tries in total, not three retries", SegmentedRetryPolicy.Decision.GiveUp, third)
    }

    @Test
    fun `the backoff grows with every retry and stays short`() {
        val first = policy.onFailure(0) as SegmentedRetryPolicy.Decision.Retry
        val second = policy.onFailure(1) as SegmentedRetryPolicy.Decision.Retry

        assertEquals(1_200L, first.backoffMillis)
        assertEquals(2_400L, second.backoffMillis)
        assertTrue("the user is waiting, so the whole schedule stays in seconds", first.backoffMillis + second.backoffMillis < 10_000L)
    }

    @Test
    fun `a custom budget is honoured exactly`() {
        val oneTryOnly = SegmentedRetryPolicy(maxRetries = 0)
        assertEquals(SegmentedRetryPolicy.Decision.GiveUp, oneTryOnly.onFailure(0))

        val generous = SegmentedRetryPolicy(maxRetries = 4, baseDelayMillis = 250L)
        for (attempt in 0 until 4) {
            assertTrue(generous.onFailure(attempt) is SegmentedRetryPolicy.Decision.Retry)
        }
        assertEquals(SegmentedRetryPolicy.Decision.GiveUp, generous.onFailure(4))
    }
}
