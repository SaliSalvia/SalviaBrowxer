package com.salvia.salviabrowxer.feature.browser

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salvia.salviabrowxer.R
import com.salvia.salviabrowxer.core.database.entities.BookmarkEntity
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
import com.salvia.salviabrowxer.ui.utils.Constants
import com.salvia.salviabrowxer.ui.utils.sanitizeFilename
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.net.URLEncoder
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

/** One-shot commands the browser UI has to execute against the [android.webkit.WebView]. */
sealed interface BrowserCommand {
    data class Load(val url: String) : BrowserCommand
    data object Back : BrowserCommand
    data object Forward : BrowserCommand
    data object Reload : BrowserCommand
    data object Stop : BrowserCommand
}

/** Position of the floating download button, relative to the bottom-end corner of the page area. */
data class FabPosition(val x: Float = 0f, val y: Float = 0f)

/** Everything the browser screen renders, derived from a single immutable state object. */
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
    val activeDownloadCount: Int = 0,
    val detectedMedia: List<MediaCandidate> = emptyList(),
    val fabPosition: FabPosition = FabPosition(),
    val floatingButtonSize: Int = 56,
    val qualitySheet: QualitySheetState? = null
) {
    val isMediaDetected: Boolean get() = detectedMedia.isNotEmpty()
}

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

    private val initialHomepageHandled = AtomicBoolean(false)

    init {
        openTab(url = Constants.DEFAULT_HOMEPAGE, isPrivate = false, navigate = false)
        _uiState.update { it.copy(url = Constants.DEFAULT_HOMEPAGE, addressBarInput = Constants.DEFAULT_HOMEPAGE) }
        observeSettings()
        observeDownloadQueue()
    }

    /**
     * Live settings stream. The WebView applies these in its Compose `update` block, so a change in
     * SettingsScreen is reflected without recreating the browser destination.
     */
    private fun observeSettings() {
        viewModelScope.launch(Dispatchers.IO) {
            launch {
                settingsDataStore.homepage.collectLatest { value ->
                    val safe = value.ifBlank { Constants.DEFAULT_HOMEPAGE }
                    _uiState.update { it.copy(homepage = safe) }
                    // Only the first emitted homepage should drive navigation, and only when the
                    // user has not already started browsing. This keeps startup deterministic and
                    // avoids overriding an address typed/reloaded during the async settings load.
                    if (initialHomepageHandled.compareAndSet(false, true)) {
                        val current = _uiState.value.url
                        if (safe != Constants.DEFAULT_HOMEPAGE &&
                            (current.isBlank() || current == Constants.DEFAULT_HOMEPAGE)
                        ) {
                            navigate(safe)
                        }
                    }
                }
            }
            launch {
                settingsDataStore.searchEngine.collectLatest { value ->
                    _uiState.update { it.copy(searchEngine = value.ifBlank { Constants.DEFAULT_SEARCH_ENGINE }) }
                }
            }
            launch {
                settingsDataStore.isJavaScriptEnabled.collectLatest { value ->
                    _uiState.update { it.copy(isJavaScriptEnabled = value) }
                }
            }
            launch {
                settingsDataStore.areCookiesEnabled.collectLatest { value ->
                    _uiState.update { it.copy(areCookiesEnabled = value) }
                }
            }
            launch {
                settingsDataStore.isDesktopSite.collectLatest { value ->
                    _uiState.update { it.copy(isDesktopSite = value) }
                }
            }
            launch {
                settingsDataStore.floatingButtonX.collectLatest { value ->
                    _uiState.update { it.copy(fabPosition = FabPosition(value, it.fabPosition.y)) }
                }
            }
            launch {
                settingsDataStore.floatingButtonY.collectLatest { value ->
                    _uiState.update { it.copy(fabPosition = FabPosition(it.fabPosition.x, value)) }
                }
            }
            launch {
                settingsDataStore.floatingButtonSize.collectLatest { value ->
                    _uiState.update { it.copy(floatingButtonSize = value.coerceIn(MIN_FLOATING_BUTTON_SIZE, MAX_FLOATING_BUTTON_SIZE)) }
                }
            }
        }
    }

    /** Keeps the bottom-bar badge in sync with the real download queue. */
    private fun observeDownloadQueue() {
        viewModelScope.launch(Dispatchers.IO) {
            downloadRepository.getDownloadsByStates(
                listOf(
                    DownloadState.QUEUED,
                    DownloadState.RESOLVING,
                    DownloadState.PREPARING,
                    DownloadState.DOWNLOADING,
                    DownloadState.RETRYING,
                    DownloadState.PROCESSING
                )
            ).collectLatest { pending ->
                _uiState.update { it.copy(activeDownloadCount = pending.size) }
            }
        }
    }

    // region tabs

    fun createNewTab(url: String = "", isPrivate: Boolean = false) {
        openTab(url, isPrivate, navigate = url.isNotEmpty())
    }

    private fun openTab(url: String, isPrivate: Boolean, navigate: Boolean) {
        val tab = Tab(
            title = if (url.isEmpty()) "New Tab" else url,
            url = url,
            isPrivate = isPrivate || _uiState.value.isPrivateMode
        )
        _uiState.update { state ->
            state.copy(
                tabs = state.tabs + tab.copy(position = state.tabs.size),
                currentTabId = tab.id,
                url = url.ifEmpty { state.url },
                addressBarInput = url.ifEmpty { state.addressBarInput },
                detectedMedia = emptyList()
            )
        }
        if (navigate && url.isNotEmpty()) {
            _commands.trySend(BrowserCommand.Load(url))
        }
    }

    fun switchTab(tabId: String) {
        val tab = _uiState.value.tabs.firstOrNull { it.id == tabId } ?: return
        _uiState.update { state ->
            state.copy(
                currentTabId = tab.id,
                url = tab.url,
                addressBarInput = tab.url,
                title = tab.title,
                detectedMedia = emptyList()
            )
        }
        if (tab.url.isNotEmpty()) {
            _commands.trySend(BrowserCommand.Load(tab.url))
        }
    }

    fun closeTab(tabId: String) {
        _uiState.update { state ->
            val index = state.tabs.indexOfFirst { it.id == tabId }
            if (index == -1) return@update state
            val remaining = state.tabs.toMutableList().also { it.removeAt(index) }
                .mapIndexed { position, tab -> tab.copy(position = position) }
            val current = if (state.currentTabId != tabId) {
                state.currentTabId
            } else {
                remaining.getOrNull(minOf(index, remaining.size - 1))?.id
            }
            val currentTab = remaining.firstOrNull { it.id == current }
            state.copy(
                tabs = remaining,
                currentTabId = current,
                url = currentTab?.url ?: "",
                addressBarInput = currentTab?.url ?: "",
                title = currentTab?.title ?: ""
            )
        }
    }

    // endregion

    // region navigation

    /**
     * Turns whatever the user typed into a real navigation target: a URL when it looks like a
     * host, otherwise a search on the configured engine.
     */
    fun loadFromAddressBar(input: String = _uiState.value.addressBarInput) {
        val target = resolveTargetUrl(input) ?: return
        _uiState.update { it.copy(addressBarInput = target) }
        navigate(target)
    }

    fun onAddressInputChange(input: String) {
        _uiState.update { it.copy(addressBarInput = input) }
    }

    fun navigate(url: String) {
        val target = normalizeUrl(url) ?: return
        _uiState.update { state ->
            state.copy(
                url = target,
                addressBarInput = target,
                isLoading = true,
                progress = 0,
                isSecure = target.startsWith("https://"),
                detectedMedia = emptyList(),
                tabs = state.tabs.map { tab ->
                    if (tab.id == state.currentTabId) {
                        tab.copy(url = target, lastVisited = System.currentTimeMillis())
                    } else {
                        tab
                    }
                }
            )
        }
        _commands.trySend(BrowserCommand.Load(target))
    }

    fun goHome() {
        navigate(_uiState.value.homepage.ifBlank { Constants.DEFAULT_HOMEPAGE })
    }

    fun goBack() {
        _commands.trySend(BrowserCommand.Back)
    }

    fun goForward() {
        _commands.trySend(BrowserCommand.Forward)
    }

    fun reload() {
        _commands.trySend(BrowserCommand.Reload)
    }

    fun stopLoading() {
        _commands.trySend(BrowserCommand.Stop)
        _uiState.update { it.copy(isLoading = false) }
    }

    fun onProgressChanged(progress: Int) {
        _uiState.update {
            it.copy(progress = progress.coerceIn(0, 100), isLoading = progress < 100)
        }
    }

    fun onPageStarted(url: String?) {
        val safeUrl = url ?: return
        _uiState.update { state ->
            state.copy(
                url = safeUrl,
                addressBarInput = safeUrl,
                isLoading = true,
                isSecure = safeUrl.startsWith("https://"),
                tabs = state.tabs.map { tab ->
                    if (tab.id == state.currentTabId) tab.copy(url = safeUrl) else tab
                }
            )
        }
    }

    fun onPageFinished(url: String?, title: String?) {
        val safeUrl = url ?: _uiState.value.url
        _uiState.update { state ->
            state.copy(
                url = safeUrl,
                addressBarInput = if (state.addressBarInput == state.url) safeUrl else state.addressBarInput,
                title = title.orEmpty(),
                isLoading = false,
                progress = 100,
                tabs = state.tabs.map { tab ->
                    if (tab.id == state.currentTabId) {
                        tab.copy(
                            url = safeUrl,
                            title = title?.takeIf { it.isNotBlank() } ?: tab.title,
                            lastVisited = System.currentTimeMillis()
                        )
                    } else {
                        tab
                    }
                }
            )
        }
        updateNavigationState()
        addHistoryEntry(safeUrl, title.orEmpty())
    }

    fun updateNavigationState(canGoBack: Boolean? = null, canGoForward: Boolean? = null) {
        val back = canGoBack ?: _uiState.value.canGoBack
        val forward = canGoForward ?: _uiState.value.canGoForward
        if (back != _uiState.value.canGoBack || forward != _uiState.value.canGoForward) {
            _uiState.update { it.copy(canGoBack = back, canGoForward = forward) }
        }
    }

    // endregion

    // region media detection & quality sheet

    fun detectMediaInPage(pageUrl: String, html: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            val candidates = runCatching { mediaDetector.detect(pageUrl, html) }.getOrNull().orEmpty()
            mergeCandidates(candidates)
        }
    }

    fun onMediaIntercepted(candidate: MediaCandidate) {
        mergeCandidates(listOf(candidate))
    }

    private fun mergeCandidates(candidates: List<MediaCandidate>) {
        if (candidates.isEmpty()) return
        _uiState.update { state ->
            val merged = (state.detectedMedia + candidates)
                .distinctBy { it.mediaUrl }
                .sortedByDescending { it.confidence }
                .take(MAX_DETECTED_MEDIA)
            state.copy(detectedMedia = merged)
        }
    }

    /** Opens the quality sheet for the first (most confident) detected media item. */
    fun openQualitySheet() {
        val candidate = _uiState.value.detectedMedia.firstOrNull() ?: return
        openQualitySheetFor(candidate)
    }

    fun openQualitySheetFor(candidate: MediaCandidate) {
        _uiState.update {
            it.copy(
                qualitySheet = QualitySheetState(
                    candidate = candidate,
                    mediaInfo = mediaInfoFrom(candidate),
                    isResolving = true
                )
            )
        }
        viewModelScope.launch(Dispatchers.IO) {
            val resolved = runCatching { mediaResolver.resolve(candidate.mediaUrl) }.getOrNull()
            val merged = resolved?.let { info ->
                val synthesized = mediaInfoFrom(candidate)
                val formats = (info.combinedFormats + synthesized.combinedFormats).distinctBy { it.url }
                info.copy(
                    title = info.title.ifBlank { synthesized.title },
                    thumbnail = info.thumbnail ?: synthesized.thumbnail,
                    combinedFormats = formats.ifEmpty { synthesized.combinedFormats },
                    formats = formats
                )
            } ?: mediaInfoFrom(candidate)

            _uiState.update { state ->
                val current = state.qualitySheet ?: return@update state
                state.copy(qualitySheet = current.copy(mediaInfo = merged, isResolving = false))
            }
        }
    }

    fun closeQualitySheet() {
        _uiState.update { it.copy(qualitySheet = null) }
    }

    private fun mediaInfoFrom(candidate: MediaCandidate): MediaInfo {
        val extension = candidate.extension
            ?: com.salvia.salviabrowxer.media.downloader.DownloadManager.extensionFromUrl(candidate.mediaUrl)
        val title = candidate.title?.takeIf { it.isNotBlank() }
            ?: extension?.let { "Media .$it" }
            ?: "Media"
        val format = MediaFormat(
            id = candidate.mediaUrl,
            format = formatLabel(candidate, extension),
            url = candidate.mediaUrl,
            mimeType = candidate.mimeType ?: "application/octet-stream",
            extension = extension ?: "mp4",
            size = candidate.estimatedSize,
            isVideo = candidate.mimeType?.startsWith("video/") ?: true,
            isAudio = candidate.mimeType?.startsWith("audio/") ?: false
        )
        return MediaInfo(
            title = title,
            thumbnail = candidate.thumbnailUrl,
            duration = candidate.duration,
            formats = listOf(format),
            audioFormats = if (format.isAudio) listOf(format) else emptyList(),
            videoFormats = if (format.isVideo) listOf(format) else emptyList(),
            combinedFormats = listOf(format),
            source = candidate.source.name,
            extractor = "browser",
            webpageUrl = candidate.pageUrl
        )
    }

    private fun formatLabel(candidate: MediaCandidate, extension: String?): String {
        val ext = extension?.uppercase() ?: "FILE"
        return if (candidate.isLive) "$ext (live)" else ext
    }

    // endregion

    // region downloads

    /** Turns a quality selection into a real queue entry and hands it to [DownloadService]. */
    fun enqueueDownload(format: MediaFormat) {
        val sheet = _uiState.value.qualitySheet ?: return
        val candidate = sheet.candidate
        viewModelScope.launch(Dispatchers.IO) {
            val title = sheet.mediaInfo.title.takeIf { it.isNotBlank() } ?: candidate.pageUrl
            val extension = format.extension.takeIf { it.isNotBlank() }
                ?: com.salvia.salviabrowxer.media.downloader.DownloadManager.extensionFromUrl(format.url)
            val filename = sanitizeFilename(
                com.salvia.salviabrowxer.media.downloader.DownloadManager.generateFilename(title, extension)
            )
            val destination = runCatching { downloadRepository.getDefaultDownloadDestination() }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?: context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)
                    ?.absolutePath
                ?: context.filesDir.absolutePath

            val entity = downloadRepository.createDownloadEntity(
                url = format.url,
                filename = filename,
                destination = destination,
                mediaTitle = title,
                thumbnail = candidate.thumbnailUrl,
                selectedQuality = format.format,
                mimeType = format.mimeType,
                totalBytes = format.size?.takeIf { it > 0L }
            ).copy(status = DownloadState.QUEUED)

            downloadRepository.addDownload(entity)
            DownloadService.enqueueDownload(context, entity.id)

            _uiState.update { it.copy(qualitySheet = null) }
            _messages.trySend(context.getString(R.string.download_enqueued))
        }
    }

    fun clearDetectedMedia() {
        _uiState.update { it.copy(detectedMedia = emptyList()) }
    }

    // endregion

    // region browser settings & data

    fun setPrivateMode(isPrivate: Boolean) {
        _uiState.update { state ->
            state.copy(
                isPrivateMode = isPrivate,
                tabs = state.tabs.map { tab ->
                    if (tab.id == state.currentTabId) tab.copy(isPrivate = isPrivate) else tab
                }
            )
        }
    }

    fun saveFabPosition(x: Float, y: Float) {
        _uiState.update { it.copy(fabPosition = FabPosition(x, y)) }
        viewModelScope.launch {
            runCatching { settingsDataStore.setFloatingButtonPosition(x, y) }
        }
    }

    fun addBookmark(title: String, url: String) {
        viewModelScope.launch {
            bookmarkRepository.addBookmark(BookmarkEntity(title = title.ifBlank { url }, url = url))
            _messages.trySend("Bookmark added")
        }
    }

    fun removeBookmark(url: String) {
        viewModelScope.launch {
            val match = bookmarkRepository.getAllBookmarks().first()
                .firstOrNull { it.url == url }
            match?.let { bookmarkRepository.deleteBookmark(it.id) }
        }
    }

    fun addHistoryEntry(url: String, title: String) {
        if (_uiState.value.isPrivateMode || url.isBlank()) return
        viewModelScope.launch {
            runCatching {
                historyRepository.addHistory(
                    HistoryEntity(
                        url = url,
                        title = title.ifBlank { url },
                        visitedAt = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    // endregion

    private fun normalizeUrl(raw: String): String? {
        val input = raw.trim()
        if (input.isEmpty()) return null
        return when {
            input.startsWith("http://") || input.startsWith("https://") ||
                input.startsWith("file://") || input.startsWith("about:") -> input
            else -> resolveTargetUrl(input)
        }
    }

    private fun resolveTargetUrl(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://") ||
            trimmed.startsWith("file://") || trimmed.startsWith("about:")
        ) {
            return trimmed
        }
        val looksLikeHost = !trimmed.contains(' ') &&
            (trimmed.contains('.') || trimmed.startsWith("localhost") || trimmed.contains(':'))
        return if (looksLikeHost) {
            "https://$trimmed"
        } else {
            buildSearchUrl(trimmed)
        }
    }

    private fun buildSearchUrl(query: String): String {
        val template = Constants.SEARCH_ENGINES[_uiState.value.searchEngine]
            ?: Constants.SEARCH_ENGINES.getValue(Constants.DEFAULT_SEARCH_ENGINE)
        val encoded = URLEncoder.encode(query, "UTF-8")
        return template.replace("%s", encoded)
    }

    companion object {
        private const val TAG = "BrowserViewModel"
        private const val MIN_FLOATING_BUTTON_SIZE = 40
        private const val MAX_FLOATING_BUTTON_SIZE = 72
        private const val MAX_DETECTED_MEDIA = 40
    }
}
