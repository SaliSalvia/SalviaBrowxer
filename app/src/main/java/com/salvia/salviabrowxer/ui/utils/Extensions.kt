package com.salvia.salviabrowxer.ui.utils

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Opens a URL in whatever app handles it.
 *
 * The only member of this file that is still called (the About section's licence, source and
 * privacy links). `toast`, `shareText`, `Date.format`/`Long.formatDate`, `String.isValidUrl`,
 * `String.toUri` and the three `startsWithAny`/`endsWithAny`/`containsAny` helpers were removed
 * after checking every caller: the screens that needed those behaviours have their own, clearer
 * implementations (the address bar has `AddressBarResolver`, the share sheet is built in
 * `DownloadsViewModel`, and both URL validators were dead copies of rules that already live in the
 * model layer).
 *
 * A target that cannot handle the link is swallowed on purpose: it happens when the user taps a
 * link for an app that is not installed, and there is nothing the user could do about a toast here.
 */
fun Context.openUrl(url: String) {
    runCatching {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }
}
