package com.salvia.salviabrowxer.core.storage

import android.Manifest
import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import org.robolectric.shadows.ShadowMediaScannerConnection

/**
 * Executes the real export implementation against the real Android framework, at two SDK levels.
 *
 * The API 24–28 branch cannot be reached on a modern device and this sandbox cannot run an
 * emulator, so Robolectric is what actually verifies it: the file really is copied into the public
 * Movies folder, the media scanner really is handed the path, and the permission gate really does
 * refuse before the grant and allow after it.
 */
// The SDK is pinned explicitly: Robolectric 4.13 does not know this app's targetSdk (36) and
// refuses to guess a default, and each test states the level whose branch it exercises anyway.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
class MediaStoreExporterTest {

    private fun app(): Application = RuntimeEnvironment.getApplication()

    private fun sourceFile(): File {
        val directory = Files.createTempDirectory("salvia-src").toFile()
        return File(directory, "clip.mp4").apply { writeBytes(ByteArray(128) { it.toByte() }) }
    }

    private fun publicMoviesFile(name: String): File =
        File(File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "SalviaBrowxer"), name)

    // region API 24 — the legacy, permission-gated path

    @Test
    @Config(sdk = [24])
    fun `api 24 reports the storage permission as required until it is granted`() {
        val exporter = MediaStoreExporter(app())
        assertEquals(Manifest.permission.WRITE_EXTERNAL_STORAGE, exporter.requiredPermission())
        assertTrue(!exporter.isPermissionFree())
    }

    @Test
    @Config(sdk = [24])
    fun `api 24 refuses to export before the grant and writes nothing`() {
        val exporter = MediaStoreExporter(app())
        val source = sourceFile()

        assertNull("an export without the grant must be refused", exporter.export(source, source.name, "video/mp4", true))
        assertTrue("nothing may be published before the grant", !publicMoviesFile("clip.mp4").exists())
    }

    @Test
    @Config(sdk = [24])
    fun `api 24 copies the finished file into Movies and asks the scanner to index it`() {
        val app = app()
        shadowOf(app).grantPermissions(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        val exporter = MediaStoreExporter(app)
        assertNull(exporter.requiredPermission())
        assertTrue(exporter.isPermissionFree())

        val source = sourceFile()
        val uri = exporter.export(source, "clip.mp4", "video/mp4", true)

        assertNotNull(uri)
        assertTrue("export must publish a file uri, was $uri", uri!!.startsWith("file:"))
        val target = publicMoviesFile("clip.mp4")
        assertTrue("the media must exist in the public Movies folder", target.exists())
        assertEquals(source.readBytes().toList(), target.readBytes().toList())
        assertTrue(
            "the media scanner must be handed the published path",
            ShadowMediaScannerConnection.getSavedPaths().contains(target.absolutePath)
        )
    }

    @Test
    @Config(sdk = [24])
    fun `api 24 never overwrites an already published file`() {
        val app = app()
        shadowOf(app).grantPermissions(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        val exporter = MediaStoreExporter(app)

        exporter.export(sourceFile(), "clip.mp4", "video/mp4", true)
        exporter.export(sourceFile(), "clip.mp4", "video/mp4", true)

        val names = publicMoviesFile("clip.mp4").parentFile?.list()?.sorted().orEmpty()
        assertTrue("the first export must survive: $names", names.contains("clip.mp4"))
        assertTrue("the second export must land beside it: $names", names.contains("clip (1).mp4"))
    }

    // endregion

    // region API 29+ — MediaStore pending-flag publishing, no permission

    @Test
    @Config(sdk = [33])
    fun `api 29 needs no runtime permission for the export`() {
        val exporter = MediaStoreExporter(app())
        assertNull(exporter.requiredPermission())
        assertTrue(exporter.isPermissionFree())
    }

    @Test
    @Config(sdk = [33])
    fun `api 29 publishes through MediaStore and clears the pending flag`() {
        val provider = RecordingMediaProvider()
        ShadowContentResolver.registerProviderInternal(MediaStore.AUTHORITY, provider)
        val app = app()
        // Robolectric shadows ContentResolver.openOutputStream, so the published bytes are
        // observed through a registered sink rather than the provider's own file handle.
        val published = java.io.ByteArrayOutputStream()
        val expectedUri = Uri.withAppendedPath(MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), "1")
        shadowOf(app.contentResolver).registerOutputStream(expectedUri, published)
        val exporter = MediaStoreExporter(app)

        val source = sourceFile()
        val uri = exporter.export(source, "clip.mp4", "video/mp4", true)

        assertNotNull("the insert must produce a uri", uri)
        assertTrue("the export must be a content uri, was $uri", uri!!.startsWith("content://"))
        val inserted = provider.insertedValues.single()
        assertEquals("clip.mp4", inserted.getAsString(MediaStore.MediaColumns.DISPLAY_NAME))
        assertEquals("video/mp4", inserted.getAsString(MediaStore.MediaColumns.MIME_TYPE))
        assertEquals("Movies/SalviaBrowxer", inserted.getAsString(MediaStore.MediaColumns.RELATIVE_PATH))
        assertEquals("the row must start pending", 1, inserted.getAsInteger(MediaStore.MediaColumns.IS_PENDING) ?: -1)
        assertEquals("the pending flag must be cleared once written", 0, provider.finalPendingValue ?: -1)
        assertEquals("the media bytes must be copied into the store", source.readBytes().toList(), published.toByteArray().toList())
    }

    // endregion

    @Test
    fun `audio files are classified as audio and video files as video`() {
        val exporter = MediaStoreExporter(app())
        assertTrue(exporter.isVideoFile("clip.mp4", null))
        assertTrue(exporter.isVideoFile("clip.mkv", "application/octet-stream"))
        assertTrue(exporter.isVideoFile("track.bin", "video/mp4"))
        assertTrue(!exporter.isVideoFile("song.mp3", null))
        assertTrue(!exporter.isVideoFile("voice.m4a", null))
    }

    /**
     * A stand-in for the media provider: it hands back a synthetic uri and writes whatever is
     * published into a real file, which is exactly the contract `ContentResolver.openOutputStream`
     * needs in order to observe the exporter's bytes and its pending-flag update.
     */
    private class RecordingMediaProvider : ContentProvider() {
        val insertedValues = mutableListOf<ContentValues>()
        var finalPendingValue: Int? = null

        override fun onCreate(): Boolean = true

        override fun insert(uri: Uri, values: ContentValues?): Uri? {
            values?.let { insertedValues += ContentValues(it) }
            return Uri.withAppendedPath(uri, "1")
        }

        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int {
            values?.getAsInteger(MediaStore.MediaColumns.IS_PENDING)?.let { finalPendingValue = it }
            return 1
        }

        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
        override fun getType(uri: Uri): String? = null
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    }
}
