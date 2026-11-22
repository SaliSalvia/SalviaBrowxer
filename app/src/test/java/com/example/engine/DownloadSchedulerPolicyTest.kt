package com.example.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DownloadSchedulerPolicyTest {

    @Test
    fun `API 24 through 33 use WorkManager`() {
        (24..33).forEach { api ->
            assertEquals(
                "API $api",
                DownloadSchedulerBackend.WORK_MANAGER,
                DownloadSchedulerPolicy.backendForSdk(api),
            )
        }
    }

    @Test
    fun `API 34 and newer use user initiated jobs`() {
        listOf(34, 35, 36, 37).forEach { api ->
            assertEquals(
                "API $api",
                DownloadSchedulerBackend.USER_INITIATED_JOB,
                DownloadSchedulerPolicy.backendForSdk(api),
            )
        }
    }

    @Test
    fun `startup recovery only auto schedules legacy WorkManager tasks`() {
        (24..33).forEach { api ->
            assertEquals(true, DownloadSchedulerPolicy.shouldRescheduleQueuedWorkOnAppStart(api))
        }
        (34..37).forEach { api ->
            assertEquals(false, DownloadSchedulerPolicy.shouldRescheduleQueuedWorkOnAppStart(api))
        }
    }

    @Test
    fun `job identity is stable and positive for persistent download IDs`() {
        assertEquals(1, DownloadSchedulerPolicy.jobIdFor(1L))
        assertEquals(42, DownloadSchedulerPolicy.jobIdFor(42L))
        assertEquals(
            DownloadSchedulerPolicy.jobIdFor(123456L),
            DownloadSchedulerPolicy.jobIdFor(123456L),
        )
    }

    @Test
    fun `unique work names are stable and do not collide for adjacent IDs`() {
        val first = DownloadSchedulerPolicy.uniqueWorkName(100L)
        val second = DownloadSchedulerPolicy.uniqueWorkName(101L)
        assertEquals("salvia-download-100", first)
        assertNotEquals(first, second)
    }

    @Test
    fun `non positive IDs are rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            DownloadSchedulerPolicy.jobIdFor(0L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            DownloadSchedulerPolicy.uniqueWorkName(-1L)
        }
    }
}
