package com.salvia.salviabrowxer.core.storage

import android.Manifest
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On-device check of the claim that a finished download is reachable from the gallery.
 *
 * Runs against the device's real media store, so a permission-free publish on Android 10+, the
 * pending-flag handshake, and content read-back are all exercised for real. Run with
 * `./gradlew :app:connectedDebugAndroidTest` on an emulator or a connected phone.
 */
@RunWith(AndroidJUnit4::class)
class MediaStoreExporterInstrumentedTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun sourceFile(): File = File(context.cacheDir, "salvia-export-check.mp4").apply {
        writeBytes(ByteArray(4096) { (it % 251).toByte() })
    }

    @Test
    fun a_finished_file_is_published_and_readable_through_the_media_store() {
        assumeTrue(
            "permission-free publishing is an Android 10+ path",
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        )
        val source = sourceFile()
        val exporter = MediaStoreExporter(context)
        assertNull("Android 10+ must not need a runtime permission", exporter.requiredPermission())

        val uriString = exporter.export(source, source.name, "video/mp4", true)
        assertNotNull("the export must return a content uri", uriString)
        val uri = Uri.parse(uriString!!)
        assertTrue("expected a media-store uri, was $uri", uriString.startsWith("content://"))

        // The bytes a gallery would show must be the bytes that were downloaded.
        val readBack = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
        assertArrayEquals(source.readBytes(), readBack)

        // The row must be queryable, which is what makes it visible to other apps.
        var displayName: String? = null
        context.contentResolver
            .query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) displayName = cursor.getString(0) }
        assertNotNull("the published row must be queryable", displayName)
        assertTrue(
            "the published name should be the file name, was $displayName",
            displayName!!.startsWith("salvia-export-check")
        )

        // Leave no test media behind on the device.
        context.contentResolver.delete(uri, null, null)
        source.delete()
    }

    @Test
    fun older_devices_ask_for_the_storage_permission_instead_of_publishing_silently() {
        assumeTrue("only Android 9 and below need the legacy permission", Build.VERSION.SDK_INT < Build.VERSION_CODES.Q)
        val exporter = MediaStoreExporter(context)
        assertEquals(Manifest.permission.WRITE_EXTERNAL_STORAGE, exporter.requiredPermission())
        assertTrue(!exporter.isPermissionFree())
    }

    @Test
    fun video_and_audio_are_classified_for_the_right_media_collection() {
        val exporter = MediaStoreExporter(context)
        assertTrue(exporter.isVideoFile("clip.mp4", null))
        assertTrue(!exporter.isVideoFile("song.mp3", null))
    }
}
