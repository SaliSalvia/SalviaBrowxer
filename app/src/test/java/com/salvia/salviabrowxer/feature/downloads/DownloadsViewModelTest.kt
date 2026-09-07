package com.salvia.salviabrowxer.feature.downloads

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.salvia.salviabrowxer.core.database.entities.DownloadEntity
import com.salvia.salviabrowxer.core.model.DownloadState
import com.salvia.salviabrowxer.data.repository.DownloadRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadsViewModelTest {

    @get:Rule
    val instantTaskExecutorRule: TestRule = InstantTaskExecutorRule()

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var viewModel: DownloadsViewModel
    private val mockDownloadRepository: DownloadRepository = mockk(relaxed = true)
    private val mockContext: android.content.Context = mockk(relaxed = true)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        coEvery { mockDownloadRepository.getAllDownloads() } returns flowOf(emptyList())
        viewModel = DownloadsViewModel(mockDownloadRepository, mockContext)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `retryDownload updates state to QUEUED`() = runTest {
        val downloadId = "download-1"
        coEvery { mockDownloadRepository.updateDownloadState(downloadId, DownloadState.QUEUED) } returns Unit

        viewModel.retryDownload(downloadId)

        coVerify(timeout = 5000) { mockDownloadRepository.updateDownloadState(downloadId, DownloadState.QUEUED) }
    }

    @Test
    fun `pauseDownload updates state to PAUSED`() = runTest {
        val downloadId = "download-1"
        coEvery { mockDownloadRepository.updateDownloadState(downloadId, DownloadState.PAUSED) } returns Unit

        viewModel.pauseDownload(downloadId)

        coVerify(timeout = 5000) { mockDownloadRepository.updateDownloadState(downloadId, DownloadState.PAUSED) }
    }

    @Test
    fun `resumeDownload updates state to QUEUED`() = runTest {
        val downloadId = "download-1"
        coEvery { mockDownloadRepository.updateDownloadState(downloadId, DownloadState.QUEUED) } returns Unit

        viewModel.resumeDownload(downloadId)

        coVerify(timeout = 5000) { mockDownloadRepository.updateDownloadState(downloadId, DownloadState.QUEUED) }
    }

    @Test
    fun `cancelDownload updates state to CANCELLED`() = runTest {
        val downloadId = "download-1"
        coEvery { mockDownloadRepository.updateDownloadState(downloadId, DownloadState.CANCELLED) } returns Unit

        viewModel.cancelDownload(downloadId)

        coVerify(timeout = 5000) { mockDownloadRepository.updateDownloadState(downloadId, DownloadState.CANCELLED) }
    }

    @Test
    fun `deleteDownload calls repository`() = runTest {
        val downloadId = "download-1"
        coEvery { mockDownloadRepository.getDownloadById(downloadId) } returns null
        coEvery { mockDownloadRepository.deleteDownload(downloadId) } returns Unit

        viewModel.deleteDownload(downloadId)

        coVerify(timeout = 5000) { mockDownloadRepository.deleteDownload(downloadId) }
    }

    @Test
    fun `clearCompletedDownloads calls repository`() = runTest {
        coEvery { mockDownloadRepository.deleteDownloadsByState(DownloadState.COMPLETED) } returns Unit

        viewModel.clearCompletedDownloads()

        coVerify(timeout = 5000) { mockDownloadRepository.deleteDownloadsByState(DownloadState.COMPLETED) }
    }

    @Test
    fun `clearFailedDownloads calls repository`() = runTest {
        coEvery { mockDownloadRepository.deleteDownloadsByState(DownloadState.FAILED) } returns Unit

        viewModel.clearFailedDownloads()

        coVerify(timeout = 5000) { mockDownloadRepository.deleteDownloadsByState(DownloadState.FAILED) }
    }

    @Test
    fun `clearAllDownloads calls repository`() = runTest {
        coEvery { mockDownloadRepository.clearAllDownloads() } returns Unit

        viewModel.clearAllDownloads()

        coVerify(timeout = 5000) { mockDownloadRepository.clearAllDownloads() }
    }

    @Test
    fun `downloads are bucketed by status in uiState`() = runTest {
        val downloads = listOf(
            download("1", DownloadState.DOWNLOADING),
            download("2", DownloadState.QUEUED),
            download("3", DownloadState.COMPLETED),
            download("4", DownloadState.FAILED)
        )
        coEvery { mockDownloadRepository.getAllDownloads() } returns flowOf(downloads)

        val vm = DownloadsViewModel(mockDownloadRepository, mockContext)
        val state = vm.uiState.value

        assertEquals(1, state.active.size)
        assertEquals(1, state.queued.size)
        assertEquals(1, state.completed.size)
        assertEquals(1, state.failed.size)
        assertEquals(4, state.all.size)
    }

    private fun download(id: String, status: DownloadState) = DownloadEntity(
        id = id,
        url = "https://example.com/$id.mp4",
        filename = "$id.mp4",
        destination = "/tmp",
        status = status
    )
}