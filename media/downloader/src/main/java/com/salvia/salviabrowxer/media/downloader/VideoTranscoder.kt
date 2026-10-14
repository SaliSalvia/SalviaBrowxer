package com.salvia.salviabrowxer.media.downloader

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer

/**
 * Re-encodes a file into H.264/AAC MP4 — the conversion a remux cannot do.
 *
 * [MediaRemuxer] copies packets between containers, which is lossless and cheap but only helps when
 * the codecs inside are already acceptable. The gap is a file whose codecs are right for nobody:
 * HEVC in an MP4 that a TV or an older phone will not open, VP9 in a WebM, or an audio track a
 * player needs as AAC. This class decodes and re-encodes — the real thing, with the quality and time
 * cost that implies — and it is a deliberate user action for exactly that reason.
 *
 * How it works, and why this shape:
 *
 *  - a `MediaCodec` decoder is wired **straight into the encoder's input surface**, so no frame is
 *    touched by app code and no OpenGL setup is needed: the decoder renders each frame onto the
 *    encoder's surface with that frame's own timestamp, which is what keeps audio and video in sync;
 *  - the video is always encoded as H.264 at the source resolution and frame rate, with a bitrate
 *    taken from the source when it states one;
 *  - the audio is **copied untouched when it is already AAC** and re-encoded to AAC otherwise, so a
 *    1080p file does not lose its soundtrack for nothing;
 *  - rotation from the source is carried over with `setOrientationHint`, so a phone video does not
 *    come out sideways;
 *  - every failure returns `false` and deletes the partial output, and every loop is bounded by a
 *    wall-clock deadline. The caller keeps the original file, always.
 *
 * This code cannot be unit-tested without a device (MediaCodec needs real codecs), so it is written
 * defensively: each codec call that can throw is guarded, and a file the platform cannot read fails
 * in the first second rather than after a pointless encode.
 */
class VideoTranscoder {

