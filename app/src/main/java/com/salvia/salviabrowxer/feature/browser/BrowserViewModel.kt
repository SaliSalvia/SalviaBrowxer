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
import com.salvia.salviabrowxer.core.model.MediaCandidateIndex
import com.salvia.salviabrowxer.core.model.MediaFormat
import com.salvia.salviabrowxer.core.model.MediaInfo
import com.salvia.salviabrowxer.core.model.Tab
import com.salvia.salviabrowxer.data.datastore.SessionCodec
import com.salvia.salviabrowxer.data.datastore.SessionRestorer
import com.salvia.salviabrowxer.data.datastore.SessionTab
import com.salvia.salviabrowxer.data.datastore.SettingsDataStore
import com.salvia.salviabrowxer.data.repository.BookmarkRepository
import com.salvia.salviabrowxer.data.repository.DownloadRepository
import com.salvia.salviabrowxer.data.repository.HistoryRepository
import com.salvia.salviabrowxer.media.detector.MediaDetector
import com.salvia.salviabrowxer.media.downloader.DownloadManager
import com.salvia.salviabrowxer.media.downloader.MseRecorder
import com.salvia.salviabrowxer.core.model.PastedLink
import com.salvia.salviabrowxer.media.resolver.MediaResolver
import com.salvia.salviabrowxer.service.DownloadService
import com.salvia.salviabrowxer.service.MseRecordingController
import com.salvia.salviabrowxer.ui.utils.AddressBarResolver
import com.salvia.salviabrowxer.ui.utils.CleartextPolicy
import com.salvia.salviabrowxer.ui.utils.Constants
import com.salvia.salviabrowxer.ui.utils.MediaOfferability
import com.salvia.salviabrowxer.ui.utils.MseRecordingUiState
import com.salvia.salviabrowxer.ui.utils.PageErrorKind
import com.salvia.salviabrowxer.ui.utils.UnsupportedMedia
import com.salvia.salviabrowxer.ui.utils.filenameFromContentDisposition
import com.salvia.salviabrowxer.ui.utils.getMimeTypeFromExtension
import com.salvia.salviabrowxer.ui.utils.sanitizeFilename
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
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
import kotlinx.coroutines.withContext
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
    /** An admitted URL currently associated with the visible player, not proof of main content. */
    val visibleMediaUrl: String? = null,
    val fabPosition: FabPosition = FabPosition(),
    val floatingButtonSize: Int = 56,
    /** The draggable button is an advanced opt-in; the media pill in the top bar is the default. */
    val isFabAlwaysVisible: Boolean = false,
    val qualitySheet: QualitySheetState? = null,
    /** The tray lists every candidate on the page; the pill in the top bar opens it. */
    val isMediaTrayVisible: Boolean = false,
    /** Tabs whose WebView was destroyed to stay under the live-tab cap; they reload on select. */
    val hibernatedTabIds: Set<String> = emptySet(),
    val isTabSwitcherVisible: Boolean = false,
    val findInPage: FindInPageState? = null,
    /**
     * What the media sheet can honestly say about recording the current playback.
     *
     * It is in the UI state rather than read from the recorder on demand because it changes while the
     * sheet is open — bytes grow, the recording stops — and a sheet that only knew the state at the
     * moment it opened would show a frozen byte count.
     */
    val mseRecording: MseRecordingUiState = MseRecordingUiState(),
    /**
     * Bumped when a page's renderer process dies. The tab's WebView is gone by then, so the browser
     * screen has to run its attach pass again for that tab; a new state value is what recomposes it
     * and makes it build a fresh WebView on the tab's last committed URL.
     */
    val rendererEpoch: Int = 0
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
    val isResolving: Boolean = false,
    /** Set when the candidate cannot be downloaded at all; the sheet explains instead of offering. */
    val unsupported: UnsupportedMedia? = null,
    /**
     * Whether recording the playback is on offer for this candidate.
     *
     * It is exactly the case where the file download is not: a device that can hand the app a
     * segment as bytes, and a stream that is not live — a live stream is never recorded, by design.
     */
    val canRecord: Boolean = false
)

