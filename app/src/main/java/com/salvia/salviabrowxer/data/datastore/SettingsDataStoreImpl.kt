package com.salvia.salviabrowxer.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsDataStoreImpl(private val dataStore: DataStore<Preferences>) : SettingsDataStore {

    override val searchEngine: Flow<String> = dataStore.data
        .map { preferences -> preferences[SEARCH_ENGINE] ?: "Google" }

    override val homepage: Flow<String> = dataStore.data
        .map { preferences -> preferences[HOMEPAGE] ?: "https://www.google.com" }

    override val isDesktopSite: Flow<Boolean> = dataStore.data
        .map { preferences -> preferences[IS_DESKTOP_SITE] ?: false }

    override val isJavaScriptEnabled: Flow<Boolean> = dataStore.data
        .map { preferences -> preferences[IS_JAVASCRIPT_ENABLED] ?: true }

    override val areCookiesEnabled: Flow<Boolean> = dataStore.data
        .map { preferences -> preferences[ARE_COOKIES_ENABLED] ?: true }

    override val downloadDirectory: Flow<String> = dataStore.data
        .map { preferences -> preferences[DOWNLOAD_DIRECTORY] ?: "" }

    override val maxSimultaneousDownloads: Flow<Int> = dataStore.data
        .map { preferences -> preferences[MAX_SIMULTANEOUS_DOWNLOADS] ?: 3 }

    override val isWifiOnly: Flow<Boolean> = dataStore.data
        .map { preferences -> preferences[IS_WIFI_ONLY] ?: false }

    override val isDarkTheme: Flow<Boolean> = dataStore.data
        .map { preferences -> preferences[IS_DARK_THEME] ?: true }

    override val floatingButtonSize: Flow<Int> = dataStore.data
        .map { preferences -> preferences[FLOATING_BUTTON_SIZE] ?: 56 }

    override val floatingButtonX: Flow<Float> = dataStore.data
        .map { preferences -> preferences[FLOATING_BUTTON_X] ?: 0f }

    override val floatingButtonY: Flow<Float> = dataStore.data
        .map { preferences -> preferences[FLOATING_BUTTON_Y] ?: 0f }

    override suspend fun setSearchEngine(engine: String) {
        dataStore.edit { preferences -> preferences[SEARCH_ENGINE] = engine }
    }

    override suspend fun setHomepage(url: String) {
        dataStore.edit { preferences -> preferences[HOMEPAGE] = url }
    }

    override suspend fun setDesktopSite(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[IS_DESKTOP_SITE] = enabled }
    }

    override suspend fun setJavaScriptEnabled(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[IS_JAVASCRIPT_ENABLED] = enabled }
    }

    override suspend fun setCookiesEnabled(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[ARE_COOKIES_ENABLED] = enabled }
    }

    override suspend fun setDownloadDirectory(directory: String) {
        dataStore.edit { preferences -> preferences[DOWNLOAD_DIRECTORY] = directory }
    }

    override suspend fun setMaxSimultaneousDownloads(count: Int) {
        dataStore.edit { preferences -> preferences[MAX_SIMULTANEOUS_DOWNLOADS] = count }
    }

    override suspend fun setWifiOnly(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[IS_WIFI_ONLY] = enabled }
    }

    override suspend fun setDarkTheme(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[IS_DARK_THEME] = enabled }
    }

    override suspend fun setFloatingButtonSize(size: Int) {
        dataStore.edit { preferences -> preferences[FLOATING_BUTTON_SIZE] = size }
    }

    override suspend fun setFloatingButtonPosition(x: Float, y: Float) {
        dataStore.edit { preferences ->
            preferences[FLOATING_BUTTON_X] = x
            preferences[FLOATING_BUTTON_Y] = y
        }
    }

    companion object {
        private val SEARCH_ENGINE = stringPreferencesKey("search_engine")
        private val HOMEPAGE = stringPreferencesKey("homepage")
        private val IS_DESKTOP_SITE = booleanPreferencesKey("is_desktop_site")
        private val IS_JAVASCRIPT_ENABLED = booleanPreferencesKey("is_javascript_enabled")
        private val ARE_COOKIES_ENABLED = booleanPreferencesKey("are_cookies_enabled")
        private val DOWNLOAD_DIRECTORY = stringPreferencesKey("download_directory")
        private val MAX_SIMULTANEOUS_DOWNLOADS = intPreferencesKey("max_simultaneous_downloads")
        private val IS_WIFI_ONLY = booleanPreferencesKey("is_wifi_only")
        private val IS_DARK_THEME = booleanPreferencesKey("is_dark_theme")
        private val FLOATING_BUTTON_SIZE = intPreferencesKey("floating_button_size")
        private val FLOATING_BUTTON_X = floatPreferencesKey("floating_button_x")
        private val FLOATING_BUTTON_Y = floatPreferencesKey("floating_button_y")
    }
}