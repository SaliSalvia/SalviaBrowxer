package com.salvia.salviabrowxer.core.model

import com.salvia.salviabrowxer.core.model.MediaCandidate.MediaSource
import java.net.URI
import java.util.Locale

/** What a pasted link is: a file that can be fetched now, or a page that has to be opened first. */
enum class PastedLinkKind {
    /** The URL names its own container — `…/clip.mp4`, `…/master.m3u8`, `…/stream.mpd`. */
    MEDIA_FILE,

    /** Anything else: a page, which has to be loaded before its media can be found. */
    WEB_PAGE
}

/**
 * Classifies a link the user typed or pasted.
 *
 * This is what keeps the paste field honest. The app deliberately has no site-specific extractors,
 * so a link to a page cannot be turned into a download on its own — the page has to be opened and
 * detected. A link that already names a file is different: there is nothing to discover, so it can
 * go straight to the quality sheet. Getting that distinction wrong either sends a direct media URL
 * through a pointless page load, or promises a download from a page that will never produce one.
 *
 * Pure on purpose: no Context, no clipboard, no network. Extraction of a URL from clipboard prose
 * belongs to `SharedLinkParser` on the Android side; this decides what to do with it.
 */
object PastedLink {

    /** Returns `null` when [url] is not an `http`/`https` address at all. */
    fun classify(url: String): PastedLinkKind? {
        val parsed = webUri(url) ?: return null
        return if (isMediaFile(parsed.toString())) PastedLinkKind.MEDIA_FILE else PastedLinkKind.WEB_PAGE
    }

    // This is paste routing only, not a sniffer denylist: media actually loaded by a page can
    // still be admitted by MediaSniffAdmission. Match host boundaries, never URL substrings.
    private val pageHosts = setOf(
        "instagram.com", "tiktok.com", "youtube.com", "youtu.be", "facebook.com", "x.com"
    )

    private fun webUri(url: String): URI? {
        val parsed = runCatching { URI(url.trim()) }.getOrNull() ?: return null
        if (parsed.scheme?.lowercase(Locale.ROOT) !in setOf("http", "https")) return null
        if (parsed.host.isNullOrBlank() || parsed.rawUserInfo != null) return null
        if (parsed.port != -1 && parsed.port !in 1..65535) return null
        return parsed
    }

    fun isMediaFile(url: String): Boolean {
        val parsed = webUri(url) ?: return false
        val host = parsed.host.lowercase(Locale.ROOT).trimEnd('.')
        if (pageHosts.any { host == it || host.endsWith(".$it") }) return false
        val normalized = parsed.toString()
        return MediaUrlRules.hasMediaExtension(normalized) ||
            MediaUrlRules.isDashUrl(normalized) || MediaUrlRules.isPlaylistUrl(normalized)
    }

    /**
     * A candidate for a [PastedLinkKind.MEDIA_FILE] URL, so the quality sheet can open on it
     * immediately. `null` when the URL does not name a container the app can label.
     */
    fun mediaCandidate(url: String): MediaCandidate? {
        if (classify(url) != PastedLinkKind.MEDIA_FILE) return null
        val extension = MediaUrlRules.pathExtension(url)
            ?.takeIf { it in MediaUrlRules.MEDIA_EXTENSIONS || it == MediaUrlRules.DASH_EXTENSION }
            // A playlist is allowed to have no extension at all (`/hls/manifest?token=…`).
            ?: if (MediaUrlRules.isPlaylistUrl(url)) "m3u8" else null
            ?: return null
        val mime = when (extension) {
            MediaUrlRules.DASH_EXTENSION -> MediaUrlRules.DASH_MIME_TYPE
            "m3u8", "ts" -> "application/vnd.apple.mpegurl"
            in MediaUrlRules.AUDIO_EXTENSIONS -> "audio/$extension"
            else -> "video/$extension"
        }
        return MediaCandidate(
            pageUrl = url,
            mediaUrl = url,
            title = titleFrom(url),
            mimeType = mime,
            extension = extension,
            source = MediaSource.JS,
            confidence = 0.8f
        )
    }

    private fun titleFrom(url: String): String? = url
        .substringBefore('#').substringBefore('?')
        .substringAfterLast('/')
        .substringBeforeLast('.')
        .takeIf { it.isNotBlank() && it.length > 2 }
}
