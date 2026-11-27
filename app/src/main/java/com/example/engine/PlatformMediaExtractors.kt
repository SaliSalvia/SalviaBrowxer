package com.example.engine

import com.example.ui.MediaQualityOption
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI

/**
 * Platform-specific extraction of publicly available media for Instagram, TikTok, and X posts.
 *
 * Design constraints, consistent with the rest of the media pipeline:
 * - Only public content the browser could already render is parsed. There is no login-wall
 *   bypass, no authenticated/private API call, no credential capture, and no DRM handling.
 * - Parsers are pure functions of fetched text; all network fetching stays in
 *   [MediaSnifferEngine.probePlatformPage] so the browser's existing redirect, origin,
 *   header-context, and cookie policies always apply.
 * - Extraction is strictly additive and fails closed: any parse failure returns null and the
 *   established generic discovery pipeline (DOM sniffing, network hooks, MSE attribution)
 *   remains the sole source of truth.
 */
object PlatformMediaExtractors {

    data class PlatformExtraction(
        val title: String,
        val primaryUrl: String,
        val thumbnailUrl: String = "",
        val durationSeconds: Double = 0.0,
        val width: Int = 0,
        val height: Int = 0,
        val isAudioOnly: Boolean = false,
        val confidenceScore: Int,
        val qualities: List<MediaQualityOption>,
    )

    private const val MAX_PROCESSED_HTML_CHARS = 3_000_000
    private const val X_SYNDICATION_PREFIX = "https://cdn.syndication.twimg.com/tweet-result?id="
    private const val X_SYNDICATION_SUFFIX = "&token=x&lang=en"

    private val instagramPostRegex = Regex("^/(?:p|reel|reels|tv)/[A-Za-z0-9_-]+/?$")
    private val xStatusRegex = Regex("^/(?:i/web/)?[^/?]+/status/(\\d+)/?$")

    // --- URL matching (pure, host/suffix based, mirrors MediaPlatformDetector) ---

    fun isSupported(url: String): Boolean =
        matchInstagramPost(url) != null || matchTikTokVideo(url) != null || matchXStatus(url) != null

    internal fun matchInstagramPost(url: String): String? {
        val uri = safeUri(url) ?: return null
        val host = uri.host?.lowercase() ?: return null
        if (!host.endsWith("instagram.com") && !host.endsWith("instagr.am")) return null
        return instagramPostRegex.matchEntire(uri.rawPath.orEmpty())?.value
    }

    internal fun matchTikTokVideo(url: String): String? {
        val uri = safeUri(url) ?: return null
        val host = uri.host?.lowercase() ?: return null
        if (!host.endsWith("tiktok.com")) return null
        val path = uri.rawPath.orEmpty()
        if (host.startsWith("vm.") || host.startsWith("vt.")) return path
        return if (path.contains("/video/")) path else null
    }

    /** Returns the numeric status id of a public tweet/post URL, or null. */
    internal fun matchXStatus(url: String): String? {
        val uri = safeUri(url) ?: return null
        val host = uri.host?.lowercase() ?: return null
        if (!host.endsWith("twitter.com") && !host.endsWith("x.com")) return null
        return xStatusRegex.matchEntire(uri.rawPath.orEmpty())?.groupValues?.getOrNull(1)
    }

    /**
     * Extracts downloadable candidates for a supported platform page. Returns null when the
     * page cannot be fetched or no verified public media can be found.
     */
    suspend fun extract(
        pageUrl: String,
        fetchText: suspend (String) -> String?,
    ): PlatformExtraction? {
        return runCatching {
            if (matchInstagramPost(pageUrl) != null) return@runCatching extractInstagram(pageUrl, fetchText)
            if (matchTikTokVideo(pageUrl) != null) return@runCatching extractTikTok(pageUrl, fetchText)
            val tweetId = matchXStatus(pageUrl)
            if (tweetId != null) return@runCatching extractX(pageUrl, tweetId, fetchText)
            null
        }.getOrNull()
    }

    // --- Instagram ---

