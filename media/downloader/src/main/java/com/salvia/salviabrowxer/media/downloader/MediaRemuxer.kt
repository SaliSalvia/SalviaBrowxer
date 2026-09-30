package com.salvia.salviabrowxer.media.downloader

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer

/**
 * Rewrites a container without re-encoding.
 *
 * The reason this exists: an HLS or DASH transfer produces one concatenated stream on disk (MPEG-TS
 * for HLS, fragmented MP4 for DASH). That file plays in the app's own player but is a poor citizen
 * everywhere else — a `.ts` is often not visible to galleries and some devices refuse it. Remuxing
 * copies the already-decoded-friendly elementary streams into a plain MP4 container, with no
 * quality loss and no CPU-heavy transcode.
 *
 * Every failure is a `false`, never an exception: the caller keeps the original file, so a remux
 * that the platform cannot do degrades to "you still have the stream" instead of losing it.
 */
class MediaRemuxer {

    fun remux(input: File, output: File): Boolean {
        if (!input.exists() || input.length() == 0L) return false
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        return try {
            extractor.setDataSource(input.absolutePath)
            muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val trackMap = LinkedHashMap<Int, Int>()
            for (index in 0 until extractor.trackCount) {
                val format = runCatching { extractor.getTrackFormat(index) }.getOrNull() ?: continue
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (!mime.startsWith("video/") && !mime.startsWith("audio/")) continue
                val target = runCatching { muxer!!.addTrack(format) }.getOrNull() ?: continue
                trackMap[index] = target
            }
            if (trackMap.isEmpty()) return false

            muxer!!.start()
            val buffer = ByteBuffer.allocate(BUFFER_SIZE)
            val info = MediaCodec.BufferInfo()
            var wroteAnything = false
            for ((sourceTrack, targetTrack) in trackMap) {
                extractor.selectTrack(sourceTrack)
                while (true) {
                    val size = runCatching { extractor.readSampleData(buffer, 0) }.getOrDefault(-1)
                    if (size < 0) break
                    info.offset = 0
                    info.size = size
                    info.presentationTimeUs = extractor.sampleTime
                    info.flags = extractor.sampleFlags
                    val written = runCatching { muxer!!.writeSampleData(targetTrack, buffer, info) }.isSuccess
                    if (!written) break
                    wroteAnything = true
                    if (!extractor.advance()) break
                }
                extractor.unselectTrack(sourceTrack)
            }
            if (!wroteAnything) return false
            muxer!!.stop()
            output.exists() && output.length() > 0L
        } catch (_: Exception) {
            false
        } finally {
            runCatching { muxer?.release() }
            runCatching { extractor.release() }
            if (!output.exists() || output.length() == 0L) runCatching { output.delete() }
        }
    }

    private companion object {
        const val BUFFER_SIZE = 1 shl 20
    }
}
