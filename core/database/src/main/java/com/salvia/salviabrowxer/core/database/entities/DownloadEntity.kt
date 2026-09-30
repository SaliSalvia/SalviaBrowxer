package com.salvia.salviabrowxer.core.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.salvia.salviabrowxer.core.model.DownloadState
import java.util.UUID

@Entity(
    tableName = "downloads",
    indices = [
        Index(value = ["status"]),
        Index(value = ["createdAt"])
    ]
)
data class DownloadEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val url: String,
    val finalUrl: String? = null,
    val filename: String,
    val mimeType: String? = null,
    val destination: String,
    val totalBytes: Long? = null,
    val downloadedBytes: Long = 0,
    /**
     * The rate the transfer measured itself, and the seconds it estimates are left.
     *
     * The downloader has always reported both (`DownloadSnapshot`), but only the byte counters were
     * persisted, so the task list could show progress and nothing about how long it would take.
     * Both are zero/null whenever nothing is moving, so a paused or finished row never shows a
     * stale speed.
     */
    @ColumnInfo(defaultValue = "0") val bytesPerSecond: Long = 0,
    val etaSeconds: Long? = null,
    val status: DownloadState = DownloadState.QUEUED,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val error: String? = null,
    val mediaTitle: String? = null,
    val thumbnail: String? = null,
    val selectedQuality: String? = null,
    val temporaryPath: String? = null,
    val finalPath: String? = null,
    /**
     * Encodes which DASH rendition(s) a queued transfer wants (see `DashRenditionId`). Null for
     * every direct and HLS download; the manifest itself is re-fetched from `url` at transfer time.
     */
    val renditionId: String? = null,
    /** Content URI in the shared media store once the file has been published to the gallery. */
    val exportedUri: String? = null,
    /** Duration read from the finished file, for the library rows. */
    val durationMs: Long? = null
)
