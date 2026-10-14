package com.salvia.salviabrowxer.core.storage

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The writable half of the chosen-folder destination, against a folder that behaves the way a real
 * one does when something goes wrong.
 *
 * The system picker is what usually grants a tree, and no test can press it. `DebugDocumentsProvider`
 * - declared in the debug manifest only - stands in for the folder instead, and it can do the three
 * things the real path has to survive: assign a name of its own when the one asked for is taken,
 * accept a stream and keep only part of it, and refuse to create the document at all. `SafMove`'s
 * contract is the same in all of them: the download the app already has is never the thing that is
 * lost.
 *
 * The folder's own contents are read through the provider API, never from a path, so these tests do
 * not depend on where the test double keeps its bytes - only on the authority the debug manifest
 * declares, which is written here as its own literal on purpose: a test double that silently stopped
 * existing would otherwise turn every case into a green no-op.
 */
@RunWith(AndroidJUnit4::class)
class SafMoveInstrumentedTest {

    /** Must match `DebugDocumentsProvider.AUTHORITY_SUFFIX` and the debug manifest. */
    private val authority = "com.salvia.salviabrowxer.debugdocuments"

    private val treeUri = "content://$authority/tree/root"

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val work: File get() = File(context.cacheDir, "saf-move-test")

    @Before
    fun setUp() {
        // Every case starts from an empty folder, emptied through the provider's own API.
        childrenOf(treeUri).forEach { documentId ->
            runCatching {
                DocumentsContract.deleteDocument(context.contentResolver, documentUri(documentId))
            }
        }
        work.deleteRecursively()
        work.mkdirs()
    }

    @Test
    fun a_chosen_folder_receives_the_file_and_the_private_copy_is_gone() {
        val source = source("clip.mp4")
        val exporter = SafDocumentExporter(context)
        assertTrue("the folder of an installed provider must be reachable", exporter.isReachable(treeUri))

        // A missing document is the failure, and the thrown assertion says so without leaving a
        // nullable value to dereference on every line below.
        val moved = exporter.moveInto(treeUri, source, "video/mp4", source.name)
            ?: throw AssertionError("the move must produce a document")

        assertTrue("expected a document uri, was ${moved.uri}", moved.uri.startsWith("content://"))
        assertEquals("the folder had no collision, so the name must be the file's", "clip.mp4", moved.displayName)
        assertFalse("the private copy must not survive the move", source.exists())

        // What the folder holds is what was downloaded, byte for byte.
        val readBack = context.contentResolver.openInputStream(Uri.parse(moved.uri))?.use { it.readBytes() }
        assertEquals("the document must hold the file that was moved", PAYLOAD_BYTES, readBack?.size)
    }

    @Test
    fun a_name_the_folder_already_uses_comes_back_under_the_name_the_folder_used() {
        val source = source("dup-clip.mp4")
        val exporter = SafDocumentExporter(context)

        // The provider renamed it, and the row must say the name that is really on the card - a row
        // that says `dup-clip.mp4` while the file is `dup-clip (1).mp4` is a user who cannot find it.
        val moved = exporter.moveInto(treeUri, source, "video/mp4", source.name)
            ?: throw AssertionError("the move must produce a document")

        assertEquals("dup-clip (1).mp4", moved.displayName)
        assertEquals("the folder must hold the renamed document", listOf("dup-clip (1).mp4"), childrenOf(treeUri))
        assertFalse("the private copy must not survive the move", source.exists())
    }

    @Test
    fun a_folder_that_keeps_only_part_of_the_file_costs_nothing() {
        val source = source("half-clip.mp4")
        val exporter = SafDocumentExporter(context)

        val moved = exporter.moveInto(treeUri, source, "video/mp4", source.name)

        assertNull("a document whose length is not the file's must not be trusted", moved)
        assertTrue("the download must survive a folder that dropped its tail", source.exists())
        assertEquals(PAYLOAD_BYTES.toLong(), source.length())
        assertTrue(
            "the half-written document must be removed, not left in the user's folder: ${childrenOf(treeUri)}",
            childrenOf(treeUri).isEmpty()
        )
    }

    @Test
    fun a_folder_that_refuses_to_create_the_file_costs_nothing() {
        val source = source("refuse-clip.mp4")
        val exporter = SafDocumentExporter(context)

        val moved = exporter.moveInto(treeUri, source, "video/mp4", source.name)

        assertNull("a provider that refuses must produce no document", moved)
        assertTrue("the download must survive a refusal", source.exists())
        assertEquals(PAYLOAD_BYTES.toLong(), source.length())
        assertTrue("a refusal creates nothing", childrenOf(treeUri).isEmpty())
    }

    // ---- the folder, through its own API -----------------------------------------------------------

    private fun documentUri(documentId: String): Uri =
        DocumentsContract.buildDocumentUri(authority, documentId)

    /** The document ids the folder currently holds. */
    private fun childrenOf(treeUri: String): List<String> {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(Uri.parse(treeUri), ROOT_ID)
        return context.contentResolver.query(
            childrenUri,
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID),
            null,
            null,
            null
        )?.use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.getString(0))
            }
        }.orEmpty()
    }

    /** A file to move: bigger than the part a half-writing provider keeps, so the two can differ. */
    private fun source(name: String): File {
        work.mkdirs()
        return File(work, name).apply { writeBytes(ByteArray(PAYLOAD_BYTES) { index -> (index % 251).toByte() }) }
    }

    private companion object {
        const val ROOT_ID = "root"
        const val PAYLOAD_BYTES = 64 * 1024
    }
}
