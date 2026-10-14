package com.salvia.salviabrowxer.data.repository

import com.salvia.salviabrowxer.core.database.entities.DownloadEntity
import com.salvia.salviabrowxer.core.model.DownloadProgress
import com.salvia.salviabrowxer.core.model.DownloadState
import kotlinx.coroutines.flow.Flow

interface DownloadRepository {
    fun getAllDownloads(): Flow<List<DownloadEntity>>
    fun getDownloadsByState(state: DownloadState): Flow<List<DownloadEntity>>
    fun getDownloadsByStates(states: List<DownloadState>): Flow<List<DownloadEntity>>
    suspend fun getDownloadById(id: String): DownloadEntity?
    suspend fun addDownload(download: DownloadEntity)
    suspend fun updateDownload(download: DownloadEntity)
    suspend fun updateDownloadState(id: String, state: DownloadState)
    suspend fun updateDownloadProgress(id: String, progress: DownloadProgress)

    /**
     * Persists the outcome of a transfer. Only the values that are passed in are overwritten so
     * that callers can update just what changed (e.g. a failure reason).
     */
    suspend fun updateDownloadResult(
        id: String,
        status: DownloadState,
        downloadedBytes: Long? = null,
        totalBytes: Long? = null,
        finalPath: String? = null,
        mimeType: String? = null,
        error: String? = null
    )
    /**
     * Persists post-processing results that only exist once a transfer is done: the final file (which
     * may differ from the requested name after a remux or merge), its duration, its thumbnail and the
     * gallery URI it was exported to. Only the non-null values are written.
     */
    suspend fun updateDownloadArtifacts(
        id: String,
        finalPath: String? = null,
        filename: String? = null,
        mimeType: String? = null,
        durationMs: Long? = null,
        thumbnail: String? = null,
        exportedUri: String? = null
    )

    /** Moves a finished file into or out of the password-protected folder. */
    suspend fun setDownloadLocked(id: String, locked: Boolean)

    /** Forgets a published copy (the gallery copy of a file that has just been locked). */
    suspend fun clearExportedUri(id: String)

    /** How many rows are behind the password; the list draws a locked section from this. */
    fun lockedDownloadCount(): Flow<Int>

    /** Called when the password is removed: nothing may stay hidden behind a lock that no longer exists. */
    suspend fun unlockAllDownloads()

    suspend fun deleteDownload(id: String)
    suspend fun deleteDownloadsByState(state: DownloadState)
    suspend fun getDefaultDownloadDestination(): String
    suspend fun createDownloadEntity(
        url: String,
        filename: String,
        destination: String,
        mediaTitle: String? = null,
        thumbnail: String? = null,
        selectedQuality: String? = null,
        mimeType: String? = null,
        totalBytes: Long? = null,
        /** Set only for a DASH rendition; the manifest URL travels in `url`. */
        renditionId: String? = null,
        /** The page that exposed the media: sent as `Referer` on every request of the transfer. */
        pageUrl: String? = null,
        /** The user agent that page was loaded with, so the server sees the same client twice. */
        userAgent: String? = null
    ): DownloadEntity
}