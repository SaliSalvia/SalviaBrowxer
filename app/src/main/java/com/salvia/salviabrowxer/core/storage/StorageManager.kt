package com.salvia.salviabrowxer.core.storage

import android.content.Context
import android.os.Environment
import java.io.File

class StorageManager(private val context: Context) {

    /**
     * App-scoped download directory — no runtime permission needed.
     * Falls back to internal filesDir when external storage is unavailable (e.g. emulated storage not mounted).
     * The previous implementation used getExternalStoragePublicDirectory() which is deprecated since API 29
     * and is blocked by scoped-storage — that path is intentionally dropped.
     */
    fun getDefaultDownloadDirectory(): String {
        val external = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        if (external != null) {
            if (!external.exists()) external.mkdirs()
            return external.absolutePath
        }
        // Fallback: internal storage — always writable, always fast I/O (no SD-card seek lag)
        val fallback = File(context.filesDir, "Downloads")
        if (!fallback.exists()) fallback.mkdirs()
        return fallback.absolutePath
    }

    fun getMediaDirectory(type: MediaType): String {
        // Keep all media under the same app-scoped tree so cleanup stays trivial and no SAF permission is needed.
        val dirName = when (type) {
            MediaType.VIDEO -> Environment.DIRECTORY_MOVIES
            MediaType.AUDIO -> Environment.DIRECTORY_MUSIC
            MediaType.IMAGE -> Environment.DIRECTORY_PICTURES
            MediaType.DOCUMENT -> Environment.DIRECTORY_DOCUMENTS
        }
        val scoped = context.getExternalFilesDir(dirName)
        if (scoped != null) {
            if (!scoped.exists()) scoped.mkdirs()
            return scoped.absolutePath
        }
        return getDefaultDownloadDirectory()
    }

    fun createDownloadDirectory(name: String): File? {
        val baseDir = File(getDefaultDownloadDirectory(), sanitizeFilename(name).ifBlank { "SalviaBrowxer" })
        return if (baseDir.exists() || baseDir.mkdirs()) baseDir else null
    }

    fun getFileUri(file: File): android.net.Uri = android.net.Uri.fromFile(file)

    fun isExternalStorageAvailable(): Boolean =
        Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED

    enum class MediaType { VIDEO, AUDIO, IMAGE, DOCUMENT }

    fun sanitizeFilename(filename: String): String =
        filename
            .replace("[^a-zA-Z0-9._-]".toRegex(), "_")
            .replace("__+".toRegex(), "_")
            .replace("^_+".toRegex(), "")
            .replace("_+$".toRegex(), "")

    fun getMimeType(filePath: String): String {
        val extension = filePath.substringAfterLast('.', "").lowercase()
        return when (extension) {
            "mp4", "webm", "mov", "avi", "3gp", "m4v", "mkv", "flv", "ts", "m3u8", "mpd" -> "video/*"
            "mp3", "m4a", "aac", "wav", "flac", "ogg", "wma" -> "audio/*"
            "jpg", "jpeg", "png", "gif", "webp", "bmp", "svg" -> "image/*"
            "pdf" -> "application/pdf"
            "apk" -> "application/vnd.android.package-archive"
            else -> "application/octet-stream"
        }
    }
}
