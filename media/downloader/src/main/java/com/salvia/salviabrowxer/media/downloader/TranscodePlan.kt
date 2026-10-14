package com.salvia.salviabrowxer.media.downloader

/**
 * What can honestly be done with a saved file, decided from its extension, before any codec runs.
 *
 * This exists because "convert anything" is a promise no Android app can keep: the platform's own
 * demuxer (`MediaExtractor`) opens MP4/MOV/M4V, Matroska/WebM, MPEG-TS, 3GP and the audio containers
 * (M4A, MP3, AAC, WAV, OGG, FLAC, AMR…) — and does **not** open AVI, WMV/ASF, FLV, VOB or MPEG-PS.
 * AVI and WMV are the two containers the competitor's published list names, so the honest answer is
 * stated in the UI per file instead of being discovered after a minute of work: either the file is
 * re-encodable (HEVC/VP9/AV1 video → H.264, MP4 out; non-AAC audio → AAC, M4A out), or the platform
 * cannot even read it, and the app says exactly that.
 *
 * The decision is pure, so the table is unit-tested; the codecs it enables are not testable without
 * a device.
 */
object TranscodePlan {

    /** Containers the platform has no demuxer for. Nothing can be done in-process with these. */
    val NO_DEMUXER: Set<String> = setOf("avi", "wmv", "asf", "flv", "vob", "mpg", "mpeg", "divx", "rm", "rmvb")

    /** Containers `MediaExtractor` opens and that can hold video. */
    private val VIDEO_CONTAINERS: Set<String> = setOf("mp4", "m4v", "mov", "mkv", "webm", "ts", "m2ts", "mts", "3gp", "3g2", "f4v")

    /** Containers that hold audio only. */
    private val AUDIO_CONTAINERS: Set<String> = setOf("m4a", "m4b", "mp3", "aac", "wav", "ogg", "oga", "opus", "flac", "mka", "amr", "aif", "aiff", "ac3", "wma")

    /** What the app will do with a file, if the user asks. */
    enum class Decision {
        /** Video → H.264 in MP4. The case that matters: HEVC/VP9 that some players refuse. */
        VIDEO_TO_H264_MP4,

        /** Audio-only file → AAC in M4A. */
        AUDIO_TO_AAC_M4A,

        /** The platform cannot open the container at all. Refused, with the reason shown. */
        NO_DEMUXER,

        /** Nothing the app can usefully do (an unknown or already-fine container). */
        NOT_APPLICABLE
    }

    /** The extension of a filename or path, lowercased, without the dot. */
    fun extensionOf(name: String?): String? {
        val value = name?.substringBefore('?')?.substringBefore('#')?.trim().orEmpty()
        val lastSegment = value.substringAfterLast('/')
        val dot = lastSegment.lastIndexOf('.')
        if (dot <= 0 || dot == lastSegment.length - 1) return null
        return lastSegment.substring(dot + 1).lowercase().takeIf { it.length in 1..5 && it.all(Char::isLetterOrDigit) }
    }

    fun decide(fileName: String?): Decision {
        val extension = extensionOf(fileName) ?: return Decision.NOT_APPLICABLE
        return when (extension) {
            in NO_DEMUXER -> Decision.NO_DEMUXER
            in VIDEO_CONTAINERS -> Decision.VIDEO_TO_H264_MP4
            in AUDIO_CONTAINERS -> Decision.AUDIO_TO_AAC_M4A
            else -> Decision.NOT_APPLICABLE
        }
    }

    /**
     * Whether the app offers the action for [fileName].
     *
     * `NO_DEMUXER` is offered too: the user reads the reason from the app instead of guessing, and a
     * device whose vendor added an AVI demuxer is allowed to succeed.
     */
    fun isOffered(fileName: String?): Boolean = decide(fileName) != Decision.NOT_APPLICABLE

    /** The output file name for a conversion: `clip.mkv` becomes `clip (converted).mp4`. */
    fun outputNameFor(fileName: String): String {
        val base = fileName.substringBeforeLast('.', fileName).ifBlank { "media" }
        val suffix = when (decide(fileName)) {
            Decision.AUDIO_TO_AAC_M4A -> "m4a"
            else -> "mp4"
        }
        return "$base (converted).$suffix"
    }
}
