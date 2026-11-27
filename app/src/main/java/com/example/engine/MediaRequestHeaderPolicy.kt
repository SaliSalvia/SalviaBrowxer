package com.example.engine

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import okhttp3.Request

/** Strict allow-list for low-risk metadata. Credentials are never captured or replayed. */
object MediaRequestHeaderPolicy {
    private val allowedNames = setOf("accept", "accept-language", "origin")
    private val diagnosticOnlyNames = setOf("referer", "user-agent")
    val replayableHeaderNames: Set<String> = allowedNames

    fun sanitize(headers: Map<String, String>): Map<String, String> = buildMap {
        headers.forEach { (name, value) ->
            val normalizedName = name.trim().lowercase()
            val normalizedValue = value.trim()
            if (normalizedName !in allowedNames || normalizedValue.isEmpty()) return@forEach
            if (normalizedValue.length > 1024 || normalizedValue.any { it.code < 0x20 || it.code == 0x7f }) return@forEach
            val safeValue = when (normalizedName) {
                "accept" -> normalizedValue.takeIf { it.length <= 512 }
                "accept-language" -> normalizedValue.takeIf { it.length <= 256 }
                "origin" -> normalizeOriginHeader(normalizedValue)
                else -> null
            }
            if (!safeValue.isNullOrBlank()) put(normalizedName, safeValue)
        }
    }

    /** Returns a canonical HTTP(S) origin (scheme + host + effective port). */
    fun originForUrl(value: String): String? = runCatching {
        val uri = URI(value)
        val scheme = uri.scheme?.lowercase() ?: return null
        if (scheme != "http" && scheme != "https") return null
        val host = uri.host?.lowercase()?.takeIf { it.isNotBlank() } ?: return null
        if (uri.rawUserInfo != null) return null
        val port = when {
            uri.port == -1 -> null
            scheme == "https" && uri.port == 443 -> null
            scheme == "http" && uri.port == 80 -> null
            else -> uri.port
        }
        "$scheme://$host" + (port?.let { ":$it" } ?: "")
    }.getOrNull()

    private fun normalizeOriginHeader(value: String): String? {
        val uri = runCatching { URI(value) }.getOrNull() ?: return null
        if (!uri.rawPath.isNullOrEmpty() && uri.rawPath != "/") return null
        if (uri.rawQuery != null || uri.rawFragment != null || uri.rawUserInfo != null) return null
        return originForUrl(value)
    }

    fun headersForUrl(url: String, contextsByOrigin: Map<String, Map<String, String>>): Map<String, String> {
        val origin = originForUrl(url) ?: return emptyMap()
        return sanitize(contextsByOrigin[origin].orEmpty())
    }

    /** Bounded deterministic format; URL encoding prevents delimiter injection. */
    fun encodeContext(contextsByOrigin: Map<String, Map<String, String>>): String {
        val rows = contextsByOrigin.entries.mapNotNull { (origin, headers) ->
            val canonicalOrigin = originForUrl(origin) ?: return@mapNotNull null
            val safeHeaders = sanitize(headers)
            if (safeHeaders.isEmpty()) null else canonicalOrigin to safeHeaders
        }.take(8).flatMap { (origin, headers) ->
            headers.entries.map { (name, value) -> Triple(origin, name, value) }
        }.take(24)
        return rows.joinToString("\n") { (origin, name, value) ->
            listOf(origin, name, value).joinToString("\t") {
                URLEncoder.encode(it, StandardCharsets.UTF_8.name())
            }
        }
    }

    fun decodeContext(encoded: String): Map<String, Map<String, String>> {
        if (encoded.isBlank() || encoded.length > 24 * 4096) return emptyMap()
        val decoded = linkedMapOf<String, MutableMap<String, String>>()
        encoded.lineSequence().take(24).forEach { line ->
            val fields = line.split('\t')
            if (fields.size != 3) return@forEach
            val values = runCatching {
                fields.map { URLDecoder.decode(it, StandardCharsets.UTF_8.name()) }
            }.getOrNull() ?: return@forEach
            val origin = originForUrl(values[0]) ?: return@forEach
            if (origin != values[0]) return@forEach
            val safe = sanitize(mapOf(values[1] to values[2]))
            if (safe.isEmpty()) return@forEach
            if (decoded.size >= 8 && origin !in decoded) return@forEach
            decoded.getOrPut(origin) { linkedMapOf() }.putAll(safe)
        }
        return decoded.mapValues { sanitize(it.value) }.filterValues { it.isNotEmpty() }
    }

    /**
     * Adds only this request URL's allow-listed context and reports exactly how many
     * headers were newly injected. Existing caller-owned headers are never overwritten.
     */
    fun applyContextHeaders(
        request: Request,
        contextsByOrigin: Map<String, Map<String, String>>,
    ): AppliedMediaRequestContext {
        val injected = headersForUrl(request.url.toString(), contextsByOrigin)
            .filterKeys { request.header(it).isNullOrBlank() }
        val assisted = request.newBuilder().apply {
            injected.forEach { (name, value) -> header(name, value) }
        }.build()
        return AppliedMediaRequestContext(assisted, injected.keys)
    }

    /** Header-related failures get one legacy-path retry only when assistance was injected. */
    fun shouldRetryWithoutContext(responseCode: Int, injectedHeaderCount: Int): Boolean =
        injectedHeaderCount > 0 && responseCode in setOf(400, 403, 406, 415)

    /** Diagnostic-only output preserves the former safe Referer/User-Agent summary; these are not replayed. */
    fun safeSummary(headers: Map<String, String>): String {
        val summary = linkedMapOf<String, String>()
        summary.putAll(sanitize(headers))
        headers.forEach { (name, value) ->
            val normalizedName = name.trim().lowercase()
            val normalizedValue = value.trim()
            if (normalizedName in diagnosticOnlyNames &&
                normalizedValue.isNotEmpty() &&
                normalizedValue.length <= 1024 &&
                normalizedValue.none { it.code < 0x20 || it.code == 0x7f }
            ) {
                summary[normalizedName] = normalizedValue
            }
        }
        return summary.entries.joinToString(" • ") { (name, value) -> "$name: $value" }
    }
}


/** Request plus the count of headers added by the optional media-context helper. */
data class AppliedMediaRequestContext(
    val request: Request,
    val injectedHeaderNames: Set<String>,
) {
    val injectedHeaderCount: Int get() = injectedHeaderNames.size
}
