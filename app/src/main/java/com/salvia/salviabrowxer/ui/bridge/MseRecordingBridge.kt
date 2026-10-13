package com.salvia.salviabrowxer.ui.bridge

import android.net.Uri
import android.util.Log
import android.webkit.WebView
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import com.salvia.salviabrowxer.ui.utils.MsePortPayload
import com.salvia.salviabrowxer.ui.utils.decodeMseControl

/**
 * The data plane of playback recording: what a page appended to a `SourceBuffer`, arriving as bytes.
 *
 * `WebViewCompat.addWebMessageListener` is the only channel that can carry an `ArrayBuffer`
 * (`JavascriptInterface` takes strings, and base64 costs a third more memory on every segment), so
 * that is the channel the injected hook posts to. Two message shapes arrive:
 *
 *  - a **string** control message — `{"t":"frame","u":…}` when a frame announces itself,
 *    `{"t":"open","mime":"video/mp4"}` when a stream starts and `{"t":"cap"}` when the page hit the
 *    recording ceiling;
 *  - an **ArrayBuffer** — one appended segment, byte for byte.
 *
 * This class only decodes and forwards. Whether any of it may be written is decided by
 * `MseRecordingController`, which knows whether the user armed this tab and which frame is allowed to
 * speak for the stream — a page can post here whenever it likes, so a transport that decided for
 * itself would be the whole vulnerability.
 */
class MseRecordingBridge(
    /** A frame announced itself: its claimed URL, the origin WebKit reported, and whether it is the top frame. */
    private val onFrame: (claimedUrl: String, sourceOrigin: String?, isMainFrame: Boolean) -> Unit,
    private val onOpen: (mimeType: String, sourceOrigin: String?, isMainFrame: Boolean) -> Unit,
    private val onSegment: (bytes: ByteArray, sourceOrigin: String?, isMainFrame: Boolean) -> Unit,
    private val onCap: (sourceOrigin: String?, isMainFrame: Boolean) -> Unit
) : WebViewCompat.WebMessageListener {

    override fun onPostMessage(
        view: WebView,
        message: WebMessageCompat,
        sourceOrigin: Uri,
        isMainFrame: Boolean,
        replyProxy: WebViewCompat.JavaScriptReplyProxy
    ) {
        try {
            val origin = sourceOrigin.toString()
            when (message.type) {
                WebMessageCompat.TYPE_ARRAY_BUFFER -> {
                    val bytes = message.arrayBuffer ?: return
                    if (bytes.isEmpty()) return
                    onSegment(bytes, origin, isMainFrame)
                }
                WebMessageCompat.TYPE_STRING -> handleControl(message.data, origin, isMainFrame)
            }
        } catch (error: Throwable) {
            // A segment that cannot be read must never take the page down with it.
            if (error is Error && error !is StackOverflowError) throw error
            Log.w(TAG, "onPostMessage failed", error)
        }
    }

    private fun handleControl(raw: String?, origin: String, isMainFrame: Boolean) {
        when (val payload = decodeMseControl(raw)) {
            is MsePortPayload.Frame -> onFrame(payload.claimedUrl, origin, isMainFrame)
            is MsePortPayload.Open -> onOpen(payload.mimeType, origin, isMainFrame)
            MsePortPayload.Cap -> onCap(origin, isMainFrame)
            MsePortPayload.Ignored -> Unit
        }
    }

    companion object {
        private const val TAG = "MseRecordingBridge"
    }
}
