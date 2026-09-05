package com.salvia.salviabrowxer.ui.utils

import java.io.File
import java.text.DecimalFormat

fun File.safeDelete(): Boolean {
    return if (exists()) {
        delete()
    } else {
        false
    }
}

fun File.ensureParentDirectory(): Boolean {
    parentFile?.let { parent ->
        if (!parent.exists()) {
            return parent.mkdirs()
        }
    }
    return true
}

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    return DecimalFormat("#,##0.#").format(bytes / Math.pow(1024.0, digitGroups.toDouble())) + " " + units[digitGroups]
}

fun sanitizeFilename(filename: String): String {
    return filename
        .replace("[^a-zA-Z0-9._-]".toRegex(), "_")
        .replace("__+".toRegex(), "_")
        .replace("^_+".toRegex(), "")
        .replace("_+$".toRegex(), "")
}

fun getFileExtension(url: String): String? {
    val lastDotIndex = url.lastIndexOf('.')
    val lastSlashIndex = url.lastIndexOf('/')
    return if (lastDotIndex > lastSlashIndex && lastDotIndex < url.length - 1) {
        url.substring(lastDotIndex + 1).lowercase()
    } else {
        null
    }
}

fun getMimeTypeFromExtension(extension: String?): String {
    return when (extension?.lowercase()) {
        "mp4", "webm", "mov", "avi", "3gp", "m4v" -> "video/*"
        "mp3", "m4a", "aac", "wav", "flac" -> "audio/*"
        "jpg", "jpeg", "png", "gif", "webp", "bmp" -> "image/*"
        "pdf" -> "application/pdf"
        "apk" -> "application/vnd.android.package-archive"
        "zip", "rar", "7z", "tar", "gz" -> "application/zip"
        "txt", "csv", "json", "xml", "html", "htm" -> "text/*"
        else -> "application/octet-stream"
    }
}