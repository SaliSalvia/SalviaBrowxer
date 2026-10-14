package com.salvia.salviabrowxer.ui.utils

/**
 * The one place a WebView request is decided, and the order is the whole point.
 *
 * Two things happen to a request before the WebView loads it, and they must not both happen:
 *
 *  1. the opt-in ad blocker may answer it with nothing;
 *  2. the media observer may remember the URL so the tray can offer it.
 *
 * A blocked request must never reach step 2. If it did, an ad URL would show up in the tray as
 * something to download, and the app would be offering the user the very traffic they asked it to
 * remove. The reverse mistake — observing before deciding — cannot be seen in a screenshot, which is
 * exactly why the sequence lives in one testable class instead of inside a `WebViewClient`, where no
 * JVM test can reach it.
 *
 * Everything the two collaborators need is Android-free: [AdBlocker] decides from a URL and the page
 * it belongs to, and [MediaRequestObserver] records admitted requests. This class only sequences them
 * and counts what it blocked, so `RequestInterceptionTest` can assert the ordering on the JVM.
 */
class RequestInterception(
    private val adBlocker: AdBlocker = AdBlocker(),
    private val observer: MediaRequestObserver
) {

    /** What the caller is told to do with the request. */
    enum class Decision {
        /** Answer with nothing: the request never leaves the app, and is never observed. */
        BLOCKED,

        /** Observe it, then load it the way the WebView always would have. */
        PASSED
    }

    /**
     * Decides [url] in the context of the page it belongs to.
     *
     * [method] and [range] are what the observer admits on — a download the app could start is a
     * plain GET — and are untouched by the blocker.
     */
    fun decide(url: String?, method: String?, range: String?, pageUrl: String?): Decision {
        if (adBlocker.shouldBlock(url, pageUrl)) return Decision.BLOCKED
        observer.onRequest(url, method, range)
        return Decision.PASSED
    }
}
