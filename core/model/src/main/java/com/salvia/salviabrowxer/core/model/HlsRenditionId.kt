package com.salvia.salviabrowxer.core.model

/**
 * Which rendition(s) of an HLS master playlist a queued download wants.
 *
 * A master playlist can name its audio separately (`#EXT-X-MEDIA:TYPE=AUDIO`): every video variant
 * is then a silent track and the sound lives in its own playlist. Saving "1080p" from such a stream
 * means downloading the chosen variant *and* the audio rendition and muxing them, so the selection
 * has to carry both playlist URLs.
 */
sealed interface HlsSelection {
    /** A variant with no separate audio rendition — the plain, one-playlist path. */
    data class Video(val videoUrl: String) : HlsSelection

    /** A silent video variant joined with its `EXT-X-MEDIA` audio rendition. */
    data class Merged(val videoUrl: String, val audioUrl: String) : HlsSelection
}

/**
 * Encodes an HLS selection into the single `renditionId` string the queue persists — the same
 * trick as [DashRenditionId], for the same reason: the playlists are re-fetched at transfer time,
 * so the row must remember *which* variant (and, when the audio is separate, which audio playlist)
 * the user picked.
 *
 * The prefixes (`hv`, `hm`) are deliberately disjoint from DASH's (`v`, `a`, `m`): the first
 * `|`-separated token decides the family, so neither parser can mistake the other's ids. `|` is
 * not a legal raw URL character (RFC 3986), so a plain playlist URL never parses as a selection —
 * rows queued before this encoding existed keep working unchanged.
 */
object HlsRenditionId {
    private const val VIDEO = "hv"
    private const val MERGED = "hm"

    fun video(playlistUrl: String): String = "$VIDEO|$playlistUrl"
    fun merged(videoUrl: String, audioUrl: String): String = "$MERGED|$videoUrl|$audioUrl"

    fun parse(value: String?): HlsSelection? {
        val parts = value?.split('|') ?: return null
        return when (parts.firstOrNull()) {
            VIDEO -> parts.getOrNull(1)?.takeIf { it.isNotBlank() }?.let { HlsSelection.Video(it) }
            MERGED -> {
                val video = parts.getOrNull(1)?.takeIf { it.isNotBlank() }
                val audio = parts.getOrNull(2)?.takeIf { it.isNotBlank() }
                if (video != null && audio != null) HlsSelection.Merged(video, audio) else null
            }
            else -> null
        }
    }
}
