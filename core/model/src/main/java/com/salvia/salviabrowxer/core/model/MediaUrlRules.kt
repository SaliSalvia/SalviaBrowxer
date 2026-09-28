package com.salvia.salviabrowxer.core.model

/**
 * The single place that knows what a media URL looks like.
 *
 * It used to be five places: the injected sniffer, the DOM serialiser, `DomMediaDetector`, the
 * WebView request interceptor and a constants object. They disagreed. The worst case was
 * `manifest`: every layer used a bare `url.contains("manifest")` as an HLS signal, so a PWA
 * `site.webmanifest` or `manifest.json` was offered in the tray as a playlist that could never
 * download. A URL admitted by one layer could also be rejected by the next, which is how the
 * sniffer's XHR/fetch findings ended up discarded entirely.
 *
 * Everything that decides "is this media?" derives from here now, and [EXTENSION_ALTERNATION] is
 * interpolated into the injected JavaScript so the browser side cannot drift from the Kotlin side.
 */
object MediaUrlRules {

    // Kept identical to what shipped before: widening these silently reclassifies existing
    // candidates (isVideo/isAudio in the quality sheet), so it is a deliberate change, not a
    // side effect of tidying up.
    val VIDEO_EXTENSIONS: Set<String> = setOf("mp4", "webm", "mov", "avi", "3gp", "m4v", "mkv", "flv")
    val AUDIO_EXTENSIONS: Set<String> = setOf("mp3", "m4a", "aac", "wav", "flac", "ogg", "wma")
    val PLAYLIST_EXTENSIONS: Set<String> = setOf("m3u8", "ts")
    val MEDIA_EXTENSIONS: Set<String> = VIDEO_EXTENSIONS + AUDIO_EXTENSIONS + PLAYLIST_EXTENSIONS

    /** MPEG-DASH is recognised, explained and never downloaded, so it is not a "media" extension. */
    const val DASH_EXTENSION: String = "mpd"

    /**
     * `mp4|webm|…` — one alternation that Kotlin builds its regexes from and that is interpolated
     * into the injected JavaScript, so both sides match exactly the same set.
     */
    val EXTENSION_ALTERNATION: String = MEDIA_EXTENSIONS.joinToString("|")

    val PLAYLIST_MIME_TYPES: Set<String> = setOf(
        "application/vnd.apple.mpegurl",
        "application/x-mpegurl"
    )
    const val DASH_MIME_TYPE: String = "application/dash+xml"

    /**
     * A single HLS segment, not the thing the user wants. `video/mp2t` is an MPEG-TS fragment and
     * recognising it as media would fill the tray with one row per segment of every playlist.
     */
    val SEGMENT_MIME_TYPES: Set<String> = setOf("video/mp2t")

    /** `m4s`/`cmfv`/`cmfa` are MSE and DASH fragments for the same reason. */
    val SEGMENT_EXTENSIONS: Set<String> = setOf("m4s", "cmfv", "cmfa", "cmft", "mp4a")

    /**
     * CDNs routinely serve real video as `application/octet-stream`. It is therefore evidence
     * only when the URL also carries a media extension — on its own it must not admit anything,
     * or every binary download the page makes becomes a candidate.
     */
    val WEAK_MEDIA_MIME_TYPES: Set<String> = setOf("application/octet-stream", "binary/octet-stream")

    /** Requests the browser makes for itself that are never media. */
    val NON_MEDIA_EXTENSIONS: Set<String> = setOf(
        "jpg", "jpeg", "png", "gif", "webp", "bmp", "svg", "ico", "avif", "heic",
        "css", "js", "mjs", "map", "json", "xml", "txt", "html", "htm",
        "woff", "woff2", "ttf", "otf", "eot", "pdf", "zip", "gz", "vtt", "srt"
    )

    /**
     * The same alternation trick for the two reject lists, so the injected prefilter cannot drift
     * from Kotlin either. Declared after the sets they read on purpose: a Kotlin `object`
     * initialises its properties in source order, so an earlier declaration would read `null`.
     */
    val NON_MEDIA_ALTERNATION: String = NON_MEDIA_EXTENSIONS.joinToString("|")
    val SEGMENT_ALTERNATION: String = SEGMENT_EXTENSIONS.joinToString("|")

    /** Fragments a manifest lists: what a player fetches hundreds of, never a user-facing file. */
    private val SEGMENT_QUERY_MARKERS = listOf("bytestart=", "byteend=")

    /** Lowers a header value to a comparable mime: `video/mp4; codecs="avc1"` -> `video/mp4`. */
    fun normalizeMime(raw: String?): String? = raw
        ?.substringBefore(';')
        ?.trim()
        ?.trim('"', '\'')
        ?.lowercase()
        ?.takeIf { it.isNotEmpty() }

