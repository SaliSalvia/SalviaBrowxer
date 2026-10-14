package com.salvia.salviabrowxer.data.download

import com.salvia.salviabrowxer.core.database.entities.DownloadEntity
import com.salvia.salviabrowxer.media.downloader.DownloadManager
import java.io.File

/**
 * Deletes a download's own files — and only those.
 *
 * Every screen in this app that removed a row had the same two lines in it: delete `finalPath`,
 * delete `"<name>.part"`, delete the row. That leaves behind everything else the transfer wrote —
 * the staged segments of an HLS or DASH transfer (`<name>.parts/`), the `.hls.ts` assembly file,
 * the `.dash.tmp`/`video.tmp`/`audio.tmp` intermediates, and the copy that was published to the
 * gallery — and the "clear the list" actions deleted no files at all, only rows.
 *
 * The rule this class enforces instead: **delete what the app created, and nothing else.**
 *
 *  - Files are only removed when they are inside a root the app owns (its own external-files
 *    directory, its internal directory, its cache). A row whose `destination` points somewhere else
 *    — an edited database, a folder the user typed into another build — is reported and skipped
 *    rather than recursively emptied.
 *  - A published copy is only removed when the app owns it: a MediaStore entry inserted by this
 *    package, a document inside a tree this app holds a persisted grant for, or the public
 *    `Movies|Music/SalviaBrowxer` file the legacy export path created. Another app's media, or the
 *    user's own file that merely shares a name, is never touched.
 *
 * The two collaborating pieces are [PublishedCopy] (framework side: MediaStore, SAF, legacy file)
 * and this pure class, so the ownership decisions are unit-tested on the JVM.
 */
