package com.example.engine

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * Single place that knows how finished downloads are stored and exposed to other apps.
 *
 * - Android 10+: rows in MediaStore Downloads (content:// URIs owned by the system).
 * - Android 7–9: files in app-specific external storage exposed through [FileProvider].
 *   Raw file:// URIs are never handed to other apps (FileUriExposedException on API 24+);
 *   records written by older versions are transparently converted.
 */
object DownloadFileAccess {

    const val DOWNLOAD_FOLDER = "SalviaBrowxer"

    fun authority(context: Context): String = context.packageName + ".fileprovider"

    fun shareableUriForFile(context: Context, file: File): Uri =
        runCatching { FileProvider.getUriForFile(context, authority(context), file) }
            .getOrElse { Uri.fromFile(file) }

    /** Converts a stored localUri into a URI that is safe to pass in an Intent. */
    fun shareableUri(context: Context, storedUri: String): Uri? {
        if (storedUri.isBlank()) return null
        val parsed = runCatching { Uri.parse(storedUri) }.getOrNull() ?: return null
        return when (parsed.scheme) {
            ContentResolver.SCHEME_CONTENT -> parsed
            ContentResolver.SCHEME_FILE -> {
                val file = File(parsed.path ?: return null)
                if (!file.exists()) return null
                runCatching { FileProvider.getUriForFile(context, authority(context), file) }.getOrNull()
            }
            else -> null
        }
    }

    fun openIntent(context: Context, storedUri: String, mimeType: String, fileName: String): Intent? {
        val uri = shareableUri(context, storedUri) ?: return null
        val type = DownloadFilePolicy.outputMimeType(mimeType, fileName)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, type)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun shareIntent(context: Context, storedUri: String, mimeType: String, fileName: String): Intent? {
        val uri = shareableUri(context, storedUri) ?: return null
        val type = DownloadFilePolicy.outputMimeType(mimeType, fileName)
        val send = Intent(Intent.ACTION_SEND).apply {
            this.type = type
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TITLE, fileName)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, fileName).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
    }

    /**
     * Deletes the published file behind a download record. Returns true when nothing is left
     * behind (deleted, or already gone). Only files this app published are touched: a content
     * URI is removed through the resolver (the app owns its own MediaStore rows), a legacy
     * file only when it lives inside this app's download folder.
     */
    fun deletePublishedFile(context: Context, storedUri: String): Boolean {
        if (storedUri.isBlank()) return true
        val parsed = runCatching { Uri.parse(storedUri) }.getOrNull() ?: return false
        return runCatching {
            when (parsed.scheme) {
                ContentResolver.SCHEME_CONTENT -> {
                    if (parsed.authority == authority(context)) {
                        legacyFileFor(context, parsed)?.let { it.delete() || !it.exists() } ?: false
                    } else {
                        context.contentResolver.delete(parsed, null, null) >= 0
                    }
                }
                ContentResolver.SCHEME_FILE -> {
                    val file = File(parsed.path ?: return@runCatching false)
                    if (!isInsideDownloadFolder(context, file)) return@runCatching false
                    file.delete() || !file.exists()
                }
                else -> false
            }
        }.getOrDefault(false)
    }

    private fun downloadRoots(context: Context): List<File> = listOfNotNull(
        context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS),
        File(context.filesDir, "downloads"),
    ).map { File(it, DOWNLOAD_FOLDER) }

    private fun isInsideDownloadFolder(context: Context, file: File): Boolean {
        val canonical = runCatching { file.canonicalPath }.getOrNull() ?: return false
        return downloadRoots(context).any { root ->
            val rootPath = runCatching { root.canonicalPath }.getOrNull() ?: return@any false
            canonical.startsWith(rootPath + File.separator)
        }
    }

    /** Resolves a FileProvider URI produced by [shareableUriForFile] back to its file. */
    private fun legacyFileFor(context: Context, uri: Uri): File? {
        val name = uri.lastPathSegment ?: return null
        return downloadRoots(context)
            .map { File(it, name) }
            .firstOrNull { it.exists() && isInsideDownloadFolder(context, it) }
    }
}
