package com.salvia.salviabrowxer.feature.downloads

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salvia.salviabrowxer.core.database.entities.DownloadEntity
import com.salvia.salviabrowxer.core.model.DownloadState
import com.salvia.salviabrowxer.data.repository.DownloadRepository
import com.salvia.salviabrowxer.service.DownloadService
import com.salvia.salviabrowxer.ui.utils.getMimeTypeFromExtension
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class DownloadsUiState(
    val isLoading: Boolean = true,
    val all: List<DownloadEntity> = emptyList(),
    val active: List<DownloadEntity> = emptyList(),
    val queued: List<DownloadEntity> = emptyList(),
    val completed: List<DownloadEntity> = emptyList(),
    val failed: List<DownloadEntity> = emptyList()
) {
    val inFlightCount: Int get() = active.size + queued.size
}

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val downloadRepository: DownloadRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(DownloadsUiState())
    val uiState: StateFlow<DownloadsUiState> = _uiState.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            downloadRepository.getAllDownloads().collectLatest { downloads ->
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        all = downloads,
                        active = downloads.filter {
                            it.status == DownloadState.DOWNLOADING ||
                                it.status == DownloadState.RESOLVING ||
                                it.status == DownloadState.PREPARING ||
                                it.status == DownloadState.PROCESSING ||
                                it.status == DownloadState.PAUSED
                        },
                        queued = downloads.filter {
                            it.status == DownloadState.QUEUED || it.status == DownloadState.RETRYING
                        },
                        completed = downloads.filter { it.status == DownloadState.COMPLETED },
                        failed = downloads.filter {
                            it.status == DownloadState.FAILED || it.status == DownloadState.CANCELLED
                        }
                    )
                }
            }
        }
        // Pick up anything the service may have left behind (process death, app restart, ...).
        DownloadService.processQueue(context)
    }

    fun retryDownload(downloadId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            downloadRepository.updateDownloadState(downloadId, DownloadState.QUEUED)
            DownloadService.enqueueDownload(context, downloadId)
        }
    }

    fun pauseDownload(downloadId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            downloadRepository.updateDownloadState(downloadId, DownloadState.PAUSED)
            DownloadService.pauseDownload(context, downloadId)
        }
    }

    fun resumeDownload(downloadId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            downloadRepository.updateDownloadState(downloadId, DownloadState.QUEUED)
            DownloadService.enqueueDownload(context, downloadId)
        }
    }

    fun cancelDownload(downloadId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            downloadRepository.updateDownloadState(downloadId, DownloadState.CANCELLED)
            DownloadService.cancelDownload(context, downloadId)
        }
    }

    fun deleteDownload(downloadId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            downloadRepository.getDownloadById(downloadId)?.let { download ->
                download.finalPath?.takeIf { it.isNotBlank() }?.let { path ->
                    runCatching { File(path).delete() }
                }
                runCatching { File(download.destination, "${download.filename}.part").delete() }
            }
            downloadRepository.deleteDownload(downloadId)
        }
    }

    /** Opens a finished download with the system viewer through a content:// URI. */
    fun openDownload(downloadId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val download = downloadRepository.getDownloadById(downloadId)
            val path = download?.finalPath
            if (download == null || path.isNullOrBlank()) {
                _messages.trySend("File is not ready yet")
                return@launch
            }
            val file = File(path)
            if (!file.exists()) {
                _messages.trySend("File is missing on disk")
                return@launch
            }
            val launched = runCatching {
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, download.mimeType ?: getMimeTypeFromExtension(file.extension))
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }
            if (launched.isFailure) {
                _messages.trySend("No app can open this file")
            }
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

    fun downloadProgressPercent(download: DownloadEntity): Float {
        val total = download.totalBytes ?: return 0f
        return if (total > 0L) {
            (download.downloadedBytes.toFloat() / total).coerceIn(0f, 1f)
        } else {
            0f
        }
    }
}
