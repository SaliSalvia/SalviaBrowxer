package com.salvia.salviabrowxer.core.storage

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import com.salvia.salviabrowxer.core.model.MediaUrlRules
import java.io.File

/**
 * Copies a finished download into shared storage so galleries, the Files app and other players can
 * see it — not just this app.
 *
 * App-scoped storage keeps downloads permission-free, but it also means nothing else can see them.
 * This is the bridge:
 * - **API 29+**: writes through `MediaStore` with the pending-flag protocol, which needs no
 *   permission at all.
 * - **API 24–28**: copies into the public `Movies`/`Music` folder and hands the path to the media
 *   scanner, which needs `WRITE_EXTERNAL_STORAGE`. Without the grant the export is skipped rather
 *   than failing a finished download; the share sheet still reaches the file.
 *
 * The app-scoped copy is always kept as the source of truth; this is a publish step, not a move.
 */
class MediaStoreExporter(private val context: Context) {

    /** True when this device can export without requesting a runtime permission. */
    fun isPermissionFree(): Boolean = requiredPermission() == null

    /**
     * The runtime permission this device's export needs, or `null` when it needs none.
     *
     * Android 10+ publishes through `MediaStore` with no permission at all. Android 9 and below
     * writes into the public Movies/Music folder, which does need `WRITE_EXTERNAL_STORAGE` — the
     * automatic post-download export skips silently there, but a user-initiated export must ask.
     */
    fun requiredPermission(): String? = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> null
        hasLegacyWritePermission() -> null
        else -> Manifest.permission.WRITE_EXTERNAL_STORAGE
    }

    /** True when the finished file should be published as video rather than audio. */
    fun isVideoFile(fileName: String, mimeType: String?): Boolean =
        fileName.substringAfterLast('.', "").lowercase() in MediaUrlRules.VIDEO_EXTENSIONS ||
            MediaUrlRules.isVideoMime(mimeType)

    fun hasLegacyWritePermission(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    /** Returns the published URI/path, or null when the export could not be performed. */
    fun export(file: File, displayName: String, mimeType: String?, isVideo: Boolean): String? {
        if (!file.exists() || file.length() == 0L) return null
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) insertViaMediaStore(file, displayName, mimeType, isVideo)
            else legacyCopy(file, displayName, isVideo)
        }.getOrNull()
    }

    private fun insertViaMediaStore(file: File, displayName: String, mimeType: String?, isVideo: Boolean): String? {
        val collection = if (isVideo) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
        val relativePath = "${if (isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_MUSIC}/SalviaBrowxer"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType ?: if (isVideo) "video/mp4" else "audio/mp4")
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri: Uri = context.contentResolver.insert(collection, values) ?: return null
        // The success path must return a real Boolean: the previous version ended the block with
        // `use { ... }`, whose value is Unit, so every modern export was treated as a failure and
        // its freshly inserted row was deleted. This is what the Robolectric test caught.
        val copied = runCatching {
            val output = context.contentResolver.openOutputStream(uri) ?: return@runCatching false
            output.use { sink -> file.inputStream().use { source -> source.copyTo(sink) } }
            true
        }.getOrDefault(false)
        if (!copied) {
            context.contentResolver.delete(uri, null, null)
            return null
        }
        val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
        context.contentResolver.update(uri, done, null, null)
        return uri.toString()
    }

    private fun legacyCopy(file: File, displayName: String, isVideo: Boolean): String? {
        if (!hasLegacyWritePermission()) return null
        val directory = File(
            Environment.getExternalStoragePublicDirectory(if (isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_MUSIC),
            "SalviaBrowxer"
        )
        if (!directory.exists() && !directory.mkdirs()) return null
        val target = nonConflicting(File(directory, displayName))
        file.copyTo(target, overwrite = false)
        MediaScannerConnection.scanFile(context, arrayOf(target.absolutePath), null, null)
        return Uri.fromFile(target).toString()
    }

    private fun nonConflicting(file: File): File {
        if (!file.exists()) return file
        val parent = file.parentFile ?: return file
        val base = file.nameWithoutExtension
        val extension = file.extension
        var index = 1
        while (index <= 999) {
            val name = if (extension.isEmpty()) "$base ($index)" else "$base ($index).$extension"
            val candidate = File(parent, name)
            if (!candidate.exists()) return candidate
            index++
        }
        return file
    }
}
