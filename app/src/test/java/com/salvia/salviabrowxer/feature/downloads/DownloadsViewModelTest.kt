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
import kotlinx.coroutines.test.runTest
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
    private val mockDownloadRepository: DownloadRepository = mockk()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        viewModel = DownloadsViewModel(mockDownloadRepository)
    }

    @Test
    fun `retryDownload updates state to QUEUED`() = runTest {
        val downloadId = "download-1"
        coEvery { mockDownloadRepository.updateDownloadState(downloadId, DownloadState.QUEUED) } returns Unit

        viewModel.retryDownload(downloadId)

        coVerify { mockDownloadRepository.updateDownloadState(downloadId, DownloadState.QUEUED) }
    }

    @Test
    fun `pauseDownload updates state to PAUSED`() = runTest {
        val downloadId = "download-1"
        coEvery { mockDownloadRepository.updateDownloadState(downloadId, DownloadState.PAUSED) } returns Unit

        viewModel.pauseDownload(downloadId)

        coVerify { mockDownloadRepository.updateDownloadState(downloadId, DownloadState.PAUSED) }
    }

    @Test
    fun `resumeDownload updates state to QUEUED`() = runTest {
        val downloadId = "download-1"
        coEvery { mockDownloadRepository.updateDownloadState(downloadId, DownloadState.QUEUED) } returns Unit

        viewModel.resumeDownload(downloadId)

        coVerify { mockDownloadRepository.updateDownloadState(downloadId, DownloadState.QUEUED) }
    }

    @Test
    fun `cancelDownload updates state to CANCELLED`() = runTest {
        val downloadId = "download-1"
        coEvery { mockDownloadRepository.updateDownloadState(downloadId, DownloadState.CANCELLED) } returns Unit

        viewModel.cancelDownload(downloadId)

        coVerify { mockDownloadRepository.updateDownloadState(downloadId, DownloadState.CANCELLED) }
    }

    @Test
    fun `deleteDownload calls repository`() = runTest {
        val downloadId = "download-1"
        coEvery { mockDownloadRepository.deleteDownload(downloadId) } returns Unit

        viewModel.deleteDownload(downloadId)

        coVerify { mockDownloadRepository.deleteDownload(downloadId) }
    }

    @Test
    fun `clearCompletedDownloads calls repository`() = runTest {
        coEvery { mockDownloadRepository.deleteDownloadsByState(DownloadState.COMPLETED) } returns Unit

        viewModel.clearCompletedDownloads()

        coVerify { mockDownloadRepository.deleteDownloadsByState(DownloadState.COMPLETED) }
    }

    @Test
    fun `clearFailedDownloads calls repository`() = runTest {
        coEvery { mockDownloadRepository.deleteDownloadsByState(DownloadState.FAILED) } returns Unit

        viewModel.clearFailedDownloads()

        coVerify { mockDownloadRepository.deleteDownloadsByState(DownloadState.FAILED) }
    }

    @Test
    fun `clearAllDownloads calls repository`() = runTest {
        coEvery { mockDownloadRepository.clearAllDownloads() } returns Unit

        viewModel.clearAllDownloads()

        coVerify { mockDownloadRepository.clearAllDownloads() }
    }
}