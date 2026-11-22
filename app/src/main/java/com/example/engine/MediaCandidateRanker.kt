package com.example.engine

import com.example.ui.DetectedMedia
import com.example.ui.MediaCaptureStatus
import com.example.ui.MediaDetectionSource

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
        REFERENCE_ONLY(4),
        AD(5),
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
                score = calculateScore(media),
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
                    ranked.media.isDownloadable &&
                    !ranked.media.isAudioOnly &&
                    ranked.role != Role.AD &&
                    ranked.role != Role.REFERENCE_ONLY,
            )
        }
    }

    private fun classify(media: DetectedMedia): Role {
        if (looksLikeAd(media)) return Role.AD
        if (media.captureStatus != MediaCaptureStatus.DOWNLOADABLE) return Role.REFERENCE_ONLY
        if (media.isAudioOnly) return Role.AUDIO
        if (looksLikeSuggested(media)) return Role.SUGGESTED

        val strongDomEvidence =
            media.detectionSource in setOf(
                MediaDetectionSource.HTML5_ELEMENT,
                MediaDetectionSource.MSE_REFERENCE,
            ) &&
                media.isDownloadable &&
                (media.domAreaPx >= 40_000L ||
                    media.durationSeconds >= 20.0 ||
                    (media.width >= 640 && media.height >= 360))
        val structuredMetadataEvidence = media.detectionSource in setOf(
            MediaDetectionSource.JSON_LD,
            MediaDetectionSource.OPEN_GRAPH,
        )
        val networkEvidence = media.detectionSource == MediaDetectionSource.NETWORK_HOOK &&
            media.priorityScore >= 85
        val manifestEvidence = media.qualities.size > 1 &&
            media.qualities.any { it.isHls || it.isDash }
        val legacyEvidence = media.detectionSource == MediaDetectionSource.UNKNOWN &&
            media.isMainVideo

        return if (
            strongDomEvidence ||
            structuredMetadataEvidence ||
            networkEvidence ||
            manifestEvidence ||
            legacyEvidence
        ) {
            Role.MAIN
        } else {
            Role.SECONDARY
        }
    }

    private fun calculateScore(media: DetectedMedia): Int {
        val domAreaBonus = (media.domAreaPx / 10_000L).coerceIn(0L, 30L).toInt()
        val durationBonus = when {
            media.durationSeconds >= 180.0 -> 24
            media.durationSeconds >= 60.0 -> 18
            media.durationSeconds >= 20.0 -> 10
            media.durationSeconds >= 12.0 -> 4
            else -> 0
        }
        val resolutionBonus = (media.height / 120).coerceIn(0, 18)
        val qualityBonus = (media.qualities.size.coerceAtMost(6) - 1).coerceAtLeast(0) * 4
        val confidenceBonus = (media.confidenceScore.coerceIn(0, 100) / 10)
        return media.priorityScore + domAreaBonus + durationBonus +
            resolutionBonus + qualityBonus + confidenceBonus
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