    private suspend fun extractInstagram(
        pageUrl: String,
        fetchText: suspend (String) -> String?,
    ): PlatformExtraction? {
        val html = fetchText(pageUrl)?.capped() ?: return null
        val qualities = LinkedHashMap<String, MediaQualityOption>()

        val ogVideo = sanitizeMediaUrl(
            metaContent(html, "og:video:secure_url")
                ?: metaContent(html, "og:video:url")
                ?: metaContent(html, "og:video"),
        )
        ogVideo?.let {
            qualities.putIfAbsent(it, videoQuality("Original (MP4)", height = 0, url = it))
        }

        // Embedded page JSON carries video_versions with explicit heights for public posts.
        extractJsonArray(html, "video_versions")?.let { versions ->
            for (i in 0 until versions.length()) {
                val version = versions.optJSONObject(i) ?: continue
                val url = sanitizeMediaUrl(version.optString("url")) ?: continue
                val type = version.optString("type").substringBefore(';').trim().lowercase()
                val height = version.optInt("height", 0)
                val option = when {
                    type.contains("mpegurl") -> MediaQualityOption(
                        label = "Auto Quality (HLS -> MP4)",
                        resolutionHeight = height,
                        url = url,
                        format = "HLS",
                        mimeType = "application/vnd.apple.mpegurl",
                        isHls = true,
                    )
                    type.isNotEmpty() && !type.startsWith("video/") -> continue
                    else -> videoQuality(labelForHeight(height), height, url)
                }
                qualities.putIfAbsent(url, option)
            }
        }

        if (qualities.isEmpty()) return null
        val best = qualities.values.maxBy { it.resolutionHeight }
        return PlatformExtraction(
            title = metaContent(html, "og:title").orEmpty().ifBlank { "Instagram Video" },
            primaryUrl = best.url,
            thumbnailUrl = metaContent(html, "og:image").orEmpty(),
            height = best.resolutionHeight,
            confidenceScore = if (ogVideo != null) 88 else 78,
            qualities = qualities.values.sortedByDescending { it.resolutionHeight },
        )
    }

    // --- TikTok ---

    private suspend fun extractTikTok(
        pageUrl: String,
        fetchText: suspend (String) -> String?,
    ): PlatformExtraction? {
        val html = fetchText(pageUrl)?.capped() ?: return null

        // Primary evidence: the hydration payload of the official web player.
        scriptJson(html, "__UNIVERSAL_DATA_FOR_REHYDRATION__")
            ?.let { json -> runCatching { JSONObject(json) }.getOrNull() }
            ?.optJSONObject("__DEFAULT_SCOPE__")
            ?.optJSONObject("webapp.video-detail")
            ?.optJSONObject("itemInfo")
            ?.optJSONObject("itemStruct")
            ?.let { item -> parseTikTokItem(item)?.let { return it } }

        // Fallback: OpenGraph metadata.
        val ogVideo = sanitizeMediaUrl(
            metaContent(html, "og:video:secure_url")
                ?: metaContent(html, "og:video:url")
                ?: metaContent(html, "og:video"),
        ) ?: return null
        return PlatformExtraction(
            title = metaContent(html, "og:title").orEmpty().ifBlank { "TikTok Video" },
            primaryUrl = ogVideo,
            thumbnailUrl = metaContent(html, "og:image").orEmpty(),
            confidenceScore = 76,
            qualities = listOf(videoQuality("Original (MP4)", height = 0, url = ogVideo)),
        )
    }

    private fun parseTikTokItem(item: JSONObject): PlatformExtraction? {
        val video = item.optJSONObject("video") ?: return null
        val qualities = LinkedHashMap<String, MediaQualityOption>()

        video.optJSONArray("bitrateInfo")?.let { bitrateInfo ->
            for (i in 0 until bitrateInfo.length()) {
                val entry = bitrateInfo.optJSONObject(i) ?: continue
                val playAddr = entry.optJSONObject("PlayAddr") ?: continue
                val url = sanitizeMediaUrl(playAddr.optJSONArray("UrlList")?.optString(0)) ?: continue
                val height = playAddr.optInt("Height", 0)
                val width = playAddr.optInt("Width", 0)
                val sizeBytes = playAddr.optLong("DataSize", -1L)
                qualities.putIfAbsent(
                    url,
                    videoQuality(labelForHeight(height), height, url).copy(
                        width = width,
                        sizeBytes = if (sizeBytes > 0L) sizeBytes else -1L,
                    ),
                )
            }
        }
        sanitizeMediaUrl(video.optString("playAddr"))?.let {
            qualities.putIfAbsent(it, videoQuality("Original (MP4)", video.optInt("height", 0), it))
        }
        sanitizeMediaUrl(video.optString("downloadAddr"))?.let {
            qualities.putIfAbsent(it, videoQuality("Watermarked (MP4)", video.optInt("height", 0), it))
        }
        if (qualities.isEmpty()) return null

        val allQualities = qualities.values.toMutableList()
        sanitizeMediaUrl(item.optJSONObject("music")?.optString("playUrl"))?.let { audioUrl ->
            allQualities.add(
                MediaQualityOption(
                    label = "Audio Only (MP3)",
                    resolutionHeight = 0,
                    url = audioUrl,
                    format = "MP3",
                    mimeType = "audio/mpeg",
                    isAudioOnly = true,
                ),
            )
        }

        val best = qualities.values.maxBy { it.resolutionHeight }
        return PlatformExtraction(
            title = item.optString("desc").trim().ifBlank { "TikTok Video" },
            primaryUrl = best.url,
            thumbnailUrl = video.optString("cover"),
            durationSeconds = video.optDouble("duration", 0.0),
            width = best.width,
            height = best.resolutionHeight,
            confidenceScore = 86,
            qualities = allQualities,
        )
    }

