package com.salvia.salviabrowxer.service.queue

import com.salvia.salviabrowxer.core.database.entities.DownloadEntity
import com.salvia.salviabrowxer.core.model.DownloadState
import com.salvia.salviabrowxer.service.WIFI_HOLD_MARKER
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Wi-Fi-only transitions and the marker that identifies a held row.
 *
 * Both halves used to be tangled up in the service: the setting and the network report each decided
 * on their own whether to pause the whole queue, and the release path scanned for the marker by
 * hand. Here the transitions are asserted, including the ones a device only shows rarely — a network
 * callback that fires twice, or the setting being switched off while work is held.
 */
class WifiHoldGateTest {

    @Test
    fun `turning the setting on while on mobile holds the queue`() {
        val gate = WifiHoldGate()
        assertEquals(WifiHoldAction.Hold, gate.onWifiOnlyChanged(enabled = true, onWifi = false))
        assertTrue(gate.isHolding)
    }

    @Test
    fun `turning the setting on while on wifi holds nothing`() {
        val gate = WifiHoldGate()
        assertEquals(WifiHoldAction.None, gate.onWifiOnlyChanged(enabled = true, onWifi = true))
        assertFalse(gate.isHolding)
    }

    @Test
    fun `turning the setting off releases work the setting held`() {
        val gate = WifiHoldGate()
        gate.onWifiOnlyChanged(enabled = true, onWifi = false)
        assertEquals(WifiHoldAction.Release, gate.onWifiOnlyChanged(enabled = false, onWifi = false))
        assertFalse(gate.isHolding)
    }

    @Test
    fun `work is released when wifi comes back`() {
        val gate = WifiHoldGate()
        gate.onWifiOnlyChanged(enabled = true, onWifi = false)
        assertEquals(WifiHoldAction.Release, gate.onConnectivityChanged(onWifi = true))
        assertFalse(gate.isHolding)

        // After the release, the repeated callback a network change produces must be a no-op rather
        // than a second release of rows that are already queued.
        assertEquals(WifiHoldAction.None, gate.onConnectivityChanged(onWifi = true))
    }

    @Test
    fun `losing wifi while the setting is on holds the queue once`() {
        val gate = WifiHoldGate()
        gate.onWifiOnlyChanged(enabled = true, onWifi = true)
        assertEquals(WifiHoldAction.Hold, gate.onConnectivityChanged(onWifi = false))
        assertEquals(WifiHoldAction.None, gate.onConnectivityChanged(onWifi = false))
    }

    @Test
    fun `connectivity reports are ignored while the setting is off`() {
        val gate = WifiHoldGate()
        assertEquals(WifiHoldAction.None, gate.onConnectivityChanged(onWifi = false))
        assertEquals(WifiHoldAction.None, gate.onConnectivityChanged(onWifi = true))
        assertFalse(gate.isHolding)
    }

    @Test
    fun `held rows are recognised by the marker and by their status`() {
        val held = row(id = "held", status = DownloadState.PAUSED, error = WIFI_HOLD_MARKER)
        val userPaused = row(id = "user", status = DownloadState.PAUSED, error = null)
        val failedWithMarker = row(id = "failed", status = DownloadState.FAILED, error = WIFI_HOLD_MARKER)
        val downloading = row(id = "live", status = DownloadState.DOWNLOADING, error = null)

        assertTrue(WifiHold.isHeldRow(held))
        assertFalse("a user's own pause must never be resumed by the network", WifiHold.isHeldRow(userPaused))
        assertFalse("a row that already failed is not waiting for wifi", WifiHold.isHeldRow(failedWithMarker))
        assertFalse(WifiHold.isHeldRow(downloading))

        assertEquals(listOf("held"), WifiHold.heldRows(listOf(held, userPaused, failedWithMarker, downloading)).map { it.id })
    }

    @Test
    fun `the holdable and resumable state lists exclude the paused state`() {
        assertFalse("only the user resumes a paused row", WifiHold.HOLDABLE_STATES.contains(DownloadState.PAUSED))
        assertFalse(WifiHold.RESUMABLE_STATES.contains(DownloadState.PAUSED))
        assertTrue(WifiHold.RESUMABLE_STATES.contains(DownloadState.DOWNLOADING))
        assertFalse(WifiHold.RESUMABLE_STATES.contains(DownloadState.COMPLETED))
    }

    private fun row(id: String, status: DownloadState, error: String?): DownloadEntity = DownloadEntity(
        id = id,
        url = "https://cdn.example/$id.mp4",
        filename = "$id.mp4",
        destination = "/tmp",
        status = status,
        error = error
    )
}
