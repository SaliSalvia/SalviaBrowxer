package com.salvia.salviabrowxer.data.repository

import com.salvia.salviabrowxer.core.database.dao.DownloadDao
import com.salvia.salviabrowxer.core.database.entities.DownloadEntity
import com.salvia.salviabrowxer.core.model.DownloadProgress
import com.salvia.salviabrowxer.core.model.DownloadState
import com.salvia.salviabrowxer.core.storage.StorageManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadRepositoryTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var repository: DownloadRepository
    private val mockDownloadDao: DownloadDao = mockk()
    private val mockStorageManager: StorageManager = mockk()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        repository = DownloadRepositoryImpl(mockDownloadDao, mockStorageManager)
    }

    @Test
    fun `getAllDownloads returns flow from DAO`() = runTest {
        val downloads = listOf(
            DownloadEntity(id = "1", url = "https://example.com/file1.mp4"),
            DownloadEntity(id = "2", url = "https://example.com/file2.mp4")
        )
        coEvery { mockDownloadDao.getAll() } returns flowOf(downloads)

        val result = repository.getAllDownloads().first()

        assert(result == downloads)
        coVerify { mockDownloadDao.getAll() }
    }

    @Test
    fun `getDownloadsByState returns flow from DAO`() = runTest {
        val downloads = listOf(
            DownloadEntity(id = "1", url = "https://example.com/file1.mp4", status = DownloadState.DOWNLOADING)
        )
        coEvery { mockDownloadDao.getByStatus(DownloadState.DOWNLOADING) } returns flowOf(downloads)

        val result = repository.getDownloadsByState(DownloadState.DOWNLOADING).first()

        assert(result == downloads)
        coVerify { mockDownloadDao.getByStatus(DownloadState.DOWNLOADING) }
    }

    @Test
    fun `getDownloadById returns from DAO`() = runTest {
        val download = DownloadEntity(id = "1", url = "https://example.com/file.mp4")
        coEvery { mockDownloadDao.getById("1") } returns download

        val result = repository.getDownloadById("1")

        assert(result == download)
        coVerify { mockDownloadDao.getById("1") }
    }

    @Test
    fun `addDownload calls DAO`() = runTest {
        val download = DownloadEntity(id = "1", url = "https://example.com/file.mp4")
        coEvery { mockDownloadDao.insert(download) } returns Unit

        repository.addDownload(download)

        coVerify { mockDownloadDao.insert(download) }
    }

    @Test
    fun `updateDownload calls DAO`() = runTest {
        val download = DownloadEntity(id = "1", url = "https://example.com/file.mp4")
        coEvery { mockDownloadDao.update(download) } returns Unit

        repository.updateDownload(download)

        coVerify { mockDownloadDao.update(download) }
    }

    @Test
    fun `updateDownloadState calls DAO`() = runTest {
        val download = DownloadEntity(id = "1", url = "https://example.com/file.mp4")
        coEvery { mockDownloadDao.getById("1") } returns download
        coEvery { mockDownloadDao.update(any()) } returns Unit

        repository.updateDownloadState("1", DownloadState.PAUSED)

        coVerify { mockDownloadDao.getById("1") }
        coVerify { mockDownloadDao.update(withArg { it.status == DownloadState.PAUSED }) }
    }

    @Test
    fun `deleteDownload calls DAO`() = runTest {
        coEvery { mockDownloadDao.delete("1") } returns Unit

        repository.deleteDownload("1")

        coVerify { mockDownloadDao.delete("1") }
    }

    @Test
    fun `deleteDownloadsByState calls DAO`() = runTest {
        coEvery { mockDownloadDao.deleteByStatus(DownloadState.COMPLETED) } returns Unit

        repository.deleteDownloadsByState(DownloadState.COMPLETED)

        coVerify { mockDownloadDao.deleteByStatus(DownloadState.COMPLETED) }
    }

    @Test
    fun `clearAllDownloads calls DAO`() = runTest {
        coEvery { mockDownloadDao.deleteAll() } returns Unit

        repository.clearAllDownloads()

        coVerify { mockDownloadDao.deleteAll() }
    }

    @Test
    fun `getDefaultDownloadDestination returns from StorageManager`() = runTest {
        val directory = "/storage/emulated/0/Downloads"
        coEvery { mockStorageManager.getDefaultDownloadDirectory() } returns directory

        val result = repository.getDefaultDownloadDestination()

        assert(result == directory)
        coVerify { mockStorageManager.getDefaultDownloadDirectory() }
    }

    @Test
    fun `createDownloadEntity creates entity with correct values`() = runTest {
        val url = "https://example.com/file.mp4"
        val filename = "file.mp4"
        val destination = "/storage/emulated/0/Downloads"
        val mediaTitle = "Example Video"
        val thumbnail = "https://example.com/thumb.jpg"
        val selectedQuality = "1080p"

        val result = repository.createDownloadEntity(
            url = url,
            filename = filename,
            destination = destination,
            mediaTitle = mediaTitle,
            thumbnail = thumbnail,
            selectedQuality = selectedQuality
        )

        assert(result.url == url)
        assert(result.filename == filename)
        assert(result.destination == destination)
        assert(result.mediaTitle == mediaTitle)
        assert(result.thumbnail == thumbnail)
        assert(result.selectedQuality == selectedQuality)
        assert(result.status == DownloadState.QUEUED)
    }
}