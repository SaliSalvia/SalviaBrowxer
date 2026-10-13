package com.salvia.salviabrowxer.data.storage

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** The folder the user picked for a second copy of every finished download. */
data class SafDestination(val treeUri: String, val label: String)

/**
 * The chosen Storage Access Framework folder, as one remembered string.
 *
 * A tree URI is not a path: it is a permission the user granted through the system picker, and only
 * the app that took a *persistable* grant may keep using it across restarts. That grant is taken in
 * the settings screen, where the user is; this only remembers which tree it was.
 */
class SafDestinationStore(private val dataStore: DataStore<Preferences>) {

    val destination: Flow<SafDestination?> = dataStore.data.map { preferences ->
        val uri = preferences[SAF_TREE]?.takeIf { it.isNotBlank() } ?: return@map null
        SafDestination(treeUri = uri, label = preferences[SAF_LABEL]?.takeIf { it.isNotBlank() } ?: uri)
    }

    suspend fun set(uri: String, label: String) {
        dataStore.edit { preferences ->
            preferences[SAF_TREE] = uri
            preferences[SAF_LABEL] = label
        }
    }

    suspend fun clear() {
        dataStore.edit { preferences ->
            preferences.remove(SAF_TREE)
            preferences.remove(SAF_LABEL)
        }
    }

    private companion object {
        val SAF_TREE = stringPreferencesKey("saf_tree_uri")
        val SAF_LABEL = stringPreferencesKey("saf_tree_label")
    }
}
