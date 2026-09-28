package com.salvia.salviabrowxer.feature.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salvia.salviabrowxer.R
import com.salvia.salviabrowxer.core.storage.StorageManager
import com.salvia.salviabrowxer.data.datastore.SettingsDataStore
import com.salvia.salviabrowxer.data.repository.HistoryRepository
import com.salvia.salviabrowxer.ui.utils.Constants
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Every preference the settings screen renders, in one immutable snapshot. */
data class SettingsUiState(
    val isLoading: Boolean = true,
    val searchEngine: String = Constants.DEFAULT_SEARCH_ENGINE,
    val homepage: String = Constants.DEFAULT_HOMEPAGE,
    val isDesktopSite: Boolean = false,
    val isJavaScriptEnabled: Boolean = true,
    val areCookiesEnabled: Boolean = true,
    /** Real, app-scoped folder the downloader writes to. Never a user-editable label. */
    val downloadDirectoryPath: String = "",
    val maxSimultaneousDownloads: Int = 3,
    val isWifiOnly: Boolean = false,
    val isCleartextAllowed: Boolean = false,
    /** The draggable button is an advanced opt-in; the media pill in the top bar is the default. */
    val isFloatingButtonAlwaysVisible: Boolean = false,
    val floatingButtonSize: Int = 56
) {
    val searchEngineOptions: List<String> get() = Constants.SEARCH_ENGINES.keys.toList()
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val historyRepository: HistoryRepository,
    private val storageManager: StorageManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            launch { settingsDataStore.searchEngine.collectLatest { value -> mutate { copy(searchEngine = value) } } }
            launch { settingsDataStore.homepage.collectLatest { value -> mutate { copy(homepage = value) } } }
            launch { settingsDataStore.isDesktopSite.collectLatest { value -> mutate { copy(isDesktopSite = value) } } }
            launch { settingsDataStore.isJavaScriptEnabled.collectLatest { value -> mutate { copy(isJavaScriptEnabled = value) } } }
            launch { settingsDataStore.areCookiesEnabled.collectLatest { value -> mutate { copy(areCookiesEnabled = value) } } }
            mutate { copy(downloadDirectoryPath = runCatching { storageManager.getDefaultDownloadDirectory() }.getOrDefault("")) }
            launch {
                settingsDataStore.maxSimultaneousDownloads.collectLatest { value ->
                    mutate { copy(maxSimultaneousDownloads = value) }
                }
            }
            launch { settingsDataStore.isWifiOnly.collectLatest { value -> mutate { copy(isWifiOnly = value) } } }
            launch { settingsDataStore.isCleartextAllowed.collectLatest { value -> mutate { copy(isCleartextAllowed = value) } } }
            launch {
                settingsDataStore.isFloatingButtonAlwaysVisible.collectLatest { value ->
                    mutate { copy(isFloatingButtonAlwaysVisible = value) }
                }
            }
            launch { settingsDataStore.floatingButtonSize.collectLatest { value -> mutate { copy(floatingButtonSize = value) } } }
            mutate { copy(isLoading = false) }
        }
    }

    private fun mutate(transform: SettingsUiState.() -> SettingsUiState) {
        _uiState.update { current -> current.transform() }
    }

    fun updateSearchEngine(engine: String) {
        mutate { copy(searchEngine = engine) }
        viewModelScope.launch {
            runCatching { settingsDataStore.setSearchEngine(engine) }
        }
    }

    fun updateHomepage(url: String) {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return
        mutate { copy(homepage = trimmed) }
        viewModelScope.launch {
            runCatching { settingsDataStore.setHomepage(trimmed) }
        }
        _messages.trySend(context.getString(R.string.settings_homepage_updated))
    }

    fun updateDesktopSite(isDesktop: Boolean) {
        mutate { copy(isDesktopSite = isDesktop) }
        viewModelScope.launch {
            runCatching { settingsDataStore.setDesktopSite(isDesktop) }
        }
    }

    fun updateJavaScriptEnabled(enabled: Boolean) {
        mutate { copy(isJavaScriptEnabled = enabled) }
        viewModelScope.launch {
            runCatching { settingsDataStore.setJavaScriptEnabled(enabled) }
        }
    }

    fun updateCookiesEnabled(enabled: Boolean) {
        mutate { copy(areCookiesEnabled = enabled) }
        viewModelScope.launch {
            runCatching { settingsDataStore.setCookiesEnabled(enabled) }
        }
    }

    fun updateMaxSimultaneousDownloads(count: Int) {
        val safe = count.coerceIn(1, 5)
        mutate { copy(maxSimultaneousDownloads = safe) }
        viewModelScope.launch {
            runCatching { settingsDataStore.setMaxSimultaneousDownloads(safe) }
        }
    }

    fun updateWifiOnly(enabled: Boolean) {
        mutate { copy(isWifiOnly = enabled) }
        viewModelScope.launch {
            runCatching { settingsDataStore.setWifiOnly(enabled) }
        }
    }

    fun updateCleartextAllowed(enabled: Boolean) {
        mutate { copy(isCleartextAllowed = enabled) }
        viewModelScope.launch {
            runCatching { settingsDataStore.setCleartextAllowed(enabled) }
        }
    }

    fun updateFloatingButtonPosition(x: Float, y: Float) {
        viewModelScope.launch {
            runCatching { settingsDataStore.setFloatingButtonPosition(x, y) }
        }
    }

    fun updateFloatingButtonAlwaysVisible(enabled: Boolean) {
        mutate { copy(isFloatingButtonAlwaysVisible = enabled) }
        viewModelScope.launch {
            runCatching { settingsDataStore.setFloatingButtonAlwaysVisible(enabled) }
        }
    }

    fun updateFloatingButtonSize(size: Int) {
        val safe = size.coerceIn(40, 72)
        mutate { copy(floatingButtonSize = safe) }
        viewModelScope.launch {
            runCatching { settingsDataStore.setFloatingButtonSize(safe) }
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            runCatching { historyRepository.deleteAllHistory() }
            _messages.trySend(context.getString(R.string.settings_history_cleared))
        }
    }

    fun clearCookies() {
        viewModelScope.launch {
            runCatching { android.webkit.CookieManager.getInstance().removeAllCookies(null) }
            runCatching { android.webkit.CookieManager.getInstance().flush() }
            _messages.trySend(context.getString(R.string.settings_cookies_cleared))
        }
    }

    fun clearBrowsingData() {
        viewModelScope.launch {
            runCatching { historyRepository.deleteAllHistory() }
            runCatching { android.webkit.CookieManager.getInstance().removeAllCookies(null) }
            runCatching { android.webkit.CookieManager.getInstance().flush() }
            runCatching { android.webkit.WebStorage.getInstance().deleteAllData() }
            _messages.trySend(context.getString(R.string.settings_browsing_data_cleared))
        }
    }
}
