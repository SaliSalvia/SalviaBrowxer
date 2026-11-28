package com.example.engine

import android.net.Uri

/**
 * Conservative platform identification based only on exact host/suffix matches.
 * This is descriptive metadata, not an extractor or DRM/capture bypass.
 */
object MediaPlatformDetector {

    data class Match(
        val id: String,
        val label: String,
        val confidenceBonus: Int,
    )

    private data class Rule(
        val id: String,
        val label: String,
        val hosts: Set<String>,
    )

    private val rules = listOf(
        Rule(
            id = "youtube",
            label = "YouTube",
            hosts = setOf("youtube.com", "youtube-nocookie.com", "youtu.be", "googlevideo.com"),
        ),
        Rule(
            id = "aparat",
            label = "Aparat",
            hosts = setOf("aparat.com", "aparat.tv", "aparat.cloud"),
        ),
        Rule(
            id = "vimeo",
            label = "Vimeo",
            hosts = setOf("vimeo.com", "vimeocdn.com"),
        ),
        Rule(
            id = "dailymotion",
            label = "Dailymotion",
            hosts = setOf("dailymotion.com", "dai.ly", "dmcdn.net", "dmxleo.com"),
        ),
        Rule(
            id = "reddit",
            label = "Reddit",
            hosts = setOf("reddit.com", "redd.it", "redditmedia.com"),
        ),
        Rule(
            id = "twitch",
            label = "Twitch",
            hosts = setOf("twitch.tv", "ttvnw.net", "jtvnw.net"),
        ),
        Rule(
            id = "instagram",
            label = "Instagram",
            hosts = setOf("instagram.com", "instagr.am", "cdninstagram.com"),
        ),
        Rule(
            id = "tiktok",
            label = "TikTok",
            hosts = setOf("tiktok.com", "tiktokcdn.com", "tiktokv.com"),
        ),
        Rule(
            id = "twitter",
            label = "X (Twitter)",
            hosts = setOf("twitter.com", "x.com", "twimg.com"),
        ),
    )

    fun detect(pageUrl: String, mediaUrl: String): Match {
        val pageHost = hostOf(pageUrl)
        val mediaHost = hostOf(mediaUrl)

        val pageMatch = rules.firstOrNull { rule -> pageHost.matchesAny(rule.hosts) }
        if (pageMatch != null) {
            return Match(pageMatch.id, pageMatch.label, confidenceBonus = 8)
        }

        val mediaMatch = rules.firstOrNull { rule -> mediaHost.matchesAny(rule.hosts) }
        if (mediaMatch != null) {
            return Match(mediaMatch.id, mediaMatch.label, confidenceBonus = 5)
        }

        return Match(id = "web", label = "Web", confidenceBonus = 0)
    }

    private fun hostOf(value: String): String =
        runCatching { Uri.parse(value).host.orEmpty().lowercase() }.getOrDefault("")

    private fun String.matchesAny(domains: Set<String>): Boolean =
        isNotBlank() && domains.any { domain ->
            this == domain || endsWith(".$domain")
        }
}
