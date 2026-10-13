package com.salvia.salviabrowxer.ui.utils

import com.salvia.salviabrowxer.core.model.MediaCandidate
import com.salvia.salviabrowxer.core.model.MediaRequestAdmission
import java.util.concurrent.ConcurrentHashMap

/**
 * The one request-side media observer: one admission rule, one dedupe window, one cap.
 *
 * Two very different callers feed it:
 *
 *  - `WebViewClient.shouldInterceptRequest`, which sees every request the *document* makes;
 *  - `ServiceWorkerClientCompat.shouldInterceptRequest`, which sees the requests a page's **service
 *    worker** makes — and those never pass through the document's client at all, so a player that
 *    fetches its media inside a worker used to be invisible to the app.
 *
 * Requests are **observed, never answered**: the return value at both call sites is untouched, no
 * request is rewritten or blocked, and no body is ever read. The app only learns that a GET for a
 * URL that looks like media happened, which is exactly what the tray needs.
 *
 * It is deliberately free of Android types beyond the model, so the dedupe window and the cap can be
 * tested on the JVM instead of only on a device; [now] exists for that reason.
 */
class MediaRequestObserver(
    private val pageUrlProvider: () -> String,
    private val onCandidate: (MediaCandidate) -> Unit,
    private val now: () -> Long = System::currentTimeMillis
) {

    /** URL -> last emit timestamp, so a re-fetching player does not flood the tray. */
    private val lastEmit = ConcurrentHashMap<String, Long>()

    /** One candidate per URL per [DEDUPE_WINDOW_MILLIS]. */
    fun onRequest(url: String?, method: String?, range: String?) {
        val target = url?.takeIf { it.isNotBlank() } ?: return
        // A download the app could start is a plain GET. No body is ever read, but a POST/PUT is not
        // something the tray may offer as a file either.
        if (method == null || !method.equals("GET", ignoreCase = true)) return
        val candidate = MediaRequestAdmission.admit(pageUrlProvider(), target, range) ?: return
        val mediaUrl = candidate.mediaUrl
        val at = now()
        val previous = lastEmit[mediaUrl] ?: 0L
        if (at - previous < DEDUPE_WINDOW_MILLIS) return
        // Bounded even on an infinite feed: a page that requests a new segment URL forever must not
        // grow this map forever. The UI has its own URL-keyed cap as well.
        if (lastEmit.size >= MAX_TRACKED_URLS) lastEmit.clear()
        lastEmit[mediaUrl] = at
        onCandidate(candidate)
    }

    /** A new document: the previous page's URLs must not silence the new page's first request. */
    fun reset() {
        lastEmit.clear()
    }

    /** How many URLs the dedupe window currently remembers; the cap is what keeps this bounded. */
    val trackedUrlCount: Int get() = lastEmit.size

    companion object {
        /** Within a single playback a player re-requests the same URL; the tray only needs it once. */
        const val DEDUPE_WINDOW_MILLIS: Long = 5_000L

        /** How many distinct URLs are remembered before the window is dropped wholesale. */
        const val MAX_TRACKED_URLS: Int = 120
    }
}
