package com.salvia.salviabrowxer.feature.browser

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.salvia.salviabrowxer.core.database.entities.DownloadEntity
import com.salvia.salviabrowxer.core.model.MediaCandidate
import com.salvia.salviabrowxer.core.model.MediaCandidate.MediaSource
import com.salvia.salviabrowxer.data.datastore.SettingsDataStore
import com.salvia.salviabrowxer.data.repository.BookmarkRepository
import com.salvia.salviabrowxer.data.repository.DownloadRepository
import com.salvia.salviabrowxer.data.repository.HistoryRepository
import com.salvia.salviabrowxer.media.detector.MediaDetector
import com.salvia.salviabrowxer.media.resolver.MediaResolver
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule

@OptIn(ExperimentalCoroutinesApi::class)
class BrowserViewModelTest {

    @get:Rule
    val instantTaskExecutorRule: TestRule = InstantTaskExecutorRule()

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var viewModel: BrowserViewModel

    private val historyRepository: HistoryRepository = mockk(relaxed = true)
    private val bookmarkRepository: BookmarkRepository = mockk(relaxed = true)
    private val downloadRepository: DownloadRepository = mockk(relaxed = true)
    private val settingsDataStore: SettingsDataStore = mockk(relaxed = true)
    private val mediaDetector: MediaDetector = mockk(relaxed = true)
    private val mediaResolver: MediaResolver = mockk(relaxed = true)
    private val context: Context = mockk(relaxed = true)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { settingsDataStore.homepage } returns flowOf("https://www.google.com")
        every { settingsDataStore.searchEngine } returns flowOf("Google")
        every { settingsDataStore.isJavaScriptEnabled } returns flowOf(true)
        every { settingsDataStore.areCookiesEnabled } returns flowOf(true)
        every { settingsDataStore.isDesktopSite } returns flowOf(false)
        every { settingsDataStore.floatingButtonX } returns flowOf(0f)
        every { settingsDataStore.floatingButtonY } returns flowOf(0f)
        every { downloadRepository.getDownloadsByStates(any()) } returns
            flowOf(emptyList<DownloadEntity>())

        viewModel = BrowserViewModel(
            historyRepository = historyRepository,
            bookmarkRepository = bookmarkRepository,
            downloadRepository = downloadRepository,
            settingsDataStore = settingsDataStore,
            mediaDetector = mediaDetector,
            mediaResolver = mediaResolver,
            context = context
        )
    }

    @Test
    fun `a bare host in the address bar becomes an https url`() = runTest {
        viewModel.onAddressInputChange("example.com")
        viewModel.loadFromAddressBar()

        assertEquals("https://example.com", viewModel.uiState.value.url)
    }

    @Test
    fun `anything that is not a host is sent to the search engine`() {
        viewModel.loadFromAddressBar("best salvia tea")

        val url = viewModel.uiState.value.url
        assertTrue(url.startsWith("https://www.google.com/search?q="))
        assertTrue(url.contains("best+salvia+tea"))
    }

    @Test
    fun `an explicit scheme is kept as typed`() {
        viewModel.loadFromAddressBar("http://example.org/page")

        assertEquals("http://example.org/page", viewModel.uiState.value.url)
    }

    @Test
    fun `home navigation goes back to the configured homepage`() {
        viewModel.loadFromAddressBar("https://example.com/article")
        viewModel.goHome()

        assertEquals("https://www.google.com", viewModel.uiState.value.url)
    }

    @Test
    fun `createNewTab adds a tab and makes it current`() {
        val initialTabs = viewModel.uiState.value.tabs
        assertTrue(initialTabs.isNotEmpty())

        viewModel.createNewTab("https://example.com")

        val state = viewModel.uiState.value
        assertEquals(initialTabs.size + 1, state.tabs.size)
        assertEquals(state.tabs.last().id, state.currentTabId)
        assertEquals("https://example.com", state.url)
    }

    @Test
    fun `switchTab and closeTab keep the current tab consistent`() {
        viewModel.createNewTab("https://example.com")
        val firstTabId = viewModel.uiState.value.tabs.first().id
        val secondTabId = viewModel.uiState.value.tabs.last().id

        viewModel.switchTab(secondTabId)
        assertEquals(secondTabId, viewModel.uiState.value.currentTabId)

        viewModel.closeTab(secondTabId)
        assertEquals(1, viewModel.uiState.value.tabs.size)
        assertEquals(firstTabId, viewModel.uiState.value.currentTabId)
    }

    @Test
    fun `detected media is merged without duplicates`() {
        val candidate = MediaCandidate(
            pageUrl = "https://example.com",
            mediaUrl = "https://example.com/video.mp4",
            extension = "mp4",
            source = MediaSource.DOM
        )

        assertFalse(viewModel.uiState.value.isMediaDetected)

        viewModel.onMediaIntercepted(candidate)
        viewModel.onMediaIntercepted(candidate)

        assertEquals(1, viewModel.uiState.value.detectedMedia.size)
        assertTrue(viewModel.uiState.value.isMediaDetected)
    }

    @Test
    fun `quality sheet can be opened for the detected media and closed again`() {
        viewModel.onMediaIntercepted(
            MediaCandidate(
                pageUrl = "https://example.com",
                mediaUrl = "https://example.com/video.mp4",
                title = "Example video",
                extension = "mp4",
                source = MediaSource.DOM
            )
        )

        viewModel.openQualitySheet()
        val sheet = viewModel.uiState.value.qualitySheet
        assertNotNull(sheet)
        assertEquals("Example video", sheet?.mediaInfo?.title)
        assertTrue(sheet?.mediaInfo?.combinedFormats?.isNotEmpty() == true)

        viewModel.closeQualitySheet()
        assertNull(viewModel.uiState.value.qualitySheet)
    }

    @Test
    fun `page lifecycle updates the ui state and records history`() {
        viewModel.onPageStarted("https://example.com/page")
        assertTrue(viewModel.uiState.value.isLoading)

        viewModel.onPageFinished("https://example.com/page", "Example page")

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals("Example page", state.title)
        assertEquals("https://example.com/page", state.url)
        coVerify(exactly = 1) { historyRepository.addHistory(any()) }
    }

    @Test
    fun `private mode suppresses history`() {
        viewModel.setPrivateMode(true)

        viewModel.onPageFinished("https://example.com/secret", "Secret")

        coVerify(exactly = 0) { historyRepository.addHistory(any()) }
    }

    @Test
    fun `dragging the floating button persists its position`() {
        viewModel.saveFabPosition(-120f, -340f)

        assertEquals(-120f, viewModel.uiState.value.fabPosition.x)
        assertEquals(-340f, viewModel.uiState.value.fabPosition.y)
        coVerify(exactly = 1) { settingsDataStore.setFloatingButtonPosition(-120f, -340f) }
    }
}
