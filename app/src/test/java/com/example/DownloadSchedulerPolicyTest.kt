package com.example

import com.example.engine.DownloadSchedulerBackend
import com.example.engine.DownloadSchedulerPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadSchedulerPolicyTest {

    @Test
    fun `API 33 and below use WorkManager compatibility scheduler`() {
        assertEquals(DownloadSchedulerBackend.WORK_MANAGER, DownloadSchedulerPolicy.backendForSdk(24))
        assertEquals(DownloadSchedulerBackend.WORK_MANAGER, DownloadSchedulerPolicy.backendForSdk(33))
    }

    @Test
    fun `API 34 and above use user initiated transfer jobs`() {
        assertEquals(DownloadSchedulerBackend.USER_INITIATED_JOB, DownloadSchedulerPolicy.backendForSdk(34))
        assertEquals(DownloadSchedulerBackend.USER_INITIATED_JOB, DownloadSchedulerPolicy.backendForSdk(36))
    }

    @Test
    fun `job IDs are stable positive and bounded`() {
        val first = DownloadSchedulerPolicy.jobIdFor(123L)
        assertEquals(first, DownloadSchedulerPolicy.jobIdFor(123L))
        assertTrue(first > 0)
        assertTrue(first < 1_000_000_000)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `invalid download IDs are rejected`() {
        DownloadSchedulerPolicy.jobIdFor(0L)
    }

    @Test
    fun `unique work names are stable per download ID`() {
        assertEquals("salvia-download-42", DownloadSchedulerPolicy.uniqueWorkName(42L))
        assertEquals(
            DownloadSchedulerPolicy.uniqueWorkName(42L),
            DownloadSchedulerPolicy.uniqueWorkName(42L),
        )
    }
}
