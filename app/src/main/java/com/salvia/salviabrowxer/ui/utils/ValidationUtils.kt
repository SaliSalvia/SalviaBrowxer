package com.salvia.salviabrowxer.ui.utils

import java.util.regex.Pattern

fun isValidUrl(url: String): Boolean {
    val pattern: Pattern = Pattern.compile(
        "^(https?|ftp|file)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]"
    )
    return pattern.matcher(url).matches()
}

fun isValidFilename(filename: String): Boolean {
    if (filename.isEmpty()) return false
    if (filename.contains("/") || filename.contains("\\")) return false
    if (filename.contains("\u0000")) return false
    return true
}

fun sanitizeUrl(url: String): String {
    return url
        .replace("\\s+".toRegex(), "")
        .trim()
}

fun normalizeUrl(url: String): String {
    var normalized = url
    if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
        normalized = "https://$normalized"
    }
    return normalized
}