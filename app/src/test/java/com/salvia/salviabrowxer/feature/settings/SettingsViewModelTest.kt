package com.salvia.salviabrowxer.feature.settings

import com.salvia.salviabrowxer.data.datastore.SettingsDataStore
import com.salvia.salviabrowxer.data.repository.HistoryRepository
import io.mockk.coEvery
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var viewModel: SettingsViewModel
    private val settingsDataStore: SettingsDataStore = mockk(relaxed = true)
    private val historyRepository: HistoryRepository = mockk(relaxed = true)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { settingsDataStore.searchEngine } returns flowOf("Google")
        every { settingsDataStore.homepage } returns flowOf("https://www.google.com")
        every { settingsDataStore.isDesktopSite } returns flowOf(false)
        every { settingsDataStore.isJavaScriptEnabled } returns flowOf(true)
        every { settingsDataStore.areCookiesEnabled } returns flowOf(true)
        every { settingsDataStore.downloadDirectory } returns flowOf("")
        every { settingsDataStore.maxSimultaneousDownloads } returns flowOf(3)
        every { settingsDataStore.isWifiOnly } returns flowOf(false)
        every { settingsDataStore.isDarkTheme } returns flowOf(true)
        every { settingsDataStore.floatingButtonSize } returns flowOf(56)
        every { settingsDataStore.floatingButtonX } returns flowOf(0f)
        every { settingsDataStore.floatingButtonY } returns flowOf(0f)

        viewModel = SettingsViewModel(settingsDataStore, historyRepository)
    }

    @Test
    fun `stored preferences are published through uiState`() = runTest {
        val state = viewModel.uiState.value
        assertEquals("Google", state.searchEngine)
        assertEquals("https://www.google.com", state.homepage)
        assertEquals(56, state.floatingButtonSize)
        assertTrue(state.isJavaScriptEnabled)
        assertFalse(state.isDesktopSite)
    }

    @Test
    fun `updateSearchEngine writes optimistically and persists`() {
        coEvery { settingsDataStore.setSearchEngine("DuckDuckGo") } returns Unit

        viewModel.updateSearchEngine("DuckDuckGo")

        assertEquals("DuckDuckGo", viewModel.uiState.value.searchEngine)
        coVerify(exactly = 1) { settingsDataStore.setSearchEngine("DuckDuckGo") }
    }

    @Test
    fun `updateHomepage trims the value before storing it`() {
        viewModel.updateHomepage("  https://duckduckgo.com  ")

        assertEquals("https://duckduckgo.com", viewModel.uiState.value.homepage)
        coVerify(exactly = 1) { settingsDataStore.setHomepage("https://duckduckgo.com") }
    }

    @Test
    fun `switches persist their state`() {
        viewModel.updateDesktopSite(true)
        viewModel.updateJavaScriptEnabled(false)
        viewModel.updateCookiesEnabled(false)
        viewModel.updateWifiOnly(true)
        viewModel.updateDarkTheme(false)

        val state = viewModel.uiState.value
        assertTrue(state.isDesktopSite)
        assertFalse(state.isJavaScriptEnabled)
        assertFalse(state.areCookiesEnabled)
        assertTrue(state.isWifiOnly)
        assertFalse(state.isDarkTheme)

        coVerify(exactly = 1) { settingsDataStore.setDesktopSite(true) }
        coVerify(exactly = 1) { settingsDataStore.setJavaScriptEnabled(false) }
        coVerify(exactly = 1) { settingsDataStore.setCookiesEnabled(false) }
        coVerify(exactly = 1) { settingsDataStore.setWifiOnly(true) }
        coVerify(exactly = 1) { settingsDataStore.setDarkTheme(false) }
    }

    @Test
    fun `parallel download count is clamped to the supported range`() {
        viewModel.updateMaxSimultaneousDownloads(99)
        assertEquals(5, viewModel.uiState.value.maxSimultaneousDownloads)

        viewModel.updateMaxSimultaneousDownloads(0)
        assertEquals(1, viewModel.uiState.value.maxSimultaneousDownloads)
    }

    @Test
    fun `floating button size and position are persisted`() {
        viewModel.updateFloatingButtonSize(56)
        assertEquals(56, viewModel.uiState.value.floatingButtonSize)
        coVerify(exactly = 1) { settingsDataStore.setFloatingButtonSize(56) }

        viewModel.updateFloatingButtonPosition(-40f, -260f)
        coVerify(exactly = 1) { settingsDataStore.setFloatingButtonPosition(-40f, -260f) }
    }

    @Test
    fun `clearing browsing data also drops history`() {
        coEvery { historyRepository.deleteAllHistory() } returns Unit

        viewModel.clearHistory()

        coVerify(exactly = 1) { historyRepository.deleteAllHistory() }
    }

    @Test
    fun `download directory falls back to a readable label`() {
        assertEquals("Downloads", viewModel.uiState.value.downloadDirectoryLabel)
    }
}
