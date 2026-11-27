package com.example.engine

import java.net.URI

/**
 * Fail-closed validation for MSE-to-network provenance supplied by the WebView bridge.
 *
 * JavaScript reports a URL only when the exact ArrayBuffer (or its exact backing buffer)
 * returned from a network Response/XHR is the object passed into appendBuffer. This policy
 * validates the evidence and avoids promoting common HLS/DASH fragment resources.
 */
object MseNetworkAttributionPolicy {
    private val mediaSegmentSuffixes = setOf(".ts", ".m4s", ".cmfv", ".cmfa")

    fun verifiedSourceUrl(
        sourceBufferMimeType: String,
        responseContentType: String,
        sourceUrl: String,
        byteLength: Int,
        responseStatus: Int,
        contentRange: String,
    ): String? {
        // Partial/range responses are fragments, not independently downloadable resources.
        if (responseStatus != 200 || contentRange.isNotBlank()) return null
        if (byteLength <= 0 || sourceUrl.isBlank() || sourceUrl.length > 8192) return null
        val sourceMime = sourceBufferMimeType.substringBefore(';').trim().lowercase()
        val responseMime = responseContentType.substringBefore(';').trim().lowercase()
        val sourceFamily = when {
            sourceMime.startsWith("video/") -> "video"
            sourceMime.startsWith("audio/") -> "audio"
            else -> return null
        }
        if (responseMime.isNotEmpty()) {
            val responseFamily = when {
                responseMime.startsWith("video/") -> "video"
                responseMime.startsWith("audio/") -> "audio"
                responseMime == "application/octet-stream" || responseMime == "binary/octet-stream" -> "binary"
                else -> return null
            }
            if (responseFamily != "binary" && responseFamily != sourceFamily) return null
        }

        val uri = runCatching { URI(sourceUrl) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return null
        if (uri.host.isNullOrBlank() || uri.rawUserInfo != null) return null
        val path = uri.rawPath.orEmpty().lowercase()
        if (mediaSegmentSuffixes.any(path::endsWith)) return null
        return uri.toASCIIString()
    }
}
