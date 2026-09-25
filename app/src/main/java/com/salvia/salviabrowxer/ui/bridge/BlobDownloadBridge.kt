package com.salvia.salviabrowxer.ui.bridge

import android.util.Base64
import android.util.Log
import android.webkit.JavascriptInterface
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * InShot-style blob: URL extractor.
 * Chrome's FileReader returns a data: URL that for large videos (100MB+) would blow the
 * 1MB Binder transaction limit if sent in one JavascriptInterface call. We split the base64
 * into 480-KiB chunks and reassemble on the Kotlin side.
 *
 * Protocol:
 *  JS  -> onBlobChunk(pageUrl, blobUrl, index, total, chunkBase64, mime)  (called N times)
 *  JS  -> onBlobComplete(pageUrl, blobUrl) is not needed — total tells us when to flush.
 *  Kotlin reassembles all chunks, decodes, writes to a temp file and calls onBlobCaptured.
 *
 * Large blobs are also split on the JS side: FileReader.slice() is used when blob.size > 8 MiB.
 * For backwards compatibility the one-shot onBlobData path is kept.
 */
class BlobDownloadBridge(
    private val onBlobCaptured: (pageUrl: String, blobUrl: String, file: File, mimeType: String) -> Unit,
    private val cacheDirProvider: () -> File
) {
    private data class PendingBlob(val pageUrl: String, val mimeType: String, val chunks: MutableList<String?>, var received: Int)

    private val pending = ConcurrentHashMap<String, PendingBlob>()
    private val MAX_SINGLE_DECODE_BYTES = 90 * 1024 * 1024 // ~120M chars of base64

    @JavascriptInterface
    fun onBlobData(pageUrl: String, blobUrl: String, base64Data: String, mimeType: String) {
        // One-shot path for small blobs (kept for compatibility)
        try {
            if (base64Data.isBlank() || blobUrl.isBlank()) return
            val raw = base64Data.substringAfter(",", base64Data)
            finishBlob(pageUrl, blobUrl, raw, mimeType)
        } catch (e: Exception) {
            Log.w(TAG, "onBlobData failed for $blobUrl", e)
        }
    }

    @JavascriptInterface
    fun onBlobChunk(pageUrl: String, blobUrl: String, index: Int, total: Int, chunkBase64: String, mimeType: String) {
        try {
            if (chunkBase64.isBlank() || blobUrl.isBlank() || total <= 0 || total > 512) return
            val key = blobUrl
            val entry = pending.getOrPut(key) {
                PendingBlob(pageUrl, mimeType, MutableList(total) { null }, 0)
            }
            // Guard against mismatched totals across calls
            if (entry.chunks.size != total) {
                Log.w(TAG, "Blob chunk total mismatch for $blobUrl: ${entry.chunks.size} vs $total — resetting")
                pending[key] = PendingBlob(pageUrl, mimeType, MutableList(total) { null }, 0)
                pending[key]!!.chunks[index.coerceIn(0, total - 1)] = chunkBase64
                pending[key]!!.received = 1
                return
            }
            if (index !in 0 until total) return
            if (entry.chunks[index] != null) return // duplicate
            // chunkBase64 is already pure base64 (no data: prefix) — store as-is
            val pure = chunkBase64.substringAfter(",", chunkBase64)
            entry.chunks[index] = pure
            entry.received++
            if (entry.received == total) {
                val assembled = entry.chunks.joinToString("") { it ?: "" }
                pending.remove(key)
                finishBlob(pageUrl, blobUrl, assembled, mimeType.ifBlank { entry.mimeType })
            }
        } catch (e: Exception) {
            Log.w(TAG, "onBlobChunk failed for $blobUrl", e)
        }
    }

    @JavascriptInterface
    fun onBlobUrlFound(pageUrl: String, blobUrl: String) {
        Log.d(TAG, "Blob URL found on $pageUrl -> $blobUrl")
    }

    private fun finishBlob(pageUrl: String, blobUrl: String, pureBase64: String, mimeType: String) {
        if (pureBase64.length > 160 * 1024 * 1024) {
            Log.w(TAG, "Blob too large, refusing: $blobUrl (${pureBase64.length} chars)")
            return
        }
        val bytes = try { Base64.decode(pureBase64, Base64.DEFAULT) } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Base64 decode failed for $blobUrl", e); return
        }
        if (bytes.isEmpty()) return
        if (bytes.size > MAX_SINGLE_DECODE_BYTES) {
            Log.w(TAG, "Decoded blob too large: ${bytes.size} bytes for $blobUrl")
            return
        }
        val ext = mimeToExt(mimeType)
        val dir = cacheDirProvider().also { if (!it.exists()) it.mkdirs() }
        val tmp = File(dir, "blob_${System.currentTimeMillis()}.$ext")
        tmp.writeBytes(bytes)
        Log.d(TAG, "Blob captured: $blobUrl -> ${tmp.absolutePath} (${bytes.size} bytes, $mimeType)")
        onBlobCaptured(pageUrl, blobUrl, tmp, mimeType.ifBlank { "video/mp4" })
    }

    private fun mimeToExt(mime: String): String = when {
        mime.contains("mp4", true) -> "mp4"
        mime.contains("webm", true) -> "webm"
        mime.contains("quicktime", true) -> "mov"
        mime.contains("mpeg", true) && mime.contains("audio", true) -> "mp3"
        mime.contains("mp3", true) -> "mp3"
        mime.contains("aac", true) -> "aac"
        mime.contains("wav", true) -> "wav"
        mime.contains("ogg", true) -> "ogg"
        mime.contains("m3u8", true) || mime.contains("mpegurl", true) -> "m3u8"
        else -> "mp4"
    }

    companion object {
        private const val TAG = "BlobDownloadBridge"
    }
}
