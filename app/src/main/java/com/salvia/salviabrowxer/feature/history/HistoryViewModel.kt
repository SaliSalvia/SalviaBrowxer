package com.salvia.salviabrowxer.feature.history

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salvia.salviabrowxer.R
import com.salvia.salviabrowxer.core.database.entities.HistoryEntity
import com.salvia.salviabrowxer.data.repository.HistoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HistoryUiState(
    val query: String = "",
    val entries: List<HistoryEntity> = emptyList(),
    val isLoading: Boolean = true
) {
    val isEmpty: Boolean get() = !isLoading && entries.isEmpty()
}

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val historyRepository: HistoryRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            query
                .combine(historyRepository.getAllHistory()) { text, all -> text to all }
                .map { (text, all) ->
                    val trimmed = text.trim()
                    val matches = if (trimmed.isEmpty()) all else all.filter {
                        it.title.contains(trimmed, true) || it.url.contains(trimmed, true)
                    }
                    HistoryUiState(query = text, entries = matches.sortedByDescending { it.visitedAt }, isLoading = false)
                }
                .collect { _uiState.value = it }
        }
    }

    fun updateQuery(value: String) { query.value = value }

    fun deleteEntry(id: String) {
        viewModelScope.launch(Dispatchers.IO) { runCatching { historyRepository.deleteHistory(id) } }
    }

    fun clearAll() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { historyRepository.deleteAllHistory() }
            _messages.trySend(context.getString(R.string.settings_history_cleared))
        }
    }
}
