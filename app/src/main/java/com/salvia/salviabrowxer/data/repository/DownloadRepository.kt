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
    suspend fun deleteDownload(id: String)
    suspend fun deleteDownloadsByState(state: DownloadState)
    suspend fun clearAllDownloads()
    suspend fun getDefaultDownloadDestination(): String
    suspend fun createDownloadEntity(
        url: String,
        filename: String,
        destination: String,
        mediaTitle: String? = null,
        thumbnail: String? = null,
        selectedQuality: String? = null
    ): DownloadEntity
}