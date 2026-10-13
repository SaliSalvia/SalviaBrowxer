package com.salvia.salviabrowxer.service

import com.salvia.salviabrowxer.core.model.WebOrigin
import com.salvia.salviabrowxer.media.downloader.MseRecorder
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The one place that decides whether a byte a page posted becomes part of a recording.
 *
 * A page can post to the recording port whenever it likes, so the port is never trusted on its own.
 * Three things have to be true before anything is written:
 *
 *  1. the user asked to record, and asked *for this tab* ([arm]);
 *  2. the frame posting is allowed to speak for the stream — the frame that exposed the candidate, or
 *     the page itself ([allows]);
 *  3. the stream has not already ended (a cap, an encrypted payload, a finished recording).
 *
 * It also holds the only copy of the per-tab bookkeeping, so the WebView layer stays a transport and
 * the view model stays about screens: bytes go in here, a finished cache file comes out, and
 * [active] is what the sheet draws.
 */
class MseRecordingController(private val rootDir: File) {

    /** A recording session: what is being recorded, for which tab, and who is allowed to feed it. */
    data class Session(
        val tabId: String,
        val kind: String,
        val mimeType: String,
        /** The page the candidate was found on; null for a stream the app cannot place. */
        val pageUrl: String?,
        /** The frame the candidate came from, when it named one: the only frame allowed to feed it. */
        val frameOrigin: String?,
        /** Base name for the finished file. */
        val title: String?,
        val bytes: Long = 0L
    )

    /** A finished recording, with the session it belonged to, ready to be filed as a download. */
    data class Finished(val recording: MseRecorder.Recording, val session: Session)

    /** What became of a page's "here comes a stream" message. Only [RECORDING] writes bytes. */
    enum class Open { RECORDING, NOT_ARMED, DENIED_FRAME, NO_ROOM }

    private val recorders = mutableMapOf<String, MseRecorder>()
    private val sessions = mutableMapOf<String, Session>()
    private val armed = mutableSetOf<String>()

    private val _active = MutableStateFlow<Session?>(null)
    val active: StateFlow<Session?> = _active.asStateFlow()

    val isRecording: Boolean get() = recorders.isNotEmpty()

    /** The user asked to record this tab's playback. The page is armed separately, in its own JS. */
    fun arm(tabId: String, kind: String, pageUrl: String?, frameOrigin: String?, title: String?) {
        abandon(tabId)
        armed += tabId
        val session = Session(
            tabId = tabId,
            kind = kind,
            mimeType = "",
            pageUrl = pageUrl,
            frameOrigin = frameOrigin?.takeIf { it.isNotBlank() },
            title = title?.takeIf { it.isNotBlank() }
        )
        sessions[tabId] = session
        _active.value = session
    }

    fun disarm(tabId: String) {
        armed -= tabId
    }

    /** The page is telling us which stream it is about to feed. */
    fun onOpen(tabId: String, mimeType: String, sourceOrigin: String?): Open {
        if (tabId !in armed) return Open.NOT_ARMED
        if (!allows(tabId, sourceOrigin)) return Open.DENIED_FRAME
        val session = sessions[tabId] ?: return Open.NOT_ARMED
        if (recorders.containsKey(tabId)) return Open.RECORDING
        val recorder = MseRecorder(File(rootDir, safeDirectory(tabId)))
        if (!recorder.start(session.title ?: "recording", mimeType)) {
            // No room, or a cache directory that cannot be created: the session ends here and the
            // caller is told why, because "recording" that writes nothing is the worse answer.
            armed -= tabId
            return Open.NO_ROOM
        }
        recorders[tabId] = recorder
        val updated = session.copy(mimeType = mimeType)
        sessions[tabId] = updated
        _active.value = updated
        return Open.RECORDING
    }

    /** One segment exactly as the page handed it over. */
    fun onSegment(tabId: String, bytes: ByteArray, sourceOrigin: String?): MseRecorder.Append {
        if (!allows(tabId, sourceOrigin)) return MseRecorder.Append.IGNORED
        val recorder = recorders[tabId] ?: return MseRecorder.Append.IGNORED
        val outcome = recorder.append(bytes)
        sessions[tabId]?.let { session ->
            val updated = session.copy(bytes = recorder.bytesWritten)
            sessions[tabId] = updated
            if (_active.value?.tabId == tabId) _active.value = updated
        }
        return outcome
    }

    /** Whether a frame may feed this tab's recording. */
    fun allows(tabId: String, sourceOrigin: String?): Boolean {
        if (tabId !in armed) return false
        val session = sessions[tabId] ?: return false
        // The frame that exposed the stream, or the page itself. A frame that named no origin is
        // treated as the page's own (a browser's same-origin check would too), never as anonymous.
        val frame = session.frameOrigin ?: return true
        val reported = sourceOrigin?.takeIf { it.isNotBlank() } ?: return true
        return WebOrigin.of(frame) == WebOrigin.of(reported)
    }

    /** The bytes written so far for a tab — what the sheet shows growing. */
    fun bytesOf(tabId: String): Long = recorders[tabId]?.bytesWritten ?: sessions[tabId]?.bytes ?: 0L

    /**
     * Closes the recording and hands it over, or returns null when nothing was captured.
     *
     * Nothing is kept by this class: the caller files the file, so the user still has to be told what
     * happened to it.
     */
    fun finish(tabId: String): Finished? {
        val session = sessions[tabId]
        val recorder = recorders.remove(tabId)
        armed -= tabId
        sessions.remove(tabId)
        refreshActive()
        val recording = recorder?.finish() ?: return null
        return Finished(recording, session ?: Session(tabId, "video", recording.mimeType, null, null, null))
    }

    /** Gives up on a recording and deletes whatever was written — used when the stream is refused. */
    fun abandon(tabId: String) {
        recorders.remove(tabId)?.abort()
        armed -= tabId
        sessions.remove(tabId)
        refreshActive()
    }

    /** Drops everything: the tab closed, or the view model is going away. */
    fun abandonAll() {
        recorders.keys.toList().forEach { recorders.remove(it)?.abort() }
        armed.clear()
        sessions.clear()
        refreshActive()
    }

    private fun refreshActive() {
        _active.value = sessions.values.firstOrNull()
    }

    /** A tab id is not a path. It happens to be a UUID today; this does not rely on that. */
    private fun safeDirectory(tabId: String): String =
        tabId.filter { it.isLetterOrDigit() || it == '-' || it == '_' }.ifBlank { "tab" }
}
