package com.salvia.salviabrowxer.service

import org.junit.Assert.assertEquals
import org.junit.Test

class ConnectionPolicyTest {

    @Test
    fun `no internet is offline whatever the transport is`() {
        assertEquals(
            ConnectionKind.OFFLINE,
            ConnectionPolicy.kind(onWifi = false, online = false, metered = true)
        )
        assertEquals(
            ConnectionKind.OFFLINE,
            ConnectionPolicy.kind(onWifi = true, online = false, metered = false)
        )
    }

    @Test
    fun `a wifi transport is wifi even when it is metered`() {
        // A phone hotspot is metered Wi-Fi. WifiOnlyPolicy keys off transport, so this must stay WIFI.
        assertEquals(
            ConnectionKind.WIFI,
            ConnectionPolicy.kind(onWifi = true, online = true, metered = true)
        )
    }

    @Test
    fun `metered non-wifi is cellular`() {
        assertEquals(
            ConnectionKind.MOBILE,
            ConnectionPolicy.kind(onWifi = false, online = true, metered = true)
        )
    }

    @Test
    fun `unmetered non-wifi is other`() {
        assertEquals(
            ConnectionKind.OTHER,
            ConnectionPolicy.kind(onWifi = false, online = true, metered = false)
        )
    }
}
