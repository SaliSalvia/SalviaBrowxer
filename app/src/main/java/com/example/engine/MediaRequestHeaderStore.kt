package com.example.engine

/**
 * Bounded, short-lived, per-tab capture of non-credential metadata from real WebView
 * subresource requests. It is promoted to media evidence only after URL or MIME
 * detection independently identifies the resource as media.
 */
class MediaRequestHeaderStore(
    private val maxEntriesPerTab: Int = 160,
    private val maxTabs: Int = 8,
    private val ttlMs: Long = 10 * 60 * 1000L,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private data class Entry(
        val origin: String,
        val headers: Map<String, String>,
        val capturedAt: Long,
        val isMediaEvidence: Boolean,
    )

    private val lock = Any()
    private val tabs = LinkedHashMap<String, LinkedHashMap<String, Entry>>()

    fun capture(
        tabId: String,
        url: String,
        method: String,
        isMainFrame: Boolean,
        headers: Map<String, String>,
        mediaEvidence: Boolean,
    ) {
        if (tabId.isBlank() || isMainFrame || method.uppercase() !in setOf("GET", "HEAD")) return
        val origin = MediaRequestHeaderPolicy.originForUrl(url) ?: return
        val safe = MediaRequestHeaderPolicy.sanitize(headers)
        if (safe.isEmpty()) return
        val now = clock()
        synchronized(lock) {
            purgeExpired(now)
            val entries = tabs.getOrPut(tabId) { LinkedHashMap() }
            val previous = entries[url]
            entries[url] = Entry(
                origin = origin,
                headers = MediaRequestHeaderPolicy.sanitize(previous?.headers.orEmpty() + safe),
                capturedAt = now,
                isMediaEvidence = mediaEvidence || previous?.isMediaEvidence == true,
            )
            while (entries.size > maxEntriesPerTab) {
                val iterator = entries.entries.iterator()
                if (iterator.hasNext()) { iterator.next(); iterator.remove() }
            }
            while (tabs.size > maxTabs) {
                val iterator = tabs.entries.iterator()
                if (iterator.hasNext()) { iterator.next(); iterator.remove() }
            }
        }
    }

    /** Promote a previously observed request once MIME/URL evidence confirms media. */
    fun markMedia(tabId: String, url: String) {
        synchronized(lock) {
            purgeExpired(clock())
            val entry = tabs[tabId]?.get(url) ?: return
            tabs[tabId]?.set(url, entry.copy(capturedAt = clock(), isMediaEvidence = true))
        }
    }

    /**
     * Snapshot only origins with independently observed media requests. The selected
     * media origin must be present; unrelated page-resource origins are never exported.
     */
    fun snapshot(tabId: String, selectedMediaUrl: String): Map<String, Map<String, String>> {
        val selectedOrigin = MediaRequestHeaderPolicy.originForUrl(selectedMediaUrl) ?: return emptyMap()
        synchronized(lock) {
            purgeExpired(clock())
            val entries = tabs[tabId]?.values.orEmpty()
                .filter { it.isMediaEvidence && it.headers.isNotEmpty() }
            if (entries.none { it.origin == selectedOrigin }) return emptyMap()
            val grouped = entries.groupBy { it.origin }
                .mapValues { (_, values) -> values.maxByOrNull { it.capturedAt }!!.headers }
            val orderedOrigins = listOf(selectedOrigin) + grouped.keys.filter { it != selectedOrigin }.sorted()
            return orderedOrigins.take(8).associateWith { origin ->
                MediaRequestHeaderPolicy.sanitize(grouped[origin].orEmpty())
            }.filterValues { it.isNotEmpty() }
        }
    }

    fun clearTab(tabId: String) {
        synchronized(lock) { tabs.remove(tabId) }
    }

    fun clearTabs(tabIds: Collection<String>) {
        synchronized(lock) { tabIds.forEach(tabs::remove) }
    }

    /** Removes all transient request metadata when browsing data is cleared. */
    fun clearAll() {
        synchronized(lock) { tabs.clear() }
    }

    private fun purgeExpired(now: Long) {
        val tabsIterator = tabs.entries.iterator()
        while (tabsIterator.hasNext()) {
            val entries = tabsIterator.next().value
            entries.entries.removeAll { now - it.value.capturedAt > ttlMs }
            if (entries.isEmpty()) tabsIterator.remove()
        }
    }
}
