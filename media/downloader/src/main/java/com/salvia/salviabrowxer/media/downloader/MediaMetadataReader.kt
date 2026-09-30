package com.salvia.salviabrowxer.media.downloader

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import java.io.File
import java.io.FileOutputStream

/**
 * Reads what a finished file is, locally: its duration and a frame to show as a thumbnail.
 *
 * Both are best-effort. A file the platform cannot open returns `null`/`false` and the row simply
 * shows no poster, which is the honest outcome — the app never fabricates metadata.
 */
class MediaMetadataReader {

    fun durationMs(file: File): Long? {
        if (!file.exists() || file.length() == 0L) return null
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.takeIf { it > 0L }
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    /** Writes a JPEG frame to [outFile]. Returns true only when a non-empty image was written. */
    fun saveThumbnail(file: File, outFile: File, maxWidth: Int = 512): Boolean {
        if (!file.exists() || file.length() == 0L) return false
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val frame = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) ?: return false
            val scaled = scaleDown(frame, maxWidth)
            outFile.parentFile?.mkdirs()
            FileOutputStream(outFile).use { out -> scaled.compress(Bitmap.CompressFormat.JPEG, 82, out) }
            scaled.recycle()
            if (frame !== scaled) frame.recycle()
            outFile.exists() && outFile.length() > 0L
        } catch (_: Exception) {
            false
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun scaleDown(bitmap: Bitmap, maxWidth: Int): Bitmap {
        if (bitmap.width <= maxWidth) return bitmap
        val ratio = maxWidth.toFloat() / bitmap.width
        val height = (bitmap.height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, maxWidth, height, true)
    }
}
