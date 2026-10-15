package com.salvia.salviabrowxer.data.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * The manual "Check for updates" action.
 *
 * Deliberately **not** a background poll: the app has no analytics and no account, and a component
 * that phones home on a schedule would quietly break both promises. The only request this class
 * ever makes happens when the user taps the row, against this repository's own public releases
 * endpoint, with no identifier attached beyond a user agent (which the GitHub API requires).
 *
 * The network half is thin on purpose: everything that can be decided without the network —
 * parsing the response and comparing versions — lives in [parse] and [isNewer], which run on the
 * JVM in tests.
 */
class UpdateChecker(
    private val okHttpClient: OkHttpClient,
    private val currentVersion: String,
    private val repository: String = DEFAULT_REPOSITORY
) {

    /** One manual check. Never retried, never cached: the next tap is the next check. */
    suspend fun check(): UpdateCheckResult = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://$API_HOST/repos/$repository/releases/latest")
            .header("User-Agent", "SalviaBrowxer-Android/$currentVersion")
            .header("Accept", "application/vnd.github+json")
            .build()
        runCatching {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@runCatching UpdateCheckResult.Failed("HTTP ${response.code}")
                }
                val body = response.body?.string().orEmpty()
                parse(body, currentVersion)
            }
        }.getOrElse { error -> UpdateCheckResult.Failed(error.message ?: "network error") }
    }

    sealed interface UpdateCheckResult {
        /** The newest published release is the one that is running. */
        data object UpToDate : UpdateCheckResult

        /** A newer published release exists. [pageUrl] is where the user reads and downloads it. */
        data class Available(val version: String, val pageUrl: String, val notes: String?) : UpdateCheckResult

        /** The check could not be completed; [reason] is a short, non-sensitive explanation. */
        data class Failed(val reason: String) : UpdateCheckResult
    }

    companion object {
        private const val API_HOST = "api.github.com"
        const val DEFAULT_REPOSITORY = "SaliSalvia/SalviaBrowxer"

        /** How much of the release notes is kept — a dialog, not a changelog archive. */
        private const val NOTES_LIMIT = 600

        /**
         * Interprets one response body. A release the endpoint returns but marks draft or
         * prerelease is treated as absent: `/latest` already excludes them, and a future call site
         * that forgets that must not turn an unstable build into an "update".
         */
        fun parse(body: String, currentVersion: String): UpdateCheckResult {
            if (body.isBlank()) return UpdateCheckResult.Failed("empty body")
            return runCatching {
                val release = JSONObject(body)
                if (release.optBoolean("draft") || release.optBoolean("prerelease")) return UpdateCheckResult.UpToDate
                val tag = release.optString("tag_name").trim()
                if (tag.isEmpty()) return UpdateCheckResult.Failed("no tag")
                if (!isNewer(currentVersion, tag)) return UpdateCheckResult.UpToDate
                val pageUrl = release.optString("html_url").trim().ifEmpty {
                    "https://github.com/$DEFAULT_REPOSITORY/releases"
                }
                val notes = release.optString("body").trim().takeIf { it.isNotEmpty() }?.let(::cleanNotes)
                UpdateCheckResult.Available(version = tag.removePrefix("v"), pageUrl = pageUrl, notes = notes)
            }.getOrElse { UpdateCheckResult.Failed("unparsable body") }
        }

        /**
         * `1.10.0` is newer than `1.9.2` (numeric, not lexicographic), `v` prefixes are tolerated,
         * and equal versions with different lengths (`1.0` vs `1.0.0`) are the same version — the
         * store and the tag are allowed to disagree about trailing zeroes.
         */
        fun isNewer(current: String, candidate: String): Boolean {
            val currentParts = versionParts(current) ?: return false
            val candidateParts = versionParts(candidate) ?: return false
            val length = maxOf(currentParts.size, candidateParts.size)
            for (index in 0 until length) {
                val left = currentParts.getOrElse(index) { 0 }
                val right = candidateParts.getOrElse(index) { 0 }
                if (right > left) return true
                if (right < left) return false
            }
            return false
        }

        private fun versionParts(version: String): List<Int>? =
            version.trim()
                .removePrefix("v")
                .removePrefix("V")
                .takeIf { it.isNotEmpty() }
                ?.split('.')
                ?.mapNotNull { part -> part.filter(Char::isDigit).toIntOrNull() }
                ?.takeIf { it.isNotEmpty() }

        private fun cleanNotes(notes: String): String =
            notes.lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("<!--") }
                .joinToString("\n")
                .take(NOTES_LIMIT)
    }
}
