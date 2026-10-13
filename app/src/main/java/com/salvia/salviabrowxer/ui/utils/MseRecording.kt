package com.salvia.salviabrowxer.ui.utils

import androidx.webkit.WebViewFeature
import com.salvia.salviabrowxer.core.model.WebOrigin
import org.json.JSONObject

/**
 * What the media sheet needs to render the playback-recording action.
 *
 * @param isSupported false on a WebView too old to hand the app a segment as bytes; the sheet then
 *   keeps the honest explanation instead of offering a button that cannot work.
 * @param isRecording true while a recording is running for the tab on screen.
 * @param bytes what has been written so far, so the user can see it growing rather than guessing.
 */
data class MseRecordingUiState(
    val isSupported: Boolean = false,
    val isRecording: Boolean = false,
    val bytes: Long = 0L
)

/**
 * A control message from the page's recording hook, decoded.
 *
 * The page can post anything at all to the port, so the shape is validated once, here, and both the
 * WebView listener and the unit tests use the same function: a decoder that lives inside a
 * `WebMessageListener` callback cannot be tested without a device, and "malformed message is ignored"
 * would be a claim rather than a test.
 */
sealed interface MsePortPayload {
    /** A stream is starting, and this is what it says it is. */
    data class Open(val mimeType: String) : MsePortPayload

    /** The page hit the recording ceiling; whatever was appended so far is all there is. */
    data object Cap : MsePortPayload

    /**
     * A frame of this document announced itself, with the URL it claims to be.
     *
     * This is what makes a report *attributable*: a sighting from a frame can only be trusted if the
     * frame is one the document actually contains, and the announcement is the only place a frame
     * identifies itself on a channel where WebKit also tells the app the origin it really has.
     */
    data class Frame(val claimedUrl: String) : MsePortPayload

    /** Not ours, or not readable: an empty body, a huge one, broken JSON, another `t` value. */
    data object Ignored : MsePortPayload
}

/** The longest control message worth parsing; the page's own hook sends well under 100 bytes. */
const val MSE_CONTROL_MAX_CHARS: Int = 512

/** The longest mime worth carrying on from a page; anything longer is not a mime type. */
const val MSE_MIME_MAX_CHARS: Int = 160

/** The longest URL worth carrying on from a frame; anything longer is not a page URL. */
const val MSE_FRAME_URL_MAX_CHARS: Int = 4096

/**
 * Whether a frame's claim about its own URL agrees with the origin WebKit reported for it.
 *
 * A page can say anything about itself on the wire; what it cannot do is choose the origin the
 * WebView attributes to the frame that posted a message. So a claim counts only when the two agree —
 * which is the one frame identity a page cannot forge, and the reason the frames of a document can be
 * registered at all. An unparsable claim (`about:blank`, `data:`) is not a frame the app can attribute
 * anything to, so it is refused rather than accepted on trust.
 */
fun frameClaimAccepted(claimedUrl: String, sourceOrigin: String?): Boolean {
    val claimed = WebOrigin.of(claimedUrl) ?: return false
    return claimed == WebOrigin.of(sourceOrigin)
}

/**
 * Decodes a string message from the recording port.
 *
 * Malformed input is [MsePortPayload.Ignored], never an exception: a page that posts garbage must not
 * be able to break the listener, and the honest behaviour for an unrecognised message is silence.
 */
fun decodeMseControl(raw: String?): MsePortPayload {
    val body = raw?.takeIf { it.length <= MSE_CONTROL_MAX_CHARS }?.trim().orEmpty()
    if (body.isEmpty() || body[0] != '{') return MsePortPayload.Ignored
    val json = runCatching { JSONObject(body) }.getOrNull() ?: return MsePortPayload.Ignored
    return when (json.optString("t")) {
        "open" -> MsePortPayload.Open(json.optString("mime").trim().take(MSE_MIME_MAX_CHARS))
        "cap" -> MsePortPayload.Cap
        "frame" -> MsePortPayload.Frame(json.optString("u").trim().take(MSE_FRAME_URL_MAX_CHARS))
        else -> MsePortPayload.Ignored
    }
}

/**
 * Whether this device's WebView can record a `MediaSource` playback at all.
 *
 * Both features are required and they are separate on purpose: the listener is what injects the port
 * the page posts to, and ArrayBuffer support is what lets a segment cross as bytes instead of base64
 * (a third more memory per segment, on top of the copy the page already holds). Half of the pair is
 * not enough, so the check is one place and the UI asks it once.
 */
object MseRecordingSupport {

    val isSupported: Boolean = runCatching {
        WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER) &&
            WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_ARRAY_BUFFER)
    }.getOrDefault(false)
}