    /**
     * Converts [input] into [output].
     *
     * @param onProgress called with 0..100 while the video is processed
     * @param shouldStop polled between buffers: a cancelled conversion deletes its output and returns
     *   false, so a cancelled and a failed conversion are deliberately indistinguishable — nothing
     *   half-encoded is left behind either way.
     */
    fun transcode(
        input: File,
        output: File,
        onProgress: (Int) -> Unit = {},
        shouldStop: () -> Boolean = { false }
    ): Boolean {
        if (!input.exists() || input.length() == 0L) return false
        if (output.exists()) output.delete()

        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        var videoDecoder: MediaCodec? = null
        var videoEncoder: MediaCodec? = null
        var audioDecoder: MediaCodec? = null
        var audioEncoder: MediaCodec? = null
        var muxerStopped = false
        val tracks = MediaTracks()
        val deadline = System.currentTimeMillis() + MAX_TRANSCODE_MILLIS
        // What the finally block deletes on: a run that did not finish has no business leaving half
        // a file where the caller asked for a converted one.
        var succeeded = false

        try {
            extractor.setDataSource(input.absolutePath)

            val videoIndex = selectTrack(extractor) { mime -> mime.startsWith("video/") }
            val audioIndex = selectTrack(extractor) { mime -> mime.startsWith("audio/") }
            if (videoIndex == null && audioIndex == null) return false

            val videoFormat = videoIndex?.let { extractor.getTrackFormat(it) }
            val audioFormat = audioIndex?.let { extractor.getTrackFormat(it) }
            // A container whose streams are all of a kind this device cannot decode is a refusal here,
            // at the first codec lookup, rather than a hang in the middle of the encode.
            val videoMime = videoFormat?.getString(MediaFormat.KEY_MIME)
            val audioMime = audioFormat?.getString(MediaFormat.KEY_MIME)
            if (videoMime != null && runCatching { MediaCodec.createDecoderByType(videoMime) }.getOrNull() == null) return false
            if (videoMime == null && audioMime != null && runCatching { MediaCodec.createDecoderByType(audioMime) }.getOrNull() == null) return false

            muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            // ---- audio track first: an AAC track needs no codec and can be declared up front ------
            val audioPassthrough = audioFormat != null && audioMime == MIME_AAC
            if (audioPassthrough) {
                tracks.audio = runCatching { muxer!!.addTrack(audioFormat!!) }.getOrDefault(-1)
                if (tracks.audio < 0) return false
            }

            // ---- video ---------------------------------------------------------------------------
            if (videoIndex != null && videoFormat != null && videoMime != null) {
                val width = videoFormat.getIntegerOrNull(MediaFormat.KEY_WIDTH) ?: return false
                val height = videoFormat.getIntegerOrNull(MediaFormat.KEY_HEIGHT) ?: return false
                videoFormat.getIntegerOrNull(MediaFormat.KEY_ROTATION)?.let { rotation -> muxer!!.setOrientationHint(rotation) }
                val frameRate = videoFormat.getIntegerOrNull(MediaFormat.KEY_FRAME_RATE)?.takeIf { it in 1..120 } ?: 30
                val bitrate = videoFormat.getIntegerOrNull(MediaFormat.KEY_BIT_RATE)?.takeIf { it > 0 }
                    ?: (width.toLong() * height * frameRate * 0.12).toInt().coerceIn(MIN_BITRATE, MAX_BITRATE)

                val decoder = MediaCodec.createDecoderByType(videoMime)
                videoDecoder = decoder
                val encoder = MediaCodec.createEncoderByType(MIME_AVC)
                videoEncoder = encoder
                val encoderFormat = MediaFormat.createVideoFormat(MIME_AVC, width, height).apply {
                    setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                    setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
                    setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
                    setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL_SECONDS)
                }
                encoder.configure(encoderFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                val surface = encoder.createInputSurface()
                encoder.start()
                decoder.configure(videoFormat, surface, null, 0)
                decoder.start()

                val result = pumpVideo(
                    extractor = extractor,
                    sourceIndex = videoIndex,
                    decoder = decoder,
                    encoder = encoder,
                    muxer = muxer!!,
                    tracks = tracks,
                    durationUs = videoFormat.getLongOrNull(MediaFormat.KEY_DURATION),
                    deadline = deadline,
                    onProgress = onProgress,
                    shouldStop = shouldStop
                )
                if (result != PumpResult.DONE) return false
            }

            // ---- audio ---------------------------------------------------------------------------
            if (audioIndex != null && audioFormat != null) {
                extractor.selectTrack(audioIndex)
                if (audioPassthrough) {
                    if (!copyAudioSamples(extractor, muxer!!, tracks, deadline, shouldStop)) return false
                } else {
                    val decoder = MediaCodec.createDecoderByType(audioMime ?: return false)
                    audioDecoder = decoder
                    decoder.configure(audioFormat, null, null, 0)
                    decoder.start()
                    val encoder = MediaCodec.createEncoderByType(MIME_AAC)
                    audioEncoder = encoder
                    val result = pumpAudio(extractor, decoder, encoder, muxer!!, tracks, deadline, shouldStop)
                    if (result != PumpResult.DONE) return false
                }
            }

            if (!tracks.started) return false
            muxer.stop()
            muxerStopped = true
            succeeded = output.exists() && output.length() > 0L
            return succeeded
        } catch (_: Exception) {
            return false
        } finally {
            runCatching { videoDecoder?.stop() }
            runCatching { videoDecoder?.release() }
            runCatching { videoEncoder?.stop() }
            runCatching { videoEncoder?.release() }
            runCatching { audioDecoder?.stop() }
            runCatching { audioDecoder?.release() }
            runCatching { audioEncoder?.stop() }
            runCatching { audioEncoder?.release() }
            // A muxer that was started but never stopped leaves an unplayable file; stopping it here
            // is what makes the failed output deletable in one piece.
            if (!muxerStopped) runCatching { muxer?.stop() }
            runCatching { muxer?.release() }
            runCatching { extractor.release() }
            // A failure and a cancellation both land here, and both must leave nothing behind: the
            // muxer above finalises whatever it was given, which is a playable-looking fragment.
            if (!succeeded) runCatching { output.delete() }
        }
    }

    private enum class PumpResult { DONE, ENDED, ABORTED }

