package com.salvia.salviabrowxer.core.model

/** Bounded, URL-keyed feed history. Visibility ranks evidence; it never creates evidence. */
object MediaCandidateIndex {
    const val LIMIT = 24

    fun merge(
        existing: List<MediaCandidate>,
        incoming: List<MediaCandidate>,
        visibleUrl: String?
    ): List<MediaCandidate> {
        val byUrl = existing.associateByTo(linkedMapOf()) { it.mediaUrl }
        val recent = incoming.distinctBy { it.mediaUrl }.takeLast(4).map { it.mediaUrl }.toSet()
        for (candidate in incoming) {
            val old = byUrl[candidate.mediaUrl]
            val strongest = if (old != null && old.confidence >= candidate.confidence) old else candidate
            byUrl[candidate.mediaUrl] = strongest.copy(
                id = old?.id ?: candidate.id,
                title = old?.title ?: candidate.title,
                thumbnailUrl = old?.thumbnailUrl ?: candidate.thumbnailUrl,
                isLive = old?.isLive == true || candidate.isLive,
                isMediaSource = old?.isMediaSource == true || candidate.isMediaSource,
                isBlobFile = old?.isBlobFile == true || candidate.isBlobFile
            )
        }
        // Retain current + newest arrivals before confidence-based eviction. Otherwise an endless
        // feed eventually fills with 24 high-confidence old posts and never admits another video.
        return byUrl.values.sortedWith(
            compareByDescending<MediaCandidate> { it.mediaUrl == visibleUrl }
                .thenByDescending { it.mediaUrl in recent }
                .thenByDescending { it.confidence }
        ).take(LIMIT).sortedWith(
            compareByDescending<MediaCandidate> { it.mediaUrl == visibleUrl }
                .thenByDescending { it.confidence }
        )
    }
}
