package com.salvia.salviabrowxer.core.model

/** WebView interception sees request headers, NOT server response headers. Never trust Accept. */
object MediaRequestAdmission {
    fun isPartialRange(value: String?): Boolean {
        if (value.isNullOrBlank()) return false
        // The sole whole-file range. Multiple ranges, bounded ends and nonzero starts are pieces.
        return !value.filterNot { it.isWhitespace() }.equals("bytes=0-", ignoreCase = true)
    }

    fun admit(pageUrl: String, url: String, range: String? = null): MediaCandidate? {
        // An object URL cannot establish whether it names an actual Blob or a MediaSource.
        if (url.startsWith("blob:", ignoreCase = true)) return null
        return MediaSniffAdmission.admit(
            pageUrl, url, null, SniffOrigin.ELEMENT_SOURCE,
            partialRange = isPartialRange(range)
        )?.copy(source = MediaCandidate.MediaSource.WEBVIEW)
    }
}
