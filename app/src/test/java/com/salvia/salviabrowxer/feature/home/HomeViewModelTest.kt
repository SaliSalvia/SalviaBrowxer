package com.salvia.salviabrowxer.feature.home

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.salvia.salviabrowxer.R
import com.salvia.salviabrowxer.data.repository.DownloadRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule

/**
 * The clipboard is the one part of this screen that cannot be checked by looking at the code: Android
 * only hands it to a foreground app, and the rules around it (never clobber what the user typed,
 * never offer the same link twice) are exactly the kind of thing that quietly regresses.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    @get:Rule
    val instantTaskExecutorRule: TestRule = InstantTaskExecutorRule()

    private val testDispatcher = UnconfinedTestDispatcher()
    private val repository: DownloadRepository = mockk(relaxed = true)
    private val context: Context = mockk(relaxed = true)
    private lateinit var viewModel: HomeViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { repository.getAllDownloads() } returns flowOf(emptyList())
        every { context.getString(R.string.home_invalid_link) } returns "not a link"
        every { context.getString(R.string.home_empty_clipboard) } returns "empty clipboard"
        viewModel = HomeViewModel(repository, context)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `a copied media link prefills an empty field`() {
        stubClipboard("watch this https://cdn.example.com/clip.mp4 right now")
        viewModel.onForeground()

        val state = viewModel.uiState.value
        assertEquals("https://cdn.example.com/clip.mp4", state.input)
        assertEquals("https://cdn.example.com/clip.mp4", state.clipboardUrl)
        assertTrue(state.clipboardIsMediaFile)
        assertTrue(state.inputIsMediaFile)
    }

    @Test
    fun `a copied link does not overwrite what the user typed`() {
        stubClipboard("https://cdn.example.com/clip.mp4")
        viewModel.onInputChange("https://www.example.com/post/1")
        viewModel.onForeground()

        val state = viewModel.uiState.value
        assertEquals("https://www.example.com/post/1", state.input)
        // It is still offered, so the user can choose it.
        assertEquals("https://cdn.example.com/clip.mp4", state.clipboardUrl)
    }

    @Test
    fun `a copied page link is offered but not called a media file`() {
        stubClipboard("https://www.example.com/post/1")
        viewModel.onForeground()

        val state = viewModel.uiState.value
        assertEquals("https://www.example.com/post/1", state.clipboardUrl)
        assertFalse(state.clipboardIsMediaFile)
    }

    @Test
    fun `the same link is only offered once`() {
        stubClipboard("https://cdn.example.com/clip.mp4")
        viewModel.onForeground()
        assertEquals("https://cdn.example.com/clip.mp4", viewModel.uiState.value.clipboardUrl)

        viewModel.dismissClipboardSuggestion()
        viewModel.onForeground()

        assertNull(viewModel.uiState.value.clipboardUrl)
    }

    @Test
    fun `clipboard prose with no link is ignored`() {
        stubClipboard("just a note to myself")
        viewModel.onForeground()

        assertNull(viewModel.uiState.value.clipboardUrl)
        assertEquals("", viewModel.uiState.value.input)
    }

    @Test
    fun `a non-web scheme in the clipboard is ignored`() {
        stubClipboard("mailto:someone@example.com")
        viewModel.onForeground()

        assertNull(viewModel.uiState.value.clipboardUrl)
    }

    @Test
    fun `using the suggestion fills the field and clears the offer`() {
        stubClipboard("https://cdn.example.com/clip.mp4")
        viewModel.onForeground()
        viewModel.onInputChange("replaced by the user")

        viewModel.useClipboardSuggestion()

        assertEquals("https://cdn.example.com/clip.mp4", viewModel.uiState.value.input)
        assertNull(viewModel.uiState.value.clipboardUrl)
    }

    @Test
    fun `the paste button says so when the clipboard is empty`() = runTest {
        stubClipboard("")

        viewModel.pasteFromClipboard()

        assertEquals("empty clipboard", viewModel.messages.first())
    }

    @Test
    fun `submitting a valid link hands it to the browser`() = runTest {
        viewModel.onInputChange("  https://cdn.example.com/clip.mp4  ")

        viewModel.submitInput()

        assertEquals("https://cdn.example.com/clip.mp4", viewModel.openRequests.first())
        // The field is normalised, so the link the browser got is the link the user sees.
        assertEquals("https://cdn.example.com/clip.mp4", viewModel.uiState.value.input)
    }

    @Test
    fun `submitting something that is not a link explains why`() = runTest {
        viewModel.onInputChange("not a url")

        viewModel.submitInput()

        assertEquals("not a link", viewModel.messages.first())
        assertEquals("not a url", viewModel.uiState.value.input)
    }

    @Test
    fun `submitting uses up the clipboard offer`() = runTest {
        stubClipboard("https://cdn.example.com/clip.mp4")
        viewModel.onForeground()

        viewModel.submitInput()

        assertEquals("https://cdn.example.com/clip.mp4", viewModel.openRequests.first())
        assertNull(viewModel.uiState.value.clipboardUrl)
    }

    /** Fakes the platform clipboard: a real one is not readable from a JVM unit test. */
    private fun stubClipboard(text: String) {
        val item: ClipData.Item = mockk(relaxed = true)
        val clip: ClipData = mockk(relaxed = true)
        val manager: ClipboardManager = mockk(relaxed = true)
        every { item.coerceToText(any()) } returns text
        every { clip.itemCount } returns 1
        every { clip.getItemAt(0) } returns item
        every { manager.primaryClip } returns clip
        every { context.getSystemService(Context.CLIPBOARD_SERVICE) } returns manager
    }
}