    /** Decode → encode → mux, all in lockstep, with the encoder's surface doing the frame handoff. */
    private fun pumpVideo(
        extractor: MediaExtractor,
        sourceIndex: Int,
        decoder: MediaCodec,
        encoder: MediaCodec,
        muxer: MediaMuxer,
        tracks: MediaTracks,
        durationUs: Long?,
        deadline: Long,
        onProgress: (Int) -> Unit,
        shouldStop: () -> Boolean
    ): PumpResult {
        extractor.selectTrack(sourceIndex)
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var encoderDone = false
        var lastPercent = -1

        while (!encoderDone) {
            if (shouldStop()) return PumpResult.ABORTED
            if (System.currentTimeMillis() > deadline) return PumpResult.ABORTED

            if (!inputDone) {
                val inIndex = decoder.dequeueInputBuffer(TIMEOUT_US)
                if (inIndex >= 0) {
                    val buffer = decoder.getInputBuffer(inIndex)
                    val size = if (buffer == null) -1 else extractor.readSampleData(buffer, 0)
                    if (size < 0) {
                        decoder.queueInputBuffer(inIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        decoder.queueInputBuffer(inIndex, 0, size, extractor.sampleTime.coerceAtLeast(0L), 0)
                        extractor.advance()
                        durationUs?.takeIf { it > 0 }?.let { duration ->
                            val percent = ((extractor.sampleTime.coerceAtLeast(0L) * 100) / duration).toInt().coerceIn(0, 100)
                            if (percent != lastPercent) {
                                lastPercent = percent
                                onProgress(percent)
                            }
                        }
                    }
                }
            }

            val outIndex = decoder.dequeueOutputBuffer(info, TIMEOUT_US)
            if (outIndex >= 0) {
                // Rendering onto the encoder's surface is what carries the presentation timestamp
                // across; a size-zero buffer is a codec-config or a gap and must not be rendered.
                decoder.releaseOutputBuffer(outIndex, info.size > 0)
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    runCatching { encoder.signalEndOfInputStream() }
                }
            }

            while (true) {
                val encIndex = encoder.dequeueOutputBuffer(info, 0L)
                if (encIndex == MediaCodec.INFO_TRY_AGAIN_LATER) break
                if (encIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    tracks.video = runCatching { muxer.addTrack(encoder.outputFormat) }.getOrDefault(-1)
                    if (tracks.video < 0) return PumpResult.ENDED
                    if (!tracks.started) {
                        runCatching { muxer.start() }.getOrElse { return PumpResult.ENDED }
                        tracks.started = true
                    }
                    continue
                }
                if (encIndex < 0) break
                val buffer = encoder.getOutputBuffer(encIndex)
                // The codec-config buffer is metadata for the muxer's track format, not a sample.
                if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                if (info.size > 0 && buffer != null) {
                    buffer.position(info.offset)
                    buffer.limit(info.offset + info.size)
                    if (!runCatching { muxer.writeSampleData(tracks.video, buffer, info) }.isSuccess) return PumpResult.ENDED
                }
                val eos = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                encoder.releaseOutputBuffer(encIndex, false)
                if (eos) {
                    encoderDone = true
                    break
                }
            }
        }
        onProgress(100)
        return PumpResult.DONE
    }

    /** Copies an AAC track the muxer can take as-is: no decode, no loss, no CPU. */
    private fun copyAudioSamples(
        extractor: MediaExtractor,
        muxer: MediaMuxer,
        tracks: MediaTracks,
        deadline: Long,
        shouldStop: () -> Boolean
    ): Boolean {
        if (tracks.audio < 0) return false
        if (!tracks.started) {
            runCatching { muxer.start() }.getOrElse { return false }
            tracks.started = true
        }
        val buffer = ByteBuffer.allocate(BUFFER_SIZE)
        val info = MediaCodec.BufferInfo()
        while (true) {
            if (shouldStop()) return false
            if (System.currentTimeMillis() > deadline) return false
            val size = runCatching { extractor.readSampleData(buffer, 0) }.getOrDefault(-1)
            if (size < 0) break
            info.offset = 0
            info.size = size
            info.presentationTimeUs = extractor.sampleTime.coerceAtLeast(0L)
            info.flags = 0
            if (!runCatching { muxer.writeSampleData(tracks.audio, buffer, info) }.isSuccess) return false
            if (!extractor.advance()) break
        }
        return true
    }