class DownloadArtifactCleaner(
    private val ownedRoots: () -> List<File>,
    private val publishedCopy: PublishedCopy
) {

    /** What a row's transfer may have written, in a fixed order: intermediates first, result last. */
    object Paths {

        fun forRow(row: DownloadEntity): List<File> {
            val destination = File(row.destination)
            val name = DownloadManager.sanitizeFilename(row.filename)
            val base = name.substringBeforeLast('.', name).ifBlank { "download" }
            val candidates = linkedSetOf<File>()
            // Everything a segmented or remuxed transfer stages beside its output. Deleting the
            // final file of a *cancelled* row is correct: nothing else points at it.
            listOf(
                "$base.${DownloadManager.PART_SUFFIX}",
                "$base.hls.ts",
                "$base.mp4",
                "$base.dash.tmp",
                "$base.video.tmp",
                "$base.audio.tmp"
            ).forEach { candidates += File(destination, it) }
            parallelPartFiles(destination, base).forEach { candidates += it }
            candidates += File(destination, name)
            row.finalPath?.takeIf { it.isNotBlank() }?.let { candidates += File(it) }
            return candidates.toList()
        }

        /** Directories holding staged parts, which are removed as a whole when they exist. */
        fun stagingDirectoriesForRow(row: DownloadEntity): List<File> {
            val destination = File(row.destination)
            val name = DownloadManager.sanitizeFilename(row.filename)
            val base = name.substringBeforeLast('.', name).ifBlank { "download" }
            return listOf(
                "$base.hls.ts.parts",
                "$base.mp4.parts",
                "$base.dash.tmp.parts",
                "$base.video.tmp.parts",
                "$base.audio.tmp.parts"
            ).map { File(destination, it) }
        }

        /**
         * The `.part<N>` files a multi-connection transfer writes. The count is bounded by
         * [MAX_PARALLEL_PARTS], the highest connection count the settings allow, so a cancelled
         * parallel download leaves nothing behind.
         */
        fun parallelPartFiles(destination: File, base: String): List<File> =
            (0 until MAX_PARALLEL_PARTS).map { index -> File(destination, "$base.${DownloadManager.PART_SUFFIX}$index") }

        const val MAX_PARALLEL_PARTS = 8

        /** A thumbnail the app rendered into its cache, deleteable with no ownership question. */
        fun thumbnailForRow(row: DownloadEntity): File? = row.thumbnail
            ?.removePrefix("file://")
            ?.takeIf { it.isNotBlank() }
            ?.let { File(it) }
    }

    data class Result(
        val deletedFiles: Int,
        val deletedPublishedCopies: Int,
        /** Paths the app does not own; kept on disk and named so the caller can log or show them. */
        val skipped: List<String>
    ) {
        val deletedAnything: Boolean get() = deletedFiles > 0 || deletedPublishedCopies > 0
    }

    /** True when [file] is inside a directory this app created and may therefore remove. */
    fun isInsideOwnedRoots(file: File): Boolean {
        if (!file.isAbsolute) return false
        val target = runCatching { file.canonicalFile }.getOrDefault(file.absoluteFile)
        return ownedRoots().any { root ->
            val base = runCatching { root.canonicalFile }.getOrDefault(root.absoluteFile)
            target == base || target.path.startsWith(base.path + File.separator)
        }
    }

    /**
     * Removes only the intermediates a transfer staged: `.part`, `.hls.ts`, `.dash.tmp`, the
     * `video.tmp`/`audio.tmp` pair and the `*.parts/` staging directories.
     *
     * Used when a transfer is cancelled (there is no result to keep) and when one finishes (the
     * staged bytes are no longer needed). The final file and the published copy are untouched on
     * purpose: both paths are the *result* of a successful transfer, and a cancel must never be able
     * to delete a file the user already has.
     */
    fun cleanTransferStaging(row: DownloadEntity): Int {
        var deleted = 0
        // A staged name can *be* the result: a DASH rendition whose remux failed keeps its
        // `.dash.tmp` file as the row's `finalPath`, and deleting it here would throw away the only
        // copy of a finished download. The result is protected by name, not by convention.
        val result = row.finalPath?.takeIf { it.isNotBlank() }?.let { path ->
            runCatching { File(path).canonicalPath }.getOrNull()
        }
        Paths.stagingDirectoriesForRow(row).forEach { directory ->
            if (directory.exists() && isInsideOwnedRoots(directory) && directory.deleteRecursively()) deleted++
        }
        val name = DownloadManager.sanitizeFilename(row.filename)
        val base = name.substringBeforeLast('.', name).ifBlank { "download" }
        listOf(
            "$base.${DownloadManager.PART_SUFFIX}",
            "$base.hls.ts",
            "$base.dash.tmp",
            "$base.video.tmp",
            "$base.audio.tmp"
        ).forEach { intermediate ->
            val file = File(row.destination, intermediate)
            if (result != null && runCatching { file.canonicalPath }.getOrNull() == result) return@forEach
            if (file.exists() && isInsideOwnedRoots(file) && file.delete()) deleted++
        }
        Paths.parallelPartFiles(File(row.destination), base).forEach { part ->
            if (part.exists() && isInsideOwnedRoots(part) && part.delete()) deleted++
        }
        return deleted
    }

    /**
     * Removes the row's artefacts. The caller deletes the row itself afterwards: this class never
     * touches the database, so a failed delete can be retried from the same row.
     */
    fun clean(row: DownloadEntity): Result {
        var deleted = 0
        val skipped = mutableListOf<String>()

        // Staging directories first: they are the largest and they contain no user-visible file, so
        // a partially deleted download loses the most bytes with the least risk.
        Paths.stagingDirectoriesForRow(row).forEach { directory ->
            if (!directory.exists()) return@forEach
            if (!isInsideOwnedRoots(directory)) {
                skipped += directory.absolutePath
                return@forEach
            }
            if (directory.deleteRecursively()) deleted++
        }

        Paths.forRow(row).forEach { file ->
            if (!file.exists()) return@forEach
            if (!isInsideOwnedRoots(file)) {
                skipped += file.absolutePath
                return@forEach
            }
            if (file.delete()) deleted++
        }

        Paths.thumbnailForRow(row)?.takeIf { it.exists() && isInsideOwnedRoots(it) }?.let { thumbnail ->
            if (thumbnail.delete()) deleted++
        }

        var copies = 0
        val published = row.exportedUri?.takeIf { it.isNotBlank() }
        if (published != null) {
            if (publishedCopy.isOwned(published)) {
                if (publishedCopy.delete(published)) copies++
            } else {
                skipped += published
            }
        }

        return Result(deletedFiles = deleted, deletedPublishedCopies = copies, skipped = skipped)
    }
}

/**
 * The framework half of ownership: `MediaStore` rows this package inserted, documents inside a tree
 * this app holds a persisted grant for, and the legacy public-folder copy.
 *
 * Everything else — another app's media, a file the user put next to ours with the same name — is
 * reported as not owned and left alone.
 */
interface PublishedCopy {

    fun isOwned(uri: String): Boolean

    /** Deletes the app's copy; true when nothing of ours is left at [uri]. */
    fun delete(uri: String): Boolean
}

/** Used where only files can exist (a cancelled transfer has published nothing yet). */
object NoPublishedCopy : PublishedCopy {
    override fun isOwned(uri: String): Boolean = false
    override fun delete(uri: String): Boolean = false
}
