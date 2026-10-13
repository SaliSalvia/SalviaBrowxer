package com.salvia.salviabrowxer.feature.downloads

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salvia.salviabrowxer.R
import com.salvia.salviabrowxer.core.database.entities.DownloadEntity
import com.salvia.salviabrowxer.core.model.DownloadState
import com.salvia.salviabrowxer.core.storage.MediaStoreExporter
import com.salvia.salviabrowxer.data.repository.DownloadRepository
import com.salvia.salviabrowxer.data.security.LockedFolderStore
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/**
 * The password-protected folder as the list sees it.
 *
 * [isUnlocked] is a session, not a setting: closing the app locks the folder again, which is the whole
 * point of a lock. [isSecured] is the truth about where the verifier lives, so the screen can say
 * "protected by the device keystore" only when that is actually the case.
 */
data class LockedFolderUiState(
    val isConfigured: Boolean = false,
    val isUnlocked: Boolean = false,
    val isUnlocking: Boolean = false,
    val lockedCount: Int = 0,
    val isSecured: Boolean = false
)

data class DownloadsUiState(
    val isLoading: Boolean = true,
    val all: List<DownloadEntity> = emptyList(),
    val active: List<DownloadEntity> = emptyList(),
    val queued: List<DownloadEntity> = emptyList(),
    val completed: List<DownloadEntity> = emptyList(),
    val failed: List<DownloadEntity> = emptyList(),
    val lockedFolder: LockedFolderUiState = LockedFolderUiState()
) { val inFlightCount: Int get() = active.size + queued.size }

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val downloadRepository: DownloadRepository,
    private val mediaStoreExporter: MediaStoreExporter,
    private val lockedFolderStore: LockedFolderStore,
    @ApplicationContext private val context: Context
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        DownloadsUiState(
            lockedFolder = LockedFolderUiState(
                isConfigured = lockedFolderStore.isConfigured(),
                isSecured = lockedFolderStore.isSecured
            )
        )
    )
    val uiState: StateFlow<DownloadsUiState> = _uiState.asStateFlow()
    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    private val _playRequest = Channel<Pair<String, String>>(Channel.BUFFERED)
    val playRequest: Channel<Pair<String, String>> = _playRequest

    /** A runtime permission the screen must request before an export can finish (API <= 28). */
    private val _exportPermissionRequest = Channel<String>(Channel.BUFFERED)
    val exportPermissionRequest: Flow<String> = _exportPermissionRequest.receiveAsFlow()
    private var pendingExportDownloadId: String? = null

    init {
        viewModelScope.launch(Dispatchers.IO) {
            downloadRepository.getAllDownloads().distinctUntilChanged().collectLatest { downloads ->
                _uiState.update { state ->
                    // A locked row is not drawn until the password has been entered — the list is where
                    // "hidden" has to mean something. The underlying rows are all still in `all`, so the
                    // count is honest even while the files themselves are not on screen.
                    val unlocked = state.lockedFolder.isUnlocked
                    val visible = if (unlocked) downloads else downloads.filter { !it.isLocked }
                    state.copy(
                        isLoading = false, all = downloads,
                        active = visible.filter { it.status == DownloadState.DOWNLOADING || it.status == DownloadState.RESOLVING || it.status == DownloadState.PREPARING || it.status == DownloadState.PROCESSING || it.status == DownloadState.PAUSED },
                        queued = visible.filter { it.status == DownloadState.QUEUED || it.status == DownloadState.RETRYING },
                        completed = visible.filter { it.status == DownloadState.COMPLETED },
                        failed = visible.filter { it.status == DownloadState.FAILED || it.status == DownloadState.CANCELLED },
                        lockedFolder = state.lockedFolder.copy(lockedCount = downloads.count { it.isLocked })
                    )
                }
            }
        }
        DownloadService.processQueue(context)
    }

    /**
     * Checks the password. Off the main thread on purpose: the whole value of a PBKDF2 verifier is
     * that checking it is slow, and slow on the UI thread is a frozen list.
     */
    fun unlockFolder(password: String) {
        if (_uiState.value.lockedFolder.isUnlocking) return
        _uiState.update { it.copy(lockedFolder = it.lockedFolder.copy(isUnlocking = true)) }
        viewModelScope.launch(Dispatchers.Default) {
            val unlocked = lockedFolderStore.verify(password)
            _uiState.update { it.copy(lockedFolder = it.lockedFolder.copy(isUnlocking = false, isUnlocked = unlocked)) }
            if (!unlocked) _messages.trySend(context.getString(R.string.locked_folder_wrong_password))
        }
    }

    /** Locks the folder again without closing the app. */
    fun lockFolder() {
        _uiState.update { it.copy(lockedFolder = it.lockedFolder.copy(isUnlocked = false)) }
    }

    /**
     * Moves a finished file into or out of the locked folder.
     *
     * Locking also removes the copy that was published to the shared gallery and forgets it in the row:
     * a lock that leaves the file in the device's photo library is not a lock. A file that leaves the
     * folder simply becomes visible again — nothing is copied or deleted.
     */
    fun setDownloadLocked(downloadId: String, locked: Boolean) {
        if (locked && !_uiState.value.lockedFolder.isConfigured) {
            _messages.trySend(context.getString(R.string.locked_folder_not_configured))
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            downloadRepository.setDownloadLocked(downloadId, locked)
            if (locked) withdrawPublishedCopy(downloadId)
            _messages.trySend(context.getString(if (locked) R.string.download_locked else R.string.download_unlocked))
        }
    }

    /** Deletes the gallery copy of a file that has just been locked, if it had one. */
    private suspend fun withdrawPublishedCopy(downloadId: String) {
        val download = downloadRepository.getDownloadById(downloadId) ?: return
        val uri = download.exportedUri?.takeIf { it.startsWith("content://") } ?: return
        // Only the app's own MediaStore entry: a tree the user chose is the user's folder, not ours to
        // reach into, and FilesProvider refuses a delete it did not create anyway.
        runCatching { context.contentResolver.delete(Uri.parse(uri), null, null) }
        downloadRepository.clearExportedUri(downloadId)
    }

    fun retryDownload(downloadId: String) { viewModelScope.launch(Dispatchers.IO) { downloadRepository.updateDownloadState(downloadId, DownloadState.QUEUED); DownloadService.enqueueDownload(context, downloadId) } }
    fun pauseDownload(downloadId: String) { viewModelScope.launch(Dispatchers.IO) { downloadRepository.updateDownloadState(downloadId, DownloadState.PAUSED); DownloadService.pauseDownload(context, downloadId) } }
    fun resumeDownload(downloadId: String) { viewModelScope.launch(Dispatchers.IO) { downloadRepository.updateDownloadState(downloadId, DownloadState.QUEUED); DownloadService.enqueueDownload(context, downloadId) } }
    fun cancelDownload(downloadId: String) { viewModelScope.launch(Dispatchers.IO) { downloadRepository.updateDownloadState(downloadId, DownloadState.CANCELLED); DownloadService.cancelDownload(context, downloadId) } }
    fun deleteDownload(downloadId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            downloadRepository.getDownloadById(downloadId)?.let { d -> d.finalPath?.takeIf { it.isNotBlank() }?.let { runCatching { File(it).delete() } }; runCatching { File(d.destination, "${d.filename}.part").delete() } }
            downloadRepository.deleteDownload(downloadId)
        }
    }
    fun openDownload(downloadId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val download = downloadRepository.getDownloadById(downloadId)
            val path = download?.finalPath
            if (download == null || path.isNullOrBlank()) { _messages.trySend(context.getString(R.string.download_not_ready)); return@launch }
            val file = File(path)
            if (!file.exists()) { _messages.trySend(context.getString(R.string.download_file_missing)); return@launch }
            val launched = runCatching {
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                val intent = Intent(Intent.ACTION_VIEW).apply { setDataAndType(uri, download.mimeType ?: getMimeTypeFromExtension(file.extension)); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                context.startActivity(intent)
            }
            if (launched.isFailure) _messages.trySend(context.getString(R.string.download_open_failed))
        }
    }

    /**
     * Shares a finished file through FileProvider. A missing file says so instead of opening a
     * chooser that would hand the other app a dead uri.
     */
    fun shareDownload(downloadId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val download = downloadRepository.getDownloadById(downloadId)
            val path = download?.finalPath
            if (download == null || path.isNullOrBlank()) { _messages.trySend(context.getString(R.string.download_not_ready)); return@launch }
            val file = File(path)
            if (!file.exists() || file.length() == 0L) { _messages.trySend(context.getString(R.string.download_file_missing)); return@launch }
            val shared = runCatching {
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = download.mimeType ?: getMimeTypeFromExtension(file.extension)
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, download.mediaTitle ?: download.filename)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(
                    Intent.createChooser(send, context.getString(R.string.download_share)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
            if (shared.isFailure) _messages.trySend(context.getString(R.string.download_share_failed))
        }
    }

    /** Opens a completed file with the in-app SalviaBrowxer player. */
    fun playInApp(downloadId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val download = downloadRepository.getDownloadById(downloadId)
            val path = download?.finalPath
            if (download == null || path.isNullOrBlank()) { _messages.trySend(context.getString(R.string.download_not_ready)); return@launch }
            // A missing file still opens the player: it explains the problem there and offers to
            // clear the dead row, which a snackbar on a list screen cannot do.
            playRequest.trySend(path to (download.mediaTitle?.ifBlank { null } ?: download.filename))
        }
    }
    /**
     * Publishes a finished file to the shared media store on the user's explicit request.
     *
     * The automatic post-download export already covers most devices, so this exists for the two
     * cases it cannot: the export setting was off, or an export was skipped because the older
     * Android release needs a storage permission the app had not been granted.
     */
    fun exportDownload(downloadId: String) {
        viewModelScope.launch(Dispatchers.IO) { runExport(downloadId) }
    }

    private suspend fun runExport(downloadId: String, permissionAlreadyHandled: Boolean = false) {
        val download = downloadRepository.getDownloadById(downloadId)
        val path = download?.finalPath
        if (download == null || path.isNullOrBlank()) { _messages.trySend(context.getString(R.string.download_not_ready)); return }
        val file = File(path)
        if (!file.exists() || file.length() == 0L) { _messages.trySend(context.getString(R.string.download_file_missing)); return }
        // After a grant the export proceeds even if the platform still reports the permission as
        // missing (a race, or a device that ignores the manifest entry): the export then fails with
        // a message instead of asking the user a second time.
        val permission = if (permissionAlreadyHandled) null else mediaStoreExporter.requiredPermission()
        if (permission != null) {
            // Ask, then come back through onExportPermissionResult with the same download id.
            pendingExportDownloadId = downloadId
            _exportPermissionRequest.trySend(permission)
            return
        }
        publish(downloadId, file, download.mimeType)
    }

    /** Called when the storage-permission dialog closes; retries the export the user asked for. */
    fun onExportPermissionResult(granted: Boolean) {
        val downloadId = pendingExportDownloadId
        pendingExportDownloadId = null
        if (!granted) { _messages.trySend(context.getString(R.string.error_permission_required)); return }
        if (downloadId.isNullOrBlank()) return
        viewModelScope.launch(Dispatchers.IO) { runExport(downloadId, permissionAlreadyHandled = true) }
    }

    private suspend fun publish(downloadId: String, file: File, mimeType: String?) {
        val exported = mediaStoreExporter.export(file, file.name, mimeType, mediaStoreExporter.isVideoFile(file.name, mimeType))
        if (exported == null) { _messages.trySend(context.getString(R.string.download_export_failed)); return }
        downloadRepository.updateDownloadArtifacts(id = downloadId, exportedUri = exported)
        _messages.trySend(context.getString(R.string.download_exported))
    }

    fun clearCompletedDownloads() { viewModelScope.launch(Dispatchers.IO) { downloadRepository.deleteDownloadsByState(DownloadState.COMPLETED) } }
    fun clearFailedDownloads() { viewModelScope.launch(Dispatchers.IO) { downloadRepository.deleteDownloadsByState(DownloadState.FAILED) } }
    fun clearAllDownloads() { viewModelScope.launch(Dispatchers.IO) { downloadRepository.clearAllDownloads() } }
    fun downloadProgressPercent(download: DownloadEntity): Float { val total = download.totalBytes ?: return 0f; return if (total > 0L) (download.downloadedBytes.toFloat() / total).coerceIn(0f, 1f) else 0f }
}
