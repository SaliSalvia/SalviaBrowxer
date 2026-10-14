package com.salvia.salviabrowxer.feature.downloads

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.salvia.salviabrowxer.core.database.entities.DownloadEntity
import com.salvia.salviabrowxer.core.model.DownloadState
import com.salvia.salviabrowxer.core.storage.MediaStoreExporter
import com.salvia.salviabrowxer.data.download.DownloadArtifactCleaner
import com.salvia.salviabrowxer.data.download.NoPublishedCopy
import com.salvia.salviabrowxer.data.repository.DownloadRepository
import com.salvia.salviabrowxer.data.security.LockedFolderStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
    private val mockMediaStoreExporter: MediaStoreExporter = mockk(relaxed = true)
    private val mockLockedFolderStore: LockedFolderStore = mockk(relaxed = true)
    private val mockContext: android.content.Context = mockk(relaxed = true)

    /**
     * A real cleaner over a real temporary directory: these tests are about what happens on disk,
     * so a mocked cleaner would assert nothing. The published-copy half is a no-op here (the
     * framework half has its own tests in `DownloadArtifactCleanerTest`).
     */
    private lateinit var ownedRoot: java.io.File
    private lateinit var artifactCleaner: DownloadArtifactCleaner

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        ownedRoot = java.nio.file.Files.createTempDirectory("salvia-vm-owned").toFile()
        artifactCleaner = DownloadArtifactCleaner(ownedRoots = { listOf(ownedRoot) }, publishedCopy = NoPublishedCopy)
        coEvery { mockDownloadRepository.getAllDownloads() } returns flowOf(emptyList())
        viewModel = newViewModel()
    }

    private fun newViewModel() = DownloadsViewModel(
        mockDownloadRepository, mockMediaStoreExporter, mockLockedFolderStore, artifactCleaner, mockContext
    )

    @Test
    fun `retryDownload updates state to QUEUED`() = runTest {
        val downloadId = "download-1"
        coEvery { mockDownloadRepository.updateDownloadState(downloadId, DownloadState.QUEUED) } returns Unit

        viewModel.retryDownload(downloadId)

        // The ViewModel dispatches this on Dispatchers.IO (a real thread), so verifying immediately
        // raced the background write. Wait in real time instead.
        coVerify(timeout = 5_000) { mockDownloadRepository.updateDownloadState(downloadId, DownloadState.QUEUED) }
    }

    @Test
    fun `pauseDownload updates state to PAUSED`() = runTest {
        val downloadId = "download-1"
        coEvery { mockDownloadRepository.updateDownloadState(downloadId, DownloadState.PAUSED) } returns Unit

        viewModel.pauseDownload(downloadId)

        coVerify(timeout = 5_000) { mockDownloadRepository.updateDownloadState(downloadId, DownloadState.PAUSED) }
    }

    @Test
    fun `resumeDownload updates state to QUEUED`() = runTest {
        val downloadId = "download-1"
        coEvery { mockDownloadRepository.updateDownloadState(downloadId, DownloadState.QUEUED) } returns Unit

        viewModel.resumeDownload(downloadId)

        coVerify(timeout = 5_000) { mockDownloadRepository.updateDownloadState(downloadId, DownloadState.QUEUED) }
    }

    @Test
    fun `cancelDownload updates state to CANCELLED`() = runTest {
        val downloadId = "download-1"
        coEvery { mockDownloadRepository.updateDownloadState(downloadId, DownloadState.CANCELLED) } returns Unit

        viewModel.cancelDownload(downloadId)

        coVerify(timeout = 5_000) { mockDownloadRepository.updateDownloadState(downloadId, DownloadState.CANCELLED) }
    }

    @Test
    fun `deleteDownload calls repository`() = runTest {
        val downloadId = "download-1"
        coEvery { mockDownloadRepository.deleteDownload(downloadId) } returns Unit

        viewModel.deleteDownload(downloadId)

        // ViewModel work runs on Dispatchers.IO (real thread) — wait for it instead of verifying immediately
        coVerify(timeout = 5000) { mockDownloadRepository.deleteDownload(downloadId) }
    }

    @Test
    fun `clearCompletedDownloads calls repository`() = runTest {
        coEvery { mockDownloadRepository.deleteDownloadsByState(DownloadState.COMPLETED) } returns Unit

        viewModel.clearCompletedDownloads()

        // Like the other ViewModel actions, this runs on Dispatchers.IO (a real thread), so a bare
        // verify races the background write and fails intermittently on a loaded CI runner.
        coVerify(timeout = 5_000) { mockDownloadRepository.deleteDownloadsByState(DownloadState.COMPLETED) }
    }

    @Test
    fun `deleteDownload removes the file, its part and the staged parts, then the row`() = runTest {
        val id = "delete-owned"
        val file = java.io.File(ownedRoot, "clip.mp4").apply { writeBytes(ByteArray(16)) }
        val part = java.io.File(ownedRoot, "clip.part").apply { writeBytes(ByteArray(4)) }
        val staged = java.io.File(ownedRoot, "clip.hls.ts.parts/part-00000").apply { parentFile?.mkdirs(); writeBytes(ByteArray(4)) }
        coEvery { mockDownloadRepository.getDownloadById(id) } returns DownloadEntity(
            id = id, url = "https://example.com/clip.mp4", filename = "clip.mp4",
            destination = ownedRoot.absolutePath, finalPath = file.absolutePath,
            status = DownloadState.COMPLETED
        )

        viewModel.deleteDownload(id)

        coVerify(timeout = 5_000) { mockDownloadRepository.deleteDownload(id) }
        assertFalse("the row is useless without its file, but the file must not outlive it", file.exists())
        assertFalse(part.exists())
        assertFalse("staged segments used to be left behind by delete", staged.exists())
    }

    @Test
    fun `deleteDownload never reaches outside the app's own directories`() = runTest {
        val id = "delete-foreign"
        val foreignDir = java.nio.file.Files.createTempDirectory("salvia-not-ours").toFile()
        val foreign = java.io.File(foreignDir, "video.mp4").apply { writeBytes(ByteArray(8)) }
        coEvery { mockDownloadRepository.getDownloadById(id) } returns DownloadEntity(
            id = id, url = "https://example.com/v.mp4", filename = "video.mp4",
            destination = foreignDir.absolutePath, finalPath = foreign.absolutePath,
            status = DownloadState.COMPLETED
        )

        viewModel.deleteDownload(id)

        coVerify(timeout = 5_000) { mockDownloadRepository.deleteDownload(id) }
        assertTrue("a path the app does not own must survive the delete", foreign.exists())
    }

    @Test
    fun `clearing completed rows deletes their files too`() = runTest {
        val done = java.io.File(ownedRoot, "done.mp4").apply { writeBytes(ByteArray(8)) }
        val other = java.io.File(ownedRoot, "still-queued.mp4").apply { writeBytes(ByteArray(8)) }
        coEvery { mockDownloadRepository.getDownloadsByState(DownloadState.COMPLETED) } returns flowOf(
            listOf(
                DownloadEntity(
                    id = "done", url = "https://example.com/done.mp4", filename = "done.mp4",
                    destination = ownedRoot.absolutePath, finalPath = done.absolutePath,
                    status = DownloadState.COMPLETED
                ),
                DownloadEntity(
                    id = "queued", url = "https://example.com/still-queued.mp4", filename = "still-queued.mp4",
                    destination = ownedRoot.absolutePath, finalPath = other.absolutePath,
                    status = DownloadState.COMPLETED
                )
            )
        )
        coEvery { mockDownloadRepository.deleteDownloadsByState(DownloadState.COMPLETED) } returns Unit

        viewModel.clearCompletedDownloads()

        coVerify(timeout = 5_000) { mockDownloadRepository.deleteDownloadsByState(DownloadState.COMPLETED) }
        assertFalse("clear-completed used to delete rows and leave the files", done.exists())
        assertFalse(other.exists())
    }

    @Test
    fun `clearing the whole list leaves a running transfer alone`() = runTest {
        val running = java.io.File(ownedRoot, "running.mp4").apply { writeBytes(ByteArray(8)) }
        val finished = java.io.File(ownedRoot, "finished.mp4").apply { writeBytes(ByteArray(8)) }
        coEvery { mockDownloadRepository.getAllDownloads() } returns flowOf(
            listOf(
                DownloadEntity(
                    id = "running", url = "https://example.com/running.mp4", filename = "running.mp4",
                    destination = ownedRoot.absolutePath, finalPath = running.absolutePath,
                    status = DownloadState.DOWNLOADING
                ),
                DownloadEntity(
                    id = "finished", url = "https://example.com/finished.mp4", filename = "finished.mp4",
                    destination = ownedRoot.absolutePath, finalPath = finished.absolutePath,
                    status = DownloadState.COMPLETED
                )
            )
        )
        coEvery { mockDownloadRepository.deleteDownload("running") } returns Unit
        coEvery { mockDownloadRepository.deleteDownload("finished") } returns Unit

        viewModel.clearAllDownloads()

        coVerify(timeout = 5_000) { mockDownloadRepository.deleteDownload("finished") }
        coVerify(exactly = 0) { mockDownloadRepository.deleteDownload("running") }
        assertTrue("a transfer the user did not cancel keeps its file", running.exists())
        assertFalse(finished.exists())
    }

    @Test
    fun `clearFailedDownloads calls repository`() = runTest {
        coEvery { mockDownloadRepository.deleteDownloadsByState(DownloadState.FAILED) } returns Unit

        viewModel.clearFailedDownloads()

        coVerify(timeout = 5_000) { mockDownloadRepository.deleteDownloadsByState(DownloadState.FAILED) }
    }

    @Test
    fun `clearing the whole list goes through the per-row delete, not a bulk wipe`() = runTest {
        val file = java.io.File(ownedRoot, "finished.mp4").apply { writeBytes(ByteArray(8)) }
        coEvery { mockDownloadRepository.getAllDownloads() } returns flowOf(
            listOf(
                DownloadEntity(
                    id = "finished", url = "https://example.com/finished.mp4", filename = "finished.mp4",
                    destination = ownedRoot.absolutePath, finalPath = file.absolutePath,
                    status = DownloadState.COMPLETED
                )
            )
        )
        coEvery { mockDownloadRepository.deleteDownload("finished") } returns Unit

        viewModel.clearAllDownloads()

        coVerify(timeout = 5_000) { mockDownloadRepository.deleteDownload("finished") }
        assertFalse("a cleared row must take its file with it", file.exists())
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

        val vm = newViewModel()
        // The ViewModel collects on Dispatchers.IO, so the buckets are published by a real
        // background thread. Reading uiState.value straight after construction raced that thread
        // and passed or failed depending on scheduling. Waiting on the default dispatcher keeps
        // the wait in real time — a timeout on the test scheduler's virtual clock would fire
        // instantly and only make the flake look deterministic.
        val state = withContext(Dispatchers.Default) {
            withTimeout(5_000L) { vm.uiState.first { it.all.size == downloads.size } }
        }

        assertEquals(1, state.active.size)
        assertEquals(1, state.queued.size)
        assertEquals(1, state.completed.size)
        assertEquals(1, state.failed.size)
        assertEquals(4, state.all.size)
    }

    @Test
    fun `exportDownload publishes the finished file and records the gallery uri`() = runTest {
        val file = java.io.File.createTempFile("salvia-export", ".mp4").apply { writeBytes(ByteArray(64) { 7 }) }
        file.deleteOnExit()
        val downloadId = "download-export-1"
        coEvery { mockDownloadRepository.getDownloadById(downloadId) } returns DownloadEntity(
            id = downloadId, url = "https://example.com/v.mp4", filename = file.name,
            destination = file.parent.orEmpty(), finalPath = file.absolutePath,
            status = DownloadState.COMPLETED
        )
        every { mockMediaStoreExporter.requiredPermission() } returns null
        every { mockMediaStoreExporter.isVideoFile(any(), any()) } returns true
        every { mockMediaStoreExporter.export(any(), any(), any(), any()) } returns "content://media/external/video/media/42"

        viewModel.exportDownload(downloadId)

        verify(timeout = 5_000) { mockMediaStoreExporter.export(file, file.name, any(), true) }
        coVerify(timeout = 5_000) {
            mockDownloadRepository.updateDownloadArtifacts(id = downloadId, exportedUri = "content://media/external/video/media/42")
        }
    }

    @Test
    fun `exportDownload asks for storage permission first on older devices`() = runTest {
        val file = java.io.File.createTempFile("salvia-export", ".mp4").apply { writeBytes(ByteArray(64) { 7 }) }
        file.deleteOnExit()
        val downloadId = "download-export-2"
        coEvery { mockDownloadRepository.getDownloadById(downloadId) } returns DownloadEntity(
            id = downloadId, url = "https://example.com/v.mp4", filename = file.name,
            destination = file.parent.orEmpty(), finalPath = file.absolutePath,
            status = DownloadState.COMPLETED
        )
        every { mockMediaStoreExporter.requiredPermission() } returns android.Manifest.permission.WRITE_EXTERNAL_STORAGE

        viewModel.exportDownload(downloadId)

        // The request is buffered on the channel, so it can be read after the fact.
        val requestedPermission = withContext(Dispatchers.Default) {
            withTimeout(5_000L) { viewModel.exportPermissionRequest.first() }
        }
        assertEquals(android.Manifest.permission.WRITE_EXTERNAL_STORAGE, requestedPermission)
        // Nothing may be published before the grant exists.
        verify(exactly = 0) { mockMediaStoreExporter.export(any(), any(), any(), any()) }

        every { mockMediaStoreExporter.export(any(), any(), any(), any()) } returns "content://media/external/video/media/7"
        viewModel.onExportPermissionResult(granted = true)
        verify(timeout = 5_000) { mockMediaStoreExporter.export(file, file.name, any(), any()) }
    }

    @Test
    fun `a refused storage permission reports instead of exporting`() = runTest {
        every { mockMediaStoreExporter.requiredPermission() } returns android.Manifest.permission.WRITE_EXTERNAL_STORAGE

        viewModel.onExportPermissionResult(granted = false)

        // No file is published when the user declines the grant.
        verify(exactly = 0) { mockMediaStoreExporter.export(any(), any(), any(), any()) }
    }

    private fun download(id: String, status: DownloadState) = DownloadEntity(
        id = id,
        url = "https://example.com/$id.mp4",
        filename = "$id.mp4",
        destination = "/tmp",
        status = status
    )
}