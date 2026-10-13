package com.salvia.salviabrowxer.media.downloader

import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Writes the stream a page fed to its `MediaSource`, one segment at a time.
 *
 * This exists because of a gap nothing else closes: a page that plays with `SourceBuffer.appendBuffer`
 * hands the element `ArrayBuffer`s that never become a URL anybody can fetch — no request to intercept,
 * no `blob:` file to read, no manifest to follow. Taking the bytes as they are appended is the only way
 * to keep that media, and it is what the browser extensions that advertise "download from any site" do.
 *
 * What it is **not**: a DRM bypass. If the init segment declares encryption (`pssh`, `tenc`) the
 * recording is abandoned before a single byte is kept, because the fragments of an encrypted stream do
 * not decode — the honest outcome is a clear message, not a file that will not open.
 *
 * Kept off the playback path by design: the caller invokes this after the page's own `appendBuffer` has
 * already returned, so writing to disk can never delay a frame. Segments are written straight through
 * rather than accumulated, bounded by [maxBytes] and refused below [minFreeBytes] of free space, and the
 * file lives under the app's cache directory until the user's recording is handed to the download queue.
 */
class MseRecorder(
    private val rootDir: File,
    private val maxBytes: Long = DEFAULT_MAX_BYTES,
    private val minFreeBytes: Long = DEFAULT_MIN_FREE_BYTES
) {

    /** What became of an append. Anything but [CONTINUED] means no more data will be accepted. */
    enum class Append {
        CONTINUED,
        /** Not recording — nothing was armed, or the recording has already ended. */
        IGNORED,
        STOPPED_CAP,
        STOPPED_DISK,
        STOPPED_ENCRYPTED
    }

    /** A finished recording, on disk and ready to be named and moved into the queue. */
    data class Recording(val file: File, val mimeType: String, val bytes: Long, val capped: Boolean)

    private var output: FileOutputStream? = null
    private var current: File? = null
    private var mime: String = ""
    private var written: Long = 0L
    private var capped = false

    val isRecording: Boolean get() = output != null
    val bytesWritten: Long get() = written

    /**
     * Opens a stream. False means nothing was started, which is also what the caller reports to the
     * user — starting to record into a directory that is nearly full would fail on the first fragment
     * instead of here, where the reason is still knowable.
     */
    fun start(streamName: String, mimeType: String): Boolean {
        if (isRecording) return false
        val safe = DownloadManager.sanitizeFilename(streamName).ifBlank { "recording" }
        if (!rootDir.exists() && !rootDir.mkdirs()) return false
        if (rootDir.usableSpace < minFreeBytes) return false
        val target = DownloadManager.nonConflicting(File(rootDir, "$safe-${System.currentTimeMillis()}.bin"))
        return try {
            output = FileOutputStream(target)
            current = target
            mime = mimeType
            written = 0L
            capped = false
            true
        } catch (io: IOException) {
            abort()
            false
        }
    }

    /**
     * Appends one segment exactly as the page handed it over.
     *
     * The encryption check only matters for the stream's first bytes: that is where an init segment
     * declares what follows, and checking every fragment would mean reading every fragment twice.
     */
    fun append(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size): Append {
        val stream = output ?: return Append.IGNORED
        if (length <= 0) return Append.CONTINUED
        if (written == 0L && looksEncrypted(bytes, offset, length)) {
            abort()
            return Append.STOPPED_ENCRYPTED
        }
        if (written + length > maxBytes) {
            capped = true
            close()
            return Append.STOPPED_CAP
        }
        if (rootDir.usableSpace < minFreeBytes) {
            close()
            return Append.STOPPED_DISK
        }
        return try {
            stream.write(bytes, offset, length)
            written += length
            Append.CONTINUED
        } catch (io: IOException) {
            // A full disk mid-write is a stopped recording, not a lost one: finish() still hands back
            // what did make it to disk.
            close()
            Append.STOPPED_DISK
        }
    }

    /** Closes the stream and hands back what was written, or null when there is nothing to keep. */
    fun finish(): Recording? {
        close()
        val file = current ?: return null
        val bytes = written
        val result = Recording(file, mime, bytes, capped)
        current = null
        written = 0L
        mime = ""
        capped = false
        if (bytes <= 0L || !file.exists()) {
            file.delete()
            return null
        }
        return result
    }

    /** Gives up on the recording and removes whatever was written. */
    fun abort() {
        close()
        current?.delete()
        current = null
        written = 0L
        mime = ""
        capped = false
    }

    private fun close() {
        runCatching { output?.flush() }
        runCatching { output?.close() }
        output = null
    }

    companion object {
        /** Half a gigabyte per recording: past this the user is better served by a message than by a file. */
        const val DEFAULT_MAX_BYTES: Long = 512L * 1024 * 1024

        /** Never start (or continue) a recording with less than this much room left on the device. */
        const val DEFAULT_MIN_FREE_BYTES: Long = 256L * 1024 * 1024

        /**
         * Whether the first bytes of a stream declare Common Encryption.
         *
         * A CENC stream carries either a `pssh` box (the key system's own header) or a `tenc` sample
         * entry in its init segment; both are four-character box names, so this is a scan of the head
         * of the stream rather than a container parser. It is deliberately conservative — a false
         * positive costs a refusal with a message, a false negative costs a file the user cannot play.
         */
        fun looksEncrypted(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size): Boolean {
            val end = (offset + length).coerceAtMost(bytes.size)
            // Only the head can carry an init segment; scanning a whole fragment would be wasted work.
            val limit = end.coerceAtMost(offset + HEAD_SCAN_BYTES)
            for (marker in ENCRYPTION_MARKERS) {
                if (indexOf(bytes, offset, limit, marker) >= 0) return true
            }
            return false
        }

        private val ENCRYPTION_MARKERS = listOf(
            byteArrayOf(0x70, 0x73, 0x73, 0x68), // pssh
            byteArrayOf(0x74, 0x65, 0x6E, 0x63), // tenc
            byteArrayOf(0x73, 0x69, 0x6E, 0x66)  // sinf (protection scheme information)
        )

        private const val HEAD_SCAN_BYTES = 64 * 1024

        private fun indexOf(haystack: ByteArray, from: Int, to: Int, needle: ByteArray): Int {
            if (needle.isEmpty() || to - from < needle.size) return -1
            var i = from
            while (i <= to - needle.size) {
                var j = 0
                while (j < needle.size && haystack[i + j] == needle[j]) j++
                if (j == needle.size) return i
                i++
            }
            return -1
        }

        /**
         * The extension a recorded stream can honestly claim.
         *
         * A fragment stream is a real container — `video/mp4` recording is an fMP4 file, which is what
         * the browser-side recorders save too — but a stream whose type we cannot name is saved with a
         * neutral extension and reported as unidentifiable rather than renamed into a lie.
         */
        fun containerExtensionFor(mimeType: String): String = when {
            mimeType.contains("iso.segment", true) -> "m4s"
            mimeType.contains("mp4", true) || mimeType.contains("m4a", true) -> "mp4"
            mimeType.contains("webm", true) -> "webm"
            mimeType.contains("matroska", true) -> "mkv"
            mimeType.contains("aac", true) -> "aac"
            mimeType.contains("audio/mpeg", true) || mimeType.contains("mp3", true) -> "mp3"
            mimeType.contains("mp2t", true) || mimeType.contains("video/mpeg", true) -> "ts"
            else -> "bin"
        }
    }
}
