package com.salvia.salviabrowxer.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

/** One tab as it survives a process death: where it was, what it was called, and which tab it is. */
data class SessionTab(val id: String, val url: String, val title: String)

/**
 * The browser's own session, kept apart from settings on purpose.
 *
 * It changes constantly (every tab open, close and page title) while settings change rarely, and a
 * corrupt or unreadable session must never cost the user their preferences — or the reverse. A tab
 * list is also not relational data: nothing joins against it, nothing queries it, so it needs no
 * table and no migration.
 *
 * What is deliberately **not** stored: private tabs. A private tab is a promise that nothing about it
 * outlives the session, and a file on disk is exactly that.
 */
class SessionDataStore(private val dataStore: DataStore<Preferences>) {

    val tabs: Flow<List<SessionTab>> = dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { preferences -> SessionCodec.decode(preferences[TABS]) }

    suspend fun setTabs(tabs: List<SessionTab>) {
        dataStore.edit { preferences -> preferences[TABS] = SessionCodec.encode(tabs) }
    }

    suspend fun clear() {
        dataStore.edit { preferences -> preferences.remove(TABS) }
    }

    companion object {
        private val TABS = stringPreferencesKey("tabs")
    }
}

val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore(name = "session")

/**
 * The tab list as one string.
 *
 * Hand-rolled and total: it accepts anything and returns an empty list for anything unreadable, so a
 * session written by an older build — or truncated by a killed process — costs the user their tabs
 * rather than their browser.
 */
internal object SessionCodec {

    /** How many tabs are worth keeping; the browser itself keeps eight alive at a time. */
    const val MAX_TABS: Int = 24

    private const val MAX_URL_CHARS = 4096
    private const val MAX_TITLE_CHARS = 512

    fun encode(tabs: List<SessionTab>): String {
        val array = JSONArray()
        tabs.take(MAX_TABS).forEach { tab ->
            if (tab.url.isBlank()) return@forEach
            array.put(
                JSONObject()
                    .put("id", tab.id)
                    .put("url", tab.url.take(MAX_URL_CHARS))
                    .put("title", tab.title.take(MAX_TITLE_CHARS))
            )
        }
        return array.toString()
    }

    fun decode(raw: String?): List<SessionTab> {
        val body = raw?.takeIf { it.isNotBlank() } ?: return emptyList()
        val array = runCatching { JSONArray(body) }.getOrNull() ?: return emptyList()
        val tabs = ArrayList<SessionTab>(minOf(array.length(), MAX_TABS))
        for (index in 0 until minOf(array.length(), MAX_TABS)) {
            val entry = array.optJSONObject(index) ?: continue
            val url = entry.optString("url").take(MAX_URL_CHARS)
            if (!isRestorable(url)) continue
            tabs += SessionTab(
                id = entry.optString("id").takeIf { it.isNotBlank() } ?: "restored-$index",
                url = url,
                title = entry.optString("title").take(MAX_TITLE_CHARS)
            )
        }
        return tabs
    }

    /**
     * Only pages can be restored. A tab whose last URL was `about:blank`, a `file:` path or a search
     * the app no longer understands would come back as an empty frame, which is worse than not
     * coming back at all.
     */
    fun isRestorable(url: String): Boolean =
        url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)
}
