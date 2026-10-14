package com.salvia.salviabrowxer.media

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.salvia.salviabrowxer.media.downloader.TranscodePlan
import com.salvia.salviabrowxer.media.downloader.VideoTranscoder
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The conversion path, run on a device against the device's own codecs.
 *
 * `TranscodePlanTest` proves the decision table on the JVM and cannot touch a codec at all; this is
 * the other half — a real H.264/AAC pipeline opening a real file and writing a real MP4. Two source
 * files are built here on purpose, because the two branches of the transcoder need two shapes:
 *
 *  - a **video** file encoded by this device's H.264 encoder, which exercises decode → encode →
 *    mux through the encoder's input surface;
 *  - a **WAV** file, which is PCM: the platform can demux it, so it exercises the audio branch that
 *    never appears when the source is already AAC — decode to PCM, encode to AAC, mux.
 *
 * The output is then read back with `MediaExtractor`, so the test asserts what a player would see
 * (a track of the right kind, the right size, a non-zero duration) rather than that some bytes were
 * written. Run with `./gradlew :app:connectedDebugAndroidTest` on an emulator or a phone.
 */
@RunWith(AndroidJUnit4::class)
class VideoTranscoderInstrumentedTest {

    // Test names here are underscores rather than the backticked sentences the JVM unit tests use:
    // an instrumentation APK is dexed, and DEX below version 040 - what minSdk 24 produces - does not
    // allow spaces in a method name. D8 says so, but only after a full build.

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val work: File get() = File(context.cacheDir, "transcode-test").apply { mkdirs() }

    @Test
    fun a_video_file_comes_out_as_a_playable_h264_mp4() {
        val source = File(work, "source-video.mp4")
        val output = File(work, "converted-video.mp4")
        output.delete()

        assumeTrue("no byte-buffer video encoder on this device", encodeVideo(source))

        val transcoder = VideoTranscoder()
        val progress = mutableListOf<Int>()
        val converted = transcoder.transcode(source, output, onProgress = { progress.add(it) })

        assertTrue("conversion failed; source reads as ${describe(source)}", converted)
        assertTrue("output missing", output.exists() && output.length() > 0L)

        val tracks = readTracks(output)
        val video = tracks.firstOrNull { it.mime == VideoTranscoder.MIME_AVC }
        assertTrue("no H.264 track in the output: ${tracks.map { it.mime }}", video != null)
        assertEquals(VIDEO_WIDTH, video!!.width)
        assertEquals(VIDEO_HEIGHT, video.height)
        assertTrue("no duration in the output", video.durationUs > 0L)
        // Progress is reported for the user, so it has to actually arrive.
        assertTrue("no progress reported", progress.isNotEmpty())
    }

    @Test
    fun pcm_audio_is_decoded_and_encoded_into_aac_in_m4a() {
        val source = File(work, "tone.wav")
        val output = File(work, "converted-tone.m4a")
        output.delete()
        // A WAV is demuxed as `audio/raw`; a device without a raw decoder cannot run this branch at
        // all, and saying so is better than a red build on a device that is simply different.
        assumeTrue(
            "no decoder for raw PCM on this device",
            runCatching { MediaCodec.createDecoderByType("audio/raw") }.isSuccess
        )
        writeSineWave(source)
        // The decision table is what the UI uses to offer the button; the two must agree.
        assertEquals(TranscodePlan.Decision.AUDIO_TO_AAC_M4A, TranscodePlan.decide(source.name))

        val converted = VideoTranscoder().transcode(source, output)

        // The message carries what the platform sees in the file: a conversion refuses early when
        // there is no track it can use, and that is invisible from the JUnit failure alone.
        assertTrue("conversion failed; source reads as ${describe(source)}", converted)
        val tracks = readTracks(output)
        assertEquals("expected exactly one AAC track, got ${tracks.map { it.mime }}", 1, tracks.size)
        assertEquals(VideoTranscoder.MIME_AAC, tracks.first().mime)
        assertTrue("no duration in the output", tracks.first().durationUs > 0L)
    }

