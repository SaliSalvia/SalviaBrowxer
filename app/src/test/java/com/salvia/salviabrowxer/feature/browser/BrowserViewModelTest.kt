package com.salvia.salviabrowxer.feature.browser

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.salvia.salviabrowxer.core.database.entities.DownloadEntity
import com.salvia.salviabrowxer.core.model.MediaCandidate
import com.salvia.salviabrowxer.core.model.MediaCandidate.MediaSource
import com.salvia.salviabrowxer.core.model.MediaFormat
import com.salvia.salviabrowxer.data.datastore.SettingsDataStore
import com.salvia.salviabrowxer.data.repository.BookmarkRepository
import com.salvia.salviabrowxer.data.repository.DownloadRepository
import com.salvia.salviabrowxer.data.repository.HistoryRepository
import com.salvia.salviabrowxer.media.detector.MediaDetector
import com.salvia.salviabrowxer.media.resolver.MediaResolver
import com.salvia.salviabrowxer.ui.utils.UnsupportedMedia
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
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
        every { settingsDataStore.isCleartextAllowed } returns flowOf(false)
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
    fun `an https url is kept as typed`() {
        viewModel.loadFromAddressBar("https://example.org/page")

        assertEquals("https://example.org/page", viewModel.uiState.value.url)
    }

    @Test
    fun `cleartext navigation is refused and explained while the setting is off`() {
        viewModel.loadFromAddressBar("http://example.org/page")

        val state = viewModel.uiState.value
        assertEquals("http://example.org/page", state.blockedCleartextUrl)
        assertFalse("http must not become the loaded page", state.url.startsWith("http://"))
        assertFalse(state.isLoading)
        coVerify(exactly = 0) { settingsDataStore.setCleartextAllowed(true) }
    }

    @Test
    fun `allowing cleartext persists the setting and loads the requested url`() {
        viewModel.loadFromAddressBar("http://example.org/page")

        viewModel.allowCleartextAndRetry()

        val state = viewModel.uiState.value
        assertTrue(state.isCleartextAllowed)
        assertNull(state.blockedCleartextUrl)
        assertEquals("http://example.org/page", state.url)
        coVerify(exactly = 1) { settingsDataStore.setCleartextAllowed(true) }
    }

    @Test
    fun `cleartext allowed keeps loading http normally afterwards`() {
        viewModel.loadFromAddressBar("http://example.org/first")
        viewModel.allowCleartextAndRetry()

        viewModel.loadFromAddressBar("http://example.org/second")

        assertEquals("http://example.org/second", viewModel.uiState.value.url)
        assertNull(viewModel.uiState.value.blockedCleartextUrl)
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
    fun `a background tab finishing does not hijack the address bar`() {
        viewModel.createNewTab("https://example.com/second")
        val firstTabId = viewModel.uiState.value.tabs.first().id
        val secondTabId = viewModel.uiState.value.tabs.last().id

        viewModel.onPageStarted(firstTabId, "https://example.com/first")
        viewModel.onPageFinished(firstTabId, "https://example.com/first", "First page")

        val state = viewModel.uiState.value
        assertEquals(secondTabId, state.currentTabId)
        assertEquals("https://example.com/second", state.url)
        val firstTab = state.tabs.first { it.id == firstTabId }
        assertEquals("https://example.com/first", firstTab.url)
        assertEquals("First page", firstTab.title)
        // The background visit is still real history, exactly once.
        coVerify(exactly = 1) { historyRepository.addHistory(any()) }
    }

    @Test
    fun `progress from a background tab never moves the visible progress bar`() {
        viewModel.createNewTab("https://example.com/second")
        val firstTabId = viewModel.uiState.value.tabs.first().id

        viewModel.onProgressChanged(firstTabId, 42)

        assertFalse(viewModel.uiState.value.progress == 42)
    }

    @Test
    fun `a hibernated tab keeps its url and title and is restored on activation`() {
        viewModel.createNewTab("https://example.com/article")
        val tabId = viewModel.uiState.value.currentTabId!!
        viewModel.onPageFinished(tabId, "https://example.com/article", "Article")

        viewModel.onTabHibernated(tabId, "https://example.com/article", "Article")

        val state = viewModel.uiState.value
        assertTrue(state.hibernatedTabIds.contains(tabId))
        assertEquals("https://example.com/article", state.tabs.first { it.id == tabId }.url)
        assertEquals("Article", state.tabs.first { it.id == tabId }.title)

        viewModel.onTabActivated(tabId)
        assertFalse(viewModel.uiState.value.hibernatedTabIds.contains(tabId))
    }

    @Test
    fun `closing the last tab opens a fresh one instead of a dead browser`() {
        val onlyTab = viewModel.uiState.value.currentTabId!!

        viewModel.closeTab(onlyTab)

        val state = viewModel.uiState.value
        assertEquals(1, state.tabs.size)
        assertNotNull(state.currentTabId)
    }

    @Test
    fun `closing a tab drops its hibernation flag`() {
        viewModel.createNewTab("https://example.com/gone")
        val tabId = viewModel.uiState.value.currentTabId!!
        viewModel.onTabHibernated(tabId, "https://example.com/gone", "Gone")

        viewModel.closeTab(tabId)

        assertFalse(viewModel.uiState.value.hibernatedTabIds.contains(tabId))
    }

    @Test
    fun `the tab switcher opens and selecting a tab closes it`() {
        viewModel.createNewTab("https://example.com/second")
        val firstTabId = viewModel.uiState.value.tabs.first().id

        viewModel.openTabSwitcher()
        assertTrue(viewModel.uiState.value.isTabSwitcherVisible)

        viewModel.switchTab(firstTabId)
        assertFalse(viewModel.uiState.value.isTabSwitcherVisible)
        assertEquals(firstTabId, viewModel.uiState.value.currentTabId)
    }

    @Test
    fun `a bookmark or history entry loads in the current tab`() {
        val tabsBefore = viewModel.uiState.value.tabs.size
        val currentTabId = viewModel.uiState.value.currentTabId

        viewModel.openInCurrentTab("https://example.com/bookmarked")

        val state = viewModel.uiState.value
        assertEquals(tabsBefore, state.tabs.size)
        assertEquals(currentTabId, state.currentTabId)
        assertEquals("https://example.com/bookmarked", state.url)
    }

    @Test
    fun `a shared link opens in a new tab`() {
        val tabsBefore = viewModel.uiState.value.tabs.size

        viewModel.openExternalUrl("https://example.com/shared")

        val state = viewModel.uiState.value
        assertEquals(tabsBefore + 1, state.tabs.size)
        assertEquals("https://example.com/shared", state.url)
    }

    @Test
    fun `find in page keeps its query and reports matches for the current tab`() {
        assertNull(viewModel.uiState.value.findInPage)

        viewModel.openFindInPage()
        viewModel.updateFindQuery("salvia")
        assertEquals("salvia", viewModel.uiState.value.findInPage?.query)

        viewModel.onFindResult(viewModel.uiState.value.currentTabId, 4, 2)
        assertEquals(4, viewModel.uiState.value.findInPage?.matches)
        assertEquals(2, viewModel.uiState.value.findInPage?.activeMatch)

        viewModel.closeFindInPage()
        assertNull(viewModel.uiState.value.findInPage)
    }

    @Test
    fun `desktop site is a real persisted toggle`() {
        assertFalse(viewModel.uiState.value.isDesktopSite)

        viewModel.toggleDesktopSite()

        assertTrue(viewModel.uiState.value.isDesktopSite)
        // The state flips in memory first and the write happens on Dispatchers.IO, so the
        // verification has to wait for that thread instead of assuming it has already run. The
        // immediate `coVerify` here passed or failed depending on scheduling.
        coVerify(timeout = 5_000, exactly = 1) { settingsDataStore.setDesktopSite(true) }

        viewModel.toggleDesktopSite()
        assertFalse(viewModel.uiState.value.isDesktopSite)
        coVerify(timeout = 5_000, exactly = 1) { settingsDataStore.setDesktopSite(false) }
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
    fun `the tray opens for the page's media and a row fills the quality sheet`() {
        viewModel.onMediaIntercepted(
            MediaCandidate(
                pageUrl = "https://example.com",
                mediaUrl = "https://example.com/video.mp4",
                title = "Example video",
                extension = "mp4",
                source = MediaSource.DOM
            )
        )

        viewModel.openMediaTray()
        assertTrue(viewModel.uiState.value.isMediaTrayVisible)

        val candidate = viewModel.uiState.value.detectedMedia.first()
        viewModel.openQualitySheetFor(candidate)

        // The tray hands over to the sheet rather than stacking two modals.
        assertFalse(viewModel.uiState.value.isMediaTrayVisible)
        val sheet = viewModel.uiState.value.qualitySheet
        assertNotNull(sheet)
        assertEquals("Example video", sheet?.mediaInfo?.title)
        assertNull(sheet?.unsupported)
        assertTrue(sheet?.mediaInfo?.combinedFormats?.isNotEmpty() == true)

        viewModel.closeQualitySheet()
        assertNull(viewModel.uiState.value.qualitySheet)
    }

    @Test
    fun `an empty page opens no tray`() {
        viewModel.openMediaTray()

        assertFalse(viewModel.uiState.value.isMediaTrayVisible)
    }

    @Test
    fun `an mpeg-dash candidate is explained and never probed or queued`() {
        val dash = MediaCandidate(
            pageUrl = "https://example.com/watch",
            mediaUrl = "https://example.com/manifest.mpd",
            mimeType = "application/dash+xml",
            extension = "mpd",
            source = MediaSource.DOM
        )
        viewModel.onMediaIntercepted(dash)

        viewModel.openQualitySheetFor(dash)

        val sheet = viewModel.uiState.value.qualitySheet
        assertEquals(UnsupportedMedia.DASH, sheet?.unsupported)
        assertFalse(sheet?.isResolving == true)
        assertTrue(sheet?.mediaInfo?.combinedFormats.isNullOrEmpty())
        // Nothing to probe: the sheet must not wait on a network call to say no.
        coVerify(exactly = 0) { mediaResolver.resolve(any()) }

        viewModel.handleFormatSelected(
            MediaFormat(id = dash.mediaUrl, format = "MPD", url = dash.mediaUrl, mimeType = "application/dash+xml", extension = "mpd", isVideo = true)
        )
        coVerify(exactly = 0) { downloadRepository.addDownload(any()) }
    }

    @Test
    fun `a live stream is refused before it reaches the resolver`() {
        val live = MediaCandidate(
            pageUrl = "https://example.com/live",
            mediaUrl = "https://example.com/live.m3u8",
            mimeType = "application/vnd.apple.mpegurl",
            extension = "m3u8",
            isLive = true,
            source = MediaSource.DOM
        )
        viewModel.onMediaIntercepted(live)

        assertEquals(UnsupportedMedia.LIVE, viewModel.unsupportedReasonFor(live))

        viewModel.openQualitySheetFor(live)
        assertEquals(UnsupportedMedia.LIVE, viewModel.uiState.value.qualitySheet?.unsupported)
        coVerify(exactly = 0) { mediaResolver.resolve(any()) }
    }

    @Test
    fun `the draggable download button is off until the user turns it on`() {
        assertFalse(viewModel.uiState.value.isFabAlwaysVisible)
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
    fun `switching to a private tab keeps its visits out of history`() {
        viewModel.createNewTab("https://example.com/private", isPrivate = true)

        viewModel.onPageFinished("https://example.com/private", "Private page")

        coVerify(exactly = 0) { historyRepository.addHistory(any()) }
    }

    @Test
    fun `dragging the floating button persists its position`() = runTest {
        viewModel.saveFabPosition(-120f, -340f)

        assertEquals(-120f, viewModel.uiState.value.fabPosition.x)
        assertEquals(-340f, viewModel.uiState.value.fabPosition.y)
        // saveFabPosition debounces the DataStore write by 400ms — advance virtual time so it fires
        advanceTimeBy(500)
        coVerify(exactly = 1) { settingsDataStore.setFloatingButtonPosition(-120f, -340f) }
    }
}
