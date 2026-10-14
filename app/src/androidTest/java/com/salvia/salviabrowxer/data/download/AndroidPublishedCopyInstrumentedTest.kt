package com.salvia.salviabrowxer.data.download

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.salvia.salviabrowxer.core.storage.MediaStoreExporter
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * "Delete the download" on a device, where ownership is decided by the platform and not by us.
 *
 * `DownloadArtifactCleanerTest` proves the cleaner's rules against a fake; this proves the two things
 * only the platform can answer: that a row this app published carries this package as its owner, and
 * that a URI which merely *looks* like ours — another provider's document, a gallery row that is not
 * there, a file in the public download folder — is refused. Deleting a file the user put there
 * themselves is the one mistake a download manager cannot make.
 */
@RunWith(AndroidJUnit4::class)
class AndroidPublishedCopyInstrumentedTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val published = AndroidPublishedCopy(context)

    @Test
    fun a_row_this_app_published_is_owned_and_deleting_it_removes_it() {
        assumeTrue("publishing without a permission is an Android 10+ path", Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        val source = File(context.cacheDir, "ownership-check.mp4").apply {
            writeBytes(ByteArray(4096) { (it % 251).toByte() })
        }

        val uriString = MediaStoreExporter(context).export(source, source.name, "video/mp4", true)
        assertNotNull("the publish must return a uri to own", uriString)
        val uri = Uri.parse(uriString!!)

        // The ownership API speaks in URI strings, which is what a row stores.
        assertTrue("a row this package inserted must be ours: $uriString", published.isOwned(uriString))
        assertTrue("an owned row must be deletable", published.delete(uriString))

        // Gone for the gallery as well, which is what the user sees after deleting a download.
        var rows = -1
        context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns._ID), null, null, null)?.use { cursor ->
            rows = cursor.count
        }
        assertTrue("the deleted row must not be queryable any more, found $rows", rows <= 0)

        source.delete()
    }

    @Test
    fun a_document_outside_any_persisted_grant_is_not_owned() {
        // A real document URI on a real provider, with no grant this app ever persisted: the state
        // after a revoke, or simply a file the user put in their own folder.
        val foreign = "content://com.android.providers.downloads.documents/document/msf%3A1234"

        assertFalse("a document under no grant of ours must not be owned", published.isOwned(foreign))
        assertFalse("and it must not be deleted", published.delete(foreign))
    }

    @Test
    fun a_media_store_row_that_is_not_there_is_not_owned() {
        val absent = "content://media/external/video/media/999999999999"

        assertFalse("a row that does not resolve cannot be ours", published.isOwned(absent))
        assertFalse("and it must not be deleted", published.delete(absent))
    }

    @Test
    fun only_the_folder_this_app_creates_in_public_storage_counts_as_ours() {
        // The legacy export location, and a file the user happens to keep in the public download
        // folder. Neither needs to exist for the decision to be the one that matters.
        assertTrue(
            "Movies/SalviaBrowxer is the one directory this app creates in public storage",
            published.isOwned("file:///storage/emulated/0/Movies/SalviaBrowxer/clip.mp4")
        )
        assertFalse(
            "a file in the public download folder belongs to the user, not to us",
            published.isOwned("file:///storage/emulated/0/Download/clip.mp4")
        )
        assertFalse("nor is audio in the public music folder ours", published.isOwned("file:///storage/emulated/0/Music/clip.mp3"))
        assertFalse("an unknown scheme is never owned", published.isOwned("http://example.com/clip.mp4"))
        assertFalse("and neither is a blank uri", published.isOwned(""))
    }
}
