package com.salvia.salviabrowxer.core.storage

import android.content.Context
import android.os.Environment
import java.io.File

class StorageManager(private val context: Context) {

    fun getDefaultDownloadDirectory(): String {
        return context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.absolutePath
            ?: Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath
    }

    fun getMediaDirectory(type: MediaType): String {
        return when (type) {
            MediaType.VIDEO -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES).absolutePath
            MediaType.AUDIO -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC).absolutePath
            MediaType.IMAGE -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).absolutePath
            MediaType.DOCUMENT -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS).absolutePath
        }
    }

    fun createDownloadDirectory(name: String): File? {
        val baseDir = File(getDefaultDownloadDirectory(), name)
        return if (baseDir.exists() || baseDir.mkdirs()) {
            baseDir
        } else {
            null
        }
    }

    fun getFileUri(file: File): android.net.Uri {
        return android.net.Uri.fromFile(file)
    }

    fun isExternalStorageAvailable(): Boolean {
        return Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED
    }

    enum class MediaType {
        VIDEO, AUDIO, IMAGE, DOCUMENT
    }

    fun sanitizeFilename(filename: String): String {
        return filename
            .replace("[^a-zA-Z0-9._-]".toRegex(), "_")
            .replace("__+".toRegex(), "_")
            .replace("^_+".toRegex(), "")
            .replace("_+$".toRegex(), "")
    }

    fun getMimeType(filePath: String): String {
        val extension = filePath.substringAfterLast('.', "").lowercase()
        return when (extension) {
            "mp4", "webm", "mov", "avi", "3gp", "m4v" -> "video/*"
            "mp3", "m4a", "aac", "wav", "flac" -> "audio/*"
            "jpg", "jpeg", "png", "gif", "webp", "bmp" -> "image/*"
            "pdf" -> "application/pdf"
            "apk" -> "application/vnd.android.package-archive"
            else -> "application/octet-stream"
        }
    }
}