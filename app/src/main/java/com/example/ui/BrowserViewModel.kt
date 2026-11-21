package com.example.ui

import android.app.Application
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AdBlockRuleEntity
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
import com.example.engine.MediaDownloadManager
import com.example.engine.MediaSnifferEngine
import com.example.engine.WebViewConfigurator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class BrowserViewModel(application: Application) : AndroidViewModel(application) {

    private val db = BrowserDatabase.getInstance(application)
    private val repository = BrowserRepository(db)
    private val prefsRepository = PreferencesRepository(application)
    private val mediaSniffer = MediaSnifferEngine()
    private val downloadManager = MediaDownloadManager(application, repository, viewModelScope)

    private val webViews = ConcurrentHashMap<String, WebView>()
    private val pendingProbedUrls = ConcurrentHashMap.newKeySet<String>()

    private val initialTab = BrowserTab(title = "صفحه شروع", isStartPage = true)
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
        viewModelScope.launch(Dispatchers.IO) {
            repository.ensureSeeded()
        }
    }

    fun getCurrentTab(): BrowserTab =
        _tabs.value.firstOrNull { it.id == _activeTabId.value } ?: _tabs.value.first()

    fun registerWebView(tabId: String, webView: WebView) {
        webViews[tabId] = webView
    }

    fun getWebView(tabId: String): WebView? = webViews[tabId]

    fun openSheet(sheet: ActiveSheet) {
        _activeSheet.value = sheet
        if (sheet == ActiveSheet.DEVTOOLS) {
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
        val resolved = initialUrl?.let { normalizeInputToUrl(it) }
        val newTab = BrowserTab(
            title = if (isIncognito) "تب ناشناس" else "تب جدید",
            url = resolved.orEmpty(),
            isStartPage = resolved.isNullOrBlank(),
            isIncognito = isIncognito,
        )
        _tabs.update { it + newTab }
        _activeTabId.value = newTab.id
        _activeSheet.value = ActiveSheet.NONE
    }

    fun selectTab(tabId: String) {
        if (_tabs.value.any { it.id == tabId }) {
            _activeTabId.value = tabId
            _activeSheet.value = ActiveSheet.NONE
        }
    }

    fun closeTab(tabId: String) {
        val tabToClose = _tabs.value.firstOrNull { it.id == tabId } ?: return
        destroyWebView(tabId)
        val remaining = _tabs.value.filterNot { it.id == tabId }
        if (remaining.isEmpty()) {
            val fresh = BrowserTab(title = "صفحه شروع", isStartPage = true, isIncognito = false)
            _tabs.value = listOf(fresh)
            _activeTabId.value = fresh.id
        } else {
            _tabs.value = remaining
            if (_activeTabId.value == tabId) {
                _activeTabId.value = remaining.last().id
            }
        }
        if (tabToClose.isIncognito && _tabs.value.none { it.isIncognito }) {
            WebViewConfigurator.clearIncognitoAndVolatileData(getApplication(), clearCookiesToo = false)
        }
    }

    fun closeAllIncognitoTabs() {
        val incognitoIds = _tabs.value.filter { it.isIncognito }.map { it.id }
        incognitoIds.forEach { destroyWebView(it) }
        val normalTabs = _tabs.value.filterNot { it.isIncognito }
        if (normalTabs.isEmpty()) {
            val fresh = BrowserTab(title = "صفحه شروع", isStartPage = true, isIncognito = false)
            _tabs.value = listOf(fresh)
            _activeTabId.value = fresh.id
        } else {
            _tabs.value = normalTabs
            if (_tabs.value.none { it.id == _activeTabId.value }) {
                _activeTabId.value = normalTabs.last().id
            }
        }
        WebViewConfigurator.clearIncognitoAndVolatileData(getApplication(), clearCookiesToo = false)
        _statusToast.value = "تمام تب‌های ناشناس پاکسازی شدند"
    }

    private fun destroyWebView(tabId: String) {
        webViews.remove(tabId)?.apply {
            stopLoading()
            loadUrl("about:blank")
            clearHistory()
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
        updateTab(tabId) {
            it.copy(
                isStartPage = true,
                url = "",
                title = if (it.isIncognito) "صفحه شروع ناشناس" else "صفحه شروع",
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
        _statusToast.value = if (nextDesktop) "حالت دسکتاپ فعال شد" else "حالت موبایل فعال شد"
    }

    fun normalizeInputToUrl(input: String): String {
        val trimmed = input.trim()
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return if (settings.value.httpsUpgradeEnabled && trimmed.startsWith("http://") && !isLocalAddress(trimmed)) {
                "https://" + trimmed.removePrefix("http://")
            } else {
                trimmed
            }
        }
        val looksLikeDomain = !trimmed.contains(" ") &&
            (trimmed.contains(".") || trimmed.startsWith("localhost"))
        if (looksLikeDomain) {
            val scheme = if (isLocalAddress(trimmed) || !settings.value.httpsUpgradeEnabled) "http://" else "https://"
            return "$scheme$trimmed"
        }
        val encoded = runCatching { URLEncoder.encode(trimmed, "UTF-8") }.getOrDefault(trimmed)
        return settings.value.searchEngine.searchUrlTemplate.replace("%s", encoded)
    }

    private fun isLocalAddress(hostOrUrl: String): Boolean {
        val lower = hostOrUrl.lowercase()
        return lower.contains("localhost") || lower.contains("127.0.0.1") || lower.contains("192.168.") || lower.contains("10.0.")
    }

    // --- WebView Callbacks & Engines ---

    fun onPageStarted(tabId: String, url: String) {
        updateTab(tabId) {
            it.copy(
                url = url,
                isStartPage = false,
                isLoading = true,
                progress = 15,
                isSecureHttps = url.startsWith("https://"),
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
        val pageHost = runCatching { Uri.parse(tab?.url.orEmpty()).host }.getOrNull()
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
        if (isCandidateMedia) {
            probeAndAttachNetworkMedia(tabId, requestUrl, tab?.url.orEmpty(), tab?.title.orEmpty())
        }

        if (decision.isBlocked) {
            updateTab(tabId) { current ->
                val adInc = if (
                    decision.category == BlockCategory.AD ||
                    decision.category == BlockCategory.CUSTOM_RULE ||
                    decision.category == BlockCategory.CRYPTO_MINER ||
                    decision.category == BlockCategory.POPUP_REDIRECT
                ) 1 else 0
                val trkInc = if (decision.category == BlockCategory.TRACKER) 1 else 0
                current.copy(
                    blockedAdsCount = current.blockedAdsCount + adInc,
                    blockedTrackersCount = current.blockedTrackersCount + trkInc,
                )
            }
            viewModelScope.launch(Dispatchers.IO) {
                val adDelta = if (decision.category == BlockCategory.TRACKER) 0L else 1L
                val trkDelta = if (decision.category == BlockCategory.TRACKER) 1L else 0L
                prefsRepository.incrementBlockedStats(adDelta, trkDelta)
            }
        }

        val resourceType = inferResourceType(requestUrl, isMainFrame, isCandidateMedia)
        val entry = NetworkRequestEntry(
            method = method,
            url = requestUrl,
            resourceType = resourceType,
            isBlocked = decision.isBlocked,
            blockReason = decision.matchedRule.orEmpty(),
            isMediaDetected = isCandidateMedia,
            headersSummary = headers.entries.take(4).joinToString(" • ") { "${it.key}: ${it.value}" },
        )
        updateTab(tabId) { current ->
            val updated = (listOf(entry) + current.networkRequests).take(250)
            current.copy(networkRequests = updated)
        }

        return decision
    }

    fun onHookedMediaUrl(tabId: String, mediaUrl: String, titleHint: String) {
        val tab = _tabs.value.firstOrNull { it.id == tabId } ?: return
        if (!mediaSniffer.isCandidateMediaUrl(mediaUrl)) return
        probeAndAttachNetworkMedia(tabId, mediaUrl, tab.url, titleHint.ifBlank { tab.title })
    }

    private fun probeAndAttachNetworkMedia(
        tabId: String,
        mediaUrl: String,
        pageUrl: String,
        pageTitle: String,
    ) {
        val cacheKey = "$tabId|$mediaUrl"
        if (!pendingProbedUrls.add(cacheKey)) return
        val ua = WebViewConfigurator.resolveUserAgent(getApplication(), desktopMode = false)
        viewModelScope.launch(Dispatchers.IO) {
            val detected = mediaSniffer.probeNetworkMedia(mediaUrl, pageUrl, pageTitle, ua)
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
            media.qualities.filter { it.isHls || it.isDash }.forEach { streamOpt ->
                probeAndAttachNetworkMedia(tabId, streamOpt.url, tab.url, media.title)
            }
        }
    }

    // --- Media Download Action ---

    fun downloadMedia(fileName: String, quality: MediaQualityOption, sourcePageUrl: String) {
        val tab = getCurrentTab()
        val ua = WebViewConfigurator.resolveUserAgent(getApplication(), tab.isDesktopMode)
        downloadManager.startDownload(fileName, quality, sourcePageUrl, ua)
        _statusToast.value = "دانلود «$fileName» شروع شد"
    }

    fun downloadGenericUrl(url: String, mimeType: String, contentDisposition: String?) {
        val guessedName = android.webkit.URLUtil.guessFileName(url, contentDisposition, mimeType)
        val tab = getCurrentTab()
        val ua = WebViewConfigurator.resolveUserAgent(getApplication(), tab.isDesktopMode)
        val lower = url.lowercase()
        val opt = MediaQualityOption(
            label = "دانلود مستقیم",
            resolutionHeight = 0,
            url = url,
            format = guessedName.substringAfterLast('.', "BIN").uppercase(),
            mimeType = mimeType.ifBlank { "application/octet-stream" },
            isHls = lower.contains(".m3u8"),
            isDash = lower.contains(".mpd"),
        )
        downloadManager.startDownload(guessedName, opt, tab.url, ua)
        _statusToast.value = "در حال دانلود $guessedName"
    }

    fun pauseDownload(download: DownloadEntity) {
        downloadManager.pauseDownload(download)
    }

    fun resumeDownload(download: DownloadEntity) {
        val tab = getCurrentTab()
        val ua = WebViewConfigurator.resolveUserAgent(getApplication(), tab.isDesktopMode)
        downloadManager.resumeDownload(download, ua)
    }

    fun cancelDownload(download: DownloadEntity) {
        downloadManager.cancelDownload(download)
    }

    fun isHostWhitelisted(host: String?): Boolean =
        AdBlockerEngine.isSiteWhitelisted(host, customAdRules.value)

    fun toggleSiteAdBlock(host: String) {
        if (host.isBlank()) return
        val currentlyWhitelisted = AdBlockerEngine.isSiteWhitelisted(host, customAdRules.value)
        viewModelScope.launch(Dispatchers.IO) {
            if (currentlyWhitelisted) {
                repository.removeAdBlockRuleByPattern(host, isWhitelist = true)
                _statusToast.value = "سپر ضدتبلیغ برای $host فعال شد"
            } else {
                repository.addAdBlockRule(host, isWhitelist = true)
                _statusToast.value = "سپر ضدتبلیغ روی $host متوقف شد"
            }
        }
        webViews[_activeTabId.value]?.reload()
    }

    fun deleteDownload(download: DownloadEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteDownload(download.id)
        }
    }

    fun clearFinishedDownloads() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearFinishedDownloads()
        }
    }

    // --- DevTools ---

    fun onConsoleLog(tabId: String, level: String, message: String, source: String) {
        val entry = ConsoleLogEntry(level = level, message = message, source = source)
        updateTab(tabId) { current ->
            current.copy(consoleLogs = (current.consoleLogs + entry).takeLast(300))
        }
    }

    fun evaluateJsInActiveTab(code: String) {
        val trimmed = code.trim()
        if (trimmed.isEmpty()) return
        val tab = getCurrentTab()
        onConsoleLog(tab.id, "EVAL", "> $trimmed", "DevTools REPL")
        val wv = webViews[tab.id]
        if (wv == null || tab.isStartPage) {
            onConsoleLog(tab.id, "WARN", "ابتدا یک صفحه وب را باز کنید", "DevTools")
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
        updateTab(tabId) { it.copy(consoleLogs = emptyList()) }
    }

    fun clearNetworkLogs() {
        val tabId = _activeTabId.value
        updateTab(tabId) { it.copy(networkRequests = emptyList()) }
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
            val cookies = runCatching { CookieManager.getInstance().getCookie(currentUrl).orEmpty() }.getOrDefault("")
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
        if (tab.isStartPage || tab.url.isBlank()) return
        val exists = bookmarks.value.any { it.url == tab.url }
        viewModelScope.launch(Dispatchers.IO) {
            if (exists) {
                repository.removeBookmarkByUrl(tab.url)
                _statusToast.value = "از نشانک‌ها حذف شد"
            } else {
                repository.addBookmark(tab.title, tab.url)
                _statusToast.value = "به نشانک‌ها اضافه شد"
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
            _statusToast.value = "تاریخچه مرور پاک شد"
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
        val ext = ExtensionEntity(
            id = "custom_${UUID.randomUUID().toString().take(8)}",
            name = name.trim(),
            version = "1.0.0",
            author = "کاربر",
            description = description.ifBlank { "افزونه سفارشی UserScript" },
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
            _statusToast.value = "افزونه «${ext.name}» نصب و فعال شد"
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
        WebViewConfigurator.clearAllBrowsingData(getApplication())
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearHistory()
            _statusToast.value = "کوکی‌ها، کش و تاریخچه کاملاً پاکسازی شدند"
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
        super.onCleared()
        webViews.values.forEach { runCatching { it.destroy() } }
        webViews.clear()
    }
}
