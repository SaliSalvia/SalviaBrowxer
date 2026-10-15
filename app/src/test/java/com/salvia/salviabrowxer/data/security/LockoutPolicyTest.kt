package com.salvia.salviabrowxer.data.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LockoutPolicyTest {

    @Test
    fun `the first failures are free and the third starts the lockout`() {
        val policy = LockoutPolicy(maxAttempts = 3, baseDelayMillis = 2_000L)
        assertEquals(0L, policy.onFailure(0L))
        assertEquals(0L, policy.onFailure(1_000L))
        assertEquals(2_000L, policy.onFailure(2_000L))
        assertTrue(policy.isLockedOut(3_000L))
        assertFalse(policy.isLockedOut(5_000L))
    }

    @Test
    fun `the delay doubles with every further failure and is capped`() {
        val policy = LockoutPolicy(maxAttempts = 1, baseDelayMillis = 1_000L, maxDelayMillis = 8_000L)
        assertEquals(1_000L, policy.onFailure(0L))
        assertEquals(2_000L, policy.onFailure(10_000L))
        assertEquals(4_000L, policy.onFailure(20_000L))
        assertEquals(8_000L, policy.onFailure(30_000L))
        // The cap, not the next doubling.
        assertEquals(8_000L, policy.onFailure(40_000L))
        assertEquals(8_000L, policy.onFailure(50_000L))
    }

    @Test
    fun `remaining millis counts down to zero and never below`() {
        val policy = LockoutPolicy(maxAttempts = 1, baseDelayMillis = 4_000L)
        policy.onFailure(1_000L)
        assertEquals(3_000L, policy.remainingMillis(2_000L))
        assertEquals(0L, policy.remainingMillis(5_000L))
        assertEquals(0L, policy.remainingMillis(9_000L))
    }

    @Test
    fun `a success resets the streak`() {
        val policy = LockoutPolicy(maxAttempts = 2, baseDelayMillis = 2_000L)
        policy.onFailure(0L)
        policy.onSuccess()
        assertEquals(0L, policy.onFailure(1_000L))
        assertFalse(policy.isLockedOut(1_500L))
    }

    @Test
    fun `a locked out policy lets the lockout expire by itself`() {
        val policy = LockoutPolicy(maxAttempts = 1, baseDelayMillis = 2_000L)
        policy.onFailure(100L)
        assertTrue(policy.isLockedOut(2_099L))
        assertFalse(policy.isLockedOut(2_100L))
    }
}
