package com.salvia.salviabrowxer.service

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Pure Wi-Fi-only decision, kept separate from Android APIs so it is unit-testable.
 * A transfer may only start when the user did not ask for Wi-Fi-only, or the
 * active network really is Wi-Fi.
 */
object WifiOnlyPolicy {
    fun shouldHold(wifiOnly: Boolean, onWifi: Boolean): Boolean = wifiOnly && !onWifi
}

/**
 * Thin, version-guarded wrapper around [ConnectivityManager].
 *
 * Uses `registerDefaultNetworkCallback` (API 24+, our minSdk) rather than the
 * deprecated `CONNECTIVITY_ACTION` broadcast, and reports the *validated* default
 * network so a captive portal does not count as Wi-Fi.
 */
class ConnectivityGate(context: Context) {

    private val connectivityManager =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    fun isOnWifi(): Boolean {
        val manager = connectivityManager ?: return false
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return isWifiCapable(capabilities)
    }

    /** Emits the current Wi-Fi state, then every change, ending when the collector stops. */
    fun observeWifi(): Flow<Boolean> = callbackFlow {
        val manager = connectivityManager
        if (manager == null) {
            trySend(false)
            awaitClose { }
            return@callbackFlow
        }
        trySend(isOnWifi())
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { trySend(isOnWifi()) }
            override fun onLost(network: Network) { trySend(isOnWifi()) }
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                trySend(isWifiCapable(capabilities))
            }
        }
        runCatching { manager.registerDefaultNetworkCallback(callback) }
            .onFailure {
                close()
                return@callbackFlow
            }
        awaitClose { runCatching { manager.unregisterNetworkCallback(callback) } }
    }.distinctUntilChanged()

    private fun isWifiCapable(capabilities: NetworkCapabilities): Boolean =
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}
