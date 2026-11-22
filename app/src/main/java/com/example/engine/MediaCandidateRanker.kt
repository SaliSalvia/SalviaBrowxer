package com.example.engine

import com.example.ui.DetectedMedia

/**
 * Conservative, evidence-based ordering for media candidates.
 *
 * This is an additive ranking layer: it does not resolve URLs, download content,
 * or claim that blob:/MSE streams are capturable. Existing priorityScore and
 * media metadata remain the primary ranking evidence within each role.
 */
internal object MediaCandidateRanker {

    internal enum class Role(val order: Int) {
        MAIN(0),
        SECONDARY(1),
        SUGGESTED(2),
        AUDIO(3),
        AD(4),
    }

    internal data class RankedCandidate(
        val media: DetectedMedia,
        val role: Role,
        val score: Int,
    )

    private val adMarkers = listOf(
        "doubleclick",
        "googlesyndication",
        "imasdk",
        "adserver",
        "vast",
        "vmap",
        "preroll",
        "pre-roll",
        "midroll",
        "mid-roll",
        "bumper",
        "companion",
        "ad-break",
        "ad_break",
        "sponsored",
        "advertisement",
    )

    private val suggestedUrlMarkers = listOf(
        "related",
        "recommend",
        "upnext",
        "up-next",
        "sidebar",
        "thumb_video",
        "preview_clip",
        "hover_preview",
    )

    fun rankCandidates(items: List<DetectedMedia>): List<RankedCandidate> {
        return items.map { media ->
            RankedCandidate(
                media = media,
                role = classify(media),
                score = media.priorityScore,
            )
        }.sortedWith(
            compareBy<RankedCandidate> { it.role.order }
                .thenByDescending { it.score }
                .thenByDescending { it.media.domAreaPx }
                .thenByDescending { it.media.durationSeconds }
                .thenByDescending { it.media.height }
                .thenBy { it.media.id },
        )
    }

    fun prioritize(items: List<DetectedMedia>): List<DetectedMedia> {
        return rankCandidates(items).mapIndexed { index, ranked ->
            ranked.media.copy(
                isMainVideo = index == 0 &&
                    !ranked.media.isAudioOnly &&
                    ranked.role != Role.AD,
            )
        }
    }

    private fun classify(media: DetectedMedia): Role {
        if (looksLikeAd(media)) return Role.AD
        if (media.isAudioOnly) return Role.AUDIO
        if (looksLikeSuggested(media)) return Role.SUGGESTED

        val hasMainEvidence = media.isMainVideo ||
            media.priorityScore >= 100 ||
            media.domAreaPx >= 40_000L ||
            media.height >= 480 ||
            media.qualities.size > 1

        return if (hasMainEvidence) Role.MAIN else Role.SECONDARY
    }

    private fun looksLikeAd(media: DetectedMedia): Boolean {
        val evidence = "${media.primaryUrl} ${media.title}".lowercase()
        return adMarkers.any(evidence::contains)
    }

    private fun looksLikeSuggested(media: DetectedMedia): Boolean {
        val url = media.primaryUrl.lowercase()
        if (suggestedUrlMarkers.any(url::contains)) return true

        val title = media.title.lowercase()
        return title.contains("related video") ||
            title.contains("recommended video") ||
            title.contains("preview clip") ||
            title.contains("sidebar video")
    }
}
