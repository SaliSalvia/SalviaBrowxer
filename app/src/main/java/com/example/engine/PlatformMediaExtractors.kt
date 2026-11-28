package com.example.engine

import com.example.ui.MediaQualityOption
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI

/**
 * Platform-specific extraction of publicly available media for Instagram, TikTok, X, Aparat,
 * Dailymotion, and Reddit posts. The Aparat, Dailymotion, and Reddit parsers follow the public
 * data paths documented by yt-dlp's extractors (page player options, the public player
 * metadata endpoint, and the post's public .json view respectively).
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
        /** Master HLS playlist the probe should expand into per-resolution qualities. */
        val hlsManifestUrl: String = "",
        /** DASH manifest the probe should expand into per-representation qualities. */
        val dashManifestUrl: String = "",
    )

    private const val MAX_PROCESSED_HTML_CHARS = 3_000_000
    private const val X_SYNDICATION_PREFIX = "https://cdn.syndication.twimg.com/tweet-result?id="
    private const val X_SYNDICATION_SUFFIX = "&token=x&lang=en"

    private const val DAILYMOTION_METADATA_PREFIX = "https://www.dailymotion.com/player/metadata/video/"
    private const val DAILYMOTION_METADATA_SUFFIX = "?app=com.dailymotion.neon"
    private const val APARAT_EMBED_PREFIX =
        "https://www.aparat.com/video/video/embed/vt/frame/showvideo/yes/videohash/"

    private val aparatPathRegex = Regex("^/(?:v/|video/video/embed/(?:vt/frame/showvideo/yes/)?videohash/)([A-Za-z0-9]+)(?:[/?#].*)?$")
    private val dailymotionPathRegex = Regex("^/(?:embed/)?video/([A-Za-z0-9]+)(?:_[^/]*)?/?$")
    private val dailymotionIdRegex = Regex("^[A-Za-z0-9]{5,}$")
    private val redditPostRegex = Regex("^/(?:(?:r|user|u)/[^/]+/)?comments/([A-Za-z0-9]+)(?:/[^?#]*)?$")
    private val dailymotionSizeRegex = Regex("/H264-(\\d+)x(\\d+)")
    private val heightLabelRegex = Regex("(\\d{3,4})\\s*[pP]")

    private val instagramPostRegex = Regex("^/(?:p|reel|reels|tv)/[A-Za-z0-9_-]+/?$")
    // Matches both /<user>/status/<id> and the /i/web/status/<id> permalink form.
    private val xStatusRegex = Regex("^/(?:i/web/)?(?:[^/?]+/)?status/(\\d+)/?$")

    // --- URL matching (pure, host/suffix based, mirrors MediaPlatformDetector) ---

    fun isSupported(url: String): Boolean =
        matchInstagramPost(url) != null || matchTikTokVideo(url) != null || matchXStatus(url) != null ||
            matchAparatVideo(url) != null || matchDailymotionVideo(url) != null || matchRedditPost(url) != null

    /** Returns the Aparat video hash of a watch or embed page, or null. */
    internal fun matchAparatVideo(url: String): String? {
        val uri = safeUri(url) ?: return null
        val host = uri.host?.lowercase() ?: return null
        if (host != "aparat.com" && !host.endsWith(".aparat.com")) return null
        return aparatPathRegex.matchEntire(uri.rawPath.orEmpty())?.groupValues?.getOrNull(1)
    }

    /** Returns the Dailymotion xid of a watch, embed, short (dai.ly), or player page, or null. */
    internal fun matchDailymotionVideo(url: String): String? {
        val uri = safeUri(url) ?: return null
        val host = uri.host?.lowercase() ?: return null
        val path = uri.rawPath.orEmpty()
        return when {
            host == "dai.ly" || host == "www.dai.ly" ->
                path.trim('/').takeIf { dailymotionIdRegex.matches(it) }
            host == "geo.dailymotion.com" ->
                queryParam(uri, "video")?.takeIf { dailymotionIdRegex.matches(it) }
            host == "dailymotion.com" || host.endsWith(".dailymotion.com") ->
                dailymotionPathRegex.matchEntire(path)?.groupValues?.getOrNull(1)
                    ?.takeIf { dailymotionIdRegex.matches(it) }
            else -> null
        }
    }

    /** Returns the Reddit post id of a comments permalink, or null. */
    internal fun matchRedditPost(url: String): String? {
        val uri = safeUri(url) ?: return null
        val host = uri.host?.lowercase() ?: return null
        if (host != "reddit.com" && !host.endsWith(".reddit.com")) return null
        return redditPostRegex.matchEntire(uri.rawPath.orEmpty())?.groupValues?.getOrNull(1)
    }

    internal fun matchInstagramPost(url: String): String? {
        val uri = safeUri(url) ?: return null
        val host = uri.host?.lowercase() ?: return null
        if (!hostMatches(host, "instagram.com") && !hostMatches(host, "instagr.am")) return null
        return instagramPostRegex.matchEntire(uri.rawPath.orEmpty())?.value
    }

    internal fun matchTikTokVideo(url: String): String? {
        val uri = safeUri(url) ?: return null
        val host = uri.host?.lowercase() ?: return null
        if (!hostMatches(host, "tiktok.com")) return null
        val path = uri.rawPath.orEmpty()
        if (host.startsWith("vm.") || host.startsWith("vt.")) return path
        return if (path.contains("/video/")) path else null
    }

    /** Returns the numeric status id of a public tweet/post URL, or null. */
    internal fun matchXStatus(url: String): String? {
        val uri = safeUri(url) ?: return null
        val host = uri.host?.lowercase() ?: return null
        if (!hostMatches(host, "twitter.com") && !hostMatches(host, "x.com")) return null
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
            val aparatId = matchAparatVideo(pageUrl)
            if (aparatId != null) return@runCatching extractAparat(pageUrl, aparatId, fetchText)
            val dailymotionId = matchDailymotionVideo(pageUrl)
            if (dailymotionId != null) return@runCatching extractDailymotion(dailymotionId, fetchText)
            if (matchRedditPost(pageUrl) != null) return@runCatching extractReddit(pageUrl, fetchText)
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
                        resolutionWidth = width,
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
                ),
            )
        }

        val best = qualities.values.maxBy { it.resolutionHeight }
        return PlatformExtraction(
            title = item.optString("desc").trim().ifBlank { "TikTok Video" },
            primaryUrl = best.url,
            thumbnailUrl = video.optString("cover"),
            durationSeconds = video.optDouble("duration", 0.0),
            width = best.resolutionWidth,
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
            // The syndication payload normally nests duration_millis inside video_info, but some
            // responses expose it on the media object itself; accept either location.
            val durationMillis = videoInfo.optLong(
                "duration_millis",
                media.optLong("duration_millis", 0L),
            )
            durationSeconds = maxOf(durationSeconds, durationMillis / 1000.0)
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


    // --- Aparat ---

    private suspend fun extractAparat(
        pageUrl: String,
        videoHash: String,
        fetchText: suspend (String) -> String?,
    ): PlatformExtraction? {
        val pageHtml = fetchText(pageUrl)?.capped()
        pageHtml?.let { html -> parseAparatPage(html)?.let { return it } }
        // The watch page may be a client-rendered shell; the embed player always inlines options.
        val embedHtml = fetchText(APARAT_EMBED_PREFIX + videoHash)?.capped() ?: return null
        return parseAparatPage(embedHtml, titleFallbackHtml = pageHtml)
    }

    internal fun parseAparatPage(html: String, titleFallbackHtml: String? = null): PlatformExtraction? {
        val options = extractJsObjectAfter(html, Regex("options\\s*=\\s*(?=\\{)"))
            ?.let { runCatching { JSONObject(it) }.getOrNull() }
        val qualities = LinkedHashMap<String, MediaQualityOption>()
        var hlsUrl = ""

        options?.optJSONArray("multiSRC")?.let { groups ->
            for (g in 0 until groups.length()) {
                val group = groups.optJSONArray(g) ?: groups.optJSONObject(g)?.let { JSONArray().put(it) } ?: continue
                for (i in 0 until group.length()) {
                    val item = group.optJSONObject(i) ?: continue
                    val url = sanitizeMediaUrl(item.optString("src")) ?: continue
                    val type = item.optString("type").lowercase()
                    val label = item.optString("label")
                    if (type.contains("mpegurl") || url.contains(".m3u8")) {
                        if (hlsUrl.isBlank()) hlsUrl = url
                        qualities.putIfAbsent(url, hlsQuality(url, height = 0))
                    } else {
                        val height = heightLabelRegex.find(label)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
                        qualities.putIfAbsent(url, videoQuality(labelForHeight(height), height, url))
                    }
                }
            }
        }

        // Fallbacks used by some page variants: JSON-LD contentUrl and OpenGraph video.
        if (qualities.isEmpty()) {
            val candidates = listOfNotNull(
                jsonLdString(html, "contentUrl"),
                metaContent(html, "og:video:secure_url"),
                metaContent(html, "og:video"),
            )
            candidates.mapNotNull(::sanitizeMediaUrl).forEach { url ->
                if (url.contains(".m3u8")) {
                    if (hlsUrl.isBlank()) hlsUrl = url
                    qualities.putIfAbsent(url, hlsQuality(url, 0))
                } else {
                    qualities.putIfAbsent(url, videoQuality("Original (MP4)", 0, url))
                }
            }
        }
        if (qualities.isEmpty()) return null

        val progressive = qualities.values.filter { !it.isHls }
        val best = progressive.maxByOrNull { it.resolutionHeight } ?: qualities.values.first()
        val title = jsonLdString(html, "name")
            ?: metaContent(html, "og:title")
            ?: titleFallbackHtml?.let { metaContent(it, "og:title") ?: jsonLdString(it, "name") }
            ?: htmlTitle(html)
        return PlatformExtraction(
            title = title.orEmpty().ifBlank { "Aparat Video" },
            primaryUrl = best.url,
            thumbnailUrl = sanitizeMediaUrl(options?.optString("poster")).orEmpty()
                .ifBlank { metaContent(html, "og:image").orEmpty() },
            durationSeconds = options?.optDouble("duration", 0.0)?.takeIf { !it.isNaN() } ?: 0.0,
            height = best.resolutionHeight,
            confidenceScore = if (options != null) 87 else 75,
            qualities = qualities.values.sortedWith(
                compareByDescending<MediaQualityOption> { !it.isHls }.thenByDescending { it.resolutionHeight },
            ),
            hlsManifestUrl = hlsUrl,
        )
    }

    // --- Dailymotion ---

    private suspend fun extractDailymotion(
        xid: String,
        fetchText: suspend (String) -> String?,
    ): PlatformExtraction? {
        val json = fetchText(DAILYMOTION_METADATA_PREFIX + xid + DAILYMOTION_METADATA_SUFFIX) ?: return null
        return parseDailymotionMetadata(json)
    }

    internal fun parseDailymotionMetadata(json: String): PlatformExtraction? {
        val metadata = runCatching { JSONObject(json) }.getOrNull() ?: return null
        // Geo-blocked, private, password-protected, or removed videos expose an error object.
        if (metadata.optJSONObject("error") != null) return null
        val qualitiesJson = metadata.optJSONObject("qualities") ?: return null

        val qualities = LinkedHashMap<String, MediaQualityOption>()
        var hlsUrl = ""
        val keys = qualitiesJson.keys()
        while (keys.hasNext()) {
            val qualityKey = keys.next()
            val entries = qualitiesJson.optJSONArray(qualityKey) ?: continue
            for (i in 0 until entries.length()) {
                val entry = entries.optJSONObject(i) ?: continue
                val type = entry.optString("type").lowercase()
                if (type == "application/vnd.lumberjack.manifest") continue
                val url = sanitizeMediaUrl(entry.optString("url").substringBefore('#')) ?: continue
                if (type.contains("mpegurl")) {
                    if (hlsUrl.isBlank() || qualityKey == "auto") hlsUrl = url
                    qualities.putIfAbsent(url, hlsQuality(url, qualityKey.toIntOrNull() ?: 0))
                } else if (type.startsWith("video/") || type.isBlank()) {
                    val size = dailymotionSizeRegex.find(url)
                    val width = size?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
                    val height = size?.groupValues?.getOrNull(2)?.toIntOrNull() ?: qualityKey.toIntOrNull() ?: 0
                    qualities.putIfAbsent(
                        url,
                        videoQuality(labelForHeight(height), height, url).copy(resolutionWidth = width),
                    )
                }
            }
        }
        if (qualities.isEmpty()) return null

        val thumbnails = metadata.optJSONObject("thumbnails")
        var thumbnail = ""
        var thumbHeight = -1
        if (thumbnails != null) {
            val thumbKeys = thumbnails.keys()
            while (thumbKeys.hasNext()) {
                val key = thumbKeys.next()
                val h = key.toIntOrNull() ?: continue
                val url = sanitizeMediaUrl(thumbnails.optString(key)) ?: continue
                if (h > thumbHeight) {
                    thumbHeight = h
                    thumbnail = url
                }
            }
        }
        val progressive = qualities.values.filter { !it.isHls }
        val best = progressive.maxByOrNull { it.resolutionHeight } ?: qualities.values.first()
        return PlatformExtraction(
            title = metadata.optString("title").trim().ifBlank { "Dailymotion Video" },
            primaryUrl = best.url,
            thumbnailUrl = thumbnail,
            durationSeconds = metadata.optDouble("duration", 0.0).takeIf { !it.isNaN() } ?: 0.0,
            width = best.resolutionWidth,
            height = best.resolutionHeight,
            confidenceScore = 88,
            qualities = qualities.values.sortedWith(
                compareByDescending<MediaQualityOption> { !it.isHls }.thenByDescending { it.resolutionHeight },
            ),
            hlsManifestUrl = hlsUrl,
        )
    }

    // --- Reddit ---

    private suspend fun extractReddit(
        pageUrl: String,
        fetchText: suspend (String) -> String?,
    ): PlatformExtraction? {
        val json = fetchText(redditJsonUrl(pageUrl) ?: return null) ?: return null
        return parseRedditPost(json)
    }

    /** The public .json view of the same post on the page's own host. */
    internal fun redditJsonUrl(pageUrl: String): String? {
        val uri = safeUri(pageUrl) ?: return null
        val host = uri.host?.lowercase() ?: return null
        val apiHost = if (host == "old.reddit.com" || host == "www.reddit.com" || host == "new.reddit.com") host else "www.reddit.com"
        val path = uri.rawPath.orEmpty().trimEnd('/')
        if (redditPostRegex.matchEntire(path) == null) return null
        return "https://$apiHost$path/.json?raw_json=1"
    }

    internal fun parseRedditPost(json: String): PlatformExtraction? {
        val root = runCatching { JSONArray(json) }.getOrNull() ?: return null
        val post = root.optJSONObject(0)?.optJSONObject("data")?.optJSONArray("children")
            ?.optJSONObject(0)?.optJSONObject("data") ?: return null

        val candidates = mutableListOf(post)
        post.optJSONArray("crosspost_parent_list")?.let { list ->
            for (i in 0 until list.length()) list.optJSONObject(i)?.let(candidates::add)
        }
        val video: JSONObject = candidates.firstNotNullOfOrNull { candidate ->
            candidate.optJSONObject("secure_media")?.optJSONObject("reddit_video")
                ?: candidate.optJSONObject("media")?.optJSONObject("reddit_video")
                ?: candidate.optJSONObject("preview")?.optJSONObject("reddit_video_preview")
        } ?: return null

        val height = video.optInt("height", 0)
        val width = video.optInt("width", 0)
        val isGif = video.optBoolean("is_gif", false)
        val hlsUrl = sanitizeMediaUrl(decodeHtmlEntities(video.optString("hls_url")))
        val dashUrl = sanitizeMediaUrl(decodeHtmlEntities(video.optString("dash_url")))
        val fallbackUrl = sanitizeMediaUrl(decodeHtmlEntities(video.optString("fallback_url")))

        val qualities = mutableListOf<MediaQualityOption>()
        // HLS carries muxed audio, so it is the primary, complete source.
        hlsUrl?.let { qualities.add(hlsQuality(it, height).copy(resolutionWidth = width)) }
        fallbackUrl?.let { url ->
            val label = if (isGif) labelForHeight(height) else "${labelForHeight(height).removeSuffix(" (MP4)")} (MP4, no audio)"
            qualities.add(
                videoQuality(label, height, url).copy(
                    resolutionWidth = width,
                    bandwidthBps = video.optLong("bitrate_kbps", 0L) * 1000L,
                ),
            )
        }
        if (qualities.isEmpty()) return null

        val thumbnail = sanitizeMediaUrl(
            decodeHtmlEntities(
                post.optJSONObject("preview")?.optJSONArray("images")?.optJSONObject(0)
                    ?.optJSONObject("source")?.optString("url").orEmpty(),
            ),
        ) ?: sanitizeMediaUrl(post.optString("thumbnail")).orEmpty()
        return PlatformExtraction(
            title = decodeHtmlEntities(post.optString("title")).trim().ifBlank { "Reddit Video" },
            primaryUrl = qualities.first().url,
            thumbnailUrl = thumbnail,
            durationSeconds = video.optDouble("duration", 0.0).takeIf { !it.isNaN() } ?: 0.0,
            width = width,
            height = height,
            confidenceScore = 86,
            qualities = qualities,
            hlsManifestUrl = hlsUrl.orEmpty(),
            dashManifestUrl = dashUrl.orEmpty(),
        )
    }

    // --- Shared helpers ---

    private fun hlsQuality(url: String, height: Int): MediaQualityOption = MediaQualityOption(
        label = if (height > 0) "${height}p (HLS -> MP4)" else "Auto Quality (HLS -> MP4)",
        resolutionHeight = height,
        url = url,
        format = "HLS",
        mimeType = "application/vnd.apple.mpegurl",
        isHls = true,
    )

    private fun queryParam(uri: URI, name: String): String? =
        uri.rawQuery?.split('&')?.firstOrNull { it.substringBefore('=') == name }
            ?.substringAfter('=', "")?.takeIf { it.isNotBlank() }

    private fun htmlTitle(html: String): String? =
        Regex("<title[^>]*>(.*?)</title>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .find(html)?.groupValues?.getOrNull(1)?.let(::decodeHtmlEntities)?.trim()?.takeIf { it.isNotBlank() }

    /** Reads a top-level string field from the first JSON-LD block that has it. */
    private fun jsonLdString(html: String, key: String): String? {
        val blocks = Regex(
            "<script[^>]+type=\"application/ld\\+json\"[^>]*>(.*?)</script>",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        ).findAll(html)
        for (block in blocks) {
            val text = block.groupValues[1].trim()
            val obj = runCatching { JSONObject(text) }.getOrNull()
                ?: runCatching { JSONArray(text).optJSONObject(0) }.getOrNull()
                ?: continue
            val value = obj.optString(key).trim()
            if (value.isNotBlank()) return decodeHtmlEntities(value)
        }
        return null
    }

    /**
     * Extracts the balanced JavaScript object literal that starts right after [anchor], with
     * string-aware brace counting. Returns null when no complete object is found.
     */
    internal fun extractJsObjectAfter(html: String, anchor: Regex): String? {
        val match = anchor.find(html) ?: return null
        val open = html.indexOf('{', match.range.last + 1).takeIf { it >= 0 } ?: return null
        var depth = 0
        var quote: Char? = null
        var escaped = false
        for (i in open until minOf(html.length, open + 400_000)) {
            val c = html[i]
            if (escaped) {
                escaped = false
                continue
            }
            if (quote != null) {
                when (c) {
                    '\\' -> escaped = true
                    quote -> quote = null
                }
                continue
            }
            when (c) {
                '"', '\'' -> quote = c
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return html.substring(open, i + 1)
                }
            }
        }
        return null
    }

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

    /** Exact host or a subdomain of [domain]; "box.com" must never match "x.com". */
    private fun hostMatches(host: String, domain: String): Boolean =
        host == domain || host.endsWith(".$domain")

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
