package com.salvia.salviabrowxer.ui.utils

import com.salvia.salviabrowxer.core.model.FileNameSanitizer
import com.salvia.salviabrowxer.core.model.MediaFileTypes
import java.text.DecimalFormat

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    return DecimalFormat("#,##0.#").format(bytes / Math.pow(1024.0, digitGroups.toDouble())) + " " + units[digitGroups]
}

fun sanitizeFilename(filename: String): String = FileNameSanitizer.sanitize(filename)

fun getFileExtension(url: String): String? = MediaFileTypes.extensionFromUrl(url)

fun getMimeTypeFromExtension(extension: String?): String = MediaFileTypes.mimeTypeForExtension(extension)