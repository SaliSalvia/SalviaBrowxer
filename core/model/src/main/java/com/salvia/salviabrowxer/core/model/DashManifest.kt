package com.salvia.salviabrowxer.core.model

/**
 * One selectable MPEG-DASH rendition, already resolved to concrete segment URLs.
 *
 * The manifest itself is not stored in the download queue: a parsed [DashManifest] is derived data
 * and can list thousands of URLs. The queue stores the manifest URL and a [DashRenditionId]
 * instead, and the service re-parses the (bounded, static) manifest when the transfer starts.
 */
data class DashRepresentation(
    val id: String,
    val mimeType: String,
    val codecs: String? = null,
    val bandwidth: Long? = null,
    val width: Int? = null,
    val height: Int? = null,
    val language: String? = null,
    val isVideo: Boolean = false,
    val isAudio: Boolean = false,
    /** The `EXT-X-MAP`-style initialisation segment, when the template lists one. */
    val initUrl: String? = null,
    val segmentUrls: List<String> = emptyList()
)

/** A parsed, clear, static (finite) DASH presentation. */
data class DashManifest(
    val sourceUrl: String,
    val isStatic: Boolean,
    /** `mediaPresentationDuration` in seconds when the manifest declares one; used for size estimates. */
    val durationSeconds: Double? = null,
    val representations: List<DashRepresentation> = emptyList()
) {
    /** Highest definition first, which is the order the quality sheet shows them in. */
    val videoRepresentations: List<DashRepresentation>
        get() = representations.filter { it.isVideo }
            .sortedByDescending { (it.height ?: 0).toLong() * 1_000_000L + (it.bandwidth ?: 0L) }

    val audioRepresentations: List<DashRepresentation>
        get() = representations.filter { it.isAudio }.sortedByDescending { it.bandwidth ?: 0L }

    fun representationById(id: String?): DashRepresentation? =
        id?.let { target -> representations.firstOrNull { it.id == target } }
}

/** Which representation(s) a queued download wants. */
sealed interface DashSelection {
    val videoId: String?
    val audioId: String?
    data class Video(override val videoId: String) : DashSelection { override val audioId: String? get() = null }
    data class Audio(override val audioId: String) : DashSelection { override val videoId: String? get() = null }
    data class Merged(override val videoId: String, override val audioId: String) : DashSelection
}

/**
 * Encodes a DASH selection into the single `renditionId` string the queue persists.
 *
 * A DASH video rendition and its audio are usually separate files; saving "1080p" from such a
 * manifest means downloading two representations and muxing them, so the id has to carry both.
 * Representation ids are numeric in practice, so `|` is a safe separator.
 */
object DashRenditionId {
    private const val VIDEO = "v"
    private const val AUDIO = "a"
    private const val MERGED = "m"

    fun video(representationId: String): String = "$VIDEO|$representationId"
    fun audio(representationId: String): String = "$AUDIO|$representationId"
    fun merged(videoId: String, audioId: String): String = "$MERGED|$videoId|$audioId"

    fun parse(value: String?): DashSelection? {
        val parts = value?.split('|') ?: return null
        return when (parts.firstOrNull()) {
            VIDEO -> parts.getOrNull(1)?.takeIf { it.isNotBlank() }?.let { DashSelection.Video(it) }
            AUDIO -> parts.getOrNull(1)?.takeIf { it.isNotBlank() }?.let { DashSelection.Audio(it) }
            MERGED -> {
                val video = parts.getOrNull(1)?.takeIf { it.isNotBlank() }
                val audio = parts.getOrNull(2)?.takeIf { it.isNotBlank() }
                if (video != null && audio != null) DashSelection.Merged(video, audio) else null
            }
            else -> null
        }
    }
}