    /** Decodes a non-AAC audio track and encodes it as AAC. */
    private fun pumpAudio(
        extractor: MediaExtractor,
        decoder: MediaCodec,
        encoder: MediaCodec,
        muxer: MediaMuxer,
        tracks: MediaTracks,
        deadline: Long,
        shouldStop: () -> Boolean
    ): PumpResult {
        // Filled in when the decoder reports the format it actually produces; until then the
        // defaults below are used, which is what a decoder that omits the change expects.
        var decoderOutputFormat: MediaFormat? = null
        var encoderStarted = false
        val info = MediaCodec.BufferInfo()
        val pcm = ByteArray(BUFFER_SIZE)
        var inputDone = false
        var outputDone = false

        while (!outputDone) {
            if (shouldStop()) return PumpResult.ABORTED
            if (System.currentTimeMillis() > deadline) return PumpResult.ABORTED

            if (!inputDone) {
                val inIndex = decoder.dequeueInputBuffer(TIMEOUT_US)
                if (inIndex >= 0) {
                    val buffer = decoder.getInputBuffer(inIndex)
                    val size = if (buffer == null) -1 else extractor.readSampleData(buffer, 0)
                    if (size < 0) {
                        decoder.queueInputBuffer(inIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        decoder.queueInputBuffer(inIndex, 0, size, extractor.sampleTime.coerceAtLeast(0L), 0)
                        extractor.advance()
                    }
                }
            }

            val outIndex = decoder.dequeueOutputBuffer(info, TIMEOUT_US)
            if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                decoderOutputFormat = decoder.outputFormat
            } else if (outIndex >= 0) {
                val buffer = decoder.getOutputBuffer(outIndex)
                if (buffer != null && info.size > 0) {
                    if (!encoderStarted) {
                        val channels = decoderOutputFormat?.getIntegerOrNull(MediaFormat.KEY_CHANNEL_COUNT) ?: 2
                        val sampleRate = decoderOutputFormat?.getIntegerOrNull(MediaFormat.KEY_SAMPLE_RATE) ?: 44_100
                        val format = MediaFormat.createAudioFormat(MIME_AAC, sampleRate, channels).apply {
                            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                            setInteger(MediaFormat.KEY_BIT_RATE, AUDIO_BITRATE)
                            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, pcm.size)
                        }
                        runCatching { encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE) }
                            .getOrElse { return PumpResult.ENDED }
                        runCatching { encoder.start() }.getOrElse { return PumpResult.ENDED }
                        encoderStarted = true
                    }
                    buffer.position(info.offset)
                    buffer.limit(info.offset + info.size)
                    val available = minOf(info.size, pcm.size)
                    buffer.get(pcm, 0, available)
                    var offset = 0
                    while (offset < available) {
                        val encIn = encoder.dequeueInputBuffer(TIMEOUT_US)
                        if (encIn < 0) return PumpResult.ENDED
                        val encBuffer = encoder.getInputBuffer(encIn) ?: return PumpResult.ENDED
                        encBuffer.clear()
                        val chunk = minOf(available - offset, encBuffer.remaining())
                        encBuffer.put(pcm, offset, chunk)
                        encoder.queueInputBuffer(encIn, 0, chunk, info.presentationTimeUs, 0)
                        offset += chunk
                    }
                }
                val eos = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                decoder.releaseOutputBuffer(outIndex, false)
                if (eos && encoderStarted) runCatching { encoder.signalEndOfInputStream() }
                if (eos && !encoderStarted) break
            }

            if (!encoderStarted) continue
            while (true) {
                val encIndex = encoder.dequeueOutputBuffer(info, 0L)
                if (encIndex == MediaCodec.INFO_TRY_AGAIN_LATER) break
                if (encIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    tracks.audio = runCatching { muxer.addTrack(encoder.outputFormat) }.getOrDefault(-1)
                    if (tracks.audio < 0) return PumpResult.ENDED
                    if (!tracks.started) {
                        runCatching { muxer.start() }.getOrElse { return PumpResult.ENDED }
                        tracks.started = true
                    }
                    continue
                }
                if (encIndex < 0) break
                val buffer = encoder.getOutputBuffer(encIndex)
                if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                if (info.size > 0 && buffer != null) {
                    buffer.position(info.offset)
                    buffer.limit(info.offset + info.size)
                    if (!runCatching { muxer.writeSampleData(tracks.audio, buffer, info) }.isSuccess) return PumpResult.ENDED
                }
                val eos = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                encoder.releaseOutputBuffer(encIndex, false)
                if (eos) {
                    outputDone = true
                    break
                }
            }
        }
        return PumpResult.DONE
    }

    /** Track indices discovered while the muxer is being set up. */
    private class MediaTracks {
        var video: Int = -1
        var audio: Int = -1
        var started: Boolean = false
    }

    private fun selectTrack(extractor: MediaExtractor, predicate: (String) -> Boolean): Int? {
        for (index in 0 until extractor.trackCount) {
            val mime = runCatching { extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME) }.getOrNull() ?: continue
            if (predicate(mime)) return index
        }
        return null
    }

    private fun MediaFormat.getIntegerOrNull(key: String): Int? =
        runCatching { if (containsKey(key)) getInteger(key) else null }.getOrNull()

    private fun MediaFormat.getLongOrNull(key: String): Long? =
        runCatching { if (containsKey(key)) getLong(key) else null }.getOrNull()

    companion object {
        /** What the output always is: H.264 for video, AAC for audio. */
        const val MIME_AVC = "video/avc"
        const val MIME_AAC = "audio/mp4a-latm"

        private const val TIMEOUT_US = 10_000L
        private const val BUFFER_SIZE = 256 * 1024
        private const val AUDIO_BITRATE = 128_000
        private const val I_FRAME_INTERVAL_SECONDS = 1
        private const val MIN_BITRATE = 400_000
        private const val MAX_BITRATE = 12_000_000

        /** A conversion that has not finished in this long is not going to; the file is left alone. */
        private const val MAX_TRANSCODE_MILLIS = 20 * 60 * 1000L
    }
}
