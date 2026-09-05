package com.salvia.salviabrowxer.feature.downloads

import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salvia.salviabrowxer.core.database.entities.DownloadEntity
import com.salvia.salviabrowxer.core.model.DownloadState
import com.salvia.salviabrowxer.data.repository.DownloadRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val downloadRepository: DownloadRepository
) : ViewModel() {

    private val _downloads = mutableStateListOf<DownloadEntity>()
    val downloads: List<DownloadEntity> = _downloads

    private val _activeDownloads = mutableStateListOf<DownloadEntity>()
    val activeDownloads: List<DownloadEntity> = _activeDownloads

    private val _queuedDownloads = mutableStateListOf<DownloadEntity>()
    val queuedDownloads: List<DownloadEntity> = _queuedDownloads

    private val _completedDownloads = mutableStateListOf<DownloadEntity>()
    val completedDownloads: List<DownloadEntity> = _completedDownloads

    private val _failedDownloads = mutableStateListOf<DownloadEntity>()
    val failedDownloads: List<DownloadEntity> = _failedDownloads

    init {
        observeDownloads()
    }

    private fun observeDownloads() {
        viewModelScope.launch(Dispatchers.IO) {
            downloadRepository.getAllDownloads().collectLatest { allDownloads ->
                _downloads.clear()
                _downloads.addAll(allDownloads)

                _activeDownloads.clear()
                _queuedDownloads.clear()
                _completedDownloads.clear()
                _failedDownloads.clear()

                allDownloads.forEach { download ->
                    when (download.status) {
                        DownloadState.DOWNLOADING,
                        DownloadState.RESOLVING,
                        DownloadState.PREPARING,
                        DownloadState.PROCESSING -> _activeDownloads.add(download)
                        DownloadState.QUEUED,
                        DownloadState.RETRYING -> _queuedDownloads.add(download)
                        DownloadState.COMPLETED -> _completedDownloads.add(download)
                        DownloadState.FAILED,
                        DownloadState.CANCELLED -> _failedDownloads.add(download)
                    }
                }
            }
        }
    }

    fun retryDownload(downloadId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            downloadRepository.updateDownloadState(downloadId, DownloadState.QUEUED)
        }
    }

    fun pauseDownload(downloadId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            downloadRepository.updateDownloadState(downloadId, DownloadState.PAUSED)
        }
    }

    fun resumeDownload(downloadId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            downloadRepository.updateDownloadState(downloadId, DownloadState.QUEUED)
        }
    }

    fun cancelDownload(downloadId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            downloadRepository.updateDownloadState(downloadId, DownloadState.CANCELLED)
        }
    }

    fun deleteDownload(downloadId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            downloadRepository.deleteDownload(downloadId)
        }
    }

    fun clearCompletedDownloads() {
        viewModelScope.launch(Dispatchers.IO) {
            downloadRepository.deleteDownloadsByState(DownloadState.COMPLETED)
        }
    }

    fun clearFailedDownloads() {
        viewModelScope.launch(Dispatchers.IO) {
            downloadRepository.deleteDownloadsByState(DownloadState.FAILED)
        }
    }

    fun clearAllDownloads() {
        viewModelScope.launch(Dispatchers.IO) {
            downloadRepository.clearAllDownloads()
        }
    }
}