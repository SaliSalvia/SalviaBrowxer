package com.salvia.salviabrowxer.core.storage

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import java.io.File
import java.io.IOException

/**
 * Copies a finished download into the folder the user chose with the system picker.
 *
 * This is the only way an app may write to a removable card on modern Android: not a path, but a
 * tree the user granted, for as long as the grant lasts. The copy is written through the document
 * provider, which means it also works for a folder on internal storage, in another app's provider, or
 * in the cloud — none of which a `File` could reach.
 *
 * Deliberately a copy and not a move: the app's own file is what pause, resume, the built-in player
 * and delete all operate on, and a download that exists only inside a provider can vanish with the
 * card. The settings text says "also", and this is why it can.
 *
 * Nothing here trusts the provider: a tree that no longer resolves, a provider that refuses to create
 * a document and a write that fails halfway are all reported as "no copy" — and a half-written file is
 * deleted rather than left in the user's folder.
 */
class SafDocumentExporter(private val context: Context) {

    /** The created document's URI, or null when nothing could be written. */
    fun copyInto(treeUri: String, file: File, mimeType: String?, displayName: String): String? {
        val parent = treeParentOf(treeUri) ?: return null
        val resolver = context.contentResolver
        val mime = mimeType?.takeIf { it.isNotBlank() } ?: "application/octet-stream"
        val target = runCatching {
            DocumentsContract.createDocument(resolver, parent, mime, displayName)
        }.getOrNull() ?: return null
        return runCatching {
            val output = resolver.openOutputStream(target) ?: throw IOException("No stream for $target")
            output.use { sink -> file.inputStream().use { source -> source.copyTo(sink, BUFFER_SIZE) } }
            target.toString()
        }.getOrElse {
            // A truncated file in the user's folder is worse than no file: remove it.
            runCatching { DocumentsContract.deleteDocument(resolver, target) }
            null
        }
    }

    /** Whether the rememberered tree still resolves — an SD card can be removed, a grant revoked. */
    fun isReachable(treeUri: String): Boolean {
        val parent = treeParentOf(treeUri) ?: return false
        return runCatching {
            context.contentResolver.query(parent, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID), null, null, null)
                ?.use { it.moveToFirst() } ?: false
        }.getOrDefault(false)
    }

    /** The document URI of the tree itself, which is the parent any new file is created in. */
    private fun treeParentOf(treeUri: String): Uri? = runCatching {
        val tree = Uri.parse(treeUri)
        DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
    }.getOrNull()

    companion object {
        private const val BUFFER_SIZE = 64 * 1024

        /**
         * The human name of a tree, for the settings row.
         *
         * Providers expose it as the last path segment of the tree id, and it is already
         * percent-encoded — so `primary:Download/clips` becomes `clips` and the row says something the
         * user recognises instead of a URI nobody can read.
         */
        fun labelOf(treeUri: String): String {
            val id = runCatching { DocumentsContract.getTreeDocumentId(Uri.parse(treeUri)) }.getOrNull()
            val last = id?.trimEnd('/')?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
                ?: return treeUri
            return runCatching { Uri.decode(last) }.getOrDefault(last)
        }

        /** Document providers treat the root of a volume as its own label; a friendly fallback is clearer. */
        fun describe(treeUri: String): String {
            val label = labelOf(treeUri)
            val id = runCatching { DocumentsContract.getTreeDocumentId(Uri.parse(treeUri)) }.getOrNull().orEmpty()
            // "primary:Download" and "XXXX-XXXX:" carry no name of their own.
            if (label == treeUri || label.endsWith(':')) {
                return if (id.startsWith("primary")) "Internal storage" else "Storage"
            }
            return label
        }

        /** On API 26+ a missing provider is a real condition; below it there is nothing to ask. */
        fun supportsTreePicker(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
    }
}
