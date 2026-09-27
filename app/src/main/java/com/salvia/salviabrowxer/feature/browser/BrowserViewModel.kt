package com.salvia.salviabrowxer.feature.browser

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.media.MediaScannerConnection
import com.salvia.salviabrowxer.R
import com.salvia.salviabrowxer.core.database.entities.BookmarkEntity
import com.salvia.salviabrowxer.core.database.entities.DownloadEntity
import com.salvia.salviabrowxer.core.database.entities.HistoryEntity
import com.salvia.salviabrowxer.core.model.DownloadState
import com.salvia.salviabrowxer.core.model.MediaCandidate
import com.salvia.salviabrowxer.core.model.MediaFormat
import com.salvia.salviabrowxer.core.model.MediaInfo
import com.salvia.salviabrowxer.core.model.Tab
import com.salvia.salviabrowxer.data.datastore.SettingsDataStore
import com.salvia.salviabrowxer.data.repository.BookmarkRepository
import com.salvia.salviabrowxer.data.repository.DownloadRepository
import com.salvia.salviabrowxer.data.repository.HistoryRepository
import com.salvia.salviabrowxer.media.detector.MediaDetector
import com.salvia.salviabrowxer.media.resolver.MediaResolver
import com.salvia.salviabrowxer.service.DownloadService
import com.salvia.salviabrowxer.ui.utils.AddressBarResolver
import com.salvia.salviabrowxer.ui.utils.CleartextPolicy
import com.salvia.salviabrowxer.ui.utils.Constants
import com.salvia.salviabrowxer.ui.utils.sanitizeFilename
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

sealed interface BrowserCommand {
    data class Load(val url: String) : BrowserCommand
    data object Back : BrowserCommand
    data object Forward : BrowserCommand
    data object Reload : BrowserCommand
    data object Stop : BrowserCommand
    data class FetchBlob(val blobUrl: String, val pageUrl: String) : BrowserCommand
}

data class FabPosition(val x: Float = 0f, val y: Float = 0f)

data class BrowserUiState(
    val url: String = "",
    val addressBarInput: String = "",
    val title: String = "",
    val isLoading: Boolean = false,
    val progress: Int = 0,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val isSecure: Boolean = false,
    val tabs: List<Tab> = emptyList(),
    val currentTabId: String? = null,
    val isPrivateMode: Boolean = false,
    val homepage: String = Constants.DEFAULT_HOMEPAGE,
    val searchEngine: String = Constants.DEFAULT_SEARCH_ENGINE,
    val isJavaScriptEnabled: Boolean = true,
    val areCookiesEnabled: Boolean = true,
    val isDesktopSite: Boolean = false,
    val isCleartextAllowed: Boolean = false,
    /** Set when http:// navigation was refused, so the chrome can explain it and offer to allow. */
    val blockedCleartextUrl: String? = null,
    val activeDownloadCount: Int = 0,
    val detectedMedia: List<MediaCandidate> = emptyList(),
    val fabPosition: FabPosition = FabPosition(),
    val floatingButtonSize: Int = 56,
    val isFabAlwaysVisible: Boolean = true,
    val qualitySheet: QualitySheetState? = null,
    /** Tabs whose WebView was destroyed to stay under the live-tab cap; they reload on select. */
    val hibernatedTabIds: Set<String> = emptySet(),
    val isTabSwitcherVisible: Boolean = false,
    val findInPage: FindInPageState? = null
) {
    val isMediaDetected: Boolean get() = detectedMedia.isNotEmpty()
    val currentTab: Tab? get() = tabs.firstOrNull { it.id == currentTabId }
}

data class FindInPageState(
    val query: String = "",
    val matches: Int = 0,
    val activeMatch: Int = 0
)

data class QualitySheetState(
    val candidate: MediaCandidate,
    val mediaInfo: MediaInfo,
    val isResolving: Boolean = false
)

