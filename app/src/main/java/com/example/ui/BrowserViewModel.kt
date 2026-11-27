package com.example.ui

import android.app.ActivityManager
import android.app.Application
import android.content.ComponentCallbacks2
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AdBlockRuleEntity
import com.example.data.local.AppLanguage
import com.example.data.local.BookmarkEntity
import com.example.data.local.BrowserDatabase
import com.example.data.local.BrowserRepository
import com.example.data.local.BrowserSettings
import com.example.data.local.DownloadEntity
import com.example.data.local.ExtensionEntity
import com.example.data.local.HistoryEntity
import com.example.data.local.PreferencesRepository
import com.example.data.local.SearchEngine
import com.example.data.local.ThemePreference
import com.example.engine.AdBlockerEngine
import com.example.engine.BlockCategory
import com.example.engine.BlockDecision
import com.example.engine.ExtensionEngine
import com.example.engine.DownloadCoordinator
import com.example.engine.DownloadRuntime
import com.example.engine.MediaSnifferEngine
import com.example.engine.UrlInputNormalizer
import com.example.engine.WebViewConfigurator
import com.example.engine.MediaRequestHeaderPolicy
import com.example.engine.MediaRequestHeaderStore
import com.example.engine.SitePermissionStore
import com.example.engine.MseNetworkAttributionPolicy
import com.example.ui.i18n.AppStrings
import com.example.ui.i18n.stringsFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.ArrayDeque
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class BrowserViewModel(application: Application) : AndroidViewModel(application) {

    private val db = BrowserDatabase.getInstance(application)
    private val repository = BrowserRepository(db)
    private val prefsRepository = PreferencesRepository(application)
    private val mediaSniffer = MediaSnifferEngine()
    private val mediaRequestHeaderStore = MediaRequestHeaderStore()
    private val downloadCoordinator = DownloadCoordinator(application)

    private val webViews = ConcurrentHashMap<String, WebView>()
    private val savedTabStates = ConcurrentHashMap<String, Bundle>()
    private val tabMruOrder = ArrayDeque<String>()
    private val pendingProbedUrls = ConcurrentHashMap.newKeySet<String>()
    private val networkBuffers = ConcurrentHashMap<String, ArrayDeque<NetworkRequestEntry>>()
    private val consoleBuffers = ConcurrentHashMap<String, ArrayDeque<ConsoleLogEntry>>()
    private val pendingAdsDelta = java.util.concurrent.atomic.AtomicLong(0L)
    private val pendingTrackersDelta = java.util.concurrent.atomic.AtomicLong(0L)
    private var statsFlushJob: kotlinx.coroutines.Job? = null
    @Volatile
    private var lastDevToolsSyncMs: Long = 0L
    private var devToolsTrailingSyncJob: kotlinx.coroutines.Job? = null
    private var incognitoSessionGeneration: Long = 0L
    private var incognitoCleanupJob: kotlinx.coroutines.Job? = null

    private val maxLiveWebViews: Int = runCatching {
        val am = application.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        if (am?.isLowRamDevice == true) 2 else 3
    }.getOrDefault(3)

    private val initialTab = BrowserTab(title = "Start Page", isStartPage = true)
    private val _tabs = MutableStateFlow(listOf(initialTab))
    val tabs: StateFlow<List<BrowserTab>> = _tabs.asStateFlow()

    private val _activeTabId = MutableStateFlow(initialTab.id)
    val activeTabId: StateFlow<String> = _activeTabId.asStateFlow()

    private val _activeSheet = MutableStateFlow(ActiveSheet.NONE)
    val activeSheet: StateFlow<ActiveSheet> = _activeSheet.asStateFlow()

    private val _devToolsTab = MutableStateFlow(DevToolsTab.CONSOLE)
    val devToolsTab: StateFlow<DevToolsTab> = _devToolsTab.asStateFlow()

    private val _statusToast = MutableStateFlow<String?>(null)
    val statusToast: StateFlow<String?> = _statusToast.asStateFlow()

    private val _findInPageQuery = MutableStateFlow("")
    val findInPageQuery: StateFlow<String> = _findInPageQuery.asStateFlow()

    val settings: StateFlow<BrowserSettings> = prefsRepository.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, BrowserSettings())

    val bookmarks: StateFlow<List<BookmarkEntity>> = repository.bookmarks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val history: StateFlow<List<HistoryEntity>> = repository.history
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val extensions: StateFlow<List<ExtensionEntity>> = repository.extensions
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val downloads: StateFlow<List<DownloadEntity>> = repository.downloads
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val customAdRules: StateFlow<List<AdBlockRuleEntity>> = repository.customAdRules
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        // Clear leftovers from a previous process before any private WebView is created.
        if (WebViewConfigurator.supportsIsolatedIncognitoProfiles()) {
            WebViewConfigurator.clearIncognitoAndVolatileData(application, clearCookiesToo = false)
        }
        viewModelScope.launch(Dispatchers.IO) {
            downloadCoordinator.clearIncognitoDownloads()
            repository.ensureSeeded()
        }
    }

    private fun s(): AppStrings = stringsFor(settings.value.appLanguage)

    private fun cookieHeaderForTab(tab: BrowserTab, url: String): String {
        val tabWebView = webViews[tab.id]
        // A missing/hibernated private WebView must never fall back to normal-profile cookies.
        if (tab.isIncognito && tabWebView == null) return ""
        return WebViewConfigurator.cookieHeader(tabWebView, url, tab.isIncognito)
    }

    private fun cookieManagerForTab(tab: BrowserTab): CookieManager? =
        WebViewConfigurator.cookieManagerForTab(webViews[tab.id], tab.isIncognito)

    fun getCurrentTab(): BrowserTab =
        _tabs.value.firstOrNull { it.id == _activeTabId.value } ?: _tabs.value.first()

    fun registerWebView(tabId: String, webView: WebView) {
        webViews[tabId] = webView
        touchTabMru(tabId)
        syncWebViewLifecycleAndMemory(activeId = _activeTabId.value)
    }

    fun getWebView(tabId: String): WebView? = webViews[tabId]

    fun onIncognitoIsolationUnavailable() {
        _statusToast.value = getApplication<Application>().getString(
            com.example.R.string.incognito_isolation_unavailable,
        )
    }

    fun onPopupBlocked() {
        _statusToast.value = "Popup blocked: a direct user gesture is required."
    }

    fun consumeSavedTabState(tabId: String): Bundle? {
        val state = savedTabStates.remove(tabId)
        updateTab(tabId) { it.copy(isHibernated = false) }
        return state
    }

    fun onTrimMemory(level: Int) {
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW || level == ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            val activeId = _activeTabId.value
            val backgroundIds = webViews.keys.filter { it != activeId }
            backgroundIds.forEach { hibernateWebView(it) }
            if (pendingProbedUrls.size > 150) {
                pendingProbedUrls.clear()
            }
        }
    }

    private fun touchTabMru(tabId: String) {
        synchronized(tabMruOrder) {
            tabMruOrder.remove(tabId)
            tabMruOrder.addFirst(tabId)
        }
    }

    private fun syncWebViewLifecycleAndMemory(activeId: String) {
        webViews.forEach { (id, wv) ->
            if (id == activeId) {
                runCatching { wv.onResume() }
            } else {
                runCatching { wv.onPause() }
            }
        }
        val evictIds = synchronized(tabMruOrder) {
            val liveOrdered = tabMruOrder.filter { webViews.containsKey(it) && it != activeId }
            val allowedBackground = (maxLiveWebViews - 1).coerceAtLeast(1)
            if (liveOrdered.size > allowedBackground) {
                liveOrdered.drop(allowedBackground)
            } else {
                emptyList()
            }
        }
        evictIds.forEach { hibernateWebView(it) }
    }

    private fun hibernateWebView(tabId: String) {
        val wv = webViews.remove(tabId) ?: return
        runCatching {
            val bundle = Bundle()
            if (wv.saveState(bundle) != null) {
                savedTabStates[tabId] = bundle
            }
            wv.onPause()
            wv.stopLoading()
            wv.removeAllViews()
            wv.destroy()
        }
        updateTab(tabId) { it.copy(isHibernated = true, isLoading = false) }
    }

    fun openSheet(sheet: ActiveSheet) {
        _activeSheet.value = sheet
        if (sheet == ActiveSheet.DEVTOOLS) {
            syncDevToolsBuffers(_activeTabId.value)
            refreshDomInspection()
        }
    }

    fun closeSheet() {
        if (_activeSheet.value == ActiveSheet.FIND_IN_PAGE) {
            webViews[_activeTabId.value]?.clearMatches()
            _findInPageQuery.value = ""
        }
        _activeSheet.value = ActiveSheet.NONE
    }

    fun setDevToolsTab(tab: DevToolsTab) {
        _devToolsTab.value = tab
        syncDevToolsBuffers(_activeTabId.value)
        if (tab == DevToolsTab.DOM || tab == DevToolsTab.STORAGE) {
            refreshDomInspection()
        }
    }

    fun clearToast() {
        _statusToast.value = null
    }

    fun showToast(message: String) {
        _statusToast.value = message
    }

    // --- Tab Management ---

    fun createNewTab(isIncognito: Boolean = false, initialUrl: String? = null) {
        if (isIncognito) {
            incognitoSessionGeneration += 1L
            incognitoCleanupJob?.cancel()
            incognitoCleanupJob = null
        }
        val resolved = initialUrl?.let { normalizeInputToUrl(it) }
        val strings = s()
        val newTab = BrowserTab(
            title = if (isIncognito) strings.incognitoTabTitle else strings.newTabTitle,
            url = resolved.orEmpty(),
            isStartPage = resolved.isNullOrBlank(),
            isIncognito = isIncognito,
        )
        _tabs.update { it + newTab }
        _activeTabId.value = newTab.id
        _activeSheet.value = ActiveSheet.NONE
        touchTabMru(newTab.id)
        syncWebViewLifecycleAndMemory(newTab.id)
    }

    fun selectTab(tabId: String) {
        if (_tabs.value.any { it.id == tabId }) {
            _activeTabId.value = tabId
            _activeSheet.value = ActiveSheet.NONE
            touchTabMru(tabId)
            syncWebViewLifecycleAndMemory(tabId)
        }
    }

    fun closeTab(tabId: String) {
        val tabToClose = _tabs.value.firstOrNull { it.id == tabId } ?: return
        destroyWebView(tabId)
        savedTabStates.remove(tabId)
        networkBuffers.remove(tabId)
        consoleBuffers.remove(tabId)
        mediaRequestHeaderStore.clearTab(tabId)
        synchronized(tabMruOrder) { tabMruOrder.remove(tabId) }
        pendingProbedUrls.removeIf { it.startsWith("$tabId|") }

        val remaining = _tabs.value.filterNot { it.id == tabId }
        if (remaining.isEmpty()) {
            val fresh = BrowserTab(title = s().startPageTitle, isStartPage = true, isIncognito = false)
            _tabs.value = listOf(fresh)
            _activeTabId.value = fresh.id
            touchTabMru(fresh.id)
        } else {
            _tabs.value = remaining
            if (_activeTabId.value == tabId) {
                _activeTabId.value = remaining.last().id
            }
            touchTabMru(_activeTabId.value)
            syncWebViewLifecycleAndMemory(_activeTabId.value)
        }

    }

    fun closeAllIncognitoTabs() {
        val incognitoIds = _tabs.value.filter { it.isIncognito }.map { it.id }
        incognitoIds.forEach { id ->
            destroyWebView(id)
            savedTabStates.remove(id)
            networkBuffers.remove(id)
            consoleBuffers.remove(id)
            mediaRequestHeaderStore.clearTab(id)
            synchronized(tabMruOrder) { tabMruOrder.remove(id) }
            pendingProbedUrls.removeIf { it.startsWith("$id|") }
        }
        val normalTabs = _tabs.value.filterNot { it.isIncognito }
        if (normalTabs.isEmpty()) {
            val fresh = BrowserTab(title = s().startPageTitle, isStartPage = true, isIncognito = false)
            _tabs.value = listOf(fresh)
            _activeTabId.value = fresh.id
            touchTabMru(fresh.id)
        } else {
            _tabs.value = normalTabs
            if (_tabs.value.none { it.id == _activeTabId.value }) {
                _activeTabId.value = normalTabs.last().id
            }
            touchTabMru(_activeTabId.value)
            syncWebViewLifecycleAndMemory(_activeTabId.value)
        }
    }

    /**
     * Called after Compose has disposed the final private WebView and any popup
     * WebView associated with it. Cleanup is profile-scoped and never touches
     * the default browsing profile.
     */
    fun onIncognitoSessionClosed() {
        val requestedGeneration = incognitoSessionGeneration
        incognitoCleanupJob?.cancel()
        incognitoCleanupJob = viewModelScope.launch {
            // A short grace period prevents a close/open gesture from deleting the new session.
            delay(750L)
            if (requestedGeneration != incognitoSessionGeneration || _tabs.value.any { it.isIncognito }) {
                return@launch
            }

            // Stop private transfers and remove their Room metadata before deleting the profile.
            withContext(Dispatchers.IO) {
                downloadCoordinator.clearIncognitoDownloads()
            }

            // ProfileStore is a UI-thread API. Revalidate after returning to Main before deletion.
            val profileCleanupSucceeded = withContext(Dispatchers.Main) {
                if (requestedGeneration != incognitoSessionGeneration || _tabs.value.any { it.isIncognito }) {
                    return@withContext null
                }
                WebViewConfigurator.clearIncognitoAndVolatileData(
                    getApplication(),
                    clearCookiesToo = false,
                )
            } ?: return@launch

            val cleanupMessage = when {
                profileCleanupSucceeded -> com.example.R.string.incognito_profile_cleared
                !WebViewConfigurator.supportsIsolatedIncognitoProfiles() -> com.example.R.string.incognito_isolation_unavailable
                else -> com.example.R.string.incognito_cleanup_failed
            }
            _statusToast.value = getApplication<Application>().getString(cleanupMessage)
        }
    }

    private fun destroyWebView(tabId: String) {
        webViews.remove(tabId)?.apply {
            onPause()
            stopLoading()
            loadUrl("about:blank")
            clearHistory()
            removeAllViews()
            destroy()
        }
    }

    // --- Navigation ---

    fun navigateActiveTab(rawInput: String) {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return
        val targetUrl = normalizeInputToUrl(trimmed)
        val tabId = _activeTabId.value
        updateTab(tabId) {
            it.copy(
                url = targetUrl,
                isStartPage = false,
                isLoading = true,
                progress = 10,
                detectedMedia = emptyList(),
            )
        }
        webViews[tabId]?.loadUrl(targetUrl)
    }

    fun goHomeActiveTab() {
        val tabId = _activeTabId.value
        webViews[tabId]?.stopLoading()
        val strings = s()
        updateTab(tabId) {
            it.copy(
                isStartPage = true,
                url = "",
                title = if (it.isIncognito) strings.incognitoStartTitle else strings.startPageTitle,
                isLoading = false,
                progress = 0,
                detectedMedia = emptyList(),
            )
        }
    }

    fun goBack(): Boolean {
        val tab = getCurrentTab()
        val wv = webViews[tab.id]
        if (!tab.isStartPage && wv != null && wv.canGoBack()) {
            wv.goBack()
            return true
        }
        if (!tab.isStartPage) {
            goHomeActiveTab()
            return true
        }
        return false
    }

    fun goForward() {
        val tab = getCurrentTab()
        val wv = webViews[tab.id]
        if (tab.isStartPage && wv != null && wv.url != null && wv.url != "about:blank") {
            updateTab(tab.id) { it.copy(isStartPage = false, url = wv.url.orEmpty()) }
            return
        }
        if (wv != null && wv.canGoForward()) {
            wv.goForward()
        }
    }

    fun reloadOrStop() {
        val tab = getCurrentTab()
        val wv = webViews[tab.id] ?: return
        if (tab.isLoading) {
            wv.stopLoading()
            updateTab(tab.id) { it.copy(isLoading = false, progress = 0) }
        } else if (!tab.isStartPage) {
            pendingProbedUrls.removeIf { it.startsWith("${tab.id}|") }
            updateTab(tab.id) { it.copy(detectedMedia = emptyList()) }
            wv.reload()
        }
    }

    fun toggleDesktopModeForActiveTab() {
        val tab = getCurrentTab()
        val nextDesktop = !tab.isDesktopMode
        updateTab(tab.id) { it.copy(isDesktopMode = nextDesktop) }
        webViews[tab.id]?.let { wv ->
            WebViewConfigurator.configure(
                webView = wv,
                isIncognito = tab.isIncognito,
                desktopMode = nextDesktop,
                javaScriptEnabled = settings.value.javaScriptEnabled,
                blockThirdPartyCookiesIncognito = settings.value.blockThirdPartyCookiesIncognito,
            )
            if (!tab.isStartPage) wv.reload()
        }
        _statusToast.value = if (nextDesktop) s().toastDesktopEnabled else s().toastMobileEnabled
    }

    fun normalizeInputToUrl(input: String): String {
        val currentSettings = settings.value
        return UrlInputNormalizer.normalize(
            input = input,
            httpsUpgradeEnabled = currentSettings.httpsUpgradeEnabled,
            searchUrlTemplate = currentSettings.searchEngine.searchUrlTemplate,
        )
    }

    // --- WebView Callbacks & Engines ---

    fun onPageStarted(tabId: String, url: String) {
        pendingProbedUrls.removeIf { it.startsWith("$tabId|") }
        updateTab(tabId) {
            it.copy(
                url = url,
                isStartPage = false,
                isLoading = true,
                progress = 15,
                isSecureHttps = url.startsWith("https://"),
                isHibernated = false,
                detectedMedia = emptyList(),
            )
        }
    }

    fun onPageProgress(tabId: String, progress: Int) {
        updateTab(tabId) {
            it.copy(progress = progress, isLoading = progress < 100)
        }
    }

    fun onPageFinished(tabId: String, url: String, title: String?, canBack: Boolean, canForward: Boolean) {
        val resolvedTitle = title?.takeIf { it.isNotBlank() } ?: url
        val tab = _tabs.value.firstOrNull { it.id == tabId }
        updateTab(tabId) {
            it.copy(
                url = url,
                title = resolvedTitle,
                isStartPage = false,
                isLoading = false,
                progress = 100,
                canGoBack = canBack,
                canGoForward = canForward,
                isSecureHttps = url.startsWith("https://"),
            )
        }

        // Only save history in Normal tabs (strictly skipped in Incognito!)
        if (tab != null && !tab.isIncognito) {
            viewModelScope.launch(Dispatchers.IO) {
                repository.recordHistory(resolvedTitle, url)
            }
        }

        // Inject Aloha AdBlock cosmetic/popup shield, Media Sniffer, Console Hook, and active Firefox-style Extensions
        webViews[tabId]?.let { wv ->
            if (settings.value.adBlockEnabled && !AdBlockerEngine.isSiteWhitelisted(
                    runCatching { Uri.parse(url).host }.getOrNull(),
                    customAdRules.value,
                )
            ) {
                wv.evaluateJavascript(AdBlockerEngine.cosmeticAdHideScript(customAdRules.value), null)
            }
            wv.evaluateJavascript(mediaSniffer.earlyMediaHookScript(), null)
            wv.evaluateJavascript(mediaSniffer.domSnifferScript(), null)
            wv.evaluateJavascript(consoleHookScript(), null)
            val extScript = ExtensionEngine.buildInjectionScript(extensions.value, url, "document_end")
            if (extScript.isNotBlank()) {
                wv.evaluateJavascript(extScript, null)
            }
        }
    }

    fun injectStartExtensions(tabId: String, url: String) {
        webViews[tabId]?.evaluateJavascript(mediaSniffer.earlyMediaHookScript(), null)
        val script = ExtensionEngine.buildInjectionScript(extensions.value, url, "document_start")
        if (script.isNotBlank()) {
            webViews[tabId]?.evaluateJavascript(script, null)
        }
    }

    fun evaluateRequest(
        tabId: String,
        requestUrl: String,
        method: String,
        isMainFrame: Boolean,
        headers: Map<String, String>,
    ): BlockDecision {
        val tab = _tabs.value.firstOrNull { it.id == tabId }
        val tabUrl = tab?.url.orEmpty()
        val pageHost = if (tabUrl.isNotBlank()) runCatching { Uri.parse(tabUrl).host }.getOrNull() else null
        val decision = if (isMainFrame) {
            BlockDecision(false)
        } else {
            AdBlockerEngine.evaluate(
                url = requestUrl,
                pageHost = pageHost,
                adBlockEnabled = settings.value.adBlockEnabled,
                trackerBlockEnabled = settings.value.trackerBlockEnabled,
                customRules = customAdRules.value,
            )
        }

        val isCandidateMedia = !decision.isBlocked && mediaSniffer.isCandidateMediaUrl(requestUrl)
        if (!decision.isBlocked) {
            mediaRequestHeaderStore.capture(
                tabId = tabId,
                url = requestUrl,
                method = method,
                isMainFrame = isMainFrame,
                headers = headers,
                mediaEvidence = isCandidateMedia || isLikelyMediaSegmentUrl(requestUrl),
            )
        }
        if (isCandidateMedia) {
            probeAndAttachNetworkMedia(tabId, requestUrl, tabUrl, tab?.title.orEmpty())
        }

        if (decision.isBlocked) {
            val isTracker = decision.category == BlockCategory.TRACKER
            val adInc = if (isTracker) 0 else 1
            val trkInc = if (isTracker) 1 else 0
            updateTab(tabId) { current ->
                current.copy(
                    blockedAdsCount = current.blockedAdsCount + adInc,
                    blockedTrackersCount = current.blockedTrackersCount + trkInc,
                )
            }
            if (isTracker) pendingTrackersDelta.incrementAndGet() else pendingAdsDelta.incrementAndGet()
            scheduleBlockedStatsFlush()
        }

        val resourceType = inferResourceType(requestUrl, isMainFrame, isCandidateMedia)
        val entry = NetworkRequestEntry(
            method = method,
            url = requestUrl,
            resourceType = resourceType,
            isBlocked = decision.isBlocked,
            blockReason = decision.matchedRule.orEmpty(),
            isMediaDetected = isCandidateMedia,
            headersSummary = MediaRequestHeaderPolicy.safeSummary(headers),
        )
        val buf = networkBuffers.getOrPut(tabId) { ArrayDeque() }
        synchronized(buf) {
            buf.addFirst(entry)
            while (buf.size > 120) buf.removeLast()
        }
        if (_activeSheet.value == ActiveSheet.DEVTOOLS && _activeTabId.value == tabId) {
            syncDevToolsBuffersThrottled(tabId)
        }

        return decision
    }

    fun incrementBlockedAd(tabId: String) {
        updateTab(tabId) { current ->
            current.copy(blockedAdsCount = current.blockedAdsCount + 1)
        }
        pendingAdsDelta.incrementAndGet()
        scheduleBlockedStatsFlush()
    }


    private fun isLikelyMediaSegmentUrl(url: String): Boolean {
        val path = runCatching { Uri.parse(url).path?.lowercase().orEmpty() }.getOrDefault("")
        return listOf(".ts", ".m4s", ".cmfv", ".cmfa", ".aac", ".vtt").any(path::endsWith)
    }

    private fun scheduleBlockedStatsFlush() {
        if (statsFlushJob?.isActive == true) return
        statsFlushJob = viewModelScope.launch(Dispatchers.IO) {
            kotlinx.coroutines.delay(1500L)
            flushBlockedStatsNow()
        }
    }

    private suspend fun flushBlockedStatsNow() {
        val ads = pendingAdsDelta.getAndSet(0L)
        val trackers = pendingTrackersDelta.getAndSet(0L)
        if (ads > 0L || trackers > 0L) {
            prefsRepository.incrementBlockedStats(ads, trackers)
        }
    }

    fun rescanActiveTabMedia() {
        val tab = getCurrentTab()
        if (tab.isStartPage) return
        pendingProbedUrls.removeIf { it.startsWith("${tab.id}|") }
        webViews[tab.id]?.let { wv ->
            wv.evaluateJavascript(mediaSniffer.earlyMediaHookScript(), null)
            wv.evaluateJavascript(mediaSniffer.domSnifferScript(), null)
        }
        _statusToast.value = s().toastDeepMediaScan
    }

    fun onHookedMediaUrl(tabId: String, mediaUrl: String, titleHint: String) {
        val tab = _tabs.value.firstOrNull { it.id == tabId } ?: return
        val resolvedMediaUrl = mediaSniffer.resolveMediaUrl(tab.url, mediaUrl)
        if (!mediaSniffer.isCandidateMediaUrl(resolvedMediaUrl)) return
        mediaRequestHeaderStore.markMedia(tabId, resolvedMediaUrl)
        probeAndAttachNetworkMedia(
            tabId,
            resolvedMediaUrl,
            tab.url,
            titleHint.ifBlank { tab.title },
        )
    }

    /** Accepts extensionless network resources only when the browser observed a media MIME type. */
    fun onHookedResource(tabId: String, mediaUrl: String, titleHint: String, contentType: String) {
        val tab = _tabs.value.firstOrNull { it.id == tabId } ?: return
        val mime = contentType.substringBefore(";").trim().lowercase()
        if (!(mime.startsWith("video/") || mime.startsWith("audio/") ||
                mime.contains("mpegurl") || mime.contains("dash+xml"))
        ) return
        val resolved = mediaSniffer.resolveMediaUrl(tab.url, mediaUrl)
        if (!MediaSourceUrlPolicy.isVerifiedHttpUrl(resolved)) return
        mediaRequestHeaderStore.markMedia(tabId, resolved)
        probeAndAttachNetworkMedia(tabId, resolved, tab.url, titleHint.ifBlank { tab.title }, mime)
    }

    /**
     * MSE attribution is accepted only when the exact ArrayBuffer/Blob-derived backing buffer
     * observed from a network response is passed to SourceBuffer.appendBuffer. Temporal or
     * same-origin guesses are never promoted to downloadable candidates.
     */
    fun onMseNetworkSourceObserved(
        tabId: String,
        sourceBufferMimeType: String,
        responseContentType: String,
        sourceUrl: String,
        byteLength: Int,
        responseStatus: Int,
        contentRange: String,
    ) {
        val tab = _tabs.value.firstOrNull { it.id == tabId } ?: return
        val resolved = mediaSniffer.resolveMediaUrl(tab.url, sourceUrl)
        val verified = MseNetworkAttributionPolicy.verifiedSourceUrl(
            sourceBufferMimeType = sourceBufferMimeType,
            responseContentType = responseContentType,
            sourceUrl = resolved,
            byteLength = byteLength,
            responseStatus = responseStatus,
            contentRange = contentRange,
        ) ?: return
        onHookedResource(
            tabId = tabId,
            mediaUrl = verified,
            titleHint = tab.title,
            contentType = sourceBufferMimeType,
        )
        onConsoleLog(
            tabId = tabId,
            level = "INFO",
            message = "MSE buffer provenance matched an exact HTTP(S) response; existing media probe invoked (payload not captured)",
            source = "Media Intelligence",
        )
    }

    private fun probeAndAttachNetworkMedia(
        tabId: String,
        mediaUrl: String,
        pageUrl: String,
        pageTitle: String,
        contentTypeHint: String = "",
    ) {
        val cacheKey = "$tabId|$mediaUrl"
        if (!pendingProbedUrls.add(cacheKey)) return
        val sourceTab = _tabs.value.firstOrNull { it.id == tabId } ?: return
        val ua = WebViewConfigurator.resolveUserAgent(
            getApplication(),
            desktopMode = sourceTab.isDesktopMode,
        )
        val cookieHeader = cookieHeaderForTab(sourceTab, mediaUrl)
        val requestHeadersByOrigin = mediaRequestHeaderStore.snapshot(tabId, mediaUrl)
        viewModelScope.launch(Dispatchers.IO) {
            val detected = mediaSniffer.probeNetworkMedia(
                mediaUrl = mediaUrl,
                pageUrl = pageUrl,
                pageTitle = pageTitle,
                userAgent = ua,
                cookieHeader = cookieHeader,
                contentTypeHint = contentTypeHint,
                requestHeadersByOrigin = requestHeadersByOrigin,
            )
            if (detected != null) {
                updateTab(tabId) { current ->
                    current.copy(
                        detectedMedia = mediaSniffer.mergeAndPrioritize(current.detectedMedia, detected),
                    )
                }
            }
        }
    }

    fun onDomMediaSniffed(tabId: String, jsonPayload: String) {
        val tab = _tabs.value.firstOrNull { it.id == tabId } ?: return
        val items = mediaSniffer.parseDomMediaPayload(jsonPayload, tab.url, tab.title)
        if (items.isEmpty()) return
        updateTab(tabId) { current ->
            var merged = current.detectedMedia
            for (item in items) {
                merged = mediaSniffer.mergeAndPrioritize(merged, item)
            }
            current.copy(detectedMedia = merged)
        }
        items.forEach { media ->
            mediaRequestHeaderStore.markMedia(tabId, media.primaryUrl)
            media.qualities.filter { it.isHls || it.isDash }.forEach { streamOpt ->
                probeAndAttachNetworkMedia(tabId, streamOpt.url, tab.url, media.title)
            }
        }
    }

    // --- Media Download Action ---

    fun downloadMedia(fileName: String, quality: MediaQualityOption, sourcePageUrl: String) {
        if (!quality.isVerifiedDownloadSource) {
            _statusToast.value = s().unavailableMediaSourceNotice
            return
        }
        val tab = getCurrentTab()
        val ua = WebViewConfigurator.resolveUserAgent(getApplication(), tab.isDesktopMode)
        val sessionCookieManager = cookieManagerForTab(tab)
        val requestHeadersByOrigin = mediaRequestHeaderStore.snapshot(tab.id, quality.url)
        DownloadRuntime.launchMaintenance {
            runCatching {
                downloadCoordinator.enqueue(
                    fileName = fileName,
                    quality = quality,
                    sourcePageUrl = sourcePageUrl,
                    userAgent = ua,
                    sessionCookieManager = sessionCookieManager,
                    isIncognito = tab.isIncognito,
                    isDesktopMode = tab.isDesktopMode,
                    requestHeadersByOrigin = requestHeadersByOrigin,
                )
            }.onFailure {
                withContext(Dispatchers.Main) {
                    _statusToast.value = getApplication<Application>().getString(
                        com.example.R.string.download_schedule_failed,
                    )
                }
            }
        }
        _statusToast.value = String.format(s().toastDownloadStartedFormat, fileName)
    }

    fun downloadGenericUrl(url: String, mimeType: String, contentDisposition: String?) {
        if (!MediaSourceUrlPolicy.isVerifiedHttpUrl(url)) {
            _statusToast.value = s().unavailableMediaSourceNotice
            return
        }
        val guessedName = android.webkit.URLUtil.guessFileName(url, contentDisposition, mimeType)
        val tab = getCurrentTab()
        val ua = WebViewConfigurator.resolveUserAgent(getApplication(), tab.isDesktopMode)
        val lower = url.lowercase()
        val strings = s()
        val opt = MediaQualityOption(
            label = strings.directDownloadLabel,
            resolutionHeight = 0,
            url = url,
            format = guessedName.substringAfterLast('.', "BIN").uppercase(),
            mimeType = mimeType.ifBlank { "application/octet-stream" },
            isHls = lower.contains(".m3u8"),
            isDash = lower.contains(".mpd"),
        )
        val sessionCookieManager = cookieManagerForTab(tab)
        val lowerPath = runCatching { Uri.parse(url).path?.lowercase().orEmpty() }.getOrDefault("")
        val isMediaDownload = mimeType.substringBefore(";").trim().lowercase().let {
            it.startsWith("video/") || it.startsWith("audio/") ||
                it.contains("mpegurl") || it.contains("dash+xml")
        } || listOf(".m3u8", ".mpd", ".mp4", ".m4v", ".webm", ".mov", ".mp3", ".m4a", ".aac", ".ogg")
            .any(lowerPath::endsWith)
        if (isMediaDownload) mediaRequestHeaderStore.markMedia(tab.id, url)
        val requestHeadersByOrigin = if (isMediaDownload) {
            mediaRequestHeaderStore.snapshot(tab.id, url)
        } else {
            emptyMap()
        }
        DownloadRuntime.launchMaintenance {
            runCatching {
                downloadCoordinator.enqueue(
                    fileName = guessedName,
                    quality = opt,
                    sourcePageUrl = tab.url,
                    userAgent = ua,
                    sessionCookieManager = sessionCookieManager,
                    isIncognito = tab.isIncognito,
                    isDesktopMode = tab.isDesktopMode,
                    requestHeadersByOrigin = requestHeadersByOrigin,
                )
            }.onFailure {
                withContext(Dispatchers.Main) {
                    _statusToast.value = getApplication<Application>().getString(
                        com.example.R.string.download_schedule_failed,
                    )
                }
            }
        }
        _statusToast.value = String.format(strings.toastDownloadingFileFormat, guessedName)
    }

    fun pauseDownload(download: DownloadEntity) {
        DownloadRuntime.launchMaintenance {
            downloadCoordinator.pause(download)
        }
    }

    fun resumeDownload(download: DownloadEntity) {
        val sessionTab = if (download.isIncognito) {
            _tabs.value.firstOrNull { it.isIncognito }
        } else {
            getCurrentTab()
        }
        if (download.isIncognito && sessionTab == null) {
            _statusToast.value = getApplication<Application>().getString(
                com.example.R.string.incognito_isolation_unavailable,
            )
            return
        }
        val ua = WebViewConfigurator.resolveUserAgent(
            getApplication(),
            desktopMode = download.isDesktopMode,
        )
        val sessionCookieManager = if (download.isIncognito && sessionTab != null) {
            cookieManagerForTab(sessionTab)
        } else {
            CookieManager.getInstance()
        }
        val privateRequestHeadersByOrigin = if (download.isIncognito && sessionTab != null) {
            mediaRequestHeaderStore.snapshot(sessionTab.id, download.url)
        } else {
            emptyMap()
        }
        if (download.isIncognito && sessionCookieManager == null) {
            _statusToast.value = getApplication<Application>().getString(
                com.example.R.string.incognito_isolation_unavailable,
            )
            return
        }
        DownloadRuntime.launchMaintenance {
            downloadCoordinator.resume(
                download = download,
                privateCookieManager = sessionCookieManager,
                privateUserAgent = ua,
                privateRequestHeadersByOrigin = privateRequestHeadersByOrigin,
            )
        }
    }

    fun cancelDownload(download: DownloadEntity) {
        DownloadRuntime.launchMaintenance {
            downloadCoordinator.cancel(download)
        }
    }

    fun isHostWhitelisted(host: String?): Boolean =
        AdBlockerEngine.isSiteWhitelisted(host, customAdRules.value)

    fun toggleSiteAdBlock(host: String) {
        if (host.isBlank()) return
        val currentlyWhitelisted = AdBlockerEngine.isSiteWhitelisted(host, customAdRules.value)
        viewModelScope.launch(Dispatchers.IO) {
            if (currentlyWhitelisted) {
                repository.removeAdBlockRuleByPattern(host, isWhitelist = true)
                _statusToast.value = String.format(s().toastShieldEnabledHostFormat, host)
            } else {
                repository.addAdBlockRule(host, isWhitelist = true)
                _statusToast.value = String.format(s().toastShieldPausedHostFormat, host)
            }
        }
        webViews[_activeTabId.value]?.reload()
    }

    fun deleteDownload(download: DownloadEntity) {
        DownloadRuntime.launchMaintenance {
            downloadCoordinator.cancel(download)
            repository.deleteDownload(download.id)
        }
    }

    fun clearFinishedDownloads() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearFinishedDownloads()
        }
    }

    fun recoverPendingDownloads() {
        DownloadRuntime.launchMaintenance {
            downloadCoordinator.recoverPendingDownloads()
        }
    }

    // --- DevTools ---

    fun onConsoleLog(tabId: String, level: String, message: String, source: String) {
        val entry = ConsoleLogEntry(level = level, message = message, source = source)
        val buf = consoleBuffers.getOrPut(tabId) { ArrayDeque() }
        synchronized(buf) {
            buf.addLast(entry)
            while (buf.size > 200) buf.removeFirst()
        }
        if (_activeSheet.value == ActiveSheet.DEVTOOLS && _activeTabId.value == tabId) {
            syncDevToolsBuffersThrottled(tabId)
        }
    }

    fun evaluateJsInActiveTab(code: String) {
        val trimmed = code.trim()
        if (trimmed.isEmpty()) return
        val tab = getCurrentTab()
        onConsoleLog(tab.id, "EVAL", "> $trimmed", "DevTools REPL")
        val wv = webViews[tab.id]
        if (wv == null || tab.isStartPage) {
            onConsoleLog(tab.id, "WARN", s().devToolsPromptOpenPage, "DevTools")
            return
        }
        val wrapped = """
            (function() {
                try {
                    var res = eval(${JSONObject.quote(trimmed)});
                    if (typeof res === 'object') return JSON.stringify(res, null, 2);
                    return String(res);
                } catch (e) {
                    return 'Error: ' + e.message;
                }
            })();
        """.trimIndent()
        wv.evaluateJavascript(wrapped) { result ->
            val unquoted = runCatching {
                if (result != null && result.startsWith("\"")) JSONObject("{\"v\":$result}").getString("v")
                else result ?: "undefined"
            }.getOrDefault(result ?: "undefined")
            val lvl = if (unquoted.startsWith("Error:")) "ERROR" else "LOG"
            onConsoleLog(tab.id, lvl, unquoted, "Result")
        }
    }

    fun clearConsoleLogs() {
        val tabId = _activeTabId.value
        consoleBuffers[tabId]?.let { synchronized(it) { it.clear() } }
        updateTab(tabId) { it.copy(consoleLogs = emptyList()) }
    }

    fun clearNetworkLogs() {
        val tabId = _activeTabId.value
        networkBuffers[tabId]?.let { synchronized(it) { it.clear() } }
        updateTab(tabId) { it.copy(networkRequests = emptyList()) }
    }

    private fun syncDevToolsBuffersThrottled(tabId: String) {
        val now = System.currentTimeMillis()
        if (now - lastDevToolsSyncMs >= 250L) {
            lastDevToolsSyncMs = now
            syncDevToolsBuffers(tabId)
        } else if (devToolsTrailingSyncJob?.isActive != true) {
            devToolsTrailingSyncJob = viewModelScope.launch {
                kotlinx.coroutines.delay(250L)
                if (_activeSheet.value == ActiveSheet.DEVTOOLS && _activeTabId.value == tabId) {
                    lastDevToolsSyncMs = System.currentTimeMillis()
                    syncDevToolsBuffers(tabId)
                }
            }
        }
    }

    private fun syncDevToolsBuffers(tabId: String) {
        val netSnapshot = networkBuffers[tabId]?.let { buf -> synchronized(buf) { buf.toList() } } ?: emptyList()
        val conSnapshot = consoleBuffers[tabId]?.let { buf -> synchronized(buf) { buf.toList() } } ?: emptyList()
        updateTab(tabId) { it.copy(networkRequests = netSnapshot, consoleLogs = conSnapshot) }
    }

    fun refreshDomInspection() {
        val tab = getCurrentTab()
        val wv = webViews[tab.id] ?: return
        if (tab.isStartPage) return
        val inspectJs = """
            (function() {
                try {
                    var metas = [];
                    document.querySelectorAll('meta').forEach(m => {
                        var k = m.getAttribute('name') || m.getAttribute('property') || m.getAttribute('http-equiv') || '';
                        var v = m.getAttribute('content') || '';
                        if (k && v) metas.push([k, v]);
                    });
                    var ls = {};
                    try { for (var i=0; i<localStorage.length; i++) { var k=localStorage.key(i); ls[k]=localStorage.getItem(k); } } catch(e){}
                    var ss = {};
                    try { for (var i=0; i<sessionStorage.length; i++) { var k=sessionStorage.key(i); ss[k]=sessionStorage.getItem(k); } } catch(e){}
                    return JSON.stringify({
                        url: location.href,
                        title: document.title,
                        html: document.documentElement ? document.documentElement.outerHTML.slice(0, 18000) : '',
                        metas: metas.slice(0, 40),
                        localStorage: JSON.stringify(ls, null, 2),
                        sessionStorage: JSON.stringify(ss, null, 2),
                        viewport: window.innerWidth + 'x' + window.innerHeight + ' (DPR ' + window.devicePixelRatio + ')'
                    });
                } catch(e) {
                    return JSON.stringify({ error: e.message });
                }
            })();
        """.trimIndent()

        wv.evaluateJavascript(inspectJs) { raw ->
            val jsonStr = runCatching {
                if (raw != null && raw.startsWith("\"")) JSONObject("{\"v\":$raw}").getString("v") else raw
            }.getOrNull() ?: return@evaluateJavascript

            val obj = runCatching { JSONObject(jsonStr) }.getOrNull() ?: return@evaluateJavascript
            val metasArr = obj.optJSONArray("metas")
            val metaList = buildList {
                if (metasArr != null) {
                    for (i in 0 until metasArr.length()) {
                        val pair = metasArr.optJSONArray(i)
                        if (pair != null && pair.length() >= 2) {
                            add(pair.optString(0) to pair.optString(1))
                        }
                    }
                }
            }
            val currentUrl = obj.optString("url", tab.url)
            // Read cookies from the tab-owned profile; private tabs must never expose default-profile cookies.
            val cookies = cookieHeaderForTab(tab, currentUrl)
            val ua = wv.settings.userAgentString
            updateTab(tab.id) { current ->
                current.copy(
                    domSnapshot = DomInspectionSnapshot(
                        url = currentUrl,
                        title = obj.optString("title", tab.title),
                        htmlSource = obj.optString("html", ""),
                        metaTags = metaList,
                        cookies = cookies,
                        localStorageJson = obj.optString("localStorage", "{}"),
                        sessionStorageJson = obj.optString("sessionStorage", "{}"),
                        userAgent = ua,
                        viewport = obj.optString("viewport", ""),
                    ),
                )
            }
        }
    }

    fun findInPage(query: String) {
        _findInPageQuery.value = query
        val wv = webViews[_activeTabId.value] ?: return
        if (query.isBlank()) {
            wv.clearMatches()
        } else {
            wv.findAllAsync(query)
        }
    }

    fun findNext(forward: Boolean) {
        webViews[_activeTabId.value]?.findNext(forward)
    }

    // --- Bookmarks & History ---

    fun toggleBookmarkActiveTab() {
        val tab = getCurrentTab()
        if (tab.isIncognito) {
            _statusToast.value = getApplication<Application>().getString(
                com.example.R.string.incognito_bookmark_blocked,
            )
            return
        }
        if (tab.isStartPage || tab.url.isBlank()) return
        val exists = bookmarks.value.any { it.url == tab.url }
        viewModelScope.launch(Dispatchers.IO) {
            if (exists) {
                repository.removeBookmarkByUrl(tab.url)
                _statusToast.value = s().toastBookmarkRemoved
            } else {
                repository.addBookmark(tab.title, tab.url)
                _statusToast.value = s().toastBookmarkAdded
            }
        }
    }

    fun addQuickBookmark(title: String, url: String, pinned: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.addBookmark(title, normalizeInputToUrl(url), pinned = pinned)
        }
    }

    fun removeBookmark(id: Long) {
        viewModelScope.launch(Dispatchers.IO) { repository.removeBookmarkById(id) }
    }

    fun deleteHistoryItem(id: Long) {
        viewModelScope.launch(Dispatchers.IO) { repository.deleteHistoryItem(id) }
    }

    fun clearHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearHistory()
            _statusToast.value = s().toastHistoryCleared
        }
    }

    // --- Extensions ---

    fun setExtensionEnabled(id: String, enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.setExtensionEnabled(id, enabled)
        }
    }

    fun setExtensionInstalled(id: String, installed: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.setExtensionInstalled(id, installed)
        }
    }

    fun saveCustomExtension(
        name: String,
        description: String,
        matchPattern: String,
        runAt: String,
        jsCode: String,
        cssCode: String,
    ) {
        val strings = s()
        val ext = ExtensionEntity(
            id = "custom_${UUID.randomUUID().toString().take(8)}",
            name = name.trim(),
            version = "1.0.0",
            author = strings.extCustomAuthorDefault,
            description = description.ifBlank { strings.extCustomDescDefault },
            iconKey = "extension",
            matchPattern = matchPattern.ifBlank { "*://*/*" },
            runAt = runAt,
            permissions = "activeTab, scripting",
            jsCode = jsCode,
            cssCode = cssCode,
            isInstalled = true,
            isEnabled = true,
            isBuiltIn = false,
        )
        viewModelScope.launch(Dispatchers.IO) {
            repository.saveCustomExtension(ext)
            _statusToast.value = String.format(strings.toastCustomExtInstalledFormat, ext.name)
        }
    }

    fun deleteCustomExtension(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteCustomExtension(id)
        }
    }

    // --- Settings & Privacy ---

    fun setAdBlockEnabled(enabled: Boolean) = viewModelScope.launch { prefsRepository.setAdBlockEnabled(enabled) }
    fun setTrackerBlockEnabled(enabled: Boolean) = viewModelScope.launch { prefsRepository.setTrackerBlockEnabled(enabled) }
    fun setHttpsUpgradeEnabled(enabled: Boolean) = viewModelScope.launch { prefsRepository.setHttpsUpgradeEnabled(enabled) }
    fun setIncognito3pCookieBlock(enabled: Boolean) = viewModelScope.launch { prefsRepository.setIncognito3pCookieBlock(enabled) }
    fun setJavaScriptEnabled(enabled: Boolean) = viewModelScope.launch { prefsRepository.setJavaScriptEnabled(enabled) }
    fun setDoNotTrackEnabled(enabled: Boolean) = viewModelScope.launch { prefsRepository.setDoNotTrackEnabled(enabled) }
    fun setSearchEngine(engine: SearchEngine) = viewModelScope.launch { prefsRepository.setSearchEngine(engine) }
    fun setThemePreference(theme: ThemePreference) = viewModelScope.launch { prefsRepository.setThemePreference(theme) }
    fun setAppLanguage(language: AppLanguage) = viewModelScope.launch { prefsRepository.setAppLanguage(language) }

    fun addCustomAdRule(pattern: String, isWhitelist: Boolean) {
        if (pattern.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            repository.addAdBlockRule(pattern, isWhitelist)
        }
    }

    fun deleteAdRule(id: Long) {
        viewModelScope.launch(Dispatchers.IO) { repository.deleteAdBlockRule(id) }
    }

    fun clearAllBrowsingData() {
        // Clear newly introduced origin grants and transient observed request metadata too.
        SitePermissionStore(getApplication()).clearAll()
        mediaRequestHeaderStore.clearAll()
        val liveList = webViews.values.toList()
        liveList.forEach { wv ->
            runCatching {
                wv.clearCache(true)
                wv.clearHistory()
                wv.clearFormData()
            }
        }
        WebViewConfigurator.clearAllBrowsingData(getApplication(), hadLiveWebViews = liveList.isNotEmpty())
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearHistory()
            _statusToast.value = s().toastAllBrowsingDataCleared
        }
    }

    private fun updateTab(tabId: String, transform: (BrowserTab) -> BrowserTab) {
        _tabs.update { list ->
            list.map { if (it.id == tabId) transform(it) else it }
        }
    }

    private fun inferResourceType(url: String, isMainFrame: Boolean, isMedia: Boolean): String {
        if (isMainFrame) return "DOC"
        if (isMedia) return "MEDIA"
        val lower = url.lowercase()
        return when {
            lower.contains(".js") -> "SCRIPT"
            lower.contains(".css") -> "CSS"
            lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".webp") || lower.endsWith(".svg") -> "IMG"
            lower.contains("xhr") || lower.contains("api") || lower.contains("graphql") || lower.contains("json") -> "XHR"
            else -> "OTHER"
        }
    }

    private fun consoleHookScript(): String = """
        (function() {
            if (window.__navaConsoleHooked) return;
            window.__navaConsoleHooked = true;
            ['log', 'warn', 'error', 'info'].forEach(function(fn) {
                var orig = console[fn];
                console[fn] = function() {
                    try {
                        var msg = Array.from(arguments).map(function(a) {
                            if (typeof a === 'object') {
                                try { return JSON.stringify(a); } catch(e) { return String(a); }
                            }
                            return String(a);
                        }).join(' ');
                        if (window.NavaConsoleBridge) {
                            window.NavaConsoleBridge.onLog(fn.toUpperCase(), msg, location.hostname || 'page');
                        }
                    } catch(err) {}
                    if (orig) return orig.apply(console, arguments);
                };
            });
            window.addEventListener('error', function(e) {
                if (window.NavaConsoleBridge) {
                    window.NavaConsoleBridge.onLog('ERROR', e.message + ' (' + (e.filename || '') + ':' + e.lineno + ')', 'uncaught');
                }
            });
        })();
    """.trimIndent()

    override fun onCleared() {
        // Release all live/hibernated WebViews first. Private download tasks are
        // stopped in the process-wide runtime before their profile is deleted.
        webViews.keys.toList().forEach(::destroyWebView)
        DownloadRuntime.launchMaintenance {
            downloadCoordinator.clearIncognitoDownloads()
            if (WebViewConfigurator.supportsIsolatedIncognitoProfiles()) {
                withContext(Dispatchers.Main.immediate) {
                    WebViewConfigurator.clearIncognitoAndVolatileData(
                        getApplication(),
                        clearCookiesToo = false,
                    )
                }
            }
        }

        val remainingAds = pendingAdsDelta.getAndSet(0L)
        val remainingTrackers = pendingTrackersDelta.getAndSet(0L)
        if (remainingAds > 0L || remainingTrackers > 0L) {
            kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                prefsRepository.incrementBlockedStats(remainingAds, remainingTrackers)
            }
        }
        savedTabStates.clear()
        networkBuffers.clear()
        consoleBuffers.clear()
        pendingProbedUrls.clear()
        super.onCleared()
    }
}
