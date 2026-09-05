package com.salvia.salviabrowxer.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salvia.salviabrowxer.core.datastore.SettingsDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    private val _searchEngine = MutableStateFlow("Google")
    val searchEngine: StateFlow<String> = _searchEngine.asStateFlow()

    private val _homepage = MutableStateFlow("https://www.google.com")
    val homepage: StateFlow<String> = _homepage.asStateFlow()

    private val _isDesktopSite = MutableStateFlow(false)
    val isDesktopSite: StateFlow<Boolean> = _isDesktopSite.asStateFlow()

    private val _isJavaScriptEnabled = MutableStateFlow(true)
    val isJavaScriptEnabled: StateFlow<Boolean> = _isJavaScriptEnabled.asStateFlow()

    private val _areCookiesEnabled = MutableStateFlow(true)
    val areCookiesEnabled: StateFlow<Boolean> = _areCookiesEnabled.asStateFlow()

    private val _downloadDirectory = MutableStateFlow("")
    val downloadDirectory: StateFlow<String> = _downloadDirectory.asStateFlow()

    private val _maxSimultaneousDownloads = MutableStateFlow(3)
    val maxSimultaneousDownloads: StateFlow<Int> = _maxSimultaneousDownloads.asStateFlow()

    private val _isWifiOnly = MutableStateFlow(false)
    val isWifiOnly: StateFlow<Boolean> = _isWifiOnly.asStateFlow()

    private val _isDarkTheme = MutableStateFlow(true)
    val isDarkTheme: StateFlow<Boolean> = _isDarkTheme.asStateFlow()

    private val _floatingButtonSize = MutableStateFlow(56)
    val floatingButtonSize: StateFlow<Int> = _floatingButtonSize.asStateFlow()

    private val _floatingButtonX = MutableStateFlow(0f)
    val floatingButtonX: StateFlow<Float> = _floatingButtonX.asStateFlow()

    private val _floatingButtonY = MutableStateFlow(0f)
    val floatingButtonY: StateFlow<Float> = _floatingButtonY.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch(Dispatchers.IO) {
            launch { settingsDataStore.searchEngine.collectLatest { _searchEngine.value = it } }
            launch { settingsDataStore.homepage.collectLatest { _homepage.value = it } }
            launch { settingsDataStore.isDesktopSite.collectLatest { _isDesktopSite.value = it } }
            launch { settingsDataStore.isJavaScriptEnabled.collectLatest { _isJavaScriptEnabled.value = it } }
            launch { settingsDataStore.areCookiesEnabled.collectLatest { _areCookiesEnabled.value = it } }
            launch { settingsDataStore.downloadDirectory.collectLatest { _downloadDirectory.value = it } }
            launch { settingsDataStore.maxSimultaneousDownloads.collectLatest { _maxSimultaneousDownloads.value = it } }
            launch { settingsDataStore.isWifiOnly.collectLatest { _isWifiOnly.value = it } }
            launch { settingsDataStore.isDarkTheme.collectLatest { _isDarkTheme.value = it } }
            launch { settingsDataStore.floatingButtonSize.collectLatest { _floatingButtonSize.value = it } }
            launch { settingsDataStore.floatingButtonX.collectLatest { _floatingButtonX.value = it } }
            launch { settingsDataStore.floatingButtonY.collectLatest { _floatingButtonY.value = it } }
        }
    }

    fun updateSearchEngine(engine: String) {
        viewModelScope.launch(Dispatchers.IO) {
            settingsDataStore.setSearchEngine(engine)
        }
    }

    fun updateHomepage(url: String) {
        viewModelScope.launch(Dispatchers.IO) {
            settingsDataStore.setHomepage(url)
        }
    }

    fun updateDesktopSite(isDesktop: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            settingsDataStore.setDesktopSite(isDesktop)
        }
    }

    fun updateJavaScriptEnabled(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            settingsDataStore.setJavaScriptEnabled(enabled)
        }
    }

    fun updateCookiesEnabled(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            settingsDataStore.setCookiesEnabled(enabled)
        }
    }

    fun updateDownloadDirectory(directory: String) {
        viewModelScope.launch(Dispatchers.IO) {
            settingsDataStore.setDownloadDirectory(directory)
        }
    }

    fun updateMaxSimultaneousDownloads(count: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            settingsDataStore.setMaxSimultaneousDownloads(count)
        }
    }

    fun updateWifiOnly(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            settingsDataStore.setWifiOnly(enabled)
        }
    }

    fun updateFloatingButtonPosition(x: Float, y: Float) {
        viewModelScope.launch(Dispatchers.IO) {
            settingsDataStore.setFloatingButtonPosition(x, y)
        }
    }

    fun updateFloatingButtonSize(size: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            settingsDataStore.setFloatingButtonSize(size)
        }
    }
}