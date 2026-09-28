package com.salvia.salviabrowxer.core.model

import com.salvia.salviabrowxer.core.model.MediaCandidate.MediaSource

/**
 * Where the injected sniffer saw a URL. The origin is the evidence: a response header is the
 * server stating what the bytes are, a `<video>` that loaded metadata is the player stating it,
 * and a URL merely referenced by markup states nothing at all.
 */
enum class SniffOrigin(val wireName: String) {
    /** An XMLHttpRequest finished and its `Content-Type` was media. */
    XHR_RESPONSE("xhr"),

    /** A `fetch()` response header was media. */
    FETCH_RESPONSE("fetch"),

    /**
     * A `<video>`/`<audio>` reports loaded metadata (`readyState`, `duration`, `videoWidth`) for
     * this URL, or an MSE `SourceBuffer` was created with a media mime for it. The page is telling
     * us it is playable, which is real evidence even when the CDN sent no usable header.
     */
    ELEMENT_METADATA("element-metadata"),

    /** Markup points at this URL but nothing has loaded it: only the extension is evidence. */
    ELEMENT_SOURCE("element-source");

    companion object {
        fun fromWire(name: String?): SniffOrigin? =
            entries.firstOrNull { it.wireName == name?.trim()?.lowercase() }
    }
}

/**
 * Decides whether a URL the page's own network layer revealed is worth offering the user.
 *
 * The point of the whole exercise is media these sites serve from extension-less CDN paths, so the
 * rule is evidence-based rather than extension-based. But the evidence has to come from the server
 * or from a player that actually loaded the thing: admitting every fetch whose URL merely looked
 * plausible is how a sniffer turns one video into two hundred tray rows of API calls and HLS
 * fragments. Nothing here guesses, and nothing here performs a network request — by the time this
 * runs the response headers are already in hand.
 *
 * The injected JavaScript applies a cheaper version of this as a prefilter, purely so it does not
 * push every URL in the document across the bridge. This is the authority.
 */
object MediaSniffAdmission {

    /** A header is the strongest thing we can hear; a loaded element is close behind. */
    private const val CONFIDENCE_RESPONSE_HEADER = 0.93f
    private const val CONFIDENCE_PLAYABLE_ELEMENT = 0.9f
    private const val CONFIDENCE_MARKUP_REFERENCE = 0.7f

