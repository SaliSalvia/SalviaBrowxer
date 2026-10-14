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

    /**
     * Moves a finished file into the tree, and only deletes the source once the document is whole.
     *
     * This is the primary-destination path: the transfer itself runs against private storage (that is
     * where the resumable parts and the staging live; a provider offers neither `rename` nor a byte
     * range), and the finished file is copied through the document provider at the end. The order is
     * what makes it safe:
     *
     *  1. create the document — a provider that refuses, a folder that vanished or a revoked grant all
     *     end here, and the file stays where the app can still use it;
     *  2. stream the bytes in;
     *  3. **verify the length**, because a provider that reports success while writing half of it is
     *     exactly the case that loses a download;
     *  4. only then delete the private file.
     *
     * A process kill between 2 and 4 leaves both copies: the private one is still whole, so nothing is
     * lost, and the leftover document is under our own grant — the artifact cleaner treats it as ours
     * and can remove it, so it is not an orphan the user cannot explain.
     *
     * @return the created document's URI and the name the provider actually used — a name collision
     *   makes the provider rename the file (`clip (1).mp4`), and the row must say the real name.
     */
    fun moveInto(treeUri: String, file: File, mimeType: String?, displayName: String): SafMove? {
        if (!file.exists() || file.length() == 0L) return null
        val parent = treeParentOf(treeUri) ?: return null
        val resolver = context.contentResolver
        val mime = mimeType?.takeIf { it.isNotBlank() } ?: "application/octet-stream"
        val target = runCatching { DocumentsContract.createDocument(resolver, parent, mime, displayName) }.getOrNull() ?: return null
        val copied = runCatching {
            val output = resolver.openOutputStream(target) ?: throw IOException("No stream for $target")
            output.use { sink -> file.inputStream().use { source -> source.copyTo(sink, BUFFER_SIZE) } }
            val written = querySize(target)
            // A provider that does not report a size is trusted; one that reports the wrong size is not.
            written == null || written == file.length()
        }.getOrDefault(false)
        if (!copied) {
            runCatching { DocumentsContract.deleteDocument(resolver, target) }
            return null
        }
        // The source goes only now, and failing to delete it is not a failure of the move: the row
        // already points at the document, and the cleaner can still take the private file away later.
        runCatching { file.delete() }
        return SafMove(uri = target.toString(), displayName = queryDisplayName(target) ?: displayName)
    }

    /** The created document and the name the provider chose for it. */
    data class SafMove(val uri: String, val displayName: String)

    private fun querySize(uri: Uri): Long? = runCatching {
        context.contentResolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_SIZE), null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst() || cursor.isNull(0)) null else cursor.getLong(0)
        }
    }.getOrNull()

    private fun queryDisplayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) null else cursor.getString(0)
        }
    }.getOrNull()

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
