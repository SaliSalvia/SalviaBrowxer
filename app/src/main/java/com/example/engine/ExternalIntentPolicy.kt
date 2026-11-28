package com.example.engine

import android.content.Intent
import android.net.Uri

/**
 * Decides what to do with a non-http(s) navigation (intent://, mailto:, tel:, market:, ...).
 *
 * Mirrors the hardening Chrome applies to web-initiated intents:
 * - only BROWSABLE activities can be reached; explicit components and selectors are removed,
 *   so a page can never start a private (non-exported-to-web) activity;
 * - URI-permission grant flags and other dangerous flags are stripped;
 * - dangerous schemes (javascript:, file:, content:, data:, ...) are never handed to other apps;
 * - intent:// links honor `S.browser_fallback_url` (http/https only) when no app can handle them;
 * - an intent:// whose payload is just an http(s) URL without a target package stays in the
 *   browser.
 */
object ExternalIntentPolicy {

    sealed class Decision {
        /** Ask the user, then start this (sanitized) intent. */
        data class LaunchExternal(val intent: Intent, val targetLabel: String) : Decision()

        /** Load this http(s) URL in the current tab instead. */
        data class LoadInBrowser(val url: String) : Decision()

        /** Swallow the navigation. */
        object Block : Decision()
    }

    private val blockedSchemes = setOf(
        "javascript", "vbscript", "file", "content", "data", "about", "blob", "chrome",
        "chrome-extension", "filesystem", "jar", "android-app", "view-source", "ws", "wss",
    )

    /**
     * @param canResolve returns true when at least one activity can handle the intent; it is a
     *   parameter so the policy stays unit-testable without a PackageManager.
     */
    fun resolve(rawUrl: String, canResolve: (Intent) -> Boolean): Decision {
        val url = rawUrl.trim()
        if (url.isEmpty() || url.length > 8_192) return Decision.Block
        val scheme = url.substringBefore(':', "").lowercase()
        if (scheme.isEmpty() || scheme in blockedSchemes) return Decision.Block
        if (scheme == "http" || scheme == "https") return Decision.LoadInBrowser(url)

        return if (scheme == "intent") resolveIntentScheme(url, canResolve) else resolvePlainScheme(url, scheme, canResolve)
    }

    private fun resolveIntentScheme(url: String, canResolve: (Intent) -> Boolean): Decision {
        val parsed = runCatching { Intent.parseUri(url, Intent.URI_INTENT_SCHEME) }.getOrNull()
            ?: return Decision.Block
        val fallback = safeHttpUrl(parsed.getStringExtra(EXTRA_BROWSER_FALLBACK_URL))
        val intent = sanitize(parsed)

        val dataScheme = intent.data?.scheme?.lowercase()
        if (dataScheme != null && dataScheme in blockedSchemes) {
            return fallback?.let { Decision.LoadInBrowser(it) } ?: Decision.Block
        }
        // intent://example.com#Intent;scheme=https;end without a package is just a web link.
        if ((dataScheme == "http" || dataScheme == "https") && intent.`package`.isNullOrBlank()) {
            return Decision.LoadInBrowser(intent.dataString.orEmpty())
        }
        if (canResolve(intent)) {
            return Decision.LaunchExternal(intent, intent.`package` ?: intent.data?.scheme ?: "app")
        }
        if (fallback != null) return Decision.LoadInBrowser(fallback)
        // Package targeted but not installed: send the user to the store listing, as Chrome does.
        val pkg = intent.`package`
        if (!pkg.isNullOrBlank() && PACKAGE_NAME_REGEX.matches(pkg)) {
            val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg"))
                .addCategory(Intent.CATEGORY_BROWSABLE)
            if (canResolve(market)) return Decision.LaunchExternal(market, "market")
        }
        return Decision.Block
    }

    private fun resolvePlainScheme(url: String, scheme: String, canResolve: (Intent) -> Boolean): Decision {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return Decision.Block
        val intent = Intent(Intent.ACTION_VIEW, uri)
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return if (canResolve(intent)) Decision.LaunchExternal(intent, scheme) else Decision.Block
    }

    /** Restricts a web-supplied intent to what a web page may legitimately request. */
    fun sanitize(source: Intent): Intent {
        val intent = Intent(source)
        intent.addCategory(Intent.CATEGORY_BROWSABLE)
        intent.component = null
        intent.selector?.let { selector ->
            selector.addCategory(Intent.CATEGORY_BROWSABLE)
            selector.component = null
        }
        // Drop every flag a page could abuse (URI grants, persistable grants, task hijacking)
        // and start the activity in its own task.
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        return intent
    }

    private fun safeHttpUrl(value: String?): String? {
        val trimmed = value?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        val lower = trimmed.lowercase()
        return if (lower.startsWith("https://") || lower.startsWith("http://")) trimmed else null
    }

    private const val EXTRA_BROWSER_FALLBACK_URL = "browser_fallback_url"
    private val PACKAGE_NAME_REGEX = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+$")
}
