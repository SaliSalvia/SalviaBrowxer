package com.salvia.salviabrowxer.ui.utils

/**
 * Pulls the first real web address out of shared text.
 *
 * `ACTION_SEND` payloads are prose, not URLs — "Look at this https://example.com/clip it is
 * great" — so the whole payload must never be loaded as an address. Non-web schemes are
 * ignored: the browser only opens what it can actually render.
 */
object SharedLinkParser {

    private val URL_REGEX = Regex("""https?://[^\s"'<>]+""", RegexOption.IGNORE_CASE)
    private val SCHEME_REGEX = Regex("""^([a-zA-Z][a-zA-Z0-9+.\-]*):""")

    /** Returns the first `http`/`https` URL in [text], trimmed of trailing punctuation. */
    fun firstUrl(text: String?): String? {
        val match = text?.let { URL_REGEX.find(it) }?.value ?: return null
        return match.trimEnd('.', ',', ';', ':', '!', '?', ')', ']', '}', '"', '\'', '»', '،')
            .takeIf { it.length > "https://".length }
    }

    /** Normalises an `ACTION_VIEW` payload, which may be a bare host or a full URL. */
    fun fromViewData(data: String?): String? {
        val raw = data?.trim().orEmpty()
        if (raw.isEmpty()) return null
        return when (SCHEME_REGEX.find(raw)?.groupValues?.getOrNull(1)?.lowercase()) {
            "http", "https" -> raw
            // mailto:, tel:, market:, intent: belong to other apps, not to a browser.
            null -> raw.takeIf { " " !in it && "." in it }?.let { firstUrl("https://$it") }
            else -> null
        }
    }
}
