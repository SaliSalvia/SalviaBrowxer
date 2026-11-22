package com.example.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DownloadSchedulerPolicyTest {

    @Test
    fun `API 24 through 33 use WorkManager compatibility backend`() {
        (24..33).forEach { api ->
            assertEquals(
                "API $api should use WorkManager",
                DownloadSchedulerBackend.WORK_MANAGER,
                DownloadSchedulerPolicy.backendForSdk(api),
            )
        }
    }

    @Test
    fun `API 34 and later use user initiated transfer jobs`() {
        listOf(34, 35, 36, 37).forEach { api ->
            assertEquals(
                "API $api should use UIDT",
                DownloadSchedulerBackend.USER_INITIATED_JOB,
                DownloadSchedulerPolicy.backendForSdk(api),
            )
        }
    }

    @Test
    fun `scheduler identifiers are stable and valid`() {
        assertEquals(1, DownloadSchedulerPolicy.jobIdFor(1L))
        assertEquals(42, DownloadSchedulerPolicy.jobIdFor(42L))
        assertEquals(1, DownloadSchedulerPolicy.jobIdFor(1_000_000_001L))
        assertEquals(
            "salvia-download-42",
            DownloadSchedulerPolicy.uniqueWorkName(42L),
        )
    }

    @Test
    fun `non positive download identifiers are rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            DownloadSchedulerPolicy.jobIdFor(0L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            DownloadSchedulerPolicy.uniqueWorkName(-1L)
        }
    }
}
