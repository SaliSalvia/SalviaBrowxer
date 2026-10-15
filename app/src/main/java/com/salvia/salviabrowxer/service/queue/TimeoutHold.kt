package com.salvia.salviabrowxer.service.queue

import com.salvia.salviabrowxer.core.database.entities.DownloadEntity
import com.salvia.salviabrowxer.core.model.DownloadState

/** Stable, untranslated marker for a transfer paused by the Android 15+ dataSync time cap. */
const val DATA_SYNC_TIMEOUT_MARKER: String = "__salvia_data_sync_timeout__"

/**
 * Which rows the dataSync foreground-service timeout may release.
 *
 * Android 15 caps `dataSync` foreground services at six cumulative hours per 24-hour window for
 * apps targeting API 35+, after which [DownloadService.onTimeout] must stop the service within a
 * few seconds or the system stops it with a failure. The rows that were running are paused with
 * [DATA_SYNC_TIMEOUT_MARKER] so they can be told apart from a pause the user chose — exactly the
 * distinction [WifiHold] makes for the Wi-Fi-only setting, and for the same reason: a marker that
 * survives a language change and a process restart is the only thing that can be resumed safely.
 *
 * The release path is the app's next interaction (opening the app calls `processQueue`), not a
 * timer: the quota the system took away is what a timer would try to spend again.
 */
object TimeoutHold {

    fun isHeldRow(row: DownloadEntity): Boolean =
        row.status == DownloadState.PAUSED && row.error == DATA_SYNC_TIMEOUT_MARKER

    fun heldRows(rows: List<DownloadEntity>): List<DownloadEntity> = rows.filter(::isHeldRow)
}