    // --- X (Twitter) ---

    private suspend fun extractX(
        pageUrl: String,
        tweetId: String,
        fetchText: suspend (String) -> String?,
    ): PlatformExtraction? {
        // The syndication endpoint serves the same public payload the official web embed uses.
        fetchText("$X_SYNDICATION_PREFIX$tweetId$X_SYNDICATION_SUFFIX")?.let { json ->
            parseTweetResult(json)?.let { return it }
        }

        // Fallback: server-rendered OpenGraph metadata on the post page.
        val html = fetchText(pageUrl)?.capped() ?: return null
        val ogVideo = sanitizeMediaUrl(
            metaContent(html, "og:video:secure_url")
                ?: metaContent(html, "twitter:player:stream")
                ?: metaContent(html, "og:video"),
        ) ?: return null
        return PlatformExtraction(
            title = metaContent(html, "og:title").orEmpty().ifBlank { "X Video" },
            primaryUrl = ogVideo,
            thumbnailUrl = metaContent(html, "og:image").orEmpty(),
            confidenceScore = 74,
            qualities = listOf(videoQuality("Original (MP4)", height = 0, url = ogVideo)),
        )
    }

    private fun parseTweetResult(json: String): PlatformExtraction? {
        val tweet = runCatching { JSONObject(json) }.getOrNull() ?: return null
        val mediaDetails = tweet.optJSONArray("mediaDetails") ?: return null

        val qualities = LinkedHashMap<String, MediaQualityOption>()
        var durationSeconds = 0.0
        var thumbnailUrl = ""
        var hasVideo = false

        for (i in 0 until mediaDetails.length()) {
            val media = mediaDetails.optJSONObject(i) ?: continue
            if (thumbnailUrl.isBlank()) thumbnailUrl = media.optString("media_url_https")
            if (media.optString("type") != "video") continue
            val videoInfo = media.optJSONObject("video_info") ?: continue
            durationSeconds = maxOf(durationSeconds, videoInfo.optLong("duration_millis", 0L) / 1000.0)
            val variants = videoInfo.optJSONArray("variants") ?: continue
            hasVideo = true

            for (j in 0 until variants.length()) {
                val variant = variants.optJSONObject(j) ?: continue
                val url = sanitizeMediaUrl(variant.optString("url")) ?: continue
                val contentType = variant.optString("content_type").substringBefore(';').trim().lowercase()
                val bitrate = variant.optLong("bitrate", 0L)
                val option = when (contentType) {
                    "application/x-mpegurl" -> MediaQualityOption(
                        label = "Auto Quality (HLS -> MP4)",
                        resolutionHeight = 0,
                        url = url,
                        format = "HLS",
                        mimeType = "application/vnd.apple.mpegurl",
                        bandwidthBps = bitrate,
                        isHls = true,
                    )
                    "video/mp4" -> videoQuality(labelForBitrate(bitrate), height = 0, url = url)
                        .copy(bandwidthBps = bitrate)
                    else -> continue
                }
                qualities.putIfAbsent(url, option)
            }
        }
        if (!hasVideo || qualities.isEmpty()) return null

        // Prefer the highest-bitrate progressive MP4 as the primary source; keep HLS as an
        // additional quality option when the payload exposes one.
        val videoOnly = qualities.values.filter { !it.isHls }
        val best = videoOnly.maxByOrNull { it.bandwidthBps } ?: qualities.values.first()
        return PlatformExtraction(
            title = tweet.optString("text").trim().lineSequence().firstOrNull().orEmpty()
                .ifBlank { "X Video" },
            primaryUrl = best.url,
            thumbnailUrl = thumbnailUrl,
            durationSeconds = durationSeconds,
            confidenceScore = 85,
            qualities = qualities.values.sortedWith(
                compareByDescending<MediaQualityOption> { it.isHls.not() }
                    .thenByDescending { it.bandwidthBps },
            ),
        )
    }

