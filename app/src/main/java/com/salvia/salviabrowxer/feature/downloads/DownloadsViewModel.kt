package com.salvia.salviabrowxer.feature.downloads

import android.content.Context
import android.media.MediaScannerConnection
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salvia.salviabrowxer.R
import com.salvia.salviabrowxer.core.database.entities.DownloadEntity
import com.salvia.salviabrowxer.core.model.DownloadState
import com.salvia.salviabrowxer.core.storage.MediaStoreExporter
import com.salvia.salviabrowxer.data.download.DownloadArtifactCleaner
import com.salvia.salviabrowxer.data.repository.DownloadRepository
import com.salvia.salviabrowxer.data.security.LockedFolderStore
import com.salvia.salviabrowxer.data.security.LockoutPolicy
import com.salvia.salviabrowxer.media.downloader.TranscodePlan
import com.salvia.salviabrowxer.media.downloader.VideoTranscoder
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
import kotlinx.coroutines.flow.first
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
    val lockedFolder: LockedFolderUiState = LockedFolderUiState(),
    /**
     * Rows being re-encoded right now, with the percentage each has reached.
     *
     * It lives in the UI state rather than inside the coroutine because a conversion is long: the row
     * has to show that it is working, and a hidden job would look like a button that did nothing.
     */
    val converting: Map<String, Int> = emptyMap()
) { val inFlightCount: Int get() = active.size + queued.size }

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val downloadRepository: DownloadRepository,
    private val mediaStoreExporter: MediaStoreExporter,
    private val lockedFolderStore: LockedFolderStore,
    private val artifactCleaner: DownloadArtifactCleaner,
    private val videoTranscoder: VideoTranscoder,
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

    /**
     * The locked folder's brake on guessing: three free attempts, then a delay that doubles.
     * Not injected — it holds no Android type and no test wants to share one across screens.
     */
    private val lockoutPolicy = LockoutPolicy()
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
        // The progressive delay runs before the verifier: a locked-out attempt must not pay the
        // PBKDF2 cost too, and must not even be checked — waiting is the whole point.
        val now = System.currentTimeMillis()
        if (lockoutPolicy.isLockedOut(now)) {
            val seconds = (lockoutPolicy.remainingMillis(now) + 999) / 1000
            _messages.trySend(context.getString(R.string.locked_folder_try_later, seconds))
            return
        }
        if (_uiState.value.lockedFolder.isUnlocking) return
        _uiState.update { it.copy(lockedFolder = it.lockedFolder.copy(isUnlocking = true)) }
        viewModelScope.launch(Dispatchers.Default) {
            val unlocked = lockedFolderStore.verify(password)
            if (unlocked) lockoutPolicy.onSuccess() else lockoutPolicy.onFailure(now)
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
    /**
     * Deletes the row and everything the app created for it: the file, the staged parts, the
     * thumbnail and the published gallery/SAF copy — see [DownloadArtifactCleaner] for what counts
     * as "ours". This used to delete only `finalPath` and one `.part` file, which left the gallery
     * copy and every staged segment of a segmented download behind.
     */
    fun deleteDownload(downloadId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            downloadRepository.getDownloadById(downloadId)?.let { row ->
                runCatching { artifactCleaner.clean(row) }
            }
            downloadRepository.deleteDownload(downloadId)
        }
    }
    /**
     * Re-encodes a finished download into an MP4 this device's gallery and any other player can open.
     *
     * This is the conversion a remux cannot do — HEVC or VP9 video re-encoded to H.264, a non-AAC
     * audio track to AAC — and it is deliberately a user action on one file: it costs minutes of CPU
     * and a generation of quality. The original file is never touched. The converted file lands next
     * to it as `name (converted).mp4` and becomes its own row, so the list says what happened instead
     * of a file silently changing under the user's finger.
     *
     * A container the platform has no demuxer for (AVI, WMV) is refused before any codec runs, with
     * the reason shown: "cannot convert" is a fact about the file, not a failure of the attempt.
     */
    fun convertDownload(downloadId: String) {
        if (_uiState.value.converting.containsKey(downloadId)) {
            _messages.trySend(context.getString(R.string.download_convert_already))
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val row = runCatching { downloadRepository.getDownloadById(downloadId) }.getOrNull() ?: return@launch
            val path = row.finalPath ?: row.temporaryPath
            if (path.isNullOrBlank()) {
                _messages.trySend(context.getString(R.string.download_not_ready))
                return@launch
            }
            if (path.startsWith("content://")) {
                // Re-encoding reads the file many times and writes next to it; a document in another
                // provider is not a place to do that, and pretending otherwise would half-convert.
                _messages.trySend(context.getString(R.string.download_convert_needs_local_file))
                return@launch
            }
            val source = File(path)
            if (!source.exists() || source.length() == 0L) {
                _messages.trySend(context.getString(R.string.download_file_missing))
                return@launch
            }
            when (TranscodePlan.decide(source.name)) {
                TranscodePlan.Decision.NOT_APPLICABLE -> {
                    _messages.trySend(context.getString(R.string.download_convert_not_applicable))
                    return@launch
                }
                TranscodePlan.Decision.NO_DEMUXER -> {
                    // Checked first, on purpose: this is a refusal the user can act on (keep the file,
                    // or play it elsewhere), not a failure that leaves them wondering.
                    _messages.trySend(context.getString(R.string.download_convert_no_demuxer))
                    return@launch
                }
                else -> Unit
            }

            val parent = source.parentFile ?: return@launch
            val output = File(parent, TranscodePlan.outputNameFor(source.name))
            setConverting(downloadId, 0)
            val converted = runCatching {
                videoTranscoder.transcode(
                    input = source,
                    output = output,
                    onProgress = { percent -> setConverting(downloadId, percent) },
                    shouldStop = { false }
                )
            }.getOrDefault(false)
            clearConverting(downloadId)

            if (!converted || !output.exists() || output.length() == 0L) {
                runCatching { if (output.exists()) output.delete() }
                _messages.trySend(context.getString(R.string.download_convert_failed))
                return@launch
            }
            val convertedRow = row.copy(
                id = java.util.UUID.randomUUID().toString(),
                filename = output.name,
                finalPath = output.absolutePath,
                temporaryPath = null,
                mimeType = if (TranscodePlan.decide(source.name) == TranscodePlan.Decision.AUDIO_TO_AAC_M4A) "audio/mp4" else "video/mp4",
                totalBytes = output.length(),
                downloadedBytes = output.length(),
                bytesPerSecond = 0L,
                etaSeconds = null,
                status = DownloadState.COMPLETED,
                error = null,
                renditionId = null,
                // A converted file is a new file: it has no gallery copy yet, and the export flow is
                // what publishes it — claiming an exported URI would be claiming something untrue.
                exportedUri = null,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                selectedQuality = "MP4"
            )
            runCatching { downloadRepository.addDownload(convertedRow) }
            runCatching { MediaScannerConnection.scanFile(context, arrayOf(output.absolutePath), null, null) }
            _messages.trySend(context.getString(R.string.download_convert_done, output.name))
        }
    }

    private fun setConverting(downloadId: String, percent: Int) {
        _uiState.update { it.copy(converting = it.converting + (downloadId to percent.coerceIn(0, 100))) }
    }

    private fun clearConverting(downloadId: String) {
        _uiState.update { it.copy(converting = it.converting - downloadId) }
    }

    fun openDownload(downloadId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val download = downloadRepository.getDownloadById(downloadId)
            val path = download?.finalPath
            if (download == null || path.isNullOrBlank()) { _messages.trySend(context.getString(R.string.download_not_ready)); return@launch }
            // A file in the folder the user chose is a document, not a path: it is handed to the
            // system as its own URI, with the read grant the picker gave us.
            if (path.startsWith("content://")) {
                val opened = runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW)
                            .setDataAndType(Uri.parse(path), download.mimeType ?: getMimeTypeFromExtension(download.filename.substringAfterLast('.', "")))
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
                if (opened.isFailure) _messages.trySend(context.getString(R.string.download_open_failed))
                return@launch
            }
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
            val inTree = path.startsWith("content://")
            val file = if (inTree) null else File(path)
            if (!inTree && (file == null || !file.exists() || file.length() == 0L)) { _messages.trySend(context.getString(R.string.download_file_missing)); return@launch }
            val shared = runCatching {
                val uri = if (inTree) Uri.parse(path) else FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file!!)
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = download.mimeType ?: getMimeTypeFromExtension(file?.extension ?: download.filename.substringAfterLast('.', ""))
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
        if (path.startsWith("content://")) {
            // The bytes live in the folder the user chose; publishing a gallery copy still works, and
            // the exporter reads them through the provider.
            val published = runCatching {
                mediaStoreExporter.export(
                    source = Uri.parse(path),
                    displayName = download.filename,
                    mimeType = download.mimeType,
                    isVideo = mediaStoreExporter.isVideoFile(download.filename, download.mimeType)
                )
            }.getOrNull()
            if (published == null) {
                _messages.trySend(context.getString(R.string.download_export_failed))
            } else {
                downloadRepository.updateDownloadArtifacts(
                    id = downloadId, finalPath = path, filename = download.filename,
                    mimeType = download.mimeType, durationMs = download.durationMs,
                    thumbnail = download.thumbnail, exportedUri = published
                )
                _messages.trySend(context.getString(R.string.download_exported))
            }
            return
        }
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

    fun clearCompletedDownloads() { clearDownloads(DownloadState.COMPLETED) }

    fun clearFailedDownloads() { clearDownloads(DownloadState.FAILED) }

    /**
     * Clears the list — finished rows only.
     *
     * The list tabs are "all", "completed" and "failed", and the clear action on the first tab used
     * to delete *every* row, including the ones a transfer is running against; the row is what the
     * service reads for its next step, so deleting it mid-transfer would abandon work the user never
     * cancelled. What "clear" removes is therefore every row that has nothing left to do, each with
     * its own files; a running download has its own cancel action.
     */
    fun clearAllDownloads() {
        viewModelScope.launch(Dispatchers.IO) {
            val finished = runCatching { downloadRepository.getAllDownloads().first() }
                .getOrNull().orEmpty()
                .filter { it.status == DownloadState.COMPLETED || it.status == DownloadState.FAILED || it.status == DownloadState.CANCELLED }
            finished.forEach { row -> runCatching { artifactCleaner.clean(row) } }
            finished.forEach { row -> runCatching { downloadRepository.deleteDownload(row.id) } }
        }
    }

    /**
     * The rows are read first and their files deleted before the rows themselves disappear: after a
     * delete there is no row left that knows which files belonged to it, which is exactly how files
     * used to be orphaned on disk by the clear actions.
     */
    private fun clearDownloads(state: DownloadState) {
        viewModelScope.launch(Dispatchers.IO) {
            val rows = runCatching { downloadRepository.getDownloadsByState(state).first() }.getOrNull().orEmpty()
            rows.forEach { row -> runCatching { artifactCleaner.clean(row) } }
            downloadRepository.deleteDownloadsByState(state)
        }
    }
    fun downloadProgressPercent(download: DownloadEntity): Float { val total = download.totalBytes ?: return 0f; return if (total > 0L) (download.downloadedBytes.toFloat() / total).coerceIn(0f, 1f) else 0f }
}
