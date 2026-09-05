package com.salvia.salviabrowxer.feature.browser

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salvia.salviabrowxer.core.database.entities.BookmarkEntity
import com.salvia.salviabrowxer.core.database.entities.HistoryEntity
import com.salvia.salviabrowxer.core.media.detector.MediaDetector
import com.salvia.salviabrowxer.core.model.MediaCandidate
import com.salvia.salviabrowxer.core.model.Tab
import com.salvia.salviabrowxer.data.repository.BookmarkRepository
import com.salvia.salviabrowxer.data.repository.HistoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BrowserViewModel @Inject constructor(
    private val historyRepository: HistoryRepository,
    private val bookmarkRepository: BookmarkRepository,
    private val mediaDetector: MediaDetector
) : ViewModel() {

    private val _tabs = mutableStateListOf<Tab>()
    val tabs: List<Tab> = _tabs

    private val _currentTabId = mutableStateOf<String?>(null)
    val currentTabId: String? = _currentTabId.value

    private val _currentUrl = mutableStateOf("")
    val currentUrl: String = _currentUrl.value

    private val _isLoading = mutableStateOf(false)
    val isLoading: Boolean = _isLoading.value

    private val _canGoBack = mutableStateOf(false)
    val canGoBack: Boolean = _canGoBack.value

    private val _canGoForward = mutableStateOf(false)
    val canGoForward: Boolean = _canGoForward.value

    private val _detectedMedia = mutableStateListOf<MediaCandidate>()
    val detectedMedia: List<MediaCandidate> = _detectedMedia

    private val _isMediaDetected = mutableStateOf(false)
    val isMediaDetected: Boolean = _isMediaDetected.value

    private val _isPrivateMode = mutableStateOf(false)
    val isPrivateMode: Boolean = _isPrivateMode.value

    init {
        createNewTab()
    }

    fun createNewTab(url: String = "", isPrivate: Boolean = false) {
        val newTab = Tab(
            title = "New Tab",
            url = url,
            isPrivate = isPrivate || _isPrivateMode.value,
            position = _tabs.size
        )
        _tabs.add(newTab)
        _currentTabId.value = newTab.id
        if (url.isNotEmpty()) {
            _currentUrl.value = url
        }
    }

    fun switchTab(tabId: String) {
        _currentTabId.value = tabId
        _tabs.find { it.id == tabId }?.let { tab ->
            _currentUrl.value = tab.url
        }
    }

    fun closeTab(tabId: String) {
        val tabIndex = _tabs.indexOfFirst { it.id == tabId }
        if (tabIndex != -1) {
            _tabs.removeAt(tabIndex)
            if (_currentTabId.value == tabId) {
                if (_tabs.isNotEmpty()) {
                    _currentTabId.value = _tabs[minOf(tabIndex, _tabs.size - 1)].id
                    _currentUrl.value = _tabs[minOf(tabIndex, _tabs.size - 1)].url
                } else {
                    _currentTabId.value = null
                    _currentUrl.value = ""
                }
            }
        }
    }

    fun updateUrl(url: String) {
        _currentUrl.value = url
        _tabs.find { it.id == _currentTabId.value }?.let { tab ->
            val index = _tabs.indexOf(tab)
            _tabs[index] = tab.copy(url = url, lastVisited = System.currentTimeMillis())
        }
    }

    fun updateTitle(title: String) {
        _tabs.find { it.id == _currentTabId.value }?.let { tab ->
            val index = _tabs.indexOf(tab)
            _tabs[index] = tab.copy(title = title)
        }
    }

    fun updateCanGoBack(canGoBack: Boolean) {
        _canGoBack.value = canGoBack
    }

    fun updateCanGoForward(canGoForward: Boolean) {
        _canGoForward.value = canGoForward
    }

    fun updateLoading(isLoading: Boolean) {
        _isLoading.value = isLoading
    }

    fun setPrivateMode(isPrivate: Boolean) {
        _isPrivateMode.value = isPrivate
    }

    fun detectMediaInPage(pageUrl: String, html: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            val candidates = mediaDetector.detect(pageUrl, html)
            _detectedMedia.clear()
            _detectedMedia.addAll(candidates)
            _isMediaDetected.value = candidates.isNotEmpty()
        }
    }

    fun addBookmark(title: String, url: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val bookmark = BookmarkEntity(
                title = title,
                url = url
            )
            bookmarkRepository.addBookmark(bookmark)
        }
    }

    fun removeBookmark(url: String) {
        viewModelScope.launch(Dispatchers.IO) {
            bookmarkRepository.getAllBookmarks().collectLatest { bookmarks ->
                bookmarks.find { it.url == url }?.let { bookmark ->
                    bookmarkRepository.deleteBookmark(bookmark.id)
                }
            }
        }
    }

    fun addHistoryEntry(url: String, title: String) {
        if (_isPrivateMode.value) return

        viewModelScope.launch(Dispatchers.IO) {
            val history = HistoryEntity(
                url = url,
                title = title,
                visitedAt = System.currentTimeMillis()
            )
            historyRepository.addHistory(history)
        }
    }
}