package com.salvia.salviabrowxer.ui.bridge

import android.util.Log
import android.webkit.JavascriptInterface
import com.salvia.salviabrowxer.core.model.MediaCandidate
import com.salvia.salviabrowxer.core.model.MediaSniffAdmission
import com.salvia.salviabrowxer.core.model.SniffOrigin

/**
 * Receives what the injected sniffer script saw.
 *
 * This is the piece that was missing. The old script hooked the same APIs but had no listener on
 * this side — it called Kotlin only for `blob:` URLs, and that handler did nothing but log — so
 * every other sighting it made was written to a page-side array and thrown away downstream. A
 * detector can only detect if its findings have somewhere to go.
 *
 * The signature is string-only and deliberately small: every call crosses Binder and arrives on the
 * WebView's JavaBridge thread, so the decision itself belongs in
 * [MediaSniffAdmission], which is pure and tested.
 */
class MediaSnifferBridge(
    private val onAdmitted: (MediaCandidate) -> Unit
) {

    @JavascriptInterface
    fun onMediaUrlFound(
        pageUrl: String,
        url: String,
        mimeType: String,
        origin: String,
        elementKind: String
    ) {
        try {
            val candidate = MediaSniffAdmission.admit(
                pageUrl = pageUrl,
                url = url,
                mimeType = mimeType.takeIf { it.isNotBlank() },
                origin = SniffOrigin.fromWire(origin),
                elementKind = elementKind.takeIf { it.isNotBlank() }
            ) ?: return
            onAdmitted(candidate)
        } catch (error: Throwable) {
            if (error is Error && error !is StackOverflowError) throw error
            Log.w(TAG, "onMediaUrlFound failed for $url", error)
        }
    }

    companion object {
        private const val TAG = "MediaSnifferBridge"
    }
}
