package com.salvia.salviabrowxer.data.datastore

import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Decides, once per process, whether the browser starts on the tabs that were open last time.
 *
 * Two things make this a singleton rather than a view model member:
 *
 *  - **once per process, not once per view model.** A configuration change creates the view model
 *    again, and restoring into it would duplicate every tab the user already had on screen;
 *  - **writing must outlive the view model.** The tab list is persisted from `onCleared`'s neighbour
 *    (a debounced collector), and a scope owned by the view model would be cancelled together with
 *    the very state it is trying to save.
 */
@Singleton
class SessionRestorer @Inject constructor(private val sessionDataStore: SessionDataStore) {

    /** Outlives every view model: a tab list is small, and losing it is not worth a race. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val claimed = AtomicBoolean(false)

    /** True for exactly one caller per process — the first view model that asks. */
    fun claimRestore(): Boolean = claimed.compareAndSet(false, true)

    /** What the last session left behind; empty when there is nothing usable to restore. */
    suspend fun saved(): List<SessionTab> =
        runCatching { sessionDataStore.tabs.first() }.getOrDefault(emptyList())

    /**
     * Writes the list in the background. Nothing waits for it: the tabs of a browser that is being
     * closed are not worth blocking a frame for, and the next process reads whatever is on disk.
     */
    fun persist(tabs: List<SessionTab>) {
        scope.launch { runCatching { sessionDataStore.setTabs(tabs) } }
    }
}
