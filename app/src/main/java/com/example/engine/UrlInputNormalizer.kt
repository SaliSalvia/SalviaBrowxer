package com.example.engine

import java.net.InetAddress
import java.net.URI
import java.net.URLEncoder
import java.util.Locale

object UrlInputNormalizer {

    fun normalize(
        input: String,
        httpsUpgradeEnabled: Boolean,
        searchUrlTemplate: String,
    ): String {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return ""

        val isHttp = trimmed.startsWith("http://", ignoreCase = true)
        val isHttps = trimmed.startsWith("https://", ignoreCase = true)
        if (isHttp || isHttps) {
            val rest = trimmed.substringAfter("://")
            val scheme = when {
                isHttp && httpsUpgradeEnabled && !isLocalAddress(trimmed) -> "https"
                isHttp -> "http"
                else -> "https"
            }
            return "$scheme://$rest"
        }

        val localAddress = isLocalAddress("http://$trimmed")
        val looksLikeDomain = !trimmed.any(Char::isWhitespace) &&
            (trimmed.contains('.') || localAddress)
        if (looksLikeDomain) {
            val scheme = if (localAddress || !httpsUpgradeEnabled) "http" else "https"
            return "$scheme://$trimmed"
        }

        val encoded = runCatching { URLEncoder.encode(trimmed, Charsets.UTF_8.name()) }
            .getOrDefault(trimmed)
        return searchUrlTemplate.replace("%s", encoded)
    }

    private fun isLocalAddress(url: String): Boolean {
        val host = runCatching { URI(url).host }
            .getOrNull()
            ?.removeSurrounding("[", "]")
            ?.lowercase(Locale.ROOT)
            ?: return false

        if (host == "localhost" || host.endsWith(".localhost")) return true

        val ipv4 = host.split('.')
        if (ipv4.size == 4) {
            val octets = ipv4.map { it.toIntOrNull() ?: return@map -1 }
            if (octets.all { it in 0..255 }) {
                val first = octets[0]
                val second = octets[1]
                return first == 0 ||
                    first == 10 ||
                    first == 127 ||
                    (first == 169 && second == 254) ||
                    (first == 172 && second in 16..31) ||
                    (first == 192 && second == 168)
            }
        }

        // Only resolve numeric IPv6 literals. Hostnames never trigger DNS lookups here.
        if (':' in host) {
            val address = runCatching { InetAddress.getByName(host).address }.getOrNull() ?: return false
            if (address.size == 16) {
                val first = address[0].toInt() and 0xff
                val second = address[1].toInt() and 0xff
                return address.all { it == 0.toByte() } ||
                    (address.dropLast(1).all { it == 0.toByte() } && address.last() == 1.toByte()) ||
                    (first and 0xfe) == 0xfc ||
                    (first == 0xfe && (second and 0xc0) == 0x80)
            }
        }
        return false
    }
}
