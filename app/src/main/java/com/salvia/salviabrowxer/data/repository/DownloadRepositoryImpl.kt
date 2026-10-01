package com.salvia.salviabrowxer.data.repository

import com.salvia.salviabrowxer.core.database.dao.DownloadDao
import com.salvia.salviabrowxer.core.database.entities.DownloadEntity
import com.salvia.salviabrowxer.core.model.DownloadProgress
import com.salvia.salviabrowxer.core.model.DownloadState
import com.salvia.salviabrowxer.core.storage.StorageManager
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class DownloadRepositoryImpl @Inject constructor(
    private val downloadDao: DownloadDao,
    private val storageManager: StorageManager
) : DownloadRepository {

    override fun getAllDownloads(): Flow<List<DownloadEntity>> = downloadDao.getAll()
    override fun getDownloadsByState(state: DownloadState): Flow<List<DownloadEntity>> = downloadDao.getByStatus(state)
    override fun getDownloadsByStates(states: List<DownloadState>): Flow<List<DownloadEntity>> = downloadDao.getByStates(states)
    override suspend fun getDownloadById(id: String): DownloadEntity? = downloadDao.getById(id)
    override suspend fun addDownload(download: DownloadEntity) = downloadDao.insert(download)
    override suspend fun updateDownload(download: DownloadEntity) = downloadDao.update(download)

    override suspend fun updateDownloadState(id: String, state: DownloadState) {
        downloadDao.updateStatus(id, state, System.currentTimeMillis())
    }

    override suspend fun updateDownloadProgress(id: String, progress: DownloadProgress) {
        // The rate and the estimate come from the transfer that is actually running, so they are
        // persisted with the byte counters instead of being recomputed from sampling in the UI.
        downloadDao.updateProgressColumns(
            id = id,
            downloadedBytes = progress.downloadedBytes,
            totalBytes = progress.totalBytes,
            bytesPerSecond = progress.speed,
            etaSeconds = progress.eta,
            updatedAt = System.currentTimeMillis()
        )
    }

    override suspend fun updateDownloadResult(id: String, status: DownloadState, downloadedBytes: Long?, totalBytes: Long?, finalPath: String?, mimeType: String?, error: String?) {
        downloadDao.updateResult(
            id = id,
            status = status,
            downloadedBytes = downloadedBytes,
            totalBytes = totalBytes,
            finalPath = finalPath,
            mimeType = mimeType,
            error = error,
            updatedAt = System.currentTimeMillis()
        )
    }

    override suspend fun updateDownloadArtifacts(id: String, finalPath: String?, filename: String?, mimeType: String?, durationMs: Long?, thumbnail: String?, exportedUri: String?) {
        downloadDao.updateArtifacts(
            id = id,
            finalPath = finalPath,
            filename = filename,
            mimeType = mimeType,
            durationMs = durationMs,
            thumbnail = thumbnail,
            exportedUri = exportedUri,
            updatedAt = System.currentTimeMillis()
        )
    }

    override suspend fun deleteDownload(id: String) = downloadDao.delete(id)
    override suspend fun deleteDownloadsByState(state: DownloadState) = downloadDao.deleteByStatus(state)
    override suspend fun clearAllDownloads() = downloadDao.deleteAll()
    override suspend fun getDefaultDownloadDestination(): String = storageManager.getDefaultDownloadDirectory()
    override suspend fun createDownloadEntity(url: String, filename: String, destination: String, mediaTitle: String?, thumbnail: String?, selectedQuality: String?, mimeType: String?, totalBytes: Long?, renditionId: String?): DownloadEntity {
        return DownloadEntity(url = url, finalUrl = null, filename = filename, mimeType = mimeType, destination = destination, totalBytes = totalBytes, mediaTitle = mediaTitle, thumbnail = thumbnail, selectedQuality = selectedQuality, renditionId = renditionId, status = DownloadState.QUEUED)
    }
}