@HiltViewModel
class BrowserViewModel @Inject constructor(
    private val historyRepository: HistoryRepository,
    private val bookmarkRepository: BookmarkRepository,
    private val downloadRepository: DownloadRepository,
    private val settingsDataStore: SettingsDataStore,
    private val mediaDetector: MediaDetector,
    private val mediaResolver: MediaResolver,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(BrowserUiState())
    val uiState: StateFlow<BrowserUiState> = _uiState.asStateFlow()

    private val _commands = Channel<BrowserCommand>(Channel.BUFFERED)
    val commands: Flow<BrowserCommand> = _commands.receiveAsFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    private var detectJob: Job? = null
    private var fabSaveJob: Job? = null

    init {
        openTab(url = Constants.DEFAULT_HOMEPAGE, isPrivate = false, navigate = false)
        _uiState.update { it.copy(url = Constants.DEFAULT_HOMEPAGE, addressBarInput = Constants.DEFAULT_HOMEPAGE) }
        // The stored homepage may differ from the placeholder. Only follow it while the user has
        // not navigated yet, so a slow settings read can never clobber a page the user opened.
        val startupUrl = Constants.DEFAULT_HOMEPAGE
        viewModelScope.launch(Dispatchers.IO) {
            val homepage = runCatching { settingsDataStore.homepage.first() }.getOrNull()?.takeIf { it.isNotBlank() } ?: Constants.DEFAULT_HOMEPAGE
            val engine = runCatching { settingsDataStore.searchEngine.first() }.getOrNull() ?: Constants.DEFAULT_SEARCH_ENGINE
            val jsEnabled = runCatching { settingsDataStore.isJavaScriptEnabled.first() }.getOrNull() ?: true
            val cookiesEnabled = runCatching { settingsDataStore.areCookiesEnabled.first() }.getOrNull() ?: true
            val desktop = runCatching { settingsDataStore.isDesktopSite.first() }.getOrNull() ?: false
            val fabX = runCatching { settingsDataStore.floatingButtonX.first() }.getOrNull() ?: 0f
            val fabY = runCatching { settingsDataStore.floatingButtonY.first() }.getOrNull() ?: 0f
            val fabSize = runCatching { settingsDataStore.floatingButtonSize.first() }.getOrNull() ?: 56
            val fabAlways = runCatching { settingsDataStore.isFloatingButtonAlwaysVisible.first() }.getOrNull() ?: true
            val cleartextAllowed = runCatching { settingsDataStore.isCleartextAllowed.first() }.getOrNull() ?: false
            _uiState.update {
                it.copy(
                    homepage = homepage, searchEngine = engine, isJavaScriptEnabled = jsEnabled,
                    areCookiesEnabled = cookiesEnabled, isDesktopSite = desktop, fabPosition = FabPosition(fabX, fabY), floatingButtonSize = fabSize.coerceIn(40, 72),
                    isFabAlwaysVisible = fabAlways, isCleartextAllowed = cleartextAllowed
                )
            }
            if (homepage != startupUrl && _uiState.value.url == startupUrl) navigate(homepage)
        }
        observeSettingsLive()
        observeDownloadQueue()
    }

    private fun observeSettingsLive() {
        viewModelScope.launch(Dispatchers.IO) {
            settingsDataStore.floatingButtonSize.collectLatest { size -> _uiState.update { it.copy(floatingButtonSize = size.coerceIn(40, 72)) } }
        }
        viewModelScope.launch(Dispatchers.IO) {
            settingsDataStore.isFloatingButtonAlwaysVisible.collectLatest { enabled ->
                _uiState.update { it.copy(isFabAlwaysVisible = enabled) }
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            settingsDataStore.isCleartextAllowed.collectLatest { allowed ->
                _uiState.update { it.copy(isCleartextAllowed = allowed) }
            }
        }
    }

    private fun observeDownloadQueue() {
        viewModelScope.launch(Dispatchers.IO) {
            // conflate() keeps only the latest count when DB emits faster than the UI consumes,
            // and distinctUntilChanged() prevents redundant recompositions on identical counts.
            downloadRepository.getDownloadsByStates(
                listOf(DownloadState.QUEUED, DownloadState.RESOLVING, DownloadState.PREPARING, DownloadState.DOWNLOADING, DownloadState.RETRYING, DownloadState.PROCESSING)
            )
                .map { pending -> pending.size }
                .distinctUntilChanged()
                .conflate()
                .collectLatest { count -> _uiState.update { it.copy(activeDownloadCount = count) } }
        }
    }

    // region tabs
    fun createNewTab(url: String = "", isPrivate: Boolean = false) { openTab(url, isPrivate, navigate = url.isNotEmpty()) }

    private fun openTab(url: String, isPrivate: Boolean, navigate: Boolean) {
        val tab = Tab(title = if (url.isEmpty()) "New Tab" else url, url = url, isPrivate = isPrivate || _uiState.value.isPrivateMode)
        _uiState.update { state -> state.copy(tabs = state.tabs + tab.copy(position = state.tabs.size), currentTabId = tab.id, url = url.ifEmpty { state.url }, addressBarInput = url.ifEmpty { state.addressBarInput }, detectedMedia = emptyList()) }
        if (navigate && url.isNotEmpty()) _commands.trySend(BrowserCommand.Load(url))
    }

    fun switchTab(tabId: String) {
        val tab = _uiState.value.tabs.firstOrNull { it.id == tabId } ?: return
        _uiState.update { state ->
            state.copy(
                currentTabId = tab.id, url = tab.url, addressBarInput = tab.url, title = tab.title,
                isPrivateMode = tab.isPrivate, detectedMedia = emptyList(),
                isTabSwitcherVisible = false,
                // A hibernated tab restores the last committed URL; the store reloads it.
                canGoBack = false, canGoForward = false
            )
        }
        if (tab.url.isNotEmpty()) _commands.trySend(BrowserCommand.Load(tab.url))
    }

    fun closeTab(tabId: String) {
        _uiState.update { state ->
            val index = state.tabs.indexOfFirst { it.id == tabId }
            if (index == -1) return@update state
            val remaining = state.tabs.toMutableList().also { it.removeAt(index) }.mapIndexed { position, tab -> tab.copy(position = position) }
            val current = if (state.currentTabId != tabId) state.currentTabId else remaining.getOrNull(minOf(index, remaining.size - 1))?.id
            val currentTab = remaining.firstOrNull { it.id == current }
            state.copy(
                tabs = remaining, currentTabId = current, url = currentTab?.url ?: "",
                addressBarInput = currentTab?.url ?: "", title = currentTab?.title ?: "",
                hibernatedTabIds = state.hibernatedTabIds - tabId
            )
        }
        // Closing the last tab must not leave a dead browser behind.
        if (_uiState.value.tabs.isEmpty()) createNewTab()
    }

    fun openTabSwitcher() { _uiState.update { it.copy(isTabSwitcherVisible = true) } }

    fun closeTabSwitcher() { _uiState.update { it.copy(isTabSwitcherVisible = false) } }

    /** The store hibernated [tabId]: keep its last committed URL and title so it can be restored. */
    fun onTabHibernated(tabId: String, url: String, title: String) {
        _uiState.update { state ->
            state.copy(
                hibernatedTabIds = state.hibernatedTabIds + tabId,
                tabs = state.tabs.map { tab ->
                    if (tab.id != tabId) tab
                    else tab.copy(url = url.ifBlank { tab.url }, title = title.ifBlank { tab.title })
                }
            )
        }
    }

    fun onTabActivated(tabId: String) {
        if (tabId !in _uiState.value.hibernatedTabIds) return
        _uiState.update { it.copy(hibernatedTabIds = it.hibernatedTabIds - tabId) }
    }
    // endregion

    // region navigation
    fun loadFromAddressBar(input: String = _uiState.value.addressBarInput) {
        val target = resolveTargetUrl(input) ?: return
        _uiState.update { it.copy(addressBarInput = target) }
        navigate(target)
    }

    fun onAddressInputChange(input: String) { _uiState.update { it.copy(addressBarInput = input) } }

    fun navigate(url: String) {
        val target = normalizeUrl(url) ?: return
        if (CleartextPolicy.isBlocked(target, _uiState.value.isCleartextAllowed)) {
            _uiState.update { it.copy(blockedCleartextUrl = target, addressBarInput = target, isLoading = false, progress = 0) }
            _messages.trySend(context.getString(R.string.error_cleartext_blocked))
            return
        }
        _uiState.update { state -> state.copy(url = target, addressBarInput = target, isLoading = true, progress = 0, isSecure = target.startsWith("https://"), detectedMedia = emptyList(), tabs = state.tabs.map { tab -> if (tab.id == state.currentTabId) tab.copy(url = target, lastVisited = System.currentTimeMillis()) else tab }) }
        _commands.trySend(BrowserCommand.Load(target))
    }

    fun goHome() { navigate(_uiState.value.homepage.ifBlank { Constants.DEFAULT_HOMEPAGE }) }
    fun goBack() { _commands.trySend(BrowserCommand.Back) }
    fun goForward() { _commands.trySend(BrowserCommand.Forward) }
    fun reload() { _commands.trySend(BrowserCommand.Reload) }
    fun stopLoading() { _commands.trySend(BrowserCommand.Stop); _uiState.update { it.copy(isLoading = false) } }
    /** Page callbacks are tab-scoped: a background tab must never hijack the address bar. */
    fun onProgressChanged(tabId: String?, progress: Int) {
        if (tabId != null && tabId != _uiState.value.currentTabId) return
        _uiState.update { it.copy(progress = progress.coerceIn(0, 100), isLoading = progress < 100) }
    }

    fun onProgressChanged(progress: Int) = onProgressChanged(_uiState.value.currentTabId, progress)

    fun onPageStarted(tabId: String?, url: String?) {
        val safeUrl = url ?: return
        val target = tabId ?: _uiState.value.currentTabId ?: return
        // A form or link can still reach http:// even when the address bar refused it.
        if (CleartextPolicy.isBlocked(safeUrl, _uiState.value.isCleartextAllowed)) {
            if (target == _uiState.value.currentTabId) {
                _commands.trySend(BrowserCommand.Stop)
                _uiState.update { it.copy(blockedCleartextUrl = safeUrl, isLoading = false) }
                _messages.trySend(context.getString(R.string.error_cleartext_blocked))
            }
            return
        }
        _uiState.update { state ->
            val isCurrent = target == state.currentTabId
            state.copy(
                url = if (isCurrent) safeUrl else state.url,
                addressBarInput = if (isCurrent) safeUrl else state.addressBarInput,
                isLoading = if (isCurrent) true else state.isLoading,
                isSecure = if (isCurrent) safeUrl.startsWith("https://") else state.isSecure,
                tabs = state.tabs.map { tab -> if (tab.id == target) tab.copy(url = safeUrl) else tab }
            )
        }
    }

    fun onPageStarted(url: String?) = onPageStarted(_uiState.value.currentTabId, url)

    fun dismissCleartextBlock() { _uiState.update { it.copy(blockedCleartextUrl = null) } }

    /** Turns the setting on and loads the URL the user already asked for. */
    fun allowCleartextAndRetry() {
        val target = _uiState.value.blockedCleartextUrl ?: return
        viewModelScope.launch(Dispatchers.IO) { runCatching { settingsDataStore.setCleartextAllowed(true) } }
        _uiState.update { it.copy(isCleartextAllowed = true, blockedCleartextUrl = null) }
        navigate(target)
    }

    fun onPageFinished(tabId: String?, url: String?, title: String?) {
        val safeUrl = url ?: _uiState.value.url
        val target = tabId ?: _uiState.value.currentTabId
        _uiState.update { state ->
            val isCurrent = target == null || target == state.currentTabId
            state.copy(
                url = if (isCurrent) safeUrl else state.url,
                addressBarInput = if (isCurrent && state.addressBarInput == state.url) safeUrl else state.addressBarInput,
                title = if (isCurrent) title.orEmpty() else state.title,
                isLoading = if (isCurrent) false else state.isLoading,
                progress = if (isCurrent) 100 else state.progress,
                tabs = state.tabs.map { tab ->
                    if (tab.id != target) tab
                    else tab.copy(url = safeUrl, title = title?.takeIf { it.isNotBlank() } ?: tab.title, lastVisited = System.currentTimeMillis())
                }
            )
        }
        addHistoryEntry(target, safeUrl, title.orEmpty())
    }

    fun onPageFinished(url: String?, title: String?) = onPageFinished(_uiState.value.currentTabId, url, title)

    fun updateNavigationState(canGoBack: Boolean? = null, canGoForward: Boolean? = null) {
        val back = canGoBack ?: _uiState.value.canGoBack
        val forward = canGoForward ?: _uiState.value.canGoForward
        if (back != _uiState.value.canGoBack || forward != _uiState.value.canGoForward) _uiState.update { it.copy(canGoBack = back, canGoForward = forward) }
    }

    fun updateNavigationState(tabId: String?, canGoBack: Boolean, canGoForward: Boolean) {
        if (tabId != null && tabId != _uiState.value.currentTabId) return
        updateNavigationState(canGoBack, canGoForward)
    }

    /** Media found in a background tab belongs to that tab, not to the one on screen. */
    fun onPageHtml(tabId: String?, pageUrl: String, html: String) {
        if (tabId != null && tabId != _uiState.value.currentTabId) return
        detectMediaInPage(pageUrl, html)
    }

    // region find in page
    fun openFindInPage() { _uiState.update { it.copy(findInPage = FindInPageState()) } }

    fun updateFindQuery(query: String) {
        _uiState.update { state -> state.copy(findInPage = (state.findInPage ?: FindInPageState()).copy(query = query, matches = 0, activeMatch = 0)) }
    }

    fun onFindResult(tabId: String?, matches: Int, activeMatch: Int) {
        if (tabId != null && tabId != _uiState.value.currentTabId) return
        val current = _uiState.value.findInPage ?: return
        _uiState.update { it.copy(findInPage = current.copy(matches = matches, activeMatch = activeMatch)) }
    }

    fun closeFindInPage() { _uiState.update { it.copy(findInPage = null) } }
    // endregion
    // endregion

    // region media detection & quality sheet
    fun detectMediaInPage(pageUrl: String, html: String?) {
        // Debounce: cancel previous detection if page re-loaded quickly
        detectJob?.cancel()
        detectJob = viewModelScope.launch(Dispatchers.IO) {
            delay(120)
            val candidates = runCatching { mediaDetector.detect(pageUrl, html) }.getOrNull().orEmpty()
            if (_uiState.value.url == pageUrl) mergeCandidates(candidates)
        }
    }

    fun onMediaIntercepted(candidate: MediaCandidate) { mergeCandidates(listOf(candidate)) }

    private fun mergeCandidates(candidates: List<MediaCandidate>) {
        if (candidates.isEmpty()) return
        _uiState.update { state ->
            val merged = (state.detectedMedia + candidates).distinctBy { it.mediaUrl }
                .sortedByDescending { it.confidence }
                .take(24) // cap to prevent FAB badge overflow + list bloat
            // Only update if actually new items
            if (merged.size == state.detectedMedia.size && merged.toSet() == state.detectedMedia.toSet()) state
            else state.copy(detectedMedia = merged)
        }
    }

    fun openQualitySheet() {
        val candidate = _uiState.value.detectedMedia.firstOrNull() ?: return
        openQualitySheetFor(candidate)
    }

    fun openQualitySheetFor(candidate: MediaCandidate) {
        _uiState.update { it.copy(qualitySheet = QualitySheetState(candidate = candidate, mediaInfo = mediaInfoFrom(candidate), isResolving = true)) }
        viewModelScope.launch(Dispatchers.IO) {
            val resolved = runCatching { mediaResolver.resolve(candidate.mediaUrl) }.getOrNull()
            val merged = resolved?.let { info ->
                val synthesized = mediaInfoFrom(candidate)
                val formats = (info.combinedFormats + synthesized.combinedFormats).distinctBy { it.url }
                info.copy(title = info.title.ifBlank { synthesized.title }, thumbnail = info.thumbnail ?: synthesized.thumbnail, combinedFormats = formats.ifEmpty { synthesized.combinedFormats }, formats = formats)
            } ?: mediaInfoFrom(candidate)
            _uiState.update { state ->
                val current = state.qualitySheet ?: return@update state
                if (current.candidate.mediaUrl != candidate.mediaUrl) return@update state
                state.copy(qualitySheet = current.copy(mediaInfo = merged, isResolving = false))
            }
        }
    }

    fun closeQualitySheet() { _uiState.update { it.copy(qualitySheet = null) } }

    private fun mediaInfoFrom(candidate: MediaCandidate): MediaInfo {
        val extension = candidate.extension ?: com.salvia.salviabrowxer.media.downloader.DownloadManager.extensionFromUrl(candidate.mediaUrl)
        val title = candidate.title?.takeIf { it.isNotBlank() } ?: extension?.let { "Media .$it" } ?: "Media"
        val format = MediaFormat(id = candidate.mediaUrl, format = formatLabel(candidate, extension), url = candidate.mediaUrl, mimeType = candidate.mimeType ?: "application/octet-stream", extension = extension ?: "mp4", size = candidate.estimatedSize, isVideo = candidate.mimeType?.startsWith("video/") ?: true, isAudio = candidate.mimeType?.startsWith("audio/") ?: false)
        return MediaInfo(title = title, thumbnail = candidate.thumbnailUrl, duration = candidate.duration, formats = listOf(format), audioFormats = if (format.isAudio) listOf(format) else emptyList(), videoFormats = if (format.isVideo) listOf(format) else emptyList(), combinedFormats = listOf(format), source = candidate.source.name, extractor = "browser", webpageUrl = candidate.pageUrl)
    }

    private fun formatLabel(candidate: MediaCandidate, extension: String?): String {
        val ext = extension?.uppercase() ?: "FILE"
        return if (candidate.isLive) "$ext (live)" else ext
    }
    // endregion

    // region blob handling — called from BlobDownloadBridge on the JS thread, posts to IO dispatcher
    fun onBlobCaptured(pageUrl: String, blobUrl: String, stagedFile: File, mimeType: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val candidate = _uiState.value.detectedMedia.firstOrNull { it.mediaUrl == blobUrl }
            val title = candidate?.title?.takeIf { it.isNotBlank() } ?: pageUrl.substringAfterLast('/').substringBefore('?').ifBlank { "video" }
            val ext = mimeType.substringAfter('/', "mp4").substringBefore(';').lowercase().takeIf { it.length in 2..5 } ?: "mp4"
            val filename = sanitizeFilename(com.salvia.salviabrowxer.media.downloader.DownloadManager.generateFilename(title, ext))
            val destination = runCatching { downloadRepository.getDefaultDownloadDestination() }.getOrNull()?.takeIf { it.isNotBlank() }
                ?: context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)?.absolutePath ?: context.filesDir.absolutePath
            val destDir = File(destination).also { if (!it.exists()) it.mkdirs() }
            val desired = File(destDir, filename)
            val finalFile = if (!desired.exists()) desired else run {
                val base = desired.nameWithoutExtension; val ext = desired.extension
                var i = 1; var c: File
                do { c = if (ext.isEmpty()) File(destDir, "$base ($i)") else File(destDir, "$base ($i).$ext"); i++ } while (c.exists() && i <= 999)
                c
            }
            runCatching {
                if (stagedFile.absolutePath != finalFile.absolutePath) {
                    stagedFile.copyTo(finalFile, overwrite = true)
                    stagedFile.delete()
                } else {
                    stagedFile.renameTo(finalFile)
                }
            }
            if (!finalFile.exists() || finalFile.length() == 0L) {
                _messages.trySend(context.getString(R.string.error_disk_full))
                return@launch
            }
            val entity = DownloadEntity(
                url = blobUrl, finalUrl = finalFile.absolutePath, filename = finalFile.name,
                mimeType = mimeType.ifBlank { "video/mp4" }, destination = destination,
                totalBytes = finalFile.length(), downloadedBytes = finalFile.length(),
                status = DownloadState.COMPLETED, mediaTitle = title, thumbnail = candidate?.thumbnailUrl,
                selectedQuality = ext.uppercase(), finalPath = finalFile.absolutePath
            )
            downloadRepository.addDownload(entity)
            runCatching { MediaScannerConnection.scanFile(context, arrayOf(finalFile.absolutePath), null, null) }
            _uiState.update { it.copy(qualitySheet = null) }
            _messages.trySend(context.getString(R.string.download_completed))
        }
    }

    fun handleFormatSelected(format: MediaFormat) {
        if (format.url.startsWith("blob:")) {
            val pageUrl = _uiState.value.url
            _commands.trySend(BrowserCommand.FetchBlob(format.url, pageUrl))
            return
        }
        enqueueDownload(format)
    }

    // region downloads
    fun enqueueDownload(format: MediaFormat) {
        val sheet = _uiState.value.qualitySheet ?: return
        val candidate = sheet.candidate
        viewModelScope.launch(Dispatchers.IO) {
            val title = sheet.mediaInfo.title.takeIf { it.isNotBlank() } ?: candidate.pageUrl
            val extension = format.extension.takeIf { it.isNotBlank() } ?: com.salvia.salviabrowxer.media.downloader.DownloadManager.extensionFromUrl(format.url)
            val filename = sanitizeFilename(com.salvia.salviabrowxer.media.downloader.DownloadManager.generateFilename(title, extension))
            val destination = runCatching { downloadRepository.getDefaultDownloadDestination() }.getOrNull()?.takeIf { it.isNotBlank() }
                ?: context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)?.absolutePath ?: context.filesDir.absolutePath
            val entity = downloadRepository.createDownloadEntity(url = format.url, filename = filename, destination = destination, mediaTitle = title, thumbnail = candidate.thumbnailUrl, selectedQuality = format.format, mimeType = format.mimeType, totalBytes = format.size?.takeIf { it > 0L }).copy(status = DownloadState.QUEUED)
            downloadRepository.addDownload(entity)
            DownloadService.enqueueDownload(context, entity.id)
            _uiState.update { it.copy(qualitySheet = null) }
            _messages.trySend(context.getString(R.string.download_enqueued))
        }
    }

    fun clearDetectedMedia() { _uiState.update { it.copy(detectedMedia = emptyList()) } }
    // endregion

    // region browser settings & data
    fun setPrivateMode(isPrivate: Boolean) { _uiState.update { state -> state.copy(isPrivateMode = isPrivate, tabs = state.tabs.map { tab -> if (tab.id == state.currentTabId) tab.copy(isPrivate = isPrivate) else tab }) } }

    /** Desktop site is a real toggle: it is persisted and the store reloads the open page. */
    fun toggleDesktopSite() {
        val next = !_uiState.value.isDesktopSite
        _uiState.update { it.copy(isDesktopSite = next) }
        viewModelScope.launch(Dispatchers.IO) { runCatching { settingsDataStore.setDesktopSite(next) } }
    }

    fun saveFabPosition(x: Float, y: Float) {
        _uiState.update { it.copy(fabPosition = FabPosition(x, y)) }
        fabSaveJob?.cancel()
        fabSaveJob = viewModelScope.launch {
            delay(400)
            runCatching { settingsDataStore.setFloatingButtonPosition(x, y) }
        }
    }

    fun addBookmark(title: String, url: String) {
        viewModelScope.launch { bookmarkRepository.addBookmark(BookmarkEntity(title = title.ifBlank { url }, url = url)); _messages.trySend(context.getString(R.string.bookmark_added)) }
    }

    fun removeBookmark(url: String) {
        viewModelScope.launch { val match = bookmarkRepository.getAllBookmarks().first().firstOrNull { it.url == url }; match?.let { bookmarkRepository.deleteBookmark(it.id) } }
    }

    /** Bookmarks the page currently on screen, straight from the browser menu. */
    fun bookmarkCurrentTab() {
        val url = _uiState.value.url.takeIf { it.isNotBlank() } ?: return
        addBookmark(_uiState.value.title, url)
    }

    fun shareCurrentPage() {
        val url = _uiState.value.url.takeIf { it.isNotBlank() } ?: return
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, _uiState.value.title)
            putExtra(Intent.EXTRA_TEXT, url)
        }
        runCatching {
            context.startActivity(
                Intent.createChooser(send, context.getString(R.string.share_page)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    fun copyCurrentLink() {
        val url = _uiState.value.url.takeIf { it.isNotBlank() } ?: return
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText(_uiState.value.title.ifBlank { url }, url))
        _messages.trySend(context.getString(R.string.link_copied))
    }

    /**
     * A link picked inside the app — a bookmark or a history entry. It replaces the page in the
     * current tab, which is what a browser does when you tap a bookmark.
     */
    fun openInCurrentTab(url: String) { navigate(url) }

    /** Opens a URL handed to the app by ACTION_VIEW or ACTION_SEND in its own tab. */
    fun openExternalUrl(url: String) {
        val target = AddressBarResolver.resolve(url, _uiState.value.searchEngine) ?: return
        createNewTab(url = target)
    }

    /** Hands tel:, mailto: and intent: URLs to the system; only ACTION_VIEW without a selector. */
    fun openExternalScheme(url: String): Boolean {
        val intent = runCatching { Intent.parseUri(url, Intent.URI_INTENT_SCHEME) }.getOrNull() ?: return false
        if (intent.action != Intent.ACTION_VIEW) return false
        intent.selector = null
        intent.component = null
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(intent); true }.getOrDefault(false)
    }

    fun addHistoryEntry(tabId: String?, url: String, title: String) {
        if (url.isBlank()) return
        val target = _uiState.value.tabs.firstOrNull { it.id == (tabId ?: _uiState.value.currentTabId) }
        if (_uiState.value.isPrivateMode || target?.isPrivate == true) return
        viewModelScope.launch { runCatching { historyRepository.addHistory(HistoryEntity(url = url, title = title.ifBlank { url }, visitedAt = System.currentTimeMillis())) } }
    }

    fun addHistoryEntry(url: String, title: String) = addHistoryEntry(_uiState.value.currentTabId, url, title)
    // endregion

    private fun normalizeUrl(raw: String): String? {
        val input = raw.trim()
        if (input.isEmpty()) return null
        return AddressBarResolver.resolve(input, _uiState.value.searchEngine)
    }

    private fun resolveTargetUrl(input: String): String? =
        AddressBarResolver.resolve(input, _uiState.value.searchEngine)

    companion object { private const val TAG = "BrowserViewModel" }
}
