package com.salvia.salviabrowxer.data.download

import com.salvia.salviabrowxer.core.database.entities.DownloadEntity
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The ownership rules of deletion, on the JVM.
 *
 * The two failures this pins down are the ones the app actually had: a delete that leaves the
 * gallery copy behind (the file keeps existing in Photos after the row is gone), and a "clear the
 * list" that removes rows and no files at all. Both are checked here, together with the rule that
 * matters more than either: a path this app does not own is never deleted.
 */
class DownloadArtifactCleanerTest {

    /** Records what the framework half was asked to remove. */
    private class FakePublishedCopy(private val owned: Set<String>) : PublishedCopy {
        val deleted = mutableListOf<String>()
        val asked = mutableListOf<String>()

        override fun isOwned(uri: String): Boolean = uri in owned

        override fun delete(uri: String): Boolean {
            asked += uri
            if (uri !in owned) return false
            deleted += uri
            return true
        }
    }

    private lateinit var root: File
    private lateinit var outside: File

    private fun setUpDirs(existing: List<String>) {
        root = Files.createTempDirectory("salvia-owned").toFile()
        outside = Files.createTempDirectory("salvia-outside").toFile()
        existing.forEach { relative ->
            val file = File(root, relative)
            file.parentFile?.mkdirs()
            file.writeBytes(ByteArray(8) { 1 })
        }
    }

    private fun ownedFile(relative: String): File = File(root, relative)

    private fun row(
        filename: String,
        finalPath: String? = null,
        exportedUri: String? = null,
        thumbnail: String? = null
    ): DownloadEntity = DownloadEntity(
        id = filename,
        url = "https://cdn.example/$filename",
        filename = filename,
        destination = root.absolutePath,
        finalPath = finalPath,
        exportedUri = exportedUri,
        thumbnail = thumbnail
    )

    private fun cleaner(published: PublishedCopy = FakePublishedCopy(emptySet())): DownloadArtifactCleaner =
        DownloadArtifactCleaner(ownedRoots = { listOf(root) }, publishedCopy = published)

    @Test
    fun `the file, its part and every staged intermediate are removed`() {
        setUpDirs(
            listOf(
                "clip.mp4",
                "clip.part",
                "clip.hls.ts",
                "clip.dash.tmp",
                "clip.video.tmp",
                "clip.audio.tmp",
                "clip.hls.ts.parts/part-00000",
                "clip.hls.ts.parts/part-00001",
                "clip.part0",
                "clip.part3"
            )
        )
        val target = row("clip.mp4", finalPath = ownedFile("clip.mp4").absolutePath)

        val result = cleaner().clean(target)

        assertFalse(ownedFile("clip.mp4").exists())
        assertFalse("the resume file must not survive its row", ownedFile("clip.part").exists())
        assertFalse(ownedFile("clip.hls.ts").exists())
        assertFalse(ownedFile("clip.dash.tmp").exists())
        assertFalse(ownedFile("clip.video.tmp").exists())
        assertFalse(ownedFile("clip.audio.tmp").exists())
        assertFalse("staged segments are the largest leftover", ownedFile("clip.hls.ts.parts").exists())
        assertFalse("a cancelled parallel transfer leaves .part<N> files", ownedFile("clip.part0").exists())
        assertFalse(ownedFile("clip.part3").exists())
        assertTrue(result.deletedAnything)
        assertTrue(result.skipped.isEmpty())
    }

    @Test
    fun `a file this app does not own is never deleted`() {
        setUpDirs(listOf("clip.mp4"))
        val foreign = File(outside, "clip.mp4").apply { writeBytes(ByteArray(4)) }
        val target = row("clip.mp4", finalPath = foreign.absolutePath)

        val result = cleaner().clean(target)

        assertTrue("the app must not reach outside its own directories", foreign.exists())
        assertFalse(ownedFile("clip.mp4").exists())
        assertEquals(listOf(foreign.absolutePath), result.skipped)
    }

    @Test
    fun `an owned published copy is deleted and another app's copy is left alone`() {
        setUpDirs(listOf("clip.mp4"))
        val mine = "content://media/external/video/media/42"
        val theirs = "content://media/external/video/media/43"
        val published = FakePublishedCopy(owned = setOf(mine))

        val result = cleaner(published).clean(row("clip.mp4", exportedUri = mine))
        assertEquals(1, result.deletedPublishedCopies)
        assertEquals(listOf(mine), published.deleted)

        val other = cleaner(published).clean(row("clip.mp4", exportedUri = theirs))
        assertEquals(0, other.deletedPublishedCopies)
        assertTrue("another app's media is not ours to delete", other.skipped.contains(theirs))
    }

    @Test
    fun `the thumbnail is deleted with its row`() {
        setUpDirs(listOf("clip.mp4"))
        val thumbnail = ownedFile("thumb.jpg")
        thumbnail.writeBytes(ByteArray(2))

        cleaner().clean(row("clip.mp4", thumbnail = "file://${thumbnail.absolutePath}"))

        assertFalse(thumbnail.exists())
    }

    @Test
    fun `staging-only cleanup keeps the result and the published copy`() {
        setUpDirs(listOf("movie.mp4", "movie.part", "movie.dash.tmp"))
        val uri = "content://media/external/video/media/7"
        val published = FakePublishedCopy(owned = setOf(uri))

        val deleted = cleaner(published).cleanTransferStaging(row("movie.mp4", exportedUri = uri))

        assertEquals(2, deleted)
        assertTrue("a finished download keeps its file", ownedFile("movie.mp4").exists())
        assertFalse(ownedFile("movie.part").exists())
        assertFalse(ownedFile("movie.dash.tmp").exists())
        assertTrue("staging cleanup never touches the published copy", published.deleted.isEmpty())
    }

    @Test
    fun `staging cleanup never deletes the row's own result`() {
        setUpDirs(listOf("clip.dash.tmp"))
        // A DASH rendition that finished but could not be remuxed keeps the staged container as the
        // download's final path. Cleaning the staging of that row must not delete it.
        val target = row("clip.mp4", finalPath = ownedFile("clip.dash.tmp").absolutePath)

        val deleted = cleaner().cleanTransferStaging(target)

        assertEquals(0, deleted)
        assertTrue("the only copy of the download is still there", ownedFile("clip.dash.tmp").exists())
    }

    @Test
    fun `cleaning a row whose files are already gone is a no-op, not an error`() {
        setUpDirs(emptyList())

        val result = cleaner().clean(row("gone.mp4"))

        assertFalse(result.deletedAnything)
        assertTrue(result.skipped.isEmpty())
    }

    @Test
    fun `ownership refuses relative paths and directory-name prefix collisions`() {
        setUpDirs(listOf("deep/deeper.bin"))
        val sibling = File(root.parentFile, root.name + "-sibling").apply { mkdirs() }

        assertFalse("a relative path cannot be proven to be the app's", cleaner().isInsideOwnedRoots(File("somewhere/else/rel.mp4")))
        assertFalse(
            "a sibling directory whose name merely starts with the root's name is not inside it",
            cleaner().isInsideOwnedRoots(File(sibling, "clip.mp4"))
        )
        assertTrue(cleaner().isInsideOwnedRoots(ownedFile("deep/deeper.bin")))
        assertTrue("the root itself is owned", cleaner().isInsideOwnedRoots(root))
    }
}
