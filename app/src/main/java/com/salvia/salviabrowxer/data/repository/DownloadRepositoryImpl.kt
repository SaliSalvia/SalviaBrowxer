package com.salvia.salviabrowxer.data.repository

import com.salvia.salviabrowxer.core.database.dao.DownloadDao
import com.salvia.salviabrowxer.core.database.entities.DownloadEntity
import com.salvia.salviabrowxer.core.model.DownloadProgress
import com.salvia.salviabrowxer.core.model.DownloadState
import com.salvia.salviabrowxer.core.storage.StorageManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class DownloadRepositoryImpl @Inject constructor(
    private val downloadDao: DownloadDao,
    private val storageManager: StorageManager
) : DownloadRepository {

    override fun getAllDownloads(): Flow<List<DownloadEntity>> = downloadDao.getAll()

    override fun getDownloadsByState(state: DownloadState): Flow<List<DownloadEntity>> =
        downloadDao.getByStatus(state)

    override fun getDownloadsByStates(states: List<DownloadState>): Flow<List<DownloadEntity>> =
        downloadDao.getByStates(states)

    override suspend fun getDownloadById(id: String): DownloadEntity? = downloadDao.getById(id)

    override suspend fun addDownload(download: DownloadEntity) = downloadDao.insert(download)

    override suspend fun updateDownload(download: DownloadEntity) = downloadDao.update(download)

    override suspend fun updateDownloadState(id: String, state: DownloadState) {
        downloadDao.getById(id)?.let { download ->
            downloadDao.update(download.copy(status = state, updatedAt = System.currentTimeMillis()))
        }
    }

    override suspend fun updateDownloadProgress(
        id: String,
        progress: DownloadProgress
    ) {
        downloadDao.getById(id)?.let { download ->
            downloadDao.update(
                download.copy(
                    downloadedBytes = progress.downloadedBytes,
                    totalBytes = progress.totalBytes,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    override suspend fun deleteDownload(id: String) = downloadDao.delete(id)

    override suspend fun deleteDownloadsByState(state: DownloadState) =
        downloadDao.deleteByStatus(state)

    override suspend fun clearAllDownloads() = downloadDao.deleteAll()

    override suspend fun getDefaultDownloadDestination(): String =
        storageManager.getDefaultDownloadDirectory()

    override suspend fun createDownloadEntity(
        url: String,
        filename: String,
        destination: String,
        mediaTitle: String?,
        thumbnail: String?,
        selectedQuality: String?
    ): DownloadEntity {
        return DownloadEntity(
            url = url,
            finalUrl = null,
            filename = filename,
            destination = destination,
            mediaTitle = mediaTitle,
            thumbnail = thumbnail,
            selectedQuality = selectedQuality,
            status = DownloadState.QUEUED
        )
    }
}