    @Test
    fun a_file_no_codec_can_open_fails_and_leaves_nothing_behind() {
        val source = File(work, "broken.mp4")
        val output = File(work, "converted-broken.mp4")
        source.writeBytes(ByteArray(4096) { 0x1b })
        output.delete()

        val converted = VideoTranscoder().transcode(source, output)

        assertFalse("a file with no readable track must not convert", converted)
        assertFalse("a failed conversion must not leave an output", output.exists())
    }

    @Test
    fun a_cancelled_conversion_leaves_nothing_behind_either() {
        val source = File(work, "cancel-source.mp4")
        val output = File(work, "cancel-output.mp4")
        output.delete()
        assumeTrue("no byte-buffer video encoder on this device", encodeVideo(source))

        val converted = VideoTranscoder().transcode(source, output, shouldStop = { true })

        assertFalse("a cancelled conversion must report failure", converted)
        assertFalse("a cancelled conversion must not leave an output", output.exists())
    }

    // ---- reading the result back ------------------------------------------------------------------

    private class Track(val mime: String?, val width: Int, val height: Int, val durationUs: Long)

    private fun readTracks(file: File): List<Track> {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            return (0 until extractor.trackCount).map { index ->
                val format = extractor.getTrackFormat(index)
                Track(
                    mime = format.getString(MediaFormat.KEY_MIME),
                    width = format.getIntegerOrNull(MediaFormat.KEY_WIDTH) ?: 0,
                    height = format.getIntegerOrNull(MediaFormat.KEY_HEIGHT) ?: 0,
                    durationUs = format.getLongOrNull(MediaFormat.KEY_DURATION) ?: 0L
                )
            }
        } finally {
            extractor.release()
        }
    }

    /**
     * What `MediaExtractor` makes of a file, as one string.
     *
     * The first thing worth knowing when a conversion refuses to run is whether the platform can
     * read the container at all, and on CI this message is the only way that information reaches a
     * human: the failure is reported from the JUnit XML.
     */
    private fun describe(file: File): String {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(file.absolutePath)
            (0 until extractor.trackCount).joinToString(prefix = "[", postfix = "]") { index ->
                val format = extractor.getTrackFormat(index)
                val mime = format.getString(MediaFormat.KEY_MIME)
                val rate = format.getIntegerOrNull(MediaFormat.KEY_SAMPLE_RATE) ?: -1
                val channels = format.getIntegerOrNull(MediaFormat.KEY_CHANNEL_COUNT) ?: -1
                "$mime@${rate}Hz/${channels}ch"
            }
        } catch (error: Exception) {
            "unreadable: ${error.javaClass.simpleName}: ${error.message}"
        } finally {
            runCatching { extractor.release() }
        }
    }

    private fun MediaFormat.getIntegerOrNull(key: String): Int? =
        if (containsKey(key)) getInteger(key) else null

    private fun MediaFormat.getLongOrNull(key: String): Long? =
        if (containsKey(key)) getLong(key) else null

    // ---- building the two source files ------------------------------------------------------------

    /**
     * Encodes [FRAME_COUNT] black frames with the device's own H.264 encoder into a real MP4, from
     * byte buffers rather than a surface. False means the encoder offers only surface input, and the
     * caller skips: a device without a byte-buffer encoder is not a device this claim is about.
     */
    private fun encodeVideo(target: File): Boolean {
        val format = MediaFormat.createVideoFormat(VideoTranscoder.MIME_AVC, VIDEO_WIDTH, VIDEO_HEIGHT).apply {
            setInteger(MediaFormat.KEY_BIT_RATE, 400_000)
            setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
        }
        val encoder = MediaCodec.createEncoderByType(VideoTranscoder.MIME_AVC)
        var muxer: MediaMuxer? = null
        try {
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()

            val inputFormat = encoder.inputFormat
            val colorFormat = inputFormat.getIntegerOrNull(MediaFormat.KEY_COLOR_FORMAT)
                ?: MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible
            val semiPlanar = colorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar ||
                colorFormat == 0x7F420888 // COLOR_FormatYUV420Flexible: byte buffers are NV12 on AOSP
            val planar = colorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar
            if (!semiPlanar && !planar) return false

            muxer = MediaMuxer(target.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val info = MediaCodec.BufferInfo()
            val frameBytes = VIDEO_WIDTH * VIDEO_HEIGHT * 3 / 2
            var trackIndex = -1
            var started = false
            var frame = 0
            var ended = false
            // A stuck encoder must fail the test, not hang the emulator job.
            val deadline = System.currentTimeMillis() + 20_000L

            while (System.currentTimeMillis() < deadline) {
                if (frame < FRAME_COUNT) {
                    val inIndex = encoder.dequeueInputBuffer(TIMEOUT_US)
                    if (inIndex >= 0) {
                        val buffer = encoder.getInputBuffer(inIndex)!!
                        buffer.clear()
                        fillBlackFrame(buffer, semiPlanar, frameBytes)
                        encoder.queueInputBuffer(inIndex, 0, frameBytes, frame * FRAME_INTERVAL_US, 0)
                        frame++
                    }
                } else if (frame == FRAME_COUNT) {
                    val inIndex = encoder.dequeueInputBuffer(TIMEOUT_US)
                    if (inIndex >= 0) {
                        encoder.queueInputBuffer(inIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        frame++
                    }
                }

                val outIndex = encoder.dequeueOutputBuffer(info, TIMEOUT_US)
                if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    trackIndex = muxer.addTrack(encoder.outputFormat)
                    muxer.start()
                    started = true
                } else if (outIndex >= 0) {
                    val buffer = encoder.getOutputBuffer(outIndex)!!
                    val isConfig = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                    if (!isConfig && info.size > 0 && started) {
                        buffer.position(info.offset)
                        buffer.limit(info.offset + info.size)
                        muxer.writeSampleData(trackIndex, buffer, info)
                    }
                    encoder.releaseOutputBuffer(outIndex, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        ended = true
                        break
                    }
                }
            }

            // A muxer that never started has no track, so the file would be useless to the caller.
            return ended && started && target.exists() && target.length() > 0L
        } finally {
            runCatching { encoder.stop() }
            runCatching { encoder.release() }
            if (muxer != null) {
                runCatching { muxer.stop() }
                runCatching { muxer.release() }
            }
        }
    }

    /** Near-black in both layouts: Y = 16, chroma = 128. */
    private fun fillBlackFrame(buffer: ByteBuffer, semiPlanar: Boolean, size: Int) {
        buffer.clear()
        repeat(size) { index ->
            buffer.put(index, if (index < VIDEO_WIDTH * VIDEO_HEIGHT) 16.toByte() else 128.toByte())
        }
        if (!semiPlanar) {
            // I420 wants U and V as separate planes; the loop above already wrote them interleaved,
            // so they are rewritten here in planar order.
            val ySize = VIDEO_WIDTH * VIDEO_HEIGHT
            val chromaSize = ySize / 4
            for (i in 0 until chromaSize) buffer.put(ySize + i, 128.toByte())
            for (i in 0 until chromaSize) buffer.put(ySize + chromaSize + i, 128.toByte())
        }
        buffer.position(0)
        buffer.limit(size)
    }

    /**
     * A plain 16-bit PCM WAV of a quiet sine. Written by hand because it is the only audio container
     * this test can produce that is *not* AAC, which is exactly the branch that needs covering.
     */
    private fun writeSineWave(target: File) {
        val samples = SAMPLE_RATE / 2
        val pcm = ByteBuffer.allocate(samples * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until samples) {
            val value = (Math.sin(2.0 * Math.PI * 440.0 * i / SAMPLE_RATE) * 6000).toInt().toShort()
            pcm.putShort(value)
        }
        val data = pcm.array()
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + data.size); put("WAVE".toByteArray())
            put("fmt ".toByteArray()); putInt(16); putShort(1.toShort()); putShort(1.toShort())
            putInt(SAMPLE_RATE); putInt(SAMPLE_RATE * 2); putShort(2.toShort()); putShort(16.toShort())
            put("data".toByteArray()); putInt(data.size)
        }
        target.writeBytes(header.array() + data)
    }

    private companion object {
        const val VIDEO_WIDTH = 64
        const val VIDEO_HEIGHT = 64
        const val FRAME_RATE = 30
        const val FRAME_COUNT = 15
        const val FRAME_INTERVAL_US = 1_000_000L / FRAME_RATE
        const val SAMPLE_RATE = 44_100
        const val TIMEOUT_US = 10_000L
    }
}
