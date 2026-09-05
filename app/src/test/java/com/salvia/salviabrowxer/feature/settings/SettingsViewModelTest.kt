package com.salvia.salviabrowxer.feature.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.salvia.salviabrowxer.core.datastore.SettingsDataStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var viewModel: SettingsViewModel
    private val mockSettingsDataStore: SettingsDataStore = mockk()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        viewModel = SettingsViewModel(mockSettingsDataStore)
    }

    @Test
    fun `updateSearchEngine calls dataStore`() = runTest {
        val engine = "DuckDuckGo"
        coEvery { mockSettingsDataStore.setSearchEngine(engine) } returns Unit

        viewModel.updateSearchEngine(engine)

        coVerify { mockSettingsDataStore.setSearchEngine(engine) }
    }

    @Test
    fun `updateHomepage calls dataStore`() = runTest {
        val url = "https://duckduckgo.com"
        coEvery { mockSettingsDataStore.setHomepage(url) } returns Unit

        viewModel.updateHomepage(url)

        coVerify { mockSettingsDataStore.setHomepage(url) }
    }

    @Test
    fun `updateDesktopSite calls dataStore`() = runTest {
        val isDesktop = true
        coEvery { mockSettingsDataStore.setDesktopSite(isDesktop) } returns Unit

        viewModel.updateDesktopSite(isDesktop)

        coVerify { mockSettingsDataStore.setDesktopSite(isDesktop) }
    }

    @Test
    fun `updateJavaScriptEnabled calls dataStore`() = runTest {
        val enabled = false
        coEvery { mockSettingsDataStore.setJavaScriptEnabled(enabled) } returns Unit

        viewModel.updateJavaScriptEnabled(enabled)

        coVerify { mockSettingsDataStore.setJavaScriptEnabled(enabled) }
    }

    @Test
    fun `updateCookiesEnabled calls dataStore`() = runTest {
        val enabled = false
        coEvery { mockSettingsDataStore.setCookiesEnabled(enabled) } returns Unit

        viewModel.updateCookiesEnabled(enabled)

        coVerify { mockSettingsDataStore.setCookiesEnabled(enabled) }
    }

    @Test
    fun `updateDownloadDirectory calls dataStore`() = runTest {
        val directory = "/storage/emulated/0/Downloads"
        coEvery { mockSettingsDataStore.setDownloadDirectory(directory) } returns Unit

        viewModel.updateDownloadDirectory(directory)

        coVerify { mockSettingsDataStore.setDownloadDirectory(directory) }
    }

    @Test
    fun `updateMaxSimultaneousDownloads calls dataStore`() = runTest {
        val count = 5
        coEvery { mockSettingsDataStore.setMaxSimultaneousDownloads(count) } returns Unit

        viewModel.updateMaxSimultaneousDownloads(count)

        coVerify { mockSettingsDataStore.setMaxSimultaneousDownloads(count) }
    }

    @Test
    fun `updateWifiOnly calls dataStore`() = runTest {
        val enabled = true
        coEvery { mockSettingsDataStore.setWifiOnly(enabled) } returns Unit

        viewModel.updateWifiOnly(enabled)

        coVerify { mockSettingsDataStore.setWifiOnly(enabled) }
    }

    @Test
    fun `updateFloatingButtonPosition calls dataStore`() = runTest {
        val x = 100f
        val y = 200f
        coEvery { mockSettingsDataStore.setFloatingButtonPosition(x, y) } returns Unit

        viewModel.updateFloatingButtonPosition(x, y)

        coVerify { mockSettingsDataStore.setFloatingButtonPosition(x, y) }
    }

    @Test
    fun `updateFloatingButtonSize calls dataStore`() = runTest {
        val size = 64
        coEvery { mockSettingsDataStore.setFloatingButtonSize(size) } returns Unit

        viewModel.updateFloatingButtonSize(size)

        coVerify { mockSettingsDataStore.setFloatingButtonSize(size) }
    }
}