    // --- Shared helpers ---

    private fun videoQuality(label: String, height: Int, url: String): MediaQualityOption =
        MediaQualityOption(
            label = label,
            resolutionHeight = height,
            url = url,
            format = "MP4",
            mimeType = "video/mp4",
        )

    private fun labelForHeight(height: Int): String = when {
        height >= 2160 -> "2160p 4K (MP4)"
        height >= 1440 -> "1440p 2K (MP4)"
        height >= 1080 -> "1080p Full HD (MP4)"
        height >= 720 -> "720p HD (MP4)"
        height >= 480 -> "480p SD (MP4)"
        height > 0 -> "${height}p (MP4)"
        else -> "Original (MP4)"
    }

    private fun labelForBitrate(bitrate: Long): String =
        if (bitrate > 0) "${bitrate / 1000} kbps (MP4)" else "Original (MP4)"

    /**
     * Accepts only plain http(s) URLs without embedded credentials. Opaque browser references
     * (blob:, data:) and anything with user info are rejected, mirroring
     * MseNetworkAttributionPolicy's fail-closed posture.
     */
    internal fun sanitizeMediaUrl(value: String?): String? {
        val trimmed = value?.trim().orEmpty()
        if (trimmed.isBlank() || trimmed.length > 8192) return null
        if (trimmed.startsWith("blob:", ignoreCase = true) || trimmed.startsWith("data:", ignoreCase = true)) {
            return null
        }
        val uri = runCatching { URI(trimmed) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return null
        if (uri.host.isNullOrBlank() || uri.rawUserInfo != null) return null
        return uri.toASCIIString()
    }

    private fun safeUri(url: String): URI? = runCatching { URI(url.trim()) }.getOrNull()

    private fun String.capped(): String = if (length > MAX_PROCESSED_HTML_CHARS) substring(0, MAX_PROCESSED_HTML_CHARS) else this

    /** Reads the content attribute of an OpenGraph/meta tag in either attribute order. */
    internal fun metaContent(html: String, key: String): String? {
        val escapedKey = Regex.escape(key)
        val propertyFirst = Regex(
            "<meta[^>]+(?:property|name)=\"$escapedKey\"[^>]*?content=\"([^\"]*)\"",
            RegexOption.IGNORE_CASE,
        )
        val contentFirst = Regex(
            "<meta[^>]+content=\"([^\"]*)\"[^>]*?(?:property|name)=\"$escapedKey\"",
            RegexOption.IGNORE_CASE,
        )
        val raw = propertyFirst.find(html)?.groupValues?.getOrNull(1)
            ?: contentFirst.find(html)?.groupValues?.getOrNull(1)
            ?: return null
        return decodeHtmlEntities(raw).trim().takeIf { it.isNotBlank() }
    }

    private fun decodeHtmlEntities(value: String): String = value
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&#x27;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&amp;", "&")

    internal fun scriptJson(html: String, scriptId: String): String? {
        val regex = Regex(
            "<script[^>]+id=\"$scriptId\"[^>]*>(.*?)</script>",
            RegexOption.DOT_MATCHES_ALL,
        )
        return regex.find(html)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }
    }

    /** Extracts the first top-level JSON array bound to [key] using bracket-depth scanning. */
    internal fun extractJsonArray(html: String, key: String): JSONArray? {
        val keyIndex = html.indexOf("\"$key\"")
        if (keyIndex < 0) return null
        val open = html.indexOf('[', keyIndex)
        if (open < 0) return null

        var depth = 0
        var inString = false
        var escaped = false
        for (i in open until minOf(html.length, open + 400_000)) {
            val c = html[i]
            if (escaped) {
                escaped = false
                continue
            }
            when {
                inString && c == '\\' -> escaped = true
                c == '"' -> inString = !inString
                !inString && c == '[' -> depth++
                !inString && c == ']' -> {
                    depth--
                    if (depth == 0) {
                        return runCatching { JSONArray(html.substring(open, i + 1)) }.getOrNull()
                    }
                }
            }
        }
        return null
    }
}
