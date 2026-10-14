package com.salvia.salviabrowxer.debug

import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsProvider
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream

/**
 * The folder the user would have picked, for instrumentation tests only.
 *
 * `SafDocumentExporter` writes the finished download into a tree the system picker granted, and the
 * cases that matter most are the ones a real provider only reaches by accident: a name it has to
 * rename, a stream it accepts but does not keep in full, and a creation it refuses. No test can make
 * the picker grant a tree, so this provider stands in for one - it is declared in the debug manifest
 * only, is not exported, and lives in the app's own cache directory.
 *
 * What the provider does is decided by the *name* the caller asks for, so nothing has to be shared
 * between the test process and the provider:
 *
 *  - `half-…` — reads the whole stream and keeps only its first [HALF_WRITE_BYTES]. This is the
 *    provider that reports success while dropping the tail, which is the case `moveInto` verifies
 *    the length for;
 *  - `dup-…` — creates the document under `… (1).<ext>`, the rename a provider does when the name is
 *    already taken, so the row can be checked against the name actually used;
 *  - `refuse-…` — throws, as a provider whose folder has gone away does.
 *
 * Anything else is a plain document that works the way the real one does.
 */
class DebugDocumentsProvider : DocumentsProvider() {

    override fun onCreate(): Boolean {
        directory().mkdirs()
        return true
    }

    /** Every document of this provider lives here, and the directory is created on first use. */
    private fun directory(): File = File(requireNotNull(context).cacheDir, DIRECTORY)

    private fun fileOf(documentId: String): File = File(directory(), documentId)

    /** The authority the manifest declares, so a returned URI resolves back to this provider. */
    private fun authorityOf(): String = requireNotNull(context).packageName + AUTHORITY_SUFFIX

    override fun queryRoots(projection: Array<out String>?): Cursor {
        val columns = projection ?: ROOT_COLUMNS
        val cursor = MatrixCursor(columns)
        val row = cursor.newRow()
        for (column in columns) {
            row.add(
                when (column) {
                    DocumentsContract.Root.COLUMN_ROOT_ID -> ROOT_ID
                    DocumentsContract.Root.COLUMN_DOCUMENT_ID -> ROOT_ID
                    DocumentsContract.Root.COLUMN_TITLE -> TITLE
                    DocumentsContract.Root.COLUMN_FLAGS ->
                        DocumentsContract.Root.FLAG_LOCAL_ONLY or DocumentsContract.Root.FLAG_SUPPORTS_IS_CHILD
                    else -> null
                }
            )
        }
        return cursor
    }

    /**
     * Every document of this provider is a direct child of the single root.
     *
     * This is not decoration: the framework validates a tree document URI by asking
     * `isChildDocument(treeId, documentId)`, and the base implementation answers `false`. Without this
     * override every `content://…/tree/root/document/<name>` URI - which is the shape a move
     * produces, and the shape the app stores in its rows - would be refused with a SecurityException
     * before the provider was ever reached.
     */
    override fun isChildDocument(parentDocumentId: String, documentId: String): Boolean =
        parentDocumentId == ROOT_ID && documentId != ROOT_ID

    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor {
        if (documentId != ROOT_ID && !fileOf(documentId).exists()) {
            throw FileNotFoundException("No document: $documentId")
        }
        return documentCursor(listOf(documentId), projection ?: DOCUMENT_COLUMNS)
    }

    override fun queryChildDocuments(
        parentDocumentId: String,
        projection: Array<out String>?,
        sortOrder: String?
    ): Cursor = documentCursor(directory().list()?.toList().orEmpty(), projection ?: DOCUMENT_COLUMNS)

    /**
     * A cursor carrying exactly the columns that were asked for.
     *
     * Callers read a projection positionally - `getLong(0)` for a size - so echoing the projection
     * back is what makes this behave like a provider written by someone who read the contract.
     */
    private fun documentCursor(documentIds: List<String>, columns: Array<out String>): Cursor {
        val cursor = MatrixCursor(columns)
        for (documentId in documentIds) {
            val file = fileOf(documentId)
            val isDirectory = documentId == ROOT_ID
            val row = cursor.newRow()
            for (column in columns) {
                row.add(
                    when (column) {
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID -> documentId
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME -> documentId
                        DocumentsContract.Document.COLUMN_MIME_TYPE ->
                            if (isDirectory) DocumentsContract.Document.MIME_TYPE_DIR else MIME_TYPE
                        DocumentsContract.Document.COLUMN_SIZE -> if (isDirectory) 0L else file.length()
                        DocumentsContract.Document.COLUMN_FLAGS -> 0
                        DocumentsContract.Document.COLUMN_LAST_MODIFIED -> file.lastModified()
                        else -> null
                    }
                )
            }
        }
        return cursor
    }

