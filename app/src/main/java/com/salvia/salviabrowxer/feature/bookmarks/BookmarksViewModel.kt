package com.salvia.salviabrowxer.feature.bookmarks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salvia.salviabrowxer.core.database.entities.BookmarkEntity
import com.salvia.salviabrowxer.data.repository.BookmarkRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BookmarksUiState(
    val query: String = "",
    val bookmarks: List<BookmarkEntity> = emptyList(),
    val isLoading: Boolean = true
) {
    val isEmpty: Boolean get() = !isLoading && bookmarks.isEmpty()
}

@HiltViewModel
class BookmarksViewModel @Inject constructor(
    private val bookmarkRepository: BookmarkRepository
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val _uiState = MutableStateFlow(BookmarksUiState())
    val uiState: StateFlow<BookmarksUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            query
                .combine(bookmarkRepository.getAllBookmarks()) { text, all -> text to all }
                .map { (text, all) ->
                    val trimmed = text.trim()
                    // Filter in memory so one query flow covers both the unfiltered and the
                    // searched list without a second database stream.
                    val matches = if (trimmed.isEmpty()) all else all.filter {
                        it.title.contains(trimmed, true) || it.url.contains(trimmed, true)
                    }
                    BookmarksUiState(query = text, bookmarks = matches, isLoading = false)
                }
                .collect { _uiState.value = it }
        }
    }

    fun updateQuery(value: String) { query.value = value }

    fun deleteBookmark(id: String) {
        viewModelScope.launch(Dispatchers.IO) { runCatching { bookmarkRepository.deleteBookmark(id) } }
    }
}
