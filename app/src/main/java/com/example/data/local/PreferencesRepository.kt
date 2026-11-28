package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.browserDataStore: DataStore<Preferences> by preferencesDataStore(name = "nava_browser_prefs")

enum class AppLanguage(val code: String, val nativeLabel: String) {
    ENGLISH("en", "English"),
    PERSIAN("fa", "فارسی"),
}

enum class SearchEngine(val displayName: String, val searchUrlTemplate: String) {
    GOOGLE("Google", "https://www.google.com/search?q=%s"),
    DUCKDUCKGO("DuckDuckGo (Private)", "https://duckduckgo.com/?q=%s"),
    STARTPAGE("Startpage (Secure)", "https://www.startpage.com/sp/search?query=%s"),
    BING("Bing", "https://www.bing.com/search?q=%s"),
}

enum class ThemePreference(val label: String) {
    DARK("Cosmic Dark"),
    LIGHT("Light"),
    SYSTEM("Follow System"),
}

data class BrowserSettings(
    val adBlockEnabled: Boolean = true,
    val trackerBlockEnabled: Boolean = true,
    val httpsUpgradeEnabled: Boolean = true,
    val blockThirdPartyCookiesIncognito: Boolean = true,
    val javaScriptEnabled: Boolean = true,
    val doNotTrackEnabled: Boolean = true,
    val pullToRefreshEnabled: Boolean = true,
    val searchEngine: SearchEngine = SearchEngine.GOOGLE,
    val themePreference: ThemePreference = ThemePreference.DARK,
    val appLanguage: AppLanguage = AppLanguage.ENGLISH,
    val totalAdsBlockedLifetime: Long = 0L,
    val totalTrackersBlockedLifetime: Long = 0L,
)

class PreferencesRepository(private val context: Context) {

    private object Keys {
        val AD_BLOCK = booleanPreferencesKey("ad_block_enabled")
        val TRACKER_BLOCK = booleanPreferencesKey("tracker_block_enabled")
        val HTTPS_UPGRADE = booleanPreferencesKey("https_upgrade_enabled")
        val INCOGNITO_3P_COOKIES = booleanPreferencesKey("incognito_block_3p_cookies")
        val JS_ENABLED = booleanPreferencesKey("js_enabled")
        val DO_NOT_TRACK = booleanPreferencesKey("do_not_track")
        val PULL_TO_REFRESH = booleanPreferencesKey("pull_to_refresh")
        val SEARCH_ENGINE = stringPreferencesKey("search_engine")
        val THEME = stringPreferencesKey("theme_preference")
        val APP_LANGUAGE = stringPreferencesKey("app_language")
        val LIFETIME_ADS = longPreferencesKey("lifetime_ads_blocked")
        val LIFETIME_TRACKERS = longPreferencesKey("lifetime_trackers_blocked")
    }

    val settingsFlow: Flow<BrowserSettings> = context.browserDataStore.data.map { prefs ->
        val engine = prefs[Keys.SEARCH_ENGINE]?.let { name ->
            SearchEngine.entries.firstOrNull { it.name == name }
        } ?: SearchEngine.GOOGLE
        val theme = prefs[Keys.THEME]?.let { name ->
            ThemePreference.entries.firstOrNull { it.name == name }
        } ?: ThemePreference.DARK
        val language = prefs[Keys.APP_LANGUAGE]?.let { name ->
            AppLanguage.entries.firstOrNull { it.name == name }
        } ?: AppLanguage.ENGLISH

        BrowserSettings(
            adBlockEnabled = prefs[Keys.AD_BLOCK] ?: true,
            trackerBlockEnabled = prefs[Keys.TRACKER_BLOCK] ?: true,
            httpsUpgradeEnabled = prefs[Keys.HTTPS_UPGRADE] ?: true,
            blockThirdPartyCookiesIncognito = prefs[Keys.INCOGNITO_3P_COOKIES] ?: true,
            javaScriptEnabled = prefs[Keys.JS_ENABLED] ?: true,
            doNotTrackEnabled = prefs[Keys.DO_NOT_TRACK] ?: true,
            pullToRefreshEnabled = prefs[Keys.PULL_TO_REFRESH] ?: true,
            searchEngine = engine,
            themePreference = theme,
            appLanguage = language,
            totalAdsBlockedLifetime = prefs[Keys.LIFETIME_ADS] ?: 0L,
            totalTrackersBlockedLifetime = prefs[Keys.LIFETIME_TRACKERS] ?: 0L,
        )
    }

    suspend fun setAdBlockEnabled(enabled: Boolean) {
        context.browserDataStore.edit { it[Keys.AD_BLOCK] = enabled }
    }

    suspend fun setTrackerBlockEnabled(enabled: Boolean) {
        context.browserDataStore.edit { it[Keys.TRACKER_BLOCK] = enabled }
    }

    suspend fun setHttpsUpgradeEnabled(enabled: Boolean) {
        context.browserDataStore.edit { it[Keys.HTTPS_UPGRADE] = enabled }
    }

    suspend fun setIncognito3pCookieBlock(enabled: Boolean) {
        context.browserDataStore.edit { it[Keys.INCOGNITO_3P_COOKIES] = enabled }
    }

    suspend fun setJavaScriptEnabled(enabled: Boolean) {
        context.browserDataStore.edit { it[Keys.JS_ENABLED] = enabled }
    }

    suspend fun setDoNotTrackEnabled(enabled: Boolean) {
        context.browserDataStore.edit { it[Keys.DO_NOT_TRACK] = enabled }
    }

    suspend fun setPullToRefreshEnabled(enabled: Boolean) {
        context.browserDataStore.edit { it[Keys.PULL_TO_REFRESH] = enabled }
    }

    suspend fun setSearchEngine(engine: SearchEngine) {
        context.browserDataStore.edit { it[Keys.SEARCH_ENGINE] = engine.name }
    }

    suspend fun setThemePreference(theme: ThemePreference) {
        context.browserDataStore.edit { it[Keys.THEME] = theme.name }
    }

    suspend fun setAppLanguage(language: AppLanguage) {
        context.browserDataStore.edit { it[Keys.APP_LANGUAGE] = language.name }
    }

    suspend fun incrementBlockedStats(adsDelta: Long, trackersDelta: Long) {
        if (adsDelta == 0L && trackersDelta == 0L) return
        context.browserDataStore.edit { prefs ->
            prefs[Keys.LIFETIME_ADS] = (prefs[Keys.LIFETIME_ADS] ?: 0L) + adsDelta
            prefs[Keys.LIFETIME_TRACKERS] = (prefs[Keys.LIFETIME_TRACKERS] ?: 0L) + trackersDelta
        }
    }
}
