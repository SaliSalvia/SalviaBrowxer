package com.salvia.salviabrowxer.ui.utils

import com.salvia.salviabrowxer.core.model.MediaUrlRules

/** What a detected candidate is, for the tray's kind label. `null` when it cannot be told. */
enum class MediaKind { VIDEO, AUDIO, PLAYLIST }

/**
 * Why a candidate cannot be offered. Both reasons are features the app does not have, so the
 * honest answer is a message, never a download that is guaranteed to fail.
 */
enum class UnsupportedMedia { DASH, LIVE }

/**
 * The single place that decides whether a detected URL is something this app can save.
 *
 * MPEG-DASH is segmented from a manifest the app cannot parse, and a live playlist has no end, so
 * neither is offered anywhere — not in the tray, not in the quality sheet, not in the queue. The
 * URL-shape half of that now lives in [MediaUrlRules], which every detection layer shares, so this
 * object is only the rule as the UI asks for it.
 */
object MediaOfferability {

    /** Classifies a candidate from whatever the page told us about it. */
    fun kindOf(mimeType: String?, extension: String?): MediaKind? {
        val mime = MediaUrlRules.normalizeMime(mimeType)
        val ext = extension?.lowercase()
        return when {
            ext in MediaUrlRules.PLAYLIST_EXTENSIONS || MediaUrlRules.isPlaylistMime(mime) -> MediaKind.PLAYLIST
            MediaUrlRules.isVideoMime(mime) || ext in MediaUrlRules.VIDEO_EXTENSIONS -> MediaKind.VIDEO
            MediaUrlRules.isAudioMime(mime) || ext in MediaUrlRules.AUDIO_EXTENSIONS -> MediaKind.AUDIO
            else -> null
        }
    }

    /** True when the URL or its headers describe an MPEG-DASH manifest. */
    fun isDash(url: String, mimeType: String?, extension: String?): Boolean =
        extension?.equals(MediaUrlRules.DASH_EXTENSION, ignoreCase = true) == true ||
            MediaUrlRules.isDashUrl(url, mimeType)

    /**
     * The reason this candidate cannot be offered, or `null` when it can be downloaded.
     * [isLive] comes from detection; an unknown value means "not known to be live".
     */
    fun unsupportedReason(url: String, mimeType: String?, extension: String?, isLive: Boolean = false): UnsupportedMedia? = when {
        isDash(url, mimeType, extension) -> UnsupportedMedia.DASH
        isLive -> UnsupportedMedia.LIVE
        else -> null
    }
}
