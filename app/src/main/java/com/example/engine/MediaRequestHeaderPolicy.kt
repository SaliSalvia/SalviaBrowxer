package com.example.engine

/**
 * Allow-list for request metadata that is safe to retain for media diagnostics.
 * Credential-bearing and browser-private headers are intentionally excluded.
 */
object MediaRequestHeaderPolicy {
    private val allowedNames = setOf(
        "accept",
        "accept-language",
        "origin",
        "referer",
        "user-agent",
    )

    fun sanitize(headers: Map<String, String>): Map<String, String> = buildMap {
        headers.forEach { (name, value) ->
            val normalizedName = name.trim().lowercase()
            val normalizedValue = value.trim()
            if (
                normalizedName in allowedNames &&
                normalizedValue.isNotEmpty() &&
                normalizedValue.length <= 1024 &&
                '\\r' !in normalizedValue &&
                '\\n' !in normalizedValue
            ) {
                put(normalizedName, normalizedValue)
            }
        }
    }

    fun safeSummary(headers: Map<String, String>): String =
        sanitize(headers).entries.joinToString(" • ") { (name, value) -> "$name: $value" }
}
