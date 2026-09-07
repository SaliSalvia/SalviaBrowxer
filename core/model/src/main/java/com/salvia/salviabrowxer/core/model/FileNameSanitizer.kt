package com.salvia.salviabrowxer.core.model

/**
 * Filename sanitizer shared by the downloader, storage layer and UI helpers.
 *
 * Unlike the old ASCII-only regular expression (which turned Persian/Unicode titles into `_`),
 * this only replaces characters that are actually unsafe in file names. Unicode letters, digits
 * and spaces are preserved so "ویدیو آزمایشی.mp4" stays readable after download.
 */
object FileNameSanitizer {

    private const val MAX_LENGTH = 180
    private const val FALLBACK_NAME = "download"

    private val invalidCharacters = Regex("[\\\\/:*?\"<>|\\u0000]")
    private val controlCharacters = Regex("[\\p{Cc}\\p{Cf}]")
    private val whitespace = Regex("\\s+")

    fun sanitize(filename: String): String {
        val base = filename
            .replace(invalidCharacters, "_")
            .replace(controlCharacters, "")
            .trim()
            .replace(whitespace, " ")
            .trim(' ', '.', '_')
            .take(MAX_LENGTH)
        return base.ifBlank { FALLBACK_NAME }
    }

    fun generateFilename(title: String?, extension: String?): String {
        val base = sanitize(title?.takeIf { it.isNotBlank() } ?: FALLBACK_NAME)
            .replace(whitespace, "_")
        val ext = extension?.takeIf { it.isNotBlank() }?.lowercase()?.trim('.')
        return if (ext.isNullOrBlank()) base else "$base.$ext"
    }
}