    /** The extension of the last path segment, lowercased, without the dot. */
    fun pathExtension(url: String?): String? {
        if (url.isNullOrBlank()) return null
        val path = url.substringBefore('#').substringBefore('?')
        val dot = path.lastIndexOf('.')
        val slash = path.lastIndexOf('/')
        if (dot <= slash || dot == path.length - 1) return null
        return path.substring(dot + 1).lowercase().takeIf { it.length in 1..5 && it.all(Char::isLetterOrDigit) }
    }

    fun hasMediaExtension(url: String): Boolean = pathExtension(url) in MEDIA_EXTENSIONS

    fun hasPlaylistExtension(url: String): Boolean = pathExtension(url) in PLAYLIST_EXTENSIONS

    fun isVideoMime(mimeType: String?): Boolean = normalizeMime(mimeType)?.startsWith("video/") == true

    fun isAudioMime(mimeType: String?): Boolean = normalizeMime(mimeType)?.startsWith("audio/") == true

    fun isPlaylistMime(mimeType: String?): Boolean = normalizeMime(mimeType) in PLAYLIST_MIME_TYPES

    fun isDashMime(mimeType: String?): Boolean = normalizeMime(mimeType) == DASH_MIME_TYPE

    fun isSegmentMime(mimeType: String?): Boolean = normalizeMime(mimeType) in SEGMENT_MIME_TYPES

    fun isWeakMediaMime(mimeType: String?): Boolean = normalizeMime(mimeType) in WEAK_MEDIA_MIME_TYPES

    /**
     * A video or audio mime, or an HLS mime. An MPEG-TS fragment is deliberately not media: it is
     * one piece of a playlist, and treating it as media fills the tray with one row per segment.
     */
    fun isMediaMime(mimeType: String?): Boolean {
        val mime = normalizeMime(mimeType) ?: return false
        if (isSegmentMime(mime)) return false
        return mime.startsWith("video/") || mime.startsWith("audio/") || mime in PLAYLIST_MIME_TYPES
    }

    fun isDashUrl(url: String, mimeType: String? = null): Boolean =
        pathExtension(url) == DASH_EXTENSION || isDashMime(mimeType)

    /**
     * HLS detection that does not fire on web app manifests.
     *
     * `.m3u8` and an HLS content type are proof. A bare `manifest` path segment is a hint that
     * holds on real CDNs (`/hls/manifest?token=…`), so it is kept — but only after excluding the
     * document formats that also end in `manifest`.
     */
    fun isPlaylistUrl(url: String, mimeType: String? = null): Boolean {
        if (isPlaylistMime(mimeType)) return true
        val path = url.substringBefore('#').substringBefore('?').lowercase()
        val name = path.substringAfterLast('/')
        if (name.isEmpty()) return false
        if (name.endsWith(".json") || name.endsWith(".webmanifest") || name.endsWith(".xml")) return false
        if (name.endsWith(".m3u8")) return true
        return name == "manifest" || name.startsWith("manifest.")
    }

    /** A request the browser makes for itself that cannot be media. */
    fun looksLikeNonMedia(url: String): Boolean = pathExtension(url) in NON_MEDIA_EXTENSIONS

    /** One fragment of a manifest, not a file: a segment or an explicit byte range. */
    fun looksLikeSegment(url: String): Boolean {
        if (pathExtension(url) in SEGMENT_EXTENSIONS) return true
        val query = url.substringAfter('?', "").lowercase()
        return SEGMENT_QUERY_MARKERS.any { it in query }
    }

    /** The container to record for a mime the page told us about. */
    fun extensionForMime(mimeType: String?): String? = when (val mime = normalizeMime(mimeType)) {
        null -> null
        in PLAYLIST_MIME_TYPES -> "m3u8"
        DASH_MIME_TYPE -> "mpd"
        else -> when {
            mime.startsWith("video/mp4") -> "mp4"
            mime.startsWith("video/webm") -> "webm"
            mime.startsWith("video/quicktime") -> "mov"
            mime.startsWith("video/x-matroska") -> "mkv"
            mime.startsWith("video/x-flv") -> "flv"
            mime.startsWith("video/3gpp") -> "3gp"
            mime.startsWith("audio/mpeg") -> "mp3"
            mime.startsWith("audio/mp4") -> "m4a"
            mime.startsWith("audio/aac") -> "aac"
            mime.startsWith("audio/wav") || mime.startsWith("audio/x-wav") -> "wav"
            mime.startsWith("audio/flac") -> "flac"
            mime.startsWith("audio/ogg") -> "ogg"
            mime.startsWith("audio/x-ms-wma") -> "wma"
            else -> null
        }
    }
}