    /**
     * Returns the candidate to add, or `null` when this sighting is not something the user can
     * download.
     *
     * @param mimeType the response `Content-Type` when there was one, else `null`.
     * @param partialRange true when the page asked for a **piece** of the resource — a range that
     *   does not start at zero, or that has a bounded end. `bytes=0-` is the whole file and is not
     *   a partial read, which matters because plenty of players fetch a whole progressive MP4 with
     *   exactly that header.
     * @param elementKind `"video"`, `"audio"` or `null` — what the page's own element called itself,
     *   used only when neither the headers nor the URL name a container.
     */
    fun admit(
        pageUrl: String,
        url: String,
        mimeType: String?,
        origin: SniffOrigin?,
        partialRange: Boolean = false,
        elementKind: String? = null
    ): MediaCandidate? {
        if (origin == null) return null
        if (url.length < 12) return null

        val scheme = url.substringBefore(':').lowercase()
        val isBlob = scheme == "blob"
        if (!isBlob && scheme != "http" && scheme != "https") return null

        // A blob the page created is already a file handle, but only the page can read it, so it
        // is offered on the type the MSE hook reported and nothing else.
        if (isBlob) {
            val mime = MediaUrlRules.normalizeMime(mimeType) ?: return null
            if (!MediaUrlRules.isMediaMime(mime) && !MediaUrlRules.isDashMime(mime)) return null
            return candidate(pageUrl, url, mime, null, CONFIDENCE_PLAYABLE_ELEMENT)
        }

        if (MediaUrlRules.looksLikeNonMedia(url)) return null
        if (MediaUrlRules.looksLikeSegment(url)) return null
        if (MediaUrlRules.isSegmentMime(mimeType)) return null
        // A partial read is a player pulling fragments of something it already has. The whole
        // file, if it is downloadable, was fetched from byte zero.
        if (partialRange) return null

        val mime = MediaUrlRules.normalizeMime(mimeType)
        val hasExtension = MediaUrlRules.hasMediaExtension(url)
        val isPlaylist = MediaUrlRules.isPlaylistUrl(url, mime)
        val isDash = MediaUrlRules.isDashUrl(url, mime)
        val isStrongMime = MediaUrlRules.isMediaMime(mime)

        val admitted = when (origin) {
            // The server named the type, so an extension-less CDN path is exactly what this admits.
            SniffOrigin.XHR_RESPONSE, SniffOrigin.FETCH_RESPONSE ->
                isStrongMime || isPlaylist || isDash ||
                    (hasExtension && MediaUrlRules.isWeakMediaMime(mime))

            // The player loaded it. There may be no header and no extension worth trusting.
            SniffOrigin.ELEMENT_METADATA -> true

            SniffOrigin.ELEMENT_SOURCE -> hasExtension
        }
        if (!admitted) return null

        // Never record `video/*` as a mime: the container comes from the extension, from what the
        // server actually said, or stays unknown.
        val effectiveMime = mime?.takeIf { MediaUrlRules.isMediaMime(it) || MediaUrlRules.isDashMime(it) }

        return candidate(pageUrl, url, effectiveMime, elementKind, confidenceFor(origin))
    }

    private fun confidenceFor(origin: SniffOrigin): Float = when (origin) {
        SniffOrigin.XHR_RESPONSE, SniffOrigin.FETCH_RESPONSE -> CONFIDENCE_RESPONSE_HEADER
        SniffOrigin.ELEMENT_METADATA -> CONFIDENCE_PLAYABLE_ELEMENT
        SniffOrigin.ELEMENT_SOURCE -> CONFIDENCE_MARKUP_REFERENCE
    }

    private fun candidate(
        pageUrl: String,
        url: String,
        mime: String?,
        elementKind: String?,
        confidence: Float
    ): MediaCandidate {
        val fromMime = MediaUrlRules.extensionForMime(mime)
        val fromUrl = MediaUrlRules.pathExtension(url)
            ?.takeIf { it in MediaUrlRules.MEDIA_EXTENSIONS || it == MediaUrlRules.DASH_EXTENSION }
        var extension = fromMime ?: fromUrl
        var finalMime = mime

        if (extension == null && elementKind != null) {
            // Nothing in the URL and nothing in the headers says what the container is. The
            // element does say whether it is playing video or audio, so the download lands as a
            // file a player can open rather than an extension-less blob.
            val audio = elementKind.equals("audio", ignoreCase = true)
            extension = if (audio) "m4a" else "mp4"
            finalMime = if (audio) "audio/mp4" else "video/mp4"
        }

        return MediaCandidate(
            pageUrl = pageUrl,
            mediaUrl = url,
            title = titleFrom(url),
            mimeType = finalMime ?: defaultMimeFor(extension),
            extension = extension,
            source = MediaSource.JS,
            confidence = confidence
        )
    }

    private fun defaultMimeFor(extension: String?): String? = when (extension) {
        null -> null
        "m3u8", "ts" -> "application/vnd.apple.mpegurl"
        MediaUrlRules.DASH_EXTENSION -> MediaUrlRules.DASH_MIME_TYPE
        else -> if (extension in MediaUrlRules.AUDIO_EXTENSIONS) "audio/$extension" else "video/$extension"
    }

    private fun titleFrom(url: String): String? =
        url.substringBefore('#').substringBefore('?').substringAfterLast('/')
            .substringBeforeLast('.')
            .takeIf { it.isNotBlank() && it.length > 2 }
}
