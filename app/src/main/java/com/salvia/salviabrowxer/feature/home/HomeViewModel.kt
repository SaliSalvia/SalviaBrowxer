package com.salvia.salviabrowxer.feature.home

import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salvia.salviabrowxer.R
import com.salvia.salviabrowxer.core.database.entities.DownloadEntity
import com.salvia.salviabrowxer.core.model.DownloadState
import com.salvia.salviabrowxer.core.model.PastedLink
import com.salvia.salviabrowxer.core.model.PastedLinkKind
import com.salvia.salviabrowxer.data.repository.DownloadRepository
import com.salvia.salviabrowxer.service.DownloadService
import com.salvia.salviabrowxer.ui.utils.SharedLinkParser
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val input: String = "",
    /** A link the clipboard is offering, until the user uses it or dismisses it. */
    val clipboardUrl: String? = null,
    val clipboardIsMediaFile: Boolean = false,
    /** Transfers in progress, newest first. Capped: the home screen is a preview, not the queue. */
    val active: List<DownloadEntity> = emptyList(),
    /** Every in-flight transfer, including the ones [active] does not show. */
    val inFlightCount: Int = 0,
    val isLoading: Boolean = true
) {
    /**
     * Drives the primary button's label: a file can be fetched, a page has to be opened.
     *
     * Uses the same classifier as submission, so the button never promises a download for something
     * that would then be rejected.
     */
    val inputIsMediaFile: Boolean get() = PastedLink.classify(input) == PastedLinkKind.MEDIA_FILE
}

/**
 * The home screen: what the user pasted, and what is downloading right now.
 *
 * Clipboard detection lives here rather than in the composable because reading the clipboard is a
 * platform side effect with a real rule attached — only a foreground app is allowed to do it, it
 * must never overwrite what the user is typing, and it must not re-offer a link that was already
 * seen. Keeping that in one testable place beats scattering it through recomposition.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val downloadRepository: DownloadRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    /**
     * Links the user submitted, for the browser to open. Only the URL travels: whether it is a file
     * or a page is decided in one place — `BrowserViewModel.openPastedLink` — so the answer cannot
     * drift between the button's label and what the app then does.
     */
    private val _openRequests = Channel<String>(Channel.BUFFERED)
    val openRequests: Flow<String> = _openRequests.receiveAsFlow()

    /** The last link the clipboard offered, so a resume never re-offers the same one. */
    private var lastSuggested: String? = null

    /** Newest first, matching the repository's own ordering. */
    private val inFlightStates = setOf(
        DownloadState.DOWNLOADING,
        DownloadState.QUEUED,
        DownloadState.RETRYING,
        DownloadState.RESOLVING,
        DownloadState.PREPARING,
        DownloadState.PROCESSING,
        DownloadState.PAUSED
    )

    init {
        viewModelScope.launch(Dispatchers.IO) {
            downloadRepository.getAllDownloads().distinctUntilChanged().collectLatest { downloads ->
                val inFlight = downloads.filter { it.status in inFlightStates }
                _uiState.update {
                    it.copy(isLoading = false, active = inFlight.take(ACTIVE_PREVIEW_LIMIT), inFlightCount = inFlight.size)
                }
            }
        }
    }

    fun onInputChange(value: String) { _uiState.update { it.copy(input = value) } }

    fun clearInput() { _uiState.update { it.copy(input = "") } }

    /** The explicit paste button: fills the field from the clipboard, or says it is empty. */
    fun pasteFromClipboard() {
        val url = SharedLinkParser.firstUrl(clipboardText())
        if (url == null) {
            _messages.trySend(context.getString(R.string.home_empty_clipboard))
            return
        }
        lastSuggested = url
        _uiState.update { it.copy(input = url, clipboardUrl = null) }
    }

    /**
     * Called when the screen comes to the foreground, which is the only moment Android will hand
     * over the clipboard. A link already offered, or something that is not a web link at all, is
     * left alone — silently rewriting the field on every resume would be worse than not helping.
     */
    fun onForeground() {
        val url = SharedLinkParser.firstUrl(clipboardText()) ?: return
        if (url == lastSuggested) return
        if (PastedLink.classify(url) == null) return
        lastSuggested = url
        val isMediaFile = PastedLink.isMediaFile(url)
        _uiState.update { state ->
            state.copy(
                // Prefill only a blank field: the user's own typing outranks the clipboard.
                input = if (state.input.isBlank()) url else state.input,
                clipboardUrl = url,
                clipboardIsMediaFile = isMediaFile
            )
        }
    }

    fun useClipboardSuggestion() {
        val url = _uiState.value.clipboardUrl ?: return
        _uiState.update { it.copy(input = url, clipboardUrl = null) }
    }

    fun dismissClipboardSuggestion() { _uiState.update { it.copy(clipboardUrl = null) } }

    /**
     * Validates the field and hands the link on. A direct media URL becomes a download; anything
     * else is a page, and the app has no extractors, so it has to be loaded before its media can be
     * found. Which of the two happens is decided downstream, not here.
     */
    fun submitInput() {
        val url = _uiState.value.input.trim()
        if (PastedLink.classify(url) == null) {
            _messages.trySend(context.getString(R.string.home_invalid_link))
            return
        }
        lastSuggested = url
        _uiState.update { it.copy(input = url, clipboardUrl = null) }
        _openRequests.trySend(url)
    }

    // region row actions — the same contract the downloads screen offers, so a row works here too
    fun pauseDownload(downloadId: String) =
        transition(downloadId, DownloadState.PAUSED) { ctx, id -> DownloadService.pauseDownload(ctx, id) }

    fun resumeDownload(downloadId: String) =
        transition(downloadId, DownloadState.QUEUED) { ctx, id -> DownloadService.enqueueDownload(ctx, id) }

    fun cancelDownload(downloadId: String) =
        transition(downloadId, DownloadState.CANCELLED) { ctx, id -> DownloadService.cancelDownload(ctx, id) }

    fun retryDownload(downloadId: String) =
        transition(downloadId, DownloadState.QUEUED) { ctx, id -> DownloadService.enqueueDownload(ctx, id) }

    private fun transition(downloadId: String, state: DownloadState, service: (Context, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            downloadRepository.updateDownloadState(downloadId, state)
            runCatching { service(context, downloadId) }
        }
    }
    // endregion

    private fun clipboardText(): String? = runCatching {
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return null
        val clip = manager.primaryClip ?: return null
        if (clip.itemCount == 0) return null
        clip.getItemAt(0).coerceToText(context)?.toString()
    }.getOrNull()

    companion object {
        /** Rows the home screen shows before it defers to the downloads screen. */
        const val ACTIVE_PREVIEW_LIMIT = 3
    }
}
