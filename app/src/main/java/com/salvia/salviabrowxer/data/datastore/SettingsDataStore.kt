package com.salvia.salviabrowxer.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.flow.Flow

interface SettingsDataStore {
    val searchEngine: Flow<String>
    val homepage: Flow<String>
    val isDesktopSite: Flow<Boolean>
    val isJavaScriptEnabled: Flow<Boolean>
    val areCookiesEnabled: Flow<Boolean>
    val maxSimultaneousDownloads: Flow<Int>
    /**
     * How many Range connections one direct file may use. 1 is the single-stream download; the
     * default is a few, because on a per-connection limited server that is the speed difference.
     */
    val parallelConnections: Flow<Int>
    val isWifiOnly: Flow<Boolean>
    /** Publish finished downloads to the shared media store so galleries can see them. */
    val isExportToGallery: Flow<Boolean>
    val isCleartextAllowed: Flow<Boolean>
    val isFloatingButtonAlwaysVisible: Flow<Boolean>
    val floatingButtonSize: Flow<Int>
    val floatingButtonX: Flow<Float>
    val floatingButtonY: Flow<Float>
    /**
     * Opt-in ad blocking. Off by default: blocking is a product decision the user makes, never a
     * default the app imposes, and the blocker's own rules keep media out of its reach.
     */
    val isAdBlockEnabled: Flow<Boolean>
    /** Hosts the user exempted ("off for this site"), matched label by label like the block list. */
    val adBlockDisabledSites: Flow<Set<String>>
    /** Lifetime count of blocked requests, shown under the settings row. */
    val adBlockBlockedRequests: Flow<Int>

    suspend fun setSearchEngine(engine: String)
    suspend fun setHomepage(url: String)
    suspend fun setDesktopSite(enabled: Boolean)
    suspend fun setJavaScriptEnabled(enabled: Boolean)
    suspend fun setCookiesEnabled(enabled: Boolean)
    suspend fun setMaxSimultaneousDownloads(count: Int)
    suspend fun setParallelConnections(count: Int)
    suspend fun setWifiOnly(enabled: Boolean)
    suspend fun setExportToGallery(enabled: Boolean)
    suspend fun setCleartextAllowed(enabled: Boolean)
    suspend fun setFloatingButtonAlwaysVisible(enabled: Boolean)
    suspend fun setFloatingButtonSize(size: Int)
    suspend fun setFloatingButtonPosition(x: Float, y: Float)
    suspend fun setAdBlockEnabled(enabled: Boolean)
    suspend fun setAdBlockDisabledForSite(site: String, disabled: Boolean)
    suspend fun addAdBlockBlockedRequests(count: Int)

    companion object {
        /** Four is what the segmented fetcher already uses; more rarely helps and costs memory. */
        const val DEFAULT_PARALLEL_CONNECTIONS = 4

        /** Past this the connections compete with the player more than they help the download. */
        const val MAX_PARALLEL_CONNECTIONS = 8
    }
}