package com.salvia.salviabrowxer.ui.utils

/**
 * App-wide constants that are still referenced by the runtime. Media extensions/MIME lists now live
 * in `MediaFileTypes` (single source of truth); stale duplicates have been removed.
 */
object Constants {
    const val DEFAULT_HOMEPAGE = "https://www.google.com"
    const val DESKTOP_USER_AGENT = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/100.0.0.0 Safari/537.36"

    const val CONNECT_TIMEOUT_SECONDS = 10L

    const val DEFAULT_SEARCH_ENGINE = "Google"

    val SEARCH_ENGINES = mapOf(
        "Google" to "https://www.google.com/search?q=%s",
        "DuckDuckGo" to "https://duckduckgo.com/?q=%s",
        "Bing" to "https://www.bing.com/search?q=%s",
        "Yahoo" to "https://search.yahoo.com/search?p=%s",
        "Ecosia" to "https://www.ecosia.org/search?q=%s"
    )
}
