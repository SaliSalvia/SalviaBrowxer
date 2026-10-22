package com.salvia.salviabrowxer.service.queue

import com.salvia.salviabrowxer.core.model.DashRenditionId
import com.salvia.salviabrowxer.core.model.HlsRenditionId
import com.salvia.salviabrowxer.media.downloader.RangePlanner

/** Which of the three transfer shapes a queued row becomes. */
enum class TransferKind { DASH, HLS, DIRECT }

/**
 * What a queued row will be transferred as, and what that shape implies for retry and parallelism.
 *
 * This used to be three `when` branches buried in `DownloadService.processDownload`, where no unit
 * test could reach it and where the HLS test (`url.contains(".m3u8")`) lived next to network code.
 * Pulling it out makes the *decision* testable on the JVM: DASH is decided by the row's
 * `renditionId` alone (a manifest URL is re-fetched at transfer time, so the URL proves nothing),
 * HLS by its own renditionId, the final URL, or the mime the server sent, and everything else is
 * a plain file.
 */
data class TransferPlan(
    val kind: TransferKind,
    /**
     * A failure is worth retrying. Only a *segmented* transfer answers yes: it stages its parts
     * beside the output and resumes from them, so a retry continues instead of downloading from the
     * first byte again. Retrying a plain file would only re-do what already failed.
     */
    val retryable: Boolean,
    /**
     * A direct file may be fetched over several `Range` connections. False for segmented transfers
     * (they have their own parallel fetcher) and for a settings value below
     * [RangePlanner.MIN_CONNECTIONS].
     */
    val parallelRange: Boolean
) {
    companion object {

        fun of(renditionId: String?, finalUrl: String, mimeType: String?, parallelConnections: Int): TransferPlan {
            val dash = DashRenditionId.parse(renditionId) != null
            val hls = HlsRenditionId.parse(renditionId) != null || isHls(finalUrl, mimeType)
            return when {
                dash -> TransferPlan(TransferKind.DASH, retryable = true, parallelRange = false)
                hls -> TransferPlan(TransferKind.HLS, retryable = true, parallelRange = false)
                else -> TransferPlan(
                    kind = TransferKind.DIRECT,
                    retryable = false,
                    parallelRange = parallelConnections >= RangePlanner.MIN_CONNECTIONS
                )
            }
        }

        /**
         * HLS is proven by the playlist URL or by the mime the server sent, never by the string
         * `manifest` appearing somewhere in the path — a PWA's `site.webmanifest` is not a stream.
         */
        fun isHls(finalUrl: String, mimeType: String?): Boolean =
            finalUrl.contains(".m3u8", ignoreCase = true) ||
                mimeType?.contains("mpegurl", ignoreCase = true) == true
    }
}

/**
 * The bounded retry schedule for a segmented transfer: three attempts in total, with a backoff that
 * grows per attempt and stays short.
 *
 * Pure, so the numbers can be asserted instead of inferred: `maxRetries = 2` means the *first*
 * attempt plus two retries, and the backoff for retry `n` is `base * n` — a linear schedule that
 * keeps a flaky CDN's recovery within a few seconds of the pause the user did not ask for.
 */
class SegmentedRetryPolicy(
    private val maxRetries: Int = DEFAULT_MAX_RETRIES,
    private val baseDelayMillis: Long = DEFAULT_BASE_DELAY_MILLIS
) {

    sealed interface Decision {
        /** Try again after [backoffMillis], as attempt [attempt] (1-based). */
        data class Retry(val attempt: Int, val backoffMillis: Long) : Decision

        /** The failure is final; the caller reports it on the row. */
        data object GiveUp : Decision
    }

    /** [attempt] is how many retries have already been used (0 after the first failure). */
    fun onFailure(attempt: Int): Decision =
        if (attempt >= maxRetries) Decision.GiveUp
        else Decision.Retry(attempt = attempt + 1, backoffMillis = baseDelayMillis * (attempt + 1))

    companion object {
        /** Two retries after the first attempt: three tries in total. */
        const val DEFAULT_MAX_RETRIES = 2
        const val DEFAULT_BASE_DELAY_MILLIS = 1_200L
    }
}

/**
 * Holds or releases work for the Wi-Fi-only setting.
 *
 * The rule itself is [WifiOnlyPolicy.shouldHold], but the *transitions* are what used to be tangled
 * up with the service: both the setting and the connectivity report feed the same decision, the
 * decision has to be idempotent (a network callback fires repeatedly), and turning the setting off
 * must release work that the setting held even when the network did not change. Each of those is a
 * test here instead of an accident on a device.
 */
class WifiHoldGate {

    private var wifiOnly = false

    /** True while the gate is holding work that must be released when Wi-Fi returns. */
    var isHolding: Boolean = false
        private set

    /** The user changed the setting. */
    fun onWifiOnlyChanged(enabled: Boolean, onWifi: Boolean): WifiHoldAction {
        wifiOnly = enabled
        return when {
            !enabled -> release()
            onWifi -> release()
            else -> hold()
        }
    }

    /** The active network changed (or was reported again, which must be a no-op). */
    fun onConnectivityChanged(onWifi: Boolean): WifiHoldAction {
        if (!wifiOnly) return WifiHoldAction.None
        return if (onWifi) release() else hold()
    }

    private fun hold(): WifiHoldAction {
        if (isHolding) return WifiHoldAction.None
        isHolding = true
        return WifiHoldAction.Hold
    }

    private fun release(): WifiHoldAction {
        if (!isHolding) return WifiHoldAction.None
        isHolding = false
        return WifiHoldAction.Release
    }
}

/** What the caller must do about the queue after a Wi-Fi decision. */
sealed interface WifiHoldAction {
    /** Nothing changed. */
    data object None : WifiHoldAction

    /** Pause everything active and mark it as held. */
    data object Hold : WifiHoldAction

    /** Requeue exactly the rows the gate held. */
    data object Release : WifiHoldAction
}
