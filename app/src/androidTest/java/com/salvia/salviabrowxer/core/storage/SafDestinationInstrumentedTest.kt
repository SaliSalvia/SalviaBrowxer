package com.salvia.salviabrowxer.core.storage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The chosen-folder destination, on a device, where a document provider has the last word.
 *
 * `DestinationPolicyTest` proves on the JVM *what the app decides* when the user's folder is gone or
 * the grant is not there any more; it cannot prove what the platform does when the app then tries to
 * write into that tree. This is that half: a real `ContentResolver`, a real `DocumentsContract`, a
 * provider that is absent, and a provider that refuses. The one promise that matters in all of them is
 * the same — the file the app already downloaded is still there afterwards, because a destination that
 * cannot be written to must never cost the user the download itself.
 *
 * What a test on an emulator cannot do is grant a tree: that is the system picker's job and it is the
 * user's hand. So the writable path — duplicate names renamed by the provider, a document written
 * through to a real card, a process killed between the copy and the delete — is not covered here and
 * is not claimed anywhere. See `docs/phase-1-status.md`.
 */
@RunWith(AndroidJUnit4::class)
class SafDestinationInstrumentedTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val work: File get() = File(context.cacheDir, "saf-test").apply { mkdirs() }

    /** A file of a size worth verifying after the copy failed. */
    private fun source(name: String = "clip.mp4"): File =
        File(work, name).apply { writeBytes(ByteArray(PAYLOAD_BYTES) { index -> (index % 251).toByte() }) }

    @Test
    fun a_tree_whose_provider_is_not_installed_is_refused_and_the_file_remains() {
        val file = source()
        val exporter = SafDocumentExporter(context)
        val tree = "content://com.salvia.salviabrowxer.absent.provider/tree/primary%3ADownload"

        assertFalse("a provider that is not installed cannot be reachable", exporter.isReachable(tree))
        assertNull(
            "a tree that does not resolve must produce no document",
            exporter.moveInto(tree, file, "video/mp4", "clip.mp4")
        )
        assertTrue("the download must survive a refused move", file.exists())
        assertEquals(PAYLOAD_BYTES.toLong(), file.length())
    }

    @Test
    fun a_tree_the_app_was_never_granted_is_refused_and_the_file_remains() {
        val file = source()
        val exporter = SafDocumentExporter(context)
        // A real provider, a real tree id, and no persisted grant for this app: the state a user is in
        // after revoking access, or after restoring the app on a new device.
        val tree = "content://com.android.providers.downloads.documents/tree/downloads"

        // If this build hands that tree to any app, there is no refused grant to test here and the
        // device is simply different - not a reason for a red build.
        assumeTrue("this device grants the Downloads tree without a picker", !exporter.isReachable(tree))

        assertNull(
            "a tree the app may not write to must produce no document",
            exporter.moveInto(tree, file, "video/mp4", "clip.mp4")
        )
        assertTrue("the download must survive a refused move", file.exists())
        assertEquals(PAYLOAD_BYTES.toLong(), file.length())
    }

    @Test
    fun an_empty_file_is_never_moved() {
        val file = File(work, "empty.mp4").apply { writeBytes(ByteArray(0)) }
        val exporter = SafDocumentExporter(context)

        assertNull(
            "an empty file is not a download and must not become a document",
            exporter.moveInto("content://com.salvia.salviabrowxer.absent.provider/tree/primary%3ADownload", file, "video/mp4", "empty.mp4")
        )
    }

    @Test
    fun a_tree_is_named_for_the_settings_row() {
        // What the row says: the last path segment of the tree id, percent-decoded, and a readable
        // fallback for a volume root, which carries no name of its own.
        assertEquals(
            "clips",
            SafDocumentExporter.labelOf("content://com.android.externalstorage.documents/tree/primary%3ADownload%2Fclips")
        )
        assertEquals(
            "Internal storage",
            SafDocumentExporter.describe("content://com.android.externalstorage.documents/tree/primary%3A")
        )
        assertEquals(
            "Storage",
            SafDocumentExporter.describe("content://com.android.externalstorage.documents/tree/0000-0000%3A")
        )
    }

    private companion object {
        const val PAYLOAD_BYTES = 64 * 1024
    }
}
