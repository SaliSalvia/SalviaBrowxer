package com.salvia.salviabrowxer.ui.utils

/**
 * Cleartext (`http://`) navigation is blocked unless the user turns it on.
 *
 * The manifest cannot express a user preference, so the manifest allows cleartext
 * and this policy is the gate: the browser refuses to load `http://` and explains
 * why, instead of failing with no feedback.
 */
object CleartextPolicy {

    fun isCleartext(url: String): Boolean = url.trim().startsWith("http://", ignoreCase = true)

    fun isBlocked(url: String, cleartextAllowed: Boolean): Boolean =
        !cleartextAllowed && isCleartext(url)
}
