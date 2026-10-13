package com.salvia.salviabrowxer.service.queue

import com.salvia.salviabrowxer.core.database.entities.DownloadEntity
import com.salvia.salviabrowxer.core.model.DownloadState
import com.salvia.salviabrowxer.service.WIFI_HOLD_MARKER

/**
 * Which rows the Wi-Fi-only setting may release.
 *
 * A held row is recognised by the marker **and** by its status, not by the marker alone: a user who
 * paused a transfer while it was held, or a transfer that failed and kept the marker as its error,
 * must not be silently resumed when Wi-Fi comes back. The marker is a stable, untranslated string
 * for exactly this reason — a localised one stops matching after a language change and the transfer
 * then waits for Wi-Fi forever.
 */
object WifiHold {

    fun isHeldRow(row: DownloadEntity): Boolean =
        row.status == DownloadState.PAUSED && row.error == WIFI_HOLD_MARKER

    fun heldRows(rows: List<DownloadEntity>): List<DownloadEntity> = rows.filter(::isHeldRow)

    /**
     * The states that are *active* for a hold: everything a transfer can be doing except the states
     * a user controls by hand (PAUSED is only resumed by the user) and the terminal ones.
     */
    val HOLDABLE_STATES: List<DownloadState> = listOf(
        DownloadState.QUEUED,
        DownloadState.RETRYING,
        DownloadState.PREPARING,
        DownloadState.DOWNLOADING
    )

    /** What [recoverQueue] requeues after a process death: DOWNLOADING resumes from its `.part`. */
    val RESUMABLE_STATES: List<DownloadState> = listOf(
        DownloadState.QUEUED,
        DownloadState.RETRYING,
        DownloadState.PREPARING,
        DownloadState.DOWNLOADING
    )
}
