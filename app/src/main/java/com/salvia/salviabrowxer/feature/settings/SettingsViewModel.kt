package com.salvia.salviabrowxer.feature.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salvia.salviabrowxer.R
import com.salvia.salviabrowxer.core.storage.SafDocumentExporter
import com.salvia.salviabrowxer.core.storage.StorageManager
import com.salvia.salviabrowxer.data.datastore.SettingsDataStore
import com.salvia.salviabrowxer.data.repository.DownloadRepository
import com.salvia.salviabrowxer.data.repository.HistoryRepository
import com.salvia.salviabrowxer.data.security.LockedFolderStore
import com.salvia.salviabrowxer.data.security.PasswordVault
import com.salvia.salviabrowxer.data.storage.SafDestinationStore
import com.salvia.salviabrowxer.ui.utils.Constants
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
import kotlinx.coroutines.withContext
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
    /**
     * Range connections one direct file may use. 1 is off, and the label says so rather than showing
     * a number the user has to translate.
     */
    val parallelConnections: Int = 4,
    val isWifiOnly: Boolean = false,
    /** Finished downloads are published to the shared media store so galleries can see them. */
    val isExportToGallery: Boolean = true,
    val isCleartextAllowed: Boolean = false,
    /** The draggable button is an advanced opt-in; the media pill in the top bar is the default. */
    val isFloatingButtonAlwaysVisible: Boolean = false,
    val floatingButtonSize: Int = 56,
    /** Whether the locked folder has a password, and whether the verifier sits behind the keystore. */
    val isPasswordFolderSet: Boolean = false,
    val isPasswordFolderSecured: Boolean = true,
    /** The folder the user chose for a second copy of every download, or null for none. */
    val safDestinationLabel: String? = null
) {
    val searchEngineOptions: List<String> get() = Constants.SEARCH_ENGINES.keys.toList()
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val historyRepository: HistoryRepository,
    private val downloadRepository: DownloadRepository,
    private val storageManager: StorageManager,
    private val lockedFolderStore: LockedFolderStore,
    private val safDestinationStore: SafDestinationStore,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    init {
        // Both are read once, not collected: neither the verifier nor the chosen tree changes unless
        // this screen changes it, and this screen updates the state itself when it does.
        mutate { copy(isPasswordFolderSet = lockedFolderStore.isConfigured(), isPasswordFolderSecured = lockedFolderStore.isSecured) }
        viewModelScope.launch {
            safDestinationStore.destination.collectLatest { destination ->
                mutate { copy(safDestinationLabel = destination?.label) }
            }
        }
        loadSettings()
    }

    /**
     * Sets or replaces the password of the locked folder.
     *
     * Hashing is done off the main thread: the cost of the verifier is the point, and paying it on the
     * UI thread would be a frozen settings screen.
     */
    fun setPasswordFolderPassword(password: String) {
        viewModelScope.launch {
            if (!PasswordVault.isAcceptable(password)) {
                _messages.send(context.getString(R.string.locked_folder_password_short, PasswordVault.MIN_PASSWORD_CHARS))
                return@launch
            }
            val saved = withContext(Dispatchers.Default) { lockedFolderStore.setPassword(password) }
            mutate { copy(isPasswordFolderSet = lockedFolderStore.isConfigured(), isPasswordFolderSecured = lockedFolderStore.isSecured) }
            _messages.send(context.getString(if (saved) R.string.locked_folder_set else R.string.locked_folder_save_failed))
        }
    }

    /**
     * Removes the password — and unlocks every file with it.
     *
     * A password that no longer exists cannot be the thing hiding a file, so leaving rows locked with
     * no way to open them would be worse than either state on its own.
     */
    fun removePasswordFolder() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                lockedFolderStore.clear()
                downloadRepository.unlockAllDownloads()
            }
            mutate { copy(isPasswordFolderSet = false) }
            _messages.send(context.getString(R.string.locked_folder_removed))
        }
    }

    /**
     * Remembers the folder the user picked, and the permission to keep writing to it after a restart.
     *
     * The persistable grant is what turns "this session" into "from now on"; without it the tree would
     * stop resolving as soon as the process dies, so a failure to take it is reported rather than
     * stored as a folder that silently stops working.
     */
    fun onSafTreePicked(treeUri: String) {
        if (treeUri.isBlank()) return
        viewModelScope.launch {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            val taken = runCatching {
                context.contentResolver.takePersistableUriPermission(Uri.parse(treeUri), flags)
            }
            if (taken.isFailure) {
                _messages.send(context.getString(R.string.sd_card_grant_failed))
                return@launch
            }
            safDestinationStore.set(treeUri, SafDocumentExporter.describe(treeUri))
            _messages.send(context.getString(R.string.sd_card_folder_set))
        }
    }

    fun clearSafDestination() {
        viewModelScope.launch {
            runCatching { safDestinationStore.clear() }
            _messages.send(context.getString(R.string.sd_card_folder_cleared))
        }
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
                settingsDataStore.parallelConnections.collectLatest { value ->
                    mutate { copy(parallelConnections = value) }
                }
            }
            viewModelScope.launch {
                settingsDataStore.maxSimultaneousDownloads.collectLatest { value ->
                    mutate { copy(maxSimultaneousDownloads = value) }
                }
            }
            launch { settingsDataStore.isWifiOnly.collectLatest { value -> mutate { copy(isWifiOnly = value) } } }
            launch { settingsDataStore.isExportToGallery.collectLatest { value -> mutate { copy(isExportToGallery = value) } } }
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

    /** On means a few connections per direct file; off means the single-stream download. */
    fun updateParallelDownload(enabled: Boolean) {
        val connections = if (enabled) SettingsDataStore.DEFAULT_PARALLEL_CONNECTIONS else 1
        mutate { copy(parallelConnections = connections) }
        viewModelScope.launch {
            runCatching { settingsDataStore.setParallelConnections(connections) }
        }
    }

    fun updateWifiOnly(enabled: Boolean) {
        mutate { copy(isWifiOnly = enabled) }
        viewModelScope.launch {
            runCatching { settingsDataStore.setWifiOnly(enabled) }
        }
    }

    fun updateExportToGallery(enabled: Boolean) {
        mutate { copy(isExportToGallery = enabled) }
        viewModelScope.launch {
            runCatching { settingsDataStore.setExportToGallery(enabled) }
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
