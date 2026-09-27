package com.salvia.salviabrowxer.ui.utils

import java.net.URLEncoder

/**
 * Resolves what the user typed into the address bar.
 *
 * Rules:
 * 1. Explicit scheme (http/https/file/about/data/javascript — case-insensitive) → used as-is.
 * 2. Bare "localhost", "localhost:8080", IP or any dotted host ("example.com") → https:// prefix.
 * 3. Anything else ("wikipedia", "کاهش وزن", "best headphones 2026") → search-engine URL.
 */
object AddressBarResolver {

    private val EXPLICIT_SCHEME_PREFIXES = listOf(
        "http://", "https://", "file://", "ftp://", "about:", "data:", "javascript:", "ws://", "wss://"
    )

    fun hasExplicitScheme(input: String): Boolean {
        val lower = input.trim().lowercase()
        return EXPLICIT_SCHEME_PREFIXES.any { lower.startsWith(it) }
    }

    /** A host-like token: no spaces, and a dot, a colon (host:port) or a localhost/IP form. */
    fun looksLikeHost(input: String): Boolean {
        val trimmed = input.trim()
        if (trimmed.isEmpty() || trimmed.contains(' ') || trimmed.contains('"') || trimmed.contains('<')) return false
        // "https:foo" style without slashes is malformed — treat as a search query, not a host
        if (trimmed.contains(':') && !trimmed.contains('.') && !trimmed.startsWith("localhost")) return false
        return trimmed.contains('.') || trimmed.startsWith("localhost") || trimmed.contains(':')
    }

    fun buildSearchUrl(query: String, searchEngine: String = Constants.DEFAULT_SEARCH_ENGINE): String {
        val template = Constants.SEARCH_ENGINES[searchEngine]
            ?: Constants.SEARCH_ENGINES.getValue(Constants.DEFAULT_SEARCH_ENGINE)
        val encoded = URLEncoder.encode(query.trim(), "UTF-8")
        return template.replace("%s", encoded)
    }

    fun resolve(input: String, searchEngine: String = Constants.DEFAULT_SEARCH_ENGINE): String? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null
        if (hasExplicitScheme(trimmed)) return trimmed
        return if (looksLikeHost(trimmed)) "https://$trimmed" else buildSearchUrl(trimmed, searchEngine)
    }
}
