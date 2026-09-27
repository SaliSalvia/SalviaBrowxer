package com.salvia.salviabrowxer.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WifiOnlyPolicyTest {

    @Test
    fun `wifi only holds work on a non-wifi network`() {
        assertTrue(WifiOnlyPolicy.shouldHold(wifiOnly = true, onWifi = false))
    }

    @Test
    fun `wifi only lets work run on wifi`() {
        assertFalse(WifiOnlyPolicy.shouldHold(wifiOnly = true, onWifi = true))
    }

    @Test
    fun `with the setting off the network type is irrelevant`() {
        assertFalse(WifiOnlyPolicy.shouldHold(wifiOnly = false, onWifi = false))
        assertFalse(WifiOnlyPolicy.shouldHold(wifiOnly = false, onWifi = true))
    }
}
