package com.salvia.salviabrowxer.data.download

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import java.io.File

/**
 * The app-scoped directories this app created and may therefore delete from.
 *
 * Note what is *not* in the list: the public `Movies`/`Music` folders (the legacy export path is
 * handled per copy, by [AndroidPublishedCopy], so a file of the user's that happens to live there
 * with the same name is never removed) and the whole of external storage.
 */
fun appOwnedRoots(context: Context): List<File> = listOfNotNull(
    context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
    context.getExternalFilesDir(null),
    context.filesDir,
    context.cacheDir
)

/** The cleaner as the app uses it: app-owned file roots plus framework-side ownership checks. */
fun androidArtifactCleaner(context: Context): DownloadArtifactCleaner =
    DownloadArtifactCleaner(
        ownedRoots = { appOwnedRoots(context) },
        publishedCopy = AndroidPublishedCopy(context.applicationContext)
    )

/**
 * Whether a published copy belongs to this app, and how to remove it if so.
 *
 * Three shapes exist, and each is checked against the app's own records rather than assumed from
 * the URI alone:
 *
 *  - **MediaStore (API 29+)**: the row carries `owner_package_name`; only a row this package
 *    inserted is deleted. Another app's file that merely has our name is left alone.
 *  - **SAF document**: only inside a tree this app holds a *persisted write grant* for. The folder
 *    is the user's, but the document inside it was created by this app, and deleting it is what
 *    "delete the download" means.
 *  - **Legacy public copy (API 24–28)**: a file inside `Movies|Music/SalviaBrowxer`, the one
 *    directory this app creates in public storage.
 */
class AndroidPublishedCopy(private val context: Context) : PublishedCopy {

    private val resolver: ContentResolver get() = context.contentResolver

    override fun isOwned(uri: String): Boolean {
        val parsed = runCatching { Uri.parse(uri) }.getOrNull() ?: return false
        return when (parsed.scheme?.lowercase()) {
            "content" -> if (isMediaStoreUri(parsed)) mediaStoreRowIsOurs(parsed) else documentUriIsUnderOurGrant(parsed)
            "file" -> legacyPublicCopyIsOurs(parsed.path)
            else -> false
        }
    }

    override fun delete(uri: String): Boolean {
        if (!isOwned(uri)) return false
        val parsed = Uri.parse(uri)
        return runCatching {
            when (parsed.scheme?.lowercase()) {
                "content" -> if (isMediaStoreUri(parsed)) {
                    resolver.delete(parsed, null, null) >= 0
                } else {
                    DocumentsContract.deleteDocument(resolver, parsed)
                }
                "file" -> parsed.path?.let { File(it).delete() } ?: false
                else -> false
            }
        }.getOrDefault(false)
    }

    /** A MediaStore row is ours when the platform recorded this package as its owner. */
    private fun mediaStoreRowIsOurs(uri: Uri): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        return runCatching {
            resolver.query(uri, arrayOf(MediaStore.MediaColumns.OWNER_PACKAGE_NAME), null, null, null)?.use { cursor ->
                cursor.moveToFirst() && cursor.getString(0) == context.packageName
            } ?: false
        }.getOrDefault(false)
    }

    private fun isMediaStoreUri(uri: Uri): Boolean =
        uri.authority == MediaStore.AUTHORITY || uri.authority?.startsWith("media") == true

    /**
     * A document URI under a tree whose write grant this app persisted. The grant is the app's own
     * record of the folder it may write to, and it is what makes the difference between "our copy in
     * the folder the user chose" and "a file the user put there themselves".
     */
    private fun documentUriIsUnderOurGrant(uri: Uri): Boolean {
        val documentId = runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull() ?: return false
        return resolver.persistedUriPermissions.any { permission ->
            if (!permission.isWritePermission) return@any false
            val tree = permission.uri
            if (tree.authority != uri.authority) return@any false
            val treeId = runCatching { DocumentsContract.getTreeDocumentId(tree) }.getOrNull() ?: return@any false
            documentId == treeId || documentId.startsWith("$treeId/")
        }
    }

    private fun legacyPublicCopyIsOurs(path: String?): Boolean {
        val target = path?.let { runCatching { File(it).canonicalPath }.getOrNull() } ?: return false
        return LEGACY_EXPORT_DIRECTORIES.any { directory ->
            val base = runCatching {
                File(Environment.getExternalStoragePublicDirectory(directory), LEGACY_EXPORT_FOLDER).canonicalPath
            }.getOrNull() ?: return@any false
            target.startsWith(base + File.separator)
        }
    }

    private companion object {
        val LEGACY_EXPORT_DIRECTORIES = listOf(Environment.DIRECTORY_MOVIES, Environment.DIRECTORY_MUSIC)
        const val LEGACY_EXPORT_FOLDER = "SalviaBrowxer"
    }
}