    override fun openDocument(documentId: String, mode: String, signal: CancellationSignal?): ParcelFileDescriptor {
        val target = fileOf(documentId)
        target.parentFile?.mkdirs()
        // Reading back what was written is how a test (and the app) verifies the copy, so a read is
        // never answered with the half-writing stream.
        if (documentId.startsWith(HALF_WRITE_PREFIX) && mode != MODE_READ) return halfWritingDescriptor(target)
        val flags = when (mode) {
            MODE_READ -> ParcelFileDescriptor.MODE_READ_ONLY
            "wa" -> ParcelFileDescriptor.MODE_WRITE_ONLY or ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_APPEND
            "rw", "rwt" -> ParcelFileDescriptor.MODE_READ_WRITE or ParcelFileDescriptor.MODE_CREATE
            else -> ParcelFileDescriptor.MODE_WRITE_ONLY or ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE
        }
        return ParcelFileDescriptor.open(target, flags)
    }

    /**
     * Accepts the whole stream and writes only a prefix of it into the document.
     *
     * The pipe is what makes that possible: the caller's writes land in the pipe, this side drains
     * them (so the caller never sees a broken pipe and believes the write succeeded) and keeps
     * [HALF_WRITE_BYTES] of what arrived. The document that results is smaller than the file it was
     * copied from, which is exactly the disagreement [SafDocumentExporter] refuses to trust.
     */
    private fun halfWritingDescriptor(target: File): ParcelFileDescriptor {
        val pipe = ParcelFileDescriptor.createPipe()
        val readEnd = pipe[0]
        val writeEnd = pipe[1]
        Thread {
            runCatching {
                ParcelFileDescriptor.AutoCloseInputStream(readEnd).use { source ->
                    FileOutputStream(target).use { sink ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        var kept = 0L
                        while (true) {
                            val count = source.read(buffer)
                            if (count < 0) break
                            val room = (HALF_WRITE_BYTES - kept).toInt()
                            if (room <= 0) continue
                            val take = minOf(count, room)
                            sink.write(buffer, 0, take)
                            kept += take
                        }
                        sink.flush()
                    }
                }
            }
        }.apply {
            isDaemon = true
            name = "debug-documents-half-write"
        }.start()
        return writeEnd
    }

    override fun createDocument(parentDocumentId: String, mimeType: String, displayName: String): String {
        if (displayName.startsWith(REFUSE_PREFIX)) {
            throw FileNotFoundException("This provider refuses to create $displayName")
        }
        // The parent is ignored on purpose: this provider has a single flat folder, and the case
        // under test is about names and bytes, not about a tree.
        val documentId = if (displayName.startsWith(DUPLICATE_PREFIX)) renamed(displayName) else displayName
        fileOf(documentId).parentFile?.mkdirs()
        return DocumentsContract.buildDocumentUri(authorityOf(), documentId).toString()
    }

    override fun deleteDocument(documentId: String) {
        val file = fileOf(documentId)
        if (!file.exists() || !file.delete()) throw FileNotFoundException("No document to delete: $documentId")
    }

    /** `dup-clip.mp4` becomes `dup-clip (1).mp4`, the name a provider uses when one is taken. */
    private fun renamed(displayName: String): String {
        val base = displayName.substringBeforeLast('.', displayName)
        val extension = displayName.substringAfterLast('.', "")
        return if (extension.isEmpty()) "$base (1)" else "$base (1).$extension"
    }

    companion object {
        const val AUTHORITY_SUFFIX = ".debugdocuments"
        const val DIRECTORY = "debug-documents"
        const val ROOT_ID = "root"

        const val HALF_WRITE_PREFIX = "half-"
        const val DUPLICATE_PREFIX = "dup-"
        const val REFUSE_PREFIX = "refuse-"

        /** How much of a half-written document survives; far below any file a test copies. */
        const val HALF_WRITE_BYTES = 1024L

        private const val TITLE = "Debug documents"
        private const val MIME_TYPE = "video/mp4"
        private const val MODE_READ = "r"
        private const val BUFFER_SIZE = 8 * 1024

        private val ROOT_COLUMNS = arrayOf(
            DocumentsContract.Root.COLUMN_ROOT_ID,
            DocumentsContract.Root.COLUMN_DOCUMENT_ID,
            DocumentsContract.Root.COLUMN_TITLE,
            DocumentsContract.Root.COLUMN_FLAGS
        )

        private val DOCUMENT_COLUMNS = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_FLAGS,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        )
    }
}
