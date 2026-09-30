package com.salvia.salviabrowxer.media.downloader

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer

/**
 * Joins a video-only file and an audio-only file into one MP4, without re-encoding.
 *
 * Adaptive streams (DASH, and HLS with separate `EXT-X-MEDIA` audio) deliver the picture and the
 * sound as two files. InShot-class downloaders save a single playable video; this is the step that
 * makes that possible here. Samples are copied verbatim through `MediaMuxer`, so the result is the
 * original quality.
 *
 * Returns `false` on any failure so the caller can fall back to keeping the video track alone
 * rather than losing the whole download.
 */
class TrackMerger {

    fun merge(videoFile: File, audioFile: File, output: File): Boolean {
        if (!videoFile.exists() || videoFile.length() == 0L) return false
        if (!audioFile.exists() || audioFile.length() == 0L) return false

        val videoExtractor = MediaExtractor()
        val audioExtractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        return try {
            videoExtractor.setDataSource(videoFile.absolutePath)
            audioExtractor.setDataSource(audioFile.absolutePath)
            muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            val videoTracks = selectTracks(videoExtractor) { it.startsWith("video/") }
            val audioTracks = selectTracks(audioExtractor) { it.startsWith("audio/") }
            if (videoTracks.isEmpty() || audioTracks.isEmpty()) return false

            val mappedVideo = videoTracks.map { (source, format) -> source to muxer!!.addTrack(format) }
            val mappedAudio = audioTracks.map { (source, format) -> source to muxer!!.addTrack(format) }

            muxer!!.start()
            val buffer = ByteBuffer.allocate(BUFFER_SIZE)
            val info = MediaCodec.BufferInfo()
            val wrote = copyTracks(videoExtractor, mappedVideo, muxer!!, buffer, info) or
                copyTracks(audioExtractor, mappedAudio, muxer!!, buffer, info)
            if (!wrote) return false
            muxer!!.stop()
            output.exists() && output.length() > 0L
        } catch (_: Exception) {
            false
        } finally {
            runCatching { muxer?.release() }
            runCatching { videoExtractor.release() }
            runCatching { audioExtractor.release() }
            if (!output.exists() || output.length() == 0L) runCatching { output.delete() }
        }
    }

    private fun selectTracks(extractor: MediaExtractor, accepts: (String) -> Boolean): List<Pair<Int, MediaFormat>> {
        val out = mutableListOf<Pair<Int, MediaFormat>>()
        for (index in 0 until extractor.trackCount) {
            val format = runCatching { extractor.getTrackFormat(index) }.getOrNull() ?: continue
            val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
            if (accepts(mime)) out += index to format
        }
        return out
    }

    private fun copyTracks(
        extractor: MediaExtractor,
        mapped: List<Pair<Int, Int>>,
        muxer: MediaMuxer,
        buffer: ByteBuffer,
        info: MediaCodec.BufferInfo
    ): Boolean {
        var wrote = false
        for ((sourceTrack, targetTrack) in mapped) {
            extractor.selectTrack(sourceTrack)
            while (true) {
                val size = runCatching { extractor.readSampleData(buffer, 0) }.getOrDefault(-1)
                if (size < 0) break
                info.offset = 0
                info.size = size
                info.presentationTimeUs = extractor.sampleTime
                info.flags = extractor.sampleFlags
                val ok = runCatching { muxer.writeSampleData(targetTrack, buffer, info) }.isSuccess
                if (!ok) break
                wrote = true
                if (!extractor.advance()) break
            }
            extractor.unselectTrack(sourceTrack)
        }
        return wrote
    }

    private companion object {
        const val BUFFER_SIZE = 1 shl 20
    }
}
