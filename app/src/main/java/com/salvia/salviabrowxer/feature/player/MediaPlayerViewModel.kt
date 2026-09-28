package com.salvia.salviabrowxer.feature.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salvia.salviabrowxer.data.repository.DownloadRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

data class PlayerUiState(
    /** The file is being stat'ed before the player is built. */
    val isChecking: Boolean = true,
    /** Nothing to play: the file was deleted behind the queue's back. */
    val fileMissing: Boolean = false,
    /** Media3 refused the file itself. */
    val playbackFailed: Boolean = false
)

/**
 * Backs [MediaPlayerScreen]. The player only opens on a file that is actually there, and a file
 * that vanished can be cleaned out of the queue from the player itself.
 */
@HiltViewModel
class MediaPlayerViewModel @Inject constructor(
    private val downloadRepository: DownloadRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    /** A remote URL is played directly; a local path must exist and be non-empty. */
    fun check(mediaUrl: String) {
        if (mediaUrl.isBlank() || mediaUrl.startsWith("http://") || mediaUrl.startsWith("https://") || mediaUrl.startsWith("content://")) {
            _uiState.value = PlayerUiState(isChecking = false)
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val file = File(mediaUrl)
            val usable = file.isFile && file.length() > 0L
            _uiState.value = PlayerUiState(isChecking = false, fileMissing = !usable)
        }
    }

    fun onPlaybackFailed() {
        _uiState.value = _uiState.value.copy(playbackFailed = true)
    }

    fun clearPlaybackFailure() {
        _uiState.value = _uiState.value.copy(playbackFailed = false)
    }

    /**
     * Removes the file and its queue entry, then reports back so the screen can return to the
     * queue. Safe when the file is already gone: the row still has to disappear.
     */
    fun deleteDownload(mediaUrl: String, onDeleted: () -> Unit) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val match = runCatching {
                    downloadRepository.getAllDownloads().first()
                        .firstOrNull { it.finalPath == mediaUrl || it.url == mediaUrl }
                }.getOrNull()
                match?.let { download ->
                    download.finalPath?.takeIf { it.isNotBlank() }?.let { path -> runCatching { File(path).delete() } }
                    runCatching { downloadRepository.deleteDownload(download.id) }
                }
            }
            onDeleted()
        }
    }
}
