package com.salvia.salviabrowxer.feature.browser

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.salvia.salviabrowxer.core.database.entities.BookmarkEntity
import com.salvia.salviabrowxer.core.database.entities.HistoryEntity
import com.salvia.salviabrowxer.core.model.MediaCandidate
import com.salvia.salviabrowxer.core.model.MediaCandidate.MediaSource
import com.salvia.salviabrowxer.core.model.Tab
import com.salvia.salviabrowxer.data.repository.BookmarkRepository
import com.salvia.salviabrowxer.data.repository.HistoryRepository
import com.salvia.salviabrowxer.media.detector.MediaDetector
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
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
    private val mockHistoryRepository: HistoryRepository = mockk()
    private val mockBookmarkRepository: BookmarkRepository = mockk()
    private val mockMediaDetector: MediaDetector = mockk()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        viewModel = BrowserViewModel(
            mockHistoryRepository,
            mockBookmarkRepository,
            mockMediaDetector
        )
    }

    @Test
    fun `createNewTab adds new tab and sets as current`() = runTest {
        val initialTabs = viewModel.tabs
        assert(initialTabs.isNotEmpty())

        viewModel.createNewTab("https://example.com")

        assert(viewModel.tabs.size == initialTabs.size + 1)
        assert(viewModel.currentTabId == viewModel.tabs.last().id)
        assert(viewModel.currentUrl == "https://example.com")
    }

    @Test
    fun `switchTab updates current tab`() = runTest {
        viewModel.createNewTab("https://example.com")
        val firstTabId = viewModel.tabs.first().id
        val secondTabId = viewModel.tabs.last().id

        viewModel.switchTab(secondTabId)
        assert(viewModel.currentTabId == secondTabId)

        viewModel.switchTab(firstTabId)
        assert(viewModel.currentTabId == firstTabId)
    }

    @Test
    fun `closeTab removes tab and updates current`() = runTest {
        viewModel.createNewTab("https://example.com")
        val firstTabId = viewModel.tabs.first().id
        val secondTabId = viewModel.tabs.last().id

        viewModel.switchTab(secondTabId)
        viewModel.closeTab(secondTabId)

        assert(viewModel.tabs.size == 1)
        assert(viewModel.currentTabId == firstTabId)
    }

    @Test
    fun `detectMediaInPage calls media detector`() = runTest {
        val pageUrl = "https://example.com"
        val html = "<html><video src='video.mp4'></video></html>"
        val expectedCandidates = listOf(
            MediaCandidate(
                pageUrl = pageUrl,
                mediaUrl = "https://example.com/video.mp4",
                source = MediaSource.DOM
            )
        )

        coEvery { mockMediaDetector.detect(pageUrl, html) } returns expectedCandidates

        viewModel.detectMediaInPage(pageUrl, html)

        coVerify { mockMediaDetector.detect(pageUrl, html) }
        assert(viewModel.isMediaDetected)
        assert(viewModel.detectedMedia == expectedCandidates)
    }
}