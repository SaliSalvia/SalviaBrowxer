package com.salvia.salviabrowxer.service.queue

import com.salvia.salviabrowxer.core.database.entities.DownloadEntity
import com.salvia.salviabrowxer.core.model.DownloadState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeoutHoldTest {

    private fun row(status: DownloadState, error: String? = null) = DownloadEntity(
        url = "https://example.com/a.mp4",
        filename = "a.mp4",
        destination = "/tmp",
        status = status,
        error = error
    )

    @Test
    fun `a held row is paused with the timeout marker`() {
        assertTrue(TimeoutHold.isHeldRow(row(DownloadState.PAUSED, DATA_SYNC_TIMEOUT_MARKER)))
    }

    @Test
    fun `a user pause or a marker on another state is not a held row`() {
        assertFalse(TimeoutHold.isHeldRow(row(DownloadState.PAUSED, "__salvia_wifi_hold__")))
        assertFalse(TimeoutHold.isHeldRow(row(DownloadState.PAUSED, "HTTP 403")))
        assertFalse(TimeoutHold.isHeldRow(row(DownloadState.PAUSED, null)))
        // The marker alone is not enough: a failed transfer that kept the marker must not resume.
        assertFalse(TimeoutHold.isHeldRow(row(DownloadState.FAILED, DATA_SYNC_TIMEOUT_MARKER)))
        assertFalse(TimeoutHold.isHeldRow(row(DownloadState.QUEUED, DATA_SYNC_TIMEOUT_MARKER)))
    }

    @Test
    fun `heldRows keeps only the held ones in order`() {
        val held = row(DownloadState.PAUSED, DATA_SYNC_TIMEOUT_MARKER)
        val user = row(DownloadState.PAUSED)
        val running = row(DownloadState.DOWNLOADING)
        assertEquals(listOf(held, held), TimeoutHold.heldRows(listOf(held, user, running, held)))
    }
}
