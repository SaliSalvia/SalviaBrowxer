package com.salvia.salviabrowxer.ui.utils

import com.salvia.salviabrowxer.core.model.MediaCandidate
import java.util.Locale

/**
 * Picks the **main** video of a page from the candidates the detection layers already admitted —
 * the "which file did the user actually come here for" question every tray-only browser leaves to
 * the user.
 *
 * This is a *ranking over evidence*, not new evidence: every input is a fact the app already has
 * (which URL the visible player loaded, what kind the file is, what the page is, which hosts are
 * known ad hosts), so a candidate can never be promoted to "main" on a signal that would not also
 * justify showing it in the tray. The selector therefore never adds a request, never guesses a
 * site-specific pattern and cannot manufacture a download the layers below refused.
 *
 * The competitor's floating button downloads "the video of the page"; this is the same promise,
 * built the only honest way this app has: score what is provably there and stay quiet when nothing
 * scores well enough to deserve one tap.
 *
 * Every rule is additive and each one is small on purpose — a big score for a single signal is how
 * a pre-roll ends up labelled "main content". The strongest signal by far is the visible player
 * (`visibleUrl`), which is a relevance hint the sniffer earned by watching the page play, not an
 * assumption.
 */
object MainContentSelector {

    /** A candidate must reach this score to be worth a one-tap download. */
    const val MINIMUM_SCORE: Int = 10

    /**
     * The best "main" candidate, or `null` when nothing on the page deserves the floating button's
     * one tap — in which case the honest fallback is the tray, where the user chooses.
     *
     * [pageUrl] is the document the current tab is showing, used only to prefer a candidate the
     * page itself carried over one a frame reported; both remain in the tray either way.
     */
    fun select(
        candidates: List<MediaCandidate>,
        visibleUrl: String?,
        pageUrl: String?
    ): MediaCandidate? =
        candidates
            .asSequence()
            .filter { it.isOfferableMain() }
            .map { it to score(it, visibleUrl, pageUrl) }
            .filter { (_, score) -> score >= MINIMUM_SCORE }
            .maxWithOrNull(
                // Ascending by score on purpose: maxWithOrNull returns the element the comparator
                // ranks greatest, so a descending first key would crown the *lowest* score.
                compareBy<Pair<MediaCandidate, Int>> { it.second }
                    .thenByDescending { it.first.confidence }
                    .thenBy { it.first.duration ?: 0L }
            )
            ?.first

    /**
     * The score of one candidate. Public for tests and for the tray's future use, not for new
     * call sites: callers should ask [select], so "what is main" has one answer in the app.
     */
    fun score(candidate: MediaCandidate, visibleUrl: String?, pageUrl: String?): Int {
        var score = 0

        // The strongest evidence there is: the player the user is watching loaded this URL. Still a
        // hint (a pre-roll can occupy the same player), which is why it is +50 and not +500.
        if (visibleUrl != null && candidate.mediaUrl == visibleUrl) score += 50

        // The document itself reported it, rather than a frame. Frames are legitimate sources, so
        // this is a nudge, never a gate.
        if (!pageUrl.isNullOrBlank() && candidate.pageUrl == pageUrl) score += 8

        // Kind: the button says "download video", so video leads; an offerable playlist is the
        // same content behind a probe; audio is a legitimate but weaker main.
        when (MediaOfferability.kindOf(candidate.mimeType, candidate.extension)) {
            MediaKind.VIDEO -> score += 24
            MediaKind.PLAYLIST -> score += 14
            MediaKind.AUDIO -> score += 6
            null -> return Int.MIN_VALUE // no kind, no offer, no main
        }

        // Known ad and tracker hosts — the same narrow list the opt-in blocker uses. Here the
        // verdict is categorical rather than a demotion: media on those hosts is never blocked and
        // stays in the tray, but no score it can reach makes it "the main video", because no
        // additive penalty reliably beats player evidence + kind + duration combined.
        val host = AdBlockRules.hostOf(candidate.mediaUrl)
        if (host != null && AdBlockRules.DEFAULT_BLOCKED_HOSTS.any { AdBlockRules.matchesDomain(host, it) }) {
            return Int.MIN_VALUE
        }

        // Ad-shaped paths on otherwise ordinary hosts: pre-rolls and VAST companions. Segment-level
        // precision is impossible and not attempted; the words below are the ones ad delivery
        // URLs reliably carry.
        val lowered = candidate.mediaUrl.lowercase(Locale.ROOT)
        if (AD_PATH_MARKERS.any { lowered.contains(it) }) score -= 45

        // Duration: ads are short. A real main video runs for minutes. Both bounds are loose
        // because a six-second loop can genuinely be the content. The unit is milliseconds —
        // the same unit `MediaCandidate.duration` has carried since the resolver first filled it.
        // Local copies because the fields belong to another module: a smart cast across modules is
        // not a thing Kotlin promises, and this file should not depend on that promise.
        val duration = candidate.duration
        when {
            duration != null && duration in 1 until SHORT_CONTENT_MS -> score -= 28
            duration != null && duration >= LONG_CONTENT_MS -> score += 12
        }

        // Size behaves like duration for feeds that report it without a player sighting.
        val estimatedSize = candidate.estimatedSize
        if (estimatedSize != null && estimatedSize >= SUBSTANTIAL_BYTES) score += 6

        // A resolution named in the URL (`.../1080p/...`, `...720.mp4`) is a weak quality hint and
        // a mild "this is a rendition somebody labelled" signal.
        RESOLUTION_REGEX.find(lowered)?.groupValues?.get(1)?.toIntOrNull()?.let { height ->
            score += when {
                height >= 2160 -> 7
                height >= 1080 -> 5
                height >= 720 -> 3
                else -> 1
            }
        }

        // Whatever confidence the detecting layer itself assigned, scaled to stay a tiebreaker.
        score += (candidate.confidence.coerceIn(0f, 1f) * 8).toInt()

        return score
    }

    /** Only what the tray itself would offer can be the main file: same rules, same refusals. */
    private fun MediaCandidate.isOfferableMain(): Boolean =
        !isLive &&
            // No kind, no offer: an unclassifiable URL is never "the main video".
            MediaOfferability.kindOf(mimeType, extension) != null &&
            MediaOfferability.unsupportedReason(
                url = mediaUrl,
                mimeType = mimeType,
                extension = extension,
                isLive = isLive,
                isMediaSource = isMediaSource,
                isBlobFile = isBlobFile,
                // The tray probes DASH before offering; a manifest can therefore be the main file.
                dashIsSupported = true
            ) == null

    private const val SHORT_CONTENT_MS = 15_000L
    private const val LONG_CONTENT_MS = 60_000L
    private const val SUBSTANTIAL_BYTES = 8L * 1024 * 1024

    /**
     * Path fragments that name ad delivery rather than content. Matched on the whole lowered URL so
     * a query parameter (`?vast=...`) is covered by the same list.
     */
    private val AD_PATH_MARKERS = listOf(
        "/ads/", "/ad/", "adserver", "advertis", "preroll", "pre-roll", "/vast", "vmap",
        "doubleclick", "imasdk", "companion", "/banners/", "sponsor"
    )

    private val RESOLUTION_REGEX = Regex("(\\d{3,4})p\\b")
}