@HiltViewModel
class BrowserViewModel @Inject constructor(
    private val historyRepository: HistoryRepository,
    private val bookmarkRepository: BookmarkRepository,
    private val downloadRepository: DownloadRepository,
    private val settingsDataStore: SettingsDataStore,
    private val mediaDetector: MediaDetector,
    private val mediaResolver: MediaResolver,
    private val sessionRestorer: SessionRestorer,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(BrowserUiState())
    val uiState: StateFlow<BrowserUiState> = _uiState.asStateFlow()

    /** Completed once the session question is answered: restore, or nothing to restore. */
    private val sessionRestoreSettled = CompletableDeferred<Unit>()

    private val _commands = Channel<BrowserCommand>(Channel.BUFFERED)
    val commands: Flow<BrowserCommand> = _commands.receiveAsFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    private var detectJob: Job? = null
    private var fabSaveJob: Job? = null

    /**
     * The one authority over what a page may write into a recording, shared by every tab this view
     * model holds. Lazily built because it is the first thing that would touch the cache directory,
     * and most sessions never record anything.
     */
    private val mseRecording by lazy { MseRecordingController(File(context.cacheDir, "mse")) }

    init {
        openTab(url = Constants.DEFAULT_HOMEPAGE, isPrivate = false, navigate = false)
        _uiState.update { it.copy(url = Constants.DEFAULT_HOMEPAGE, addressBarInput = Constants.DEFAULT_HOMEPAGE) }
        // The stored homepage may differ from the placeholder. Only follow it while the user has
        // not navigated yet, so a slow settings read can never clobber a page the user opened.
        val startupUrl = Constants.DEFAULT_HOMEPAGE
        // The values the UI opens with, captured before the read starts. The update below uses
        // them to tell a field the user has since changed apart from one still holding its
        // placeholder, so a slow read can never rewind a control the user already moved.
        val startup = _uiState.value
        viewModelScope.launch(Dispatchers.IO) {
            val homepage = runCatching { settingsDataStore.homepage.first() }.getOrNull()?.takeIf { it.isNotBlank() } ?: Constants.DEFAULT_HOMEPAGE
            val engine = runCatching { settingsDataStore.searchEngine.first() }.getOrNull() ?: Constants.DEFAULT_SEARCH_ENGINE
            val jsEnabled = runCatching { settingsDataStore.isJavaScriptEnabled.first() }.getOrNull() ?: true
            val cookiesEnabled = runCatching { settingsDataStore.areCookiesEnabled.first() }.getOrNull() ?: true
            val desktop = runCatching { settingsDataStore.isDesktopSite.first() }.getOrNull() ?: false
            val fabX = runCatching { settingsDataStore.floatingButtonX.first() }.getOrNull() ?: 0f
            val fabY = runCatching { settingsDataStore.floatingButtonY.first() }.getOrNull() ?: 0f
            val fabSize = runCatching { settingsDataStore.floatingButtonSize.first() }.getOrNull() ?: 56
            val fabAlways = runCatching { settingsDataStore.isFloatingButtonAlwaysVisible.first() }.getOrNull() ?: false
            val cleartextAllowed = runCatching { settingsDataStore.isCleartextAllowed.first() }.getOrNull() ?: false
            _uiState.update { state ->
                state.copy(
                    homepage = homepage, searchEngine = engine, isJavaScriptEnabled = jsEnabled,
                    areCookiesEnabled = cookiesEnabled,
                    isDesktopSite = if (state.isDesktopSite == startup.isDesktopSite) desktop else state.isDesktopSite,
                    fabPosition = if (state.fabPosition == startup.fabPosition) FabPosition(fabX, fabY) else state.fabPosition,
                    floatingButtonSize = fabSize.coerceIn(40, 72), isFabAlwaysVisible = fabAlways,
                    isCleartextAllowed = if (state.isCleartextAllowed == startup.isCleartextAllowed) cleartextAllowed else state.isCleartextAllowed
                )
            }
            if (homepage != startupUrl && _uiState.value.url == startupUrl) navigate(homepage)
        }
        observeSettingsLive()
        observeDownloadQueue()
        observeMseRecording()
        observeSession()
    }

    /**
     * The tab list, kept across process death.
     *
     * Persisting is a collector over the tabs already in the state rather than a call at every place
     * that touches them: one hook cannot be forgotten when a new tab operation is added later. Private
     * tabs never reach the file — a private tab is a promise about what outlives the session.
     */
    private fun observeSession() {
        viewModelScope.launch {
            if (sessionRestorer.claimRestore()) {
                val saved = runCatching { sessionRestorer.saved() }.getOrDefault(emptyList())
                if (saved.isNotEmpty()) restoreSession(saved)
            }
            // Reached on every path, including "there was nothing to restore".
            sessionRestoreSettled.complete(Unit)
        }
        viewModelScope.launch {
            // Writing before the restore has been decided would save the placeholder tab over the very
            // session this process is about to bring back.
            sessionRestoreSettled.await()
            uiState
                .map { state -> state.tabs }
                .distinctUntilChanged()
                .collectLatest { tabs ->
                    // collectLatest cancels the previous wait, so a burst of tab operations settles into
                    // one write instead of a write per keystroke of the address bar.
                    delay(SESSION_PERSIST_SETTLE_MILLIS)
                    sessionRestorer.persist(
                        tabs.filter { !it.isPrivate && it.url.isNotBlank() }
                            .map { tab -> SessionTab(id = tab.id, url = tab.url, title = tab.title) }
                    )
                }
        }
    }

    /**
     * Puts the saved tabs back.
     *
     * The tab list and the current tab change, which is what the browser surface watches: it attaches
     * the first restored tab and loads the URL it was left on, the same way it opens any tab it has not
     * attached yet. A page that cannot load shows its own error rather than an empty window, and the
     * user is told how many tabs came back — a browser that silently reappears with different tabs than
     * it had is worse than one that says so.
     */
    private fun restoreSession(saved: List<SessionTab>) {
        val restored = saved
            .filter { SessionCodec.isRestorable(it.url) }
            .mapIndexed { index, tab ->
                Tab(id = tab.id, title = tab.title.ifBlank { tab.url }, url = tab.url, position = index)
            }
        if (restored.isEmpty()) return
        // The tab the browser opened with is not being restored, so its view (if one was already built)
        // must not be left attached to a tab that no longer exists.
        webViewStore?.destroyAll()
        val current = restored.first()
        _uiState.update { state ->
            state.copy(
                tabs = restored,
                currentTabId = current.id,
                url = current.url,
                addressBarInput = current.url,
                title = current.title,
                isLoading = true,
                progress = 0
            )
        }
        _messages.trySend(context.getString(R.string.session_restored, restored.size))
    }

    /** The recorder is the source of truth for a running recording; the sheet only draws it. */
    private fun observeMseRecording() {
        viewModelScope.launch {
            mseRecording.active.collectLatest { session ->
                _uiState.update { state ->
                    state.copy(
                        mseRecording = state.mseRecording.copy(
                            isRecording = session != null,
                            bytes = session?.bytes ?: 0L
                        )
                    )
                }
            }
        }
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
                // The store acknowledging the user's own decision ends the pending state; until then
                // the decision wins, because an emission of the pre-write value is not news.
                if (pendingCleartextDecision != null && pendingCleartextDecision == allowed) {
                    pendingCleartextDecision = null
                }
                val effective = pendingCleartextDecision ?: allowed
                _uiState.update { it.copy(isCleartextAllowed = effective) }
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
        _uiState.update { state -> state.copy(tabs = state.tabs + tab.copy(position = state.tabs.size), currentTabId = tab.id, url = url.ifEmpty { state.url }, addressBarInput = url.ifEmpty { state.addressBarInput }, detectedMedia = emptyList(), visibleMediaUrl = null, qualitySheet = null) }
        if (navigate && url.isNotEmpty()) _commands.trySend(BrowserCommand.Load(url))
    }

    fun switchTab(tabId: String) {
        val tab = _uiState.value.tabs.firstOrNull { it.id == tabId } ?: return
        _uiState.update { state ->
            state.copy(
                currentTabId = tab.id, url = tab.url, addressBarInput = tab.url, title = tab.title,
                isPrivateMode = tab.isPrivate, detectedMedia = emptyList(), visibleMediaUrl = null, qualitySheet = null,
                isTabSwitcherVisible = false,
                // A hibernated tab restores the last committed URL; the store reloads it.
                canGoBack = false, canGoForward = false
            )
        }
        // TabWebViewStore.attach restores a hibernated view; a live view must not reload on
        // selection. The sniffer heartbeat re-admits its current player after the tray reset.
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
                isPrivateMode = currentTab?.isPrivate == true,
                detectedMedia = if (state.currentTabId == current) state.detectedMedia else emptyList(),
                visibleMediaUrl = if (state.currentTabId == current) state.visibleMediaUrl else null,
                qualitySheet = if (state.currentTabId == current) state.qualitySheet else null,
                hibernatedTabIds = state.hibernatedTabIds - tabId
            )
        }
        // A recording belongs to its tab: closing the tab ends it, and the bytes written so far are
        // kept rather than thrown away — the user asked for them.
        if (mseRecording.active.value?.tabId == tabId) stopMseRecording()
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
        _uiState.update { state -> state.copy(url = target, addressBarInput = target, isLoading = true, progress = 0, isSecure = target.startsWith("https://"), detectedMedia = emptyList(), visibleMediaUrl = null, qualitySheet = null, tabs = state.tabs.map { tab -> if (tab.id == state.currentTabId) tab.copy(url = target, lastVisited = System.currentTimeMillis()) else tab }) }
        _commands.trySend(BrowserCommand.Load(target))
    }

    fun goHome() {
        val target = _uiState.value.homepage.ifBlank { Constants.DEFAULT_HOMEPAGE }
        if (_uiState.value.url == target) {
            _messages.trySend(context.getString(R.string.home_noop_moved))
            return
        }
        navigate(target)
    }
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
                detectedMedia = if (isCurrent) emptyList() else state.detectedMedia,
                visibleMediaUrl = if (isCurrent) null else state.visibleMediaUrl,
                qualitySheet = if (isCurrent) null else state.qualitySheet,
                tabs = state.tabs.map { tab -> if (tab.id == target) tab.copy(url = safeUrl) else tab }
            )
        }
    }

    /** SPA pushState/replaceState changes page identity without an onPageStarted callback. */
    fun onHistoryUrlChanged(tabId: String, url: String) {
        _uiState.update { state ->
            if (tabId != state.currentTabId || url == state.url) return@update state
            state.copy(url = url, addressBarInput = url, detectedMedia = emptyList(),
                visibleMediaUrl = null, qualitySheet = null, tabs = state.tabs.map { tab ->
                    if (tab.id == tabId) tab.copy(url = url) else tab
                })
        }
    }

    fun onPageStarted(url: String?) = onPageStarted(_uiState.value.currentTabId, url)

    fun dismissCleartextBlock() { _uiState.update { it.copy(blockedCleartextUrl = null) } }

    /** Turns the setting on and loads the URL the user already asked for. */
    /**
     * A cleartext decision the user just made, held until the store echoes it back.
     *
     * Persisting is a round trip: the write goes out on IO, and the settings flow can still deliver
     * the *old* value after the tap — which used to flip the state back to "not allowed" for as long
     * as it took the write to land, and (with a settings source that never changes, as in a unit
     * test) permanently. The local decision therefore wins until the store confirms it.
     */
    @Volatile private var pendingCleartextDecision: Boolean? = null

    fun allowCleartextAndRetry() {
        val target = _uiState.value.blockedCleartextUrl ?: return
        pendingCleartextDecision = true
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
        detectMediaInPage(pageUrl, html, tabId)
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
    fun detectMediaInPage(pageUrl: String, html: String?, tabId: String? = _uiState.value.currentTabId) {
        // Debounce: cancel previous detection if page re-loaded quickly
        detectJob?.cancel()
        detectJob = viewModelScope.launch(Dispatchers.IO) {
            delay(120)
            val candidates = runCatching { mediaDetector.detect(pageUrl, html) }.getOrNull().orEmpty()
            mergeCandidates(tabId, pageUrl, candidates)
        }
    }

    fun onMediaIntercepted(tabId: String?, candidate: MediaCandidate) {
        mergeCandidates(tabId, candidate.pageUrl, listOf(candidate))
    }

    fun onVisibleMedia(tabId: String, pageUrl: String, mediaUrl: String) {
        _uiState.update { state ->
            if (tabId != state.currentTabId || !isOnCurrentDocument(tabId, pageUrl)) return@update state
            // Only a previously admitted candidate may become the selected on-screen item.
            val selected = mediaUrl.takeIf { url -> state.detectedMedia.any { it.mediaUrl == url } }
            if (selected == state.visibleMediaUrl) state else state.copy(
                visibleMediaUrl = selected,
                detectedMedia = state.detectedMedia.sortedWith(
                    compareByDescending<MediaCandidate> { it.mediaUrl == selected }
                        .thenByDescending { it.confidence }
                )
            )
        }
    }

    fun onMediaExpired(tabId: String, pageUrl: String, mediaUrl: String) {
        _uiState.update { state ->
            if (tabId != state.currentTabId || !isOnCurrentDocument(tabId, pageUrl)) return@update state
            state.copy(detectedMedia = state.detectedMedia.filterNot { it.mediaUrl == mediaUrl },
                visibleMediaUrl = state.visibleMediaUrl.takeUnless { it == mediaUrl },
                qualitySheet = state.qualitySheet.takeUnless { it?.candidate?.mediaUrl == mediaUrl })
        }
    }

    /**
     * Whether [pageUrl] belongs to the document the current tab is showing.
     *
     * The page itself always counts. A frame counts when the store has seen that frame announce itself
     * in this document, which is how media inside an iframe stops being invisible without letting any
     * page claim another site's URL. On a WebView that cannot verify frames the answer is the old one:
     * only the page itself, and behaviour is exactly what it was before.
     */
    private fun isOnCurrentDocument(tabId: String?, pageUrl: String): Boolean {
        if (pageUrl == _uiState.value.url) return true
        val id = tabId ?: return false
        return webViewStore?.isDocumentUrl(id, pageUrl) == true
    }

    private fun mergeCandidates(tabId: String?, pageUrl: String, candidates: List<MediaCandidate>) {
        if (candidates.isEmpty()) return
        _uiState.update { state ->
            if (tabId != state.currentTabId || !isOnCurrentDocument(tabId, pageUrl)) return@update state
            val merged = MediaCandidateIndex.merge(state.detectedMedia, candidates, state.visibleMediaUrl)
            if (merged == state.detectedMedia) state else state.copy(detectedMedia = merged)
        }
    }

    fun openMediaTray() {
        if (_uiState.value.detectedMedia.isEmpty()) {
            _messages.trySend(context.getString(R.string.no_media_detected))
            return
        }
        _uiState.update { it.copy(isMediaTrayVisible = true) }
    }

    fun closeMediaTray() { _uiState.update { it.copy(isMediaTrayVisible = false) } }

    /** The result of one probe: the rows to show, and the honest reason when there are none. */
    private data class ProbedMedia(val info: MediaInfo, val failure: UnsupportedMedia?, val hasOptions: Boolean)

    /**
     * Opens the quality sheet for one tray row. The sheet is filled immediately from what the
     * page already told us and refines itself when the probe returns — the page is never blocked.
     * A stream no probe can help with (live, unproven blob) is still refused up front; a DASH
     * manifest is probed, and only becomes a refusal when the probe finds nothing it can segment.
     */
    fun openQualitySheetFor(candidate: MediaCandidate) {
        if (!presentResolvingSheet(candidate)) return
        viewModelScope.launch {
            val probed = probeMediaInfo(candidate)
            _uiState.update { state ->
                val current = state.qualitySheet ?: return@update state
                if (current.candidate.mediaUrl != candidate.mediaUrl) return@update state
                state.copy(
                    qualitySheet = current.copy(
                        mediaInfo = sheetMediaInfo(probed.info, probed.failure),
                        isResolving = false,
                        unsupported = probed.failure
                    )
                )
            }
            if (probed.failure != null) _messages.trySend(context.getString(messageFor(probed.failure)))
        }
    }

    /**
     * A tap on a tray row — or a pasted link — means "save this file", not "open a chooser".
     *
     * One probe decides between the two honest outcomes: a stream with exactly one selectable option
     * is queued straight away (one tap, which is the point of the tray), and the quality sheet is
     * only opened when there is a real choice to make — or when the file cannot be saved at all,
     * because the sheet is where that reason is explained. A blob candidate always goes through the
     * sheet: its bytes exist only inside the page's WebView, so reading it is a different action from
     * fetching a URL.
     */
    fun downloadOrOffer(candidate: MediaCandidate) {
        if (candidate.isBlobFile || unsupportedReasonFor(candidate) != null || isSightedOnAnotherPage(candidate)) {
            openQualitySheetFor(candidate)
            return
        }
        _uiState.update { it.copy(isMediaTrayVisible = false) }
        _messages.trySend(context.getString(R.string.download_preparing))
        viewModelScope.launch {
            val probed = probeMediaInfo(candidate)
            // Rows are compared by id *and* URL: several DASH renditions legitimately share one
            // manifest URL, so a URL-only comparison would hide a real choice.
            val options = probed.info.formats.ifEmpty { probed.info.combinedFormats }.distinctBy { it.id to it.url }
            val single = options.singleOrNull()?.takeIf { probed.failure == null }
            if (single == null) {
                presentResolvedSheet(candidate, probed)
                return@launch
            }
            // Read on this (main) thread: a WebView is main-thread-only, and enqueue() switches to IO.
            val sessionUserAgent = _uiState.value.currentTabId?.let { webViewStore?.userAgent(it) }
            enqueue(candidate, probed.info.title, single, sessionUserAgent)
        }
    }

    /**
     * Opens the sheet in its resolving state. Returns false when the sheet has already said all it
     * can: an unsupported stream (the sheet explains instead of offering), or a sighting on a page
     * the user has since left.
     */
    private fun presentResolvingSheet(candidate: MediaCandidate): Boolean {
        val unsupported = unsupportedReasonFor(candidate)
        // A stream no probe can help with is refused on its own terms, whatever page it came from:
        // its honest reason is more useful than a tray hint, and the tray would not offer it either.
        // The tray hint is for a candidate the sheet could really open — a sighting on a page the
        // user has since left, which is the shape where the sheet would show the wrong page's file.
        // A deliberate pasted file (`pageUrl == mediaUrl`) is not a sighting, so it is never held
        // back: it is a link the user handed over on purpose, not something a page exposed.
        if (unsupported == null && isSightedOnAnotherPage(candidate)) {
            _messages.trySend(context.getString(R.string.home_media_tray_only_hint))
            return false
        }
        val canRecord = deviceCanRecord()
        _uiState.update {
            it.copy(
                mseRecording = it.mseRecording.copy(isSupported = canRecord),
                isMediaTrayVisible = false,
                qualitySheet = sheetState(candidate, mediaInfoFrom(candidate), unsupported, isResolving = true, deviceCanRecord = canRecord)
            )
        }
        if (unsupported != null) {
            _messages.trySend(context.getString(messageFor(unsupported)))
            return false
        }
        return true
    }

    /** Opens the sheet with a probe that has already finished, so no URL is fetched twice. */
    private fun presentResolvedSheet(candidate: MediaCandidate, probed: ProbedMedia) {
        val canRecord = deviceCanRecord()
        _uiState.update {
            it.copy(
                mseRecording = it.mseRecording.copy(isSupported = canRecord),
                isMediaTrayVisible = false,
                qualitySheet = sheetState(candidate, probed.info, probed.failure, isResolving = false, deviceCanRecord = canRecord)
            )
        }
        if (probed.failure != null) _messages.trySend(context.getString(messageFor(probed.failure)))
    }

    private fun sheetState(
        candidate: MediaCandidate,
        info: MediaInfo,
        unsupported: UnsupportedMedia?,
        isResolving: Boolean,
        deviceCanRecord: Boolean
    ): QualitySheetState = QualitySheetState(
        candidate = candidate,
        mediaInfo = sheetMediaInfo(info, unsupported),
        isResolving = isResolving,
        unsupported = unsupported,
        canRecord = deviceCanRecord && !candidate.isLive
    )

    /** An unsupported sheet keeps a title and a poster but nothing selectable. */
    private fun sheetMediaInfo(info: MediaInfo, unsupported: UnsupportedMedia?): MediaInfo =
        if (unsupported == null) info else info.copy(
            formats = emptyList(), audioFormats = emptyList(),
            videoFormats = emptyList(), combinedFormats = emptyList()
        )

    /** Asked once per sheet, on the thread that owns the WebView. */
    private fun deviceCanRecord(): Boolean = webViewStore?.supportsMseRecording() == true

    private fun isSightedOnAnotherPage(candidate: MediaCandidate): Boolean =
        _uiState.value.url.isNotBlank() &&
            candidate.pageUrl != candidate.mediaUrl &&
            !isOnCurrentDocument(_uiState.value.currentTabId, candidate.pageUrl)

    /**
     * One probe of the candidate, shaped for both callers: the sheet shows the rows, and the
     * one-tap path counts them.
     */
    private suspend fun probeMediaInfo(candidate: MediaCandidate): ProbedMedia {
        // No explicit dispatcher: the resolver already switches to IO internally, and staying on the
        // view-model's own scope keeps the probe ordered behind the sheet state it updates.
        // Probe as the page: the sheet is built from what this call returns, and a bare probe
        // against a session-guarded CDN answered nothing at all while the media played fine.
        val probeUserAgent = _uiState.value.currentTabId?.let { webViewStore?.userAgent(it) }
        val resolved = runCatching {
            mediaResolver.resolve(candidate.mediaUrl, candidate.pageUrl.takeIf { it.isNotBlank() }, probeUserAgent)
        }.getOrNull()
        val synthesized = mediaInfoFrom(candidate)
        // When the probe described the stream, its rows are the truth. Several DASH renditions
        // share one manifest URL, so they must not be de-duplicated by URL — the fallback is
        // only used when the probe found nothing to segment.
        val hasResolvedFormats = resolved != null && (resolved.combinedFormats.isNotEmpty() || resolved.audioFormats.isNotEmpty())
        val merged = if (hasResolvedFormats) {
            val info = resolved!!
            info.copy(
                title = info.title.ifBlank { synthesized.title },
                thumbnail = info.thumbnail ?: synthesized.thumbnail,
                formats = info.formats.ifEmpty { info.combinedFormats }
            )
        } else {
            synthesized
        }
        val failure = if (!hasResolvedFormats && isDashCandidate(candidate)) UnsupportedMedia.DASH else null
        return ProbedMedia(merged, failure, hasResolvedFormats)
    }

    fun closeQualitySheet() { _uiState.update { it.copy(qualitySheet = null) } }

    /**
     * Null when the candidate can be downloaded; otherwise the honest reason it cannot.
     *
     * DASH is deliberately allowed through here: a clear, static, single-period manifest is now
     * segmented by the app itself, and the resolver is the only layer that can tell that apart from
     * a dynamic, multi-period or DRM one. When resolution comes back empty, the sheet gets the DASH
     * refusal instead — see [openQualitySheetFor].
     */
    fun unsupportedReasonFor(candidate: MediaCandidate): UnsupportedMedia? =
        MediaOfferability.unsupportedReason(candidate.mediaUrl, candidate.mimeType, candidate.extension,
            candidate.isLive, candidate.isMediaSource, candidate.isBlobFile, dashIsSupported = true)

    private fun isDashCandidate(candidate: MediaCandidate): Boolean =
        MediaOfferability.isDash(candidate.mediaUrl, candidate.mimeType, candidate.extension)

    private fun messageFor(reason: UnsupportedMedia): Int = when (reason) {
        UnsupportedMedia.DASH -> R.string.error_dash_unsupported
        UnsupportedMedia.LIVE -> R.string.error_live_unsupported
        UnsupportedMedia.BLOB_STREAM -> R.string.error_blob_stream_unsupported
    }

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

    // region playback recording
    /**
     * Records what the page is playing, because the user asked for exactly that.
     *
     * This is not a download and the sheet says so: what lands on disk is the part of the stream the
     * session actually played, at the quality the player chose. Nothing about the page's own traffic
     * changes, and nothing that is protected (DRM) is kept — the page's hook refuses those and this
     * class turns the refusal into an explanation.
     *
     * Arm order matters: the view model records *why* it is recording (the tab, the page, the title)
     * before the page is asked to start, because the first segment can arrive before the JS call
     * returns.
     */
    fun recordPlayback() {
        val sheet = _uiState.value.qualitySheet ?: return
        val tabId = _uiState.value.currentTabId ?: return
        val store = webViewStore
        if (store == null || !store.supportsMseRecording()) {
            _uiState.update { it.copy(mseRecording = it.mseRecording.copy(isSupported = false)) }
            _messages.trySend(context.getString(R.string.error_mse_unsupported))
            return
        }
        if (_uiState.value.mseRecording.isRecording) return
        val info = sheet.mediaInfo
        val pageUrl = info.webpageUrl.takeIf { it.isNotBlank() }
            ?: sheet.candidate.pageUrl.takeIf { it.isNotBlank() }
        // Which stream is kept, not which one is played: an audio-only offer records audio, anything
        // else records video with its audio track inside the same file.
        val kind = if (info.videoFormats.isEmpty() && info.audioFormats.isNotEmpty()) "audio" else "video"
        val title = info.title.takeIf { it.isNotBlank() }
            ?: DownloadManager.nameFromUrl(sheet.candidate.mediaUrl).substringBeforeLast('.', "").ifBlank { "recording" }
        // The frame allowed to feed this recording is the page itself, and the page is the main frame
        // the sheet was opened on. A player inside a cross-origin frame is out of reach for the same
        // reason `evaluateJavascript` cannot enter one; the README says so plainly.
        mseRecording.arm(
            tabId = tabId,
            kind = kind,
            pageUrl = pageUrl,
            frameOrigin = pageUrl,
            title = title
        )
        store.armMseRecording(tabId, kind) { armed ->
            if (!armed) {
                // The page has no recording hook: an old WebView, or a player the hook cannot see.
                // A recording that writes nothing must not look like it started.
                mseRecording.disarm(tabId)
                mseRecording.abandon(tabId)
                _messages.trySend(context.getString(R.string.error_mse_unsupported))
            }
        }
    }

    /** Stops the recording the user started and files whatever was captured. */
    fun stopMseRecording() {
        val tabId = mseRecording.active.value?.tabId ?: return
        webViewStore?.disarmMseRecording(tabId)
        val finished = mseRecording.finish(tabId)
        if (finished == null) {
            _messages.trySend(context.getString(R.string.error_mse_empty))
            return
        }
        saveRecording(finished, capped = finished.recording.capped)
    }

    /**
     * A page says it is about to feed the recording armed for its tab.
     *
     * Nothing here trusts the caller: [MseRecordingController.onOpen] decides, and its answer is what
     * this turns into a message. A refusal is silent on purpose — a page that posts without being
     * asked is not the user's business.
     */
    fun onMseOpen(tabId: String, mimeType: String, sourceOrigin: String?, @Suppress("UNUSED_PARAMETER") isMainFrame: Boolean) {
        when (mseRecording.onOpen(tabId, mimeType, sourceOrigin)) {
            MseRecordingController.Open.RECORDING -> {
                // Confirmed once, when the first bytes are about to arrive, not when the button was
                // pressed: the difference is the whole point of the recording.
                if (_uiState.value.mseRecording.bytes == 0L && tabId == _uiState.value.currentTabId) {
                    _messages.trySend(context.getString(R.string.recording_started))
                }
            }
            MseRecordingController.Open.NO_ROOM -> {
                webViewStore?.disarmMseRecording(tabId)
                mseRecording.abandon(tabId)
                _messages.trySend(context.getString(R.string.error_mse_no_space))
            }
            MseRecordingController.Open.NOT_ARMED, MseRecordingController.Open.DENIED_FRAME -> Unit
        }
    }

    /** One segment, exactly as the page appended it. */
    fun onMseSegment(tabId: String, bytes: ByteArray, sourceOrigin: String?, @Suppress("UNUSED_PARAMETER") isMainFrame: Boolean) {
        when (mseRecording.onSegment(tabId, bytes, sourceOrigin)) {
            MseRecorder.Append.CONTINUED, MseRecorder.Append.IGNORED -> Unit
            MseRecorder.Append.STOPPED_DISK -> {
                mseRecording.abandon(tabId)
                _messages.trySend(context.getString(R.string.error_mse_no_space))
            }
            MseRecorder.Append.STOPPED_ENCRYPTED -> {
                // The first segment's head carried a protection payload. Nothing decodable was kept,
                // and the recording ends here rather than growing into a file nothing can open.
                mseRecording.abandon(tabId)
                _messages.trySend(context.getString(R.string.error_mse_drm))
            }
            MseRecorder.Append.STOPPED_CAP -> finishRecording(tabId, capped = true)
        }
    }

    /** The page hit the recording ceiling it was given: what it sent is all there is. */
    fun onMseCap(tabId: String) = finishRecording(tabId, capped = true)

    /** The stream is protected. Nothing was kept, and saying so is the honest answer. */
    fun onMseEncrypted(tabId: String) {
        val had = mseRecording.active.value?.tabId == tabId
        mseRecording.abandon(tabId)
        if (had && tabId == _uiState.value.currentTabId) {
            _messages.trySend(context.getString(R.string.error_mse_drm))
        }
    }

    private fun finishRecording(tabId: String, capped: Boolean) {
        webViewStore?.disarmMseRecording(tabId)
        val finished = mseRecording.finish(tabId)
        if (finished == null) {
            if (tabId == _uiState.value.currentTabId) _messages.trySend(context.getString(R.string.error_mse_empty))
            return
        }
        saveRecording(finished, capped)
    }

    /**
     * Moves a finished recording out of the cache and into the download list.
     *
     * It becomes a completed download row, like a captured blob: the bytes are already on disk, so
     * there is nothing to transfer, and the row is what makes the file visible in the app, shareable
     * and findable in the system gallery.
     */
    private fun saveRecording(finished: MseRecordingController.Finished, capped: Boolean) {
        val recording = finished.recording
        val session = finished.session
        viewModelScope.launch(Dispatchers.IO) {
            val extension = MseRecorder.containerExtensionFor(recording.mimeType)
            val title = session.title?.takeIf { it.isNotBlank() } ?: "recording"
            val filename = sanitizeFilename(DownloadManager.generateFilename(title, extension))
            val destination = runCatching { downloadRepository.getDefaultDownloadDestination() }.getOrNull()?.takeIf { it.isNotBlank() }
                ?: context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)?.absolutePath ?: context.filesDir.absolutePath
            val destDir = File(destination).also { if (!it.exists()) it.mkdirs() }
            val target = DownloadManager.nonConflicting(File(destDir, filename))
            val moved = runCatching {
                recording.file.copyTo(target, overwrite = false)
                recording.file.delete()
                true
            }.getOrDefault(false)
            if (!moved || !target.exists() || target.length() == 0L) {
                _messages.trySend(context.getString(R.string.error_disk_full))
                return@launch
            }
            val entity = DownloadEntity(
                url = session.pageUrl ?: target.name,
                finalUrl = target.absolutePath,
                filename = target.name,
                mimeType = recording.mimeType.ifBlank { "video/mp4" },
                destination = destination,
                totalBytes = target.length(),
                downloadedBytes = target.length(),
                status = DownloadState.COMPLETED,
                mediaTitle = title,
                selectedQuality = extension.uppercase(),
                finalPath = target.absolutePath,
                pageUrl = session.pageUrl
            )
            downloadRepository.addDownload(entity)
            runCatching { MediaScannerConnection.scanFile(context, arrayOf(target.absolutePath), null, null) }
            _messages.trySend(
                context.getString(if (capped) R.string.recording_saved_capped else R.string.recording_saved)
            )
        }
    }
    // endregion

    // region blob handling — called from BlobDownloadBridge on the JS thread, posts to IO dispatcher
    fun onBlobCaptured(tabId: String, pageUrl: String, blobUrl: String, stagedFile: File, mimeType: String) {
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
                selectedQuality = ext.uppercase(), finalPath = finalFile.absolutePath,
                // Local file, so no request is made — the page is kept for provenance and for a later
                // re-fetch of the same media.
                pageUrl = pageUrl.takeIf { it.isNotBlank() }
            )
            downloadRepository.addDownload(entity)
            runCatching { MediaScannerConnection.scanFile(context, arrayOf(finalFile.absolutePath), null, null) }
            _uiState.update { it.copy(qualitySheet = null) }
            _messages.trySend(context.getString(R.string.download_completed))
        }
    }

    fun handleFormatSelected(format: MediaFormat) {
        val sheet = _uiState.value.qualitySheet ?: return
        val unsupported = sheet.unsupported ?: unsupportedReasonFor(sheet.candidate)
        if (unsupported != null) {
            _messages.trySend(context.getString(messageFor(unsupported)))
            return
        }
        if (format.url.startsWith("blob:") &&
            (format.url != sheet.candidate.mediaUrl || !sheet.candidate.isBlobFile)) return
        if (format.url.startsWith("blob:")) {
            val pageUrl = _uiState.value.url
            _commands.trySend(BrowserCommand.FetchBlob(format.url, pageUrl))
            return
        }
        enqueueDownload(format)
    }

    // region live web views
    private var webViewStore: TabWebViewStore? = null

    /**
     * The one store that owns the live WebViews, held here rather than by the composition.
     *
     * It used to be a `remember` in `BrowserScreen` with an `onDispose { destroyAll() }`, so leaving
     * the browser for Downloads, Settings, Bookmarks, History or the player destroyed every WebView,
     * and coming back reloaded the page from the network — losing scroll position, half-typed forms
     * and the back/forward stack. This view model lives as long as the browser's back-stack entry, so
     * the tabs now survive both in-app navigation and rotation; [onCleared] is the single place that
     * releases them.
     */
    fun webViewStore(context: Context, callbacks: TabWebViewStore.Callbacks): TabWebViewStore =
        webViewStore ?: TabWebViewStore(context, callbacks).also { webViewStore = it }

    override fun onCleared() {
        // A recording cannot outlive the process that would file it: the file is deleted rather than
        // left in the cache as an orphan nothing points at.
        runCatching { mseRecording.abandonAll() }
        webViewStore?.destroyAll()
        webViewStore = null
        super.onCleared()
    }

    /**
     * The tab's renderer process crashed. Nothing but the message is this class's job: the store has
     * already dropped (and destroyed) that WebView, and the epoch makes the screen re-attach the tab,
     * which loads the URL the tab still holds.
     */
    fun onRendererGone(tabId: String) {
        _uiState.update { it.copy(rendererEpoch = it.rendererEpoch + 1, isLoading = false) }
        if (tabId == _uiState.value.currentTabId) _messages.trySend(context.getString(R.string.renderer_gone_reloading))
    }

    /** A main-frame load failed; the tab is told why instead of just sitting on an empty page. */
    fun onPageError(tabId: String, kind: PageErrorKind, description: String) {
        if (tabId != _uiState.value.currentTabId) return
        _uiState.update { it.copy(isLoading = false) }
        _messages.trySend(
            when (kind) {
                PageErrorKind.NETWORK -> context.getString(R.string.error_page_load_failed)
                PageErrorKind.HTTP -> context.getString(R.string.error_page_http, description)
                PageErrorKind.CERTIFICATE -> context.getString(R.string.error_page_certificate)
            }
        )
    }

    /**
     * A file the page offered that is not media: a PDF, an archive, an installer.
     *
     * It joins the same Room queue as everything else, so pause, resume, retry and the notification
     * come for free, instead of the click doing nothing at all — which is what a browser with no
     * download listener does.
     */
    fun onDownloadRequested(
        tabId: String,
        url: String,
        userAgent: String?,
        contentDisposition: String?,
        mimeType: String?,
        contentLength: Long
    ) {
        if (!url.startsWith("http://", true) && !url.startsWith("https://", true)) return
        // Read on the caller's thread, which for a download listener is the main one: the page the
        // request came from is the Referer the server will expect, and the WebView's own user agent is
        // the client it already answered. Both are stored with the row so a later resume still has
        // them. A storage row that cannot name its session is the reason so many downloads 403.
        val pageUrl = webViewStore?.currentUrl(tabId)?.takeIf { it.isNotBlank() }
            ?: _uiState.value.tabs.firstOrNull { it.id == tabId }?.url?.takeIf { it.isNotBlank() }
        val sessionUserAgent = userAgent?.takeIf { it.isNotBlank() } ?: webViewStore?.userAgent(tabId)
        viewModelScope.launch(Dispatchers.IO) {
            val fromHeader = filenameFromContentDisposition(contentDisposition)
            val extension = fromHeader?.substringAfterLast('.', "")?.takeIf { it.isNotBlank() }
                ?: DownloadManager.extensionFromUrl(url)
            val title = fromHeader?.substringBeforeLast('.')
                ?.takeIf { it.isNotBlank() && it != fromHeader }
                ?: DownloadManager.nameFromUrl(url).substringBeforeLast('.', "").takeIf { it.isNotBlank() }
            // A non-Latin name sanitises down to separators — "ویدیوی آموزشی.mp4" becomes ".mp4",
            // a hidden file with no base. Such a name falls back to the URL-derived one instead.
            val fromHeaderSafe = sanitizeFilename(fromHeader.orEmpty())
                .takeIf { it.isNotBlank() && it.substringBefore('.').isNotBlank() }
            val filename = fromHeaderSafe ?: DownloadManager.generateFilename(title, extension)
            val destination = runCatching { downloadRepository.getDefaultDownloadDestination() }.getOrNull()?.takeIf { it.isNotBlank() }
                ?: context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)?.absolutePath
                ?: context.filesDir.absolutePath
            val resolvedMime = mimeType?.takeIf { it.isNotBlank() && it != "application/octet-stream" }
                ?: extension?.let { getMimeTypeFromExtension(it) }
            val entity = downloadRepository.createDownloadEntity(
                url = url,
                filename = filename,
                destination = destination,
                mediaTitle = title,
                mimeType = resolvedMime,
                totalBytes = contentLength.takeIf { it > 0L },
                pageUrl = pageUrl,
                userAgent = sessionUserAgent
            )
            downloadRepository.addDownload(entity)
            DownloadService.enqueueDownload(context, entity.id)
            _messages.trySend(context.getString(R.string.download_enqueued))
        }
    }
    // endregion

    // region downloads
    fun enqueueDownload(format: MediaFormat) {
        val sheet = _uiState.value.qualitySheet ?: return
        val candidate = sheet.candidate
        // Defence in depth: an unsupported stream must never reach the queue, even if a future
        // caller forgets to check the sheet state first.
        val unsupported = sheet.unsupported ?: unsupportedReasonFor(candidate)
        if (unsupported != null) {
            _messages.trySend(context.getString(messageFor(unsupported)))
            return
        }
        // Read on this (main) thread: a WebView is main-thread-only, and the coroutine below runs on IO.
        val sessionUserAgent = _uiState.value.currentTabId?.let { webViewStore?.userAgent(it) }
        viewModelScope.launch(Dispatchers.IO) {
            enqueue(candidate, sheet.mediaInfo.title, format, sessionUserAgent)
        }
    }

    /**
     * The one place a download row is created and handed to the service.
     *
     * Blob reads must stay inside the owning WebView and only follow an explicit selection, so a
     * `blob:` URL can never become a row through here — the format selection path routes it to the
     * page's own reader instead.
     */
    private suspend fun enqueue(candidate: MediaCandidate, mediaTitle: String, format: MediaFormat, sessionUserAgent: String?) {
        if (format.url.startsWith("blob:", ignoreCase = true)) return
        // Defence in depth for the one-tap path, which reaches here without a sheet to check first.
        val unsupported = unsupportedReasonFor(candidate)
        if (unsupported != null) {
            _messages.trySend(context.getString(messageFor(unsupported)))
            return
        }
        withContext(Dispatchers.IO) {
            val title = mediaTitle.takeIf { it.isNotBlank() } ?: candidate.pageUrl
            val extension = format.extension.takeIf { it.isNotBlank() } ?: com.salvia.salviabrowxer.media.downloader.DownloadManager.extensionFromUrl(format.url)
            val filename = sanitizeFilename(com.salvia.salviabrowxer.media.downloader.DownloadManager.generateFilename(title, extension))
            val destination = runCatching { downloadRepository.getDefaultDownloadDestination() }.getOrNull()?.takeIf { it.isNotBlank() }
                ?: context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)?.absolutePath ?: context.filesDir.absolutePath
            // The candidate remembers the page it was found on; the tab supplies the client. Both go
            // into the row so the transfer can present the same session the page did.
            val entity = downloadRepository.createDownloadEntity(
                url = format.url,
                filename = filename,
                destination = destination,
                mediaTitle = title,
                thumbnail = candidate.thumbnailUrl,
                selectedQuality = format.format,
                mimeType = format.mimeType,
                totalBytes = format.size?.takeIf { it > 0L },
                renditionId = format.id.takeIf { format.isDash },
                // A pasted direct link reports itself as its own "page"; a request must never refer
                // to itself, so such a row carries no page at all.
                pageUrl = candidate.pageUrl.takeIf { it.isNotBlank() && it != format.url },
                userAgent = sessionUserAgent
            ).copy(status = DownloadState.QUEUED)
            downloadRepository.addDownload(entity)
            DownloadService.enqueueDownload(context, entity.id)
            _uiState.update { it.copy(qualitySheet = null) }
            _messages.trySend(context.getString(R.string.download_enqueued))
        }
    }

    fun clearDetectedMedia() { _uiState.update { it.copy(detectedMedia = emptyList(), visibleMediaUrl = null, qualitySheet = null) } }
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

    /**
     * A link the user pasted on the home screen.
     *
     * A URL that already names its own container goes straight to the quality sheet and is also
     * added to the tray, so closing the sheet does not lose the file. Anything else is a page, and
     * the app deliberately has no site-specific extractors, so the page has to be loaded for the
     * media tray to find what it exposes. This is the single place that tells the two apart.
     */
    fun openPastedLink(url: String) {
        val candidate = PastedLink.mediaCandidate(url)
        if (candidate == null) {
            navigate(url)
            return
        }
        // A deliberate pasted file is not a WebView sighting; it need not match the page URL.
        _uiState.update { state -> state.copy(detectedMedia =
            MediaCandidateIndex.merge(state.detectedMedia, listOf(candidate), state.visibleMediaUrl)) }
        // Same rule as a tray tap: a link that names one file is one tap, and the sheet appears only
        // when the probe finds a real choice to make.
        downloadOrOffer(candidate)
    }

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

    companion object {
        private const val TAG = "BrowserViewModel"

        /** Long enough that opening a tab and typing in it are one write, short enough to survive a kill. */
        private const val SESSION_PERSIST_SETTLE_MILLIS = 900L
    }
}
