package com.salvia.salviabrowxer.core.storage

import android.content.Context
import android.os.Environment
import com.salvia.salviabrowxer.core.model.FileNameSanitizer
import com.salvia.salviabrowxer.core.model.MediaFileTypes
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

    fun sanitizeFilename(filename: String): String = FileNameSanitizer.sanitize(filename)

    fun getMimeType(filePath: String): String =
        MediaFileTypes.mimeTypeForExtension(filePath.substringAfterLast('.', ""))
}