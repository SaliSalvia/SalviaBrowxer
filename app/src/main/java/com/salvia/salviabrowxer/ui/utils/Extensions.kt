package com.salvia.salviabrowxer.ui.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun Context.toast(message: String, duration: Int = android.widget.Toast.LENGTH_SHORT) {
    android.widget.Toast.makeText(this, message, duration).show()
}

fun Context.openUrl(url: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    } catch (e: Exception) {
        toast("Could not open URL: $url")
    }
}

fun Context.shareText(text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    startActivity(Intent.createChooser(intent, "Share"))
}

fun Date.format(pattern: String = "yyyy-MM-dd HH:mm:ss"): String {
    return SimpleDateFormat(pattern, Locale.getDefault()).format(this)
}

fun Long.formatDate(pattern: String = "yyyy-MM-dd HH:mm:ss"): String {
    return Date(this).format(pattern)
}

fun String.isValidUrl(): Boolean {
    return try {
        URL(this)
        true
    } catch (e: Exception) {
        false
    }
}

fun String.toUri(): Uri? {
    return try {
        Uri.parse(this)
    } catch (e: Exception) {
        null
    }
}

fun String.containsAny(keywords: List<String>, ignoreCase: Boolean = true): Boolean {
    return keywords.any { keyword ->
        this.contains(keyword, ignoreCase)
    }
}

fun String.startsWithAny(prefixes: List<String>, ignoreCase: Boolean = true): Boolean {
    return prefixes.any { prefix ->
        this.startsWith(prefix, ignoreCase)
    }
}

fun String.endsWithAny(suffixes: List<String>, ignoreCase: Boolean = true): Boolean {
    return suffixes.any { suffix ->
        this.endsWith(suffix, ignoreCase)
    }
}