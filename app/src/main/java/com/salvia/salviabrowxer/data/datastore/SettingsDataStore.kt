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
    val downloadDirectory: Flow<String>
    val maxSimultaneousDownloads: Flow<Int>
    val isWifiOnly: Flow<Boolean>
    val isDarkTheme: Flow<Boolean>
    val floatingButtonSize: Flow<Int>
    val floatingButtonX: Flow<Float>
    val floatingButtonY: Flow<Float>

    suspend fun setSearchEngine(engine: String)
    suspend fun setHomepage(url: String)
    suspend fun setDesktopSite(enabled: Boolean)
    suspend fun setJavaScriptEnabled(enabled: Boolean)
    suspend fun setCookiesEnabled(enabled: Boolean)
    suspend fun setDownloadDirectory(directory: String)
    suspend fun setMaxSimultaneousDownloads(count: Int)
    suspend fun setWifiOnly(enabled: Boolean)
    suspend fun setDarkTheme(enabled: Boolean)
    suspend fun setFloatingButtonSize(size: Int)
    suspend fun setFloatingButtonPosition(x: Float, y: Float)
}