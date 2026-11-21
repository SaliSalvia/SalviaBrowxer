package com.example.engine

import android.net.Uri
import android.util.Xml
import android.webkit.CookieManager
import com.example.ui.DetectedMedia
import com.example.ui.MediaQualityOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.util.concurrent.TimeUnit

/**
 * Commercial-grade InShot-style Media Detection Engine:
 * - Intercepts network streams (.m3u8, .mpd, .mp4, .webm, .m4a, .mp3)
 * - Hooks JS fetch(), XMLHttpRequest, JSON-LD VideoObject, OpenGraph, and HTML5 <video>/<audio>
 *   to solve MSE `blob:` players seamlessly
 * - Parses HLS master playlists (including separate #EXT-X-MEDIA audio tracks) & MPEG-DASH (.mpd)
 * - Strictly excludes ad networks, VAST/VMAP pre-rolls, <=12s ad bumper clips, and .ts/.m4s chunks
 * - Prioritizes the primary visible video of the page ("ویدیو اصلی صفحه") at index 0
 */
class MediaSnifferEngine {

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    companion object {
        private const val MIN_VIDEO_DURATION_SECONDS = 12.0
        private const val MIN_VIDEO_SIZE_BYTES = 850_000L
        private const val MIN_AUDIO_SIZE_BYTES = 120_000L

        private val videoExtensions = listOf(".mp4", ".m4v", ".webm", ".mov", ".m3u8", ".mpd")
        private val audioExtensions = listOf(".mp3", ".m4a", ".aac", ".ogg", ".opus")
        private val chunkSegmentExtensions = listOf(".ts", ".m4s", ".cmfv", ".cmfa")
    }

    fun isCandidateMediaUrl(url: String): Boolean {
        if (url.isBlank() || url.startsWith("blob:") || url.startsWith("data:")) return false
        if (AdBlockerEngine.isAdOrPreRollMediaUrl(url)) return false

        val path = runCatching { Uri.parse(url).path?.lowercase().orEmpty() }.getOrDefault("")
        if (chunkSegmentExtensions.any { path.endsWith(it) }) return false

        val lower = url.lowercase()
        val hasVideoExt = videoExtensions.any { path.endsWith(it) || lower.contains("$it?") }
        val hasAudioExt = audioExtensions.any { path.endsWith(it) || lower.contains("$it?") }
        val hasStreamingHint = lower.contains("mime=video") ||
            lower.contains("mime%3dvideo") ||
            lower.contains("type=video") ||
            lower.contains("playlist.m3u8") ||
            lower.contains("master.m3u8") ||
            lower.contains("manifest.mpd") ||
            lower.contains(".ism/manifest")

        return hasVideoExt || hasAudioExt || hasStreamingHint
    }

    suspend fun probeNetworkMedia(
        mediaUrl: String,
        pageUrl: String,
        pageTitle: String,
        userAgent: String,
    ): DetectedMedia? = withContext(Dispatchers.IO) {
        if (!isCandidateMediaUrl(mediaUrl)) return@withContext null

        val lower = mediaUrl.lowercase()
        val isHls = lower.contains(".m3u8")
        val isDash = lower.contains(".mpd")
        val isAudio = audioExtensions.any { lower.contains(it) }
        val cleanTitle = sanitizeTitle(pageTitle, mediaUrl)
        val cookies = runCatching { CookieManager.getInstance().getCookie(mediaUrl) }.getOrNull().orEmpty()

        if (isHls) {
            val qualities = parseHlsPlaylist(mediaUrl, pageUrl, userAgent, cookies)
            if (qualities.isEmpty()) {
                return@withContext DetectedMedia(
                    id = normalizeMediaKey(mediaUrl),
                    title = cleanTitle,
                    sourcePageUrl = pageUrl,
                    primaryUrl = mediaUrl,
                    priorityScore = 88,
                    isMainVideo = true,
                    qualities = listOf(
                        MediaQualityOption(
                            label = "Auto Quality (HLS -> MP4)",
                            resolutionHeight = 720,
                            url = mediaUrl,
                            format = "HLS",
                            mimeType = "video/mp4",
                            isHls = true,
                        ),
                    ),
                )
            }
            val topRes = qualities.maxOfOrNull { it.resolutionHeight } ?: 720
            return@withContext DetectedMedia(
                id = normalizeMediaKey(mediaUrl),
                title = cleanTitle,
                sourcePageUrl = pageUrl,
                primaryUrl = mediaUrl,
                height = topRes,
                priorityScore = 100 + (topRes / 100),
                isMainVideo = true,
                qualities = qualities,
            )
        }

        if (isDash) {
            val qualities = parseDashManifest(mediaUrl, pageUrl, userAgent, cookies)
            if (qualities.isNotEmpty()) {
                val topRes = qualities.maxOfOrNull { it.resolutionHeight } ?: 720
                return@withContext DetectedMedia(
                    id = normalizeMediaKey(mediaUrl),
                    title = cleanTitle,
                    sourcePageUrl = pageUrl,
                    primaryUrl = mediaUrl,
                    height = topRes,
                    priorityScore = 98 + (topRes / 100),
                    isMainVideo = true,
                    qualities = qualities,
                )
            }
        }

        val probe = headProbe(mediaUrl, pageUrl, userAgent, cookies)
        val contentLength = probe.first
        val contentType = probe.second

        if (!isAudio && contentLength > 0L && contentLength < MIN_VIDEO_SIZE_BYTES) {
            return@withContext null
        }
        if (isAudio && contentLength > 0L && contentLength < MIN_AUDIO_SIZE_BYTES) {
            return@withContext null
        }

        val format = inferFormat(mediaUrl, contentType)
        val guessedHeight = inferHeightFromUrl(mediaUrl)
        val qualityLabel = when {
            isAudio -> "Audio ($format)"
            guessedHeight >= 2160 -> "4K Ultra HD ($format)"
            guessedHeight >= 1080 -> "1080p Full HD ($format)"
            guessedHeight >= 720 -> "720p HD ($format)"
            guessedHeight >= 480 -> "480p SD ($format)"
            guessedHeight > 0 -> "${guessedHeight}p ($format)"
            else -> "Original ($format)"
        }

        DetectedMedia(
            id = normalizeMediaKey(mediaUrl),
            title = cleanTitle,
            sourcePageUrl = pageUrl,
            primaryUrl = mediaUrl,
            height = guessedHeight,
            priorityScore = if (isAudio) 40 else 85 + (guessedHeight / 100),
            isMainVideo = !isAudio,
            isAudioOnly = isAudio,
            qualities = listOf(
                MediaQualityOption(
                    label = qualityLabel,
                    resolutionHeight = guessedHeight,
                    url = mediaUrl,
                    format = format,
                    mimeType = contentType.ifBlank { if (isAudio) "audio/mpeg" else "video/mp4" },
                    sizeBytes = contentLength,
                    isHls = false,
                ),
            ),
        )
    }

    fun parseDomMediaPayload(
        jsonPayload: String,
        pageUrl: String,
        pageTitle: String,
    ): List<DetectedMedia> {
        val array = runCatching { JSONArray(jsonPayload) }.getOrNull() ?: return emptyList()
        val results = mutableListOf<DetectedMedia>()

        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val src = obj.optString("src", "").trim()
            if (src.isBlank() || src.startsWith("blob:") || src.startsWith("data:")) continue
            if (AdBlockerEngine.isAdOrPreRollMediaUrl(src)) continue

            val duration = obj.optDouble("duration", 0.0)
            val width = obj.optInt("width", 0)
            val height = obj.optInt("height", 0)
            val domArea = obj.optLong("area", 0L)
            val isVisible = obj.optBoolean("visible", false)
            val isAudio = obj.optBoolean("isAudio", false)
            val isAdClass = obj.optBoolean("isAdElement", false)
            val poster = obj.optString("poster", "")
            val titleHint = obj.optString("title", "").ifBlank { pageTitle }

            if (isAdClass) continue
            if (!isAudio && duration > 0.0 && duration < MIN_VIDEO_DURATION_SECONDS) continue
            if (!isAudio && domArea in 1L..14400L && !isVisible) continue

            val qualityList = mutableListOf<MediaQualityOption>()
            val sourcesArr = obj.optJSONArray("sources")
            if (sourcesArr != null) {
                for (j in 0 until sourcesArr.length()) {
                    val sObj = sourcesArr.optJSONObject(j) ?: continue
                    val sUrl = sObj.optString("url", "").trim()
                    if (sUrl.isBlank() || sUrl.startsWith("blob:") || AdBlockerEngine.isAdOrPreRollMediaUrl(sUrl)) {
                        continue
                    }
                    val res = sObj.optInt("height", height)
                    val sLabel = sObj.optString("label", "").ifBlank {
                        if (res > 0) "${res}p" else "Original"
                    }
                    val isHls = sUrl.lowercase().contains(".m3u8")
                    val isDash = sUrl.lowercase().contains(".mpd")
                    val fmt = when {
                        isHls -> "HLS"
                        isDash -> "DASH"
                        else -> inferFormat(sUrl, "")
                    }
                    qualityList.add(
                        MediaQualityOption(
                            label = "$sLabel ($fmt)",
                            resolutionHeight = res,
                            url = sUrl,
                            format = fmt,
                            mimeType = if (isHls || isDash) "video/mp4" else "video/mp4",
                            isHls = isHls,
                            isDash = isDash,
                        ),
                    )
                }
            }

            if (qualityList.isEmpty()) {
                val isHls = src.lowercase().contains(".m3u8")
                val isDash = src.lowercase().contains(".mpd")
                val fmt = when {
                    isHls -> "HLS"
                    isDash -> "DASH"
                    else -> inferFormat(src, "")
                }
                val h = if (height > 0) height else inferHeightFromUrl(src)
                val qLabel = when {
                    isAudio -> "Audio ($fmt)"
                    h >= 1080 -> "1080p Full HD ($fmt)"
                    h >= 720 -> "720p HD ($fmt)"
                    h > 0 -> "${h}p ($fmt)"
                    else -> "Original ($fmt)"
                }
                qualityList.add(
                    MediaQualityOption(
                        label = qLabel,
                        resolutionHeight = h,
                        url = src,
                        format = fmt,
                        mimeType = if (isAudio) "audio/mpeg" else "video/mp4",
                        isHls = isHls,
                        isDash = isDash,
                    ),
                )
            }

            var score = 50
            if (isVisible) score += 60
            if (domArea > 40_000L) score += 50
            if (duration >= 60.0) score += 40 else if (duration >= 20.0) score += 20
            if (height >= 720) score += 25
            if (isAudio) score -= 40

            results.add(
                DetectedMedia(
                    id = normalizeMediaKey(src),
                    title = sanitizeTitle(titleHint, src),
                    sourcePageUrl = pageUrl,
                    primaryUrl = src,
                    thumbnailUrl = poster,
                    durationSeconds = duration,
                    width = width,
                    height = height,
                    domAreaPx = domArea,
                    priorityScore = score,
                    isAudioOnly = isAudio,
                    qualities = qualityList.distinctBy { it.url }.sortedByDescending { it.resolutionHeight },
                ),
            )
        }
        return prioritizeMedia(results)
    }

    fun mergeAndPrioritize(
        existing: List<DetectedMedia>,
        incoming: DetectedMedia,
    ): List<DetectedMedia> {
        val map = LinkedHashMap<String, DetectedMedia>()
        existing.forEach { map[it.id] = it }

        val current = map[incoming.id]
        if (current == null) {
            map[incoming.id] = incoming
        } else {
            val mergedQualities = (current.qualities + incoming.qualities)
                .distinctBy { "${it.url}|${it.audioUrl}" }
                .sortedByDescending { it.resolutionHeight }
            map[incoming.id] = current.copy(
                title = if (incoming.title.length > current.title.length) incoming.title else current.title,
                thumbnailUrl = current.thumbnailUrl.ifBlank { incoming.thumbnailUrl },
                durationSeconds = maxOf(current.durationSeconds, incoming.durationSeconds),
                width = maxOf(current.width, incoming.width),
                height = maxOf(current.height, incoming.height),
                domAreaPx = maxOf(current.domAreaPx, incoming.domAreaPx),
                priorityScore = maxOf(current.priorityScore, incoming.priorityScore),
                qualities = mergedQualities,
            )
        }
        return prioritizeMedia(map.values.toList())
    }

    fun prioritizeMedia(items: List<DetectedMedia>): List<DetectedMedia> {
        val sorted = items.sortedWith(
            compareByDescending<DetectedMedia> { !it.isAudioOnly }
                .thenByDescending { it.priorityScore }
                .thenByDescending { it.domAreaPx }
                .thenByDescending { it.durationSeconds }
                .thenByDescending { it.height },
        )
        return sorted.mapIndexed { index, item ->
            item.copy(isMainVideo = index == 0 && !item.isAudioOnly)
        }
    }

    private fun parseHlsPlaylist(
        masterUrl: String,
        referer: String,
        userAgent: String,
        cookies: String,
    ): List<MediaQualityOption> {
        return runCatching {
            val reqBuilder = Request.Builder()
                .url(masterUrl)
                .header("User-Agent", userAgent)
                .header("Referer", referer)
            if (cookies.isNotBlank()) reqBuilder.header("Cookie", cookies)

            val body = client.newCall(reqBuilder.build()).execute().use { resp ->
                if (!resp.isSuccessful) return emptyList()
                resp.body?.string().orEmpty()
            }
            if (!body.contains("#EXT-X-STREAM-INF")) return emptyList()

            val baseUri = Uri.parse(masterUrl)
            val lines = body.lines().map { it.trim() }

            // Parse separate audio tracks (#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="...",URI="...")
            val audioGroups = mutableMapOf<String, String>()
            for (line in lines) {
                if (line.startsWith("#EXT-X-MEDIA:") && line.contains("TYPE=AUDIO", ignoreCase = true)) {
                    val groupId = Regex("GROUP-ID=\"([^\"]+)\"").find(line)?.groupValues?.getOrNull(1)
                    val uri = Regex("URI=\"([^\"]+)\"").find(line)?.groupValues?.getOrNull(1)
                    if (!groupId.isNullOrBlank() && !uri.isNullOrBlank()) {
                        audioGroups.putIfAbsent(groupId, resolveRelativeUrl(baseUri, uri))
                    }
                }
            }

            val variants = mutableListOf<MediaQualityOption>()
            var i = 0
            while (i < lines.size) {
                val line = lines[i]
                if (line.startsWith("#EXT-X-STREAM-INF:")) {
                    val attrs = line.removePrefix("#EXT-X-STREAM-INF:")
                    val resMatch = Regex("RESOLUTION=\\d+x(\\d+)").find(attrs)
                    val height = resMatch?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
                    val bwMatch = Regex("BANDWIDTH=(\\d+)").find(attrs)
                    val bandwidth = bwMatch?.groupValues?.getOrNull(1)?.toLongOrNull() ?: 0L
                    val audioGroup = Regex("AUDIO=\"([^\"]+)\"").find(attrs)?.groupValues?.getOrNull(1)
                    val pairedAudioUrl = audioGroup?.let { audioGroups[it] }.orEmpty()

                    var j = i + 1
                    while (j < lines.size && (lines[j].isBlank() || lines[j].startsWith("#"))) {
                        j++
                    }
                    if (j < lines.size) {
                        val variantUrl = resolveRelativeUrl(baseUri, lines[j])
                        val label = when {
                            height >= 2160 -> "2160p 4K (MP4)"
                            height >= 1440 -> "1440p 2K (MP4)"
                            height >= 1080 -> "1080p Full HD (MP4)"
                            height >= 720 -> "720p HD (MP4)"
                            height >= 480 -> "480p SD (MP4)"
                            height > 0 -> "${height}p (MP4)"
                            bandwidth > 0 -> "${bandwidth / 1000} kbps (HLS)"
                            else -> "HLS Stream (MP4)"
                        }
                        variants.add(
                            MediaQualityOption(
                                label = label,
                                resolutionHeight = height,
                                url = variantUrl,
                                audioUrl = pairedAudioUrl,
                                format = "HLS",
                                mimeType = "video/mp4",
                                bandwidthBps = bandwidth,
                                isHls = true,
                            ),
                        )
                    }
                    i = j
                } else {
                    i++
                }
            }

            val sorted = variants.distinctBy { "${it.resolutionHeight}_${it.url}" }
                .sortedByDescending { if (it.resolutionHeight > 0) it.resolutionHeight.toLong() else it.bandwidthBps }
                .toMutableList()

            val firstAudio = audioGroups.values.firstOrNull()
            if (!firstAudio.isNullOrBlank()) {
                sorted.add(
                    MediaQualityOption(
                        label = "Audio Only (M4A)",
                        resolutionHeight = 0,
                        url = firstAudio,
                        format = "M4A",
                        mimeType = "audio/mp4",
                        isHls = true,
                    ),
                )
            }
            sorted
        }.getOrDefault(emptyList())
    }

    /**
     * Parses MPEG-DASH (.mpd) manifests extracting BaseURL video & audio representations.
     */
    private fun parseDashManifest(
        mpdUrl: String,
        referer: String,
        userAgent: String,
        cookies: String,
    ): List<MediaQualityOption> {
        return runCatching {
            val reqBuilder = Request.Builder()
                .url(mpdUrl)
                .header("User-Agent", userAgent)
                .header("Referer", referer)
            if (cookies.isNotBlank()) reqBuilder.header("Cookie", cookies)

            val xml = client.newCall(reqBuilder.build()).execute().use { resp ->
                if (!resp.isSuccessful) return emptyList()
                resp.body?.string().orEmpty()
            }
            if (!xml.contains("<MPD", ignoreCase = true)) return emptyList()

            val baseUri = Uri.parse(mpdUrl)
            val parser = Xml.newPullParser()
            parser.setInput(StringReader(xml))

            data class DashRep(
                val mimeType: String,
                val height: Int,
                val bandwidth: Long,
                var baseUrl: String = "",
            )

            var currentAdaptMime = ""
            var currentRep: DashRep? = null
            var mpdBaseUrl = ""
            val videoReps = mutableListOf<DashRep>()
            val audioReps = mutableListOf<DashRep>()

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when (parser.name) {
                            "AdaptationSet" -> {
                                currentAdaptMime = parser.getAttributeValue(null, "mimeType").orEmpty()
                            }
                            "Representation" -> {
                                val repMime = parser.getAttributeValue(null, "mimeType").orEmpty().ifBlank { currentAdaptMime }
                                val h = parser.getAttributeValue(null, "height")?.toIntOrNull() ?: 0
                                val bw = parser.getAttributeValue(null, "bandwidth")?.toLongOrNull() ?: 0L
                                currentRep = DashRep(repMime, h, bw)
                            }
                            "BaseURL" -> {
                                val text = parser.nextText().trim()
                                if (text.isNotBlank()) {
                                    val resolved = resolveRelativeUrl(baseUri, text)
                                    val rep = currentRep
                                    if (rep != null) {
                                        rep.baseUrl = resolved
                                    } else if (mpdBaseUrl.isBlank()) {
                                        mpdBaseUrl = resolved
                                    }
                                }
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (parser.name == "Representation") {
                            currentRep?.let { rep ->
                                if (rep.baseUrl.isNotBlank()) {
                                    if (rep.mimeType.contains("audio", ignoreCase = true)) {
                                        audioReps.add(rep)
                                    } else if (rep.mimeType.contains("video", ignoreCase = true) || rep.height > 0) {
                                        videoReps.add(rep)
                                    }
                                }
                            }
                            currentRep = null
                        } else if (parser.name == "AdaptationSet") {
                            currentAdaptMime = ""
                        }
                    }
                }
                eventType = parser.next()
            }

            val bestAudioUrl = audioReps.maxByOrNull { it.bandwidth }?.baseUrl.orEmpty()
            val results = videoReps
                .sortedByDescending { if (it.height > 0) it.height.toLong() else it.bandwidth }
                .distinctBy { "${it.height}_${it.baseUrl}" }
                .map { rep ->
                    val label = when {
                        rep.height >= 2160 -> "2160p 4K (DASH)"
                        rep.height >= 1080 -> "1080p Full HD (DASH)"
                        rep.height >= 720 -> "720p HD (DASH)"
                        rep.height >= 480 -> "480p SD (DASH)"
                        rep.height > 0 -> "${rep.height}p (DASH)"
                        else -> "${rep.bandwidth / 1000} kbps (DASH)"
                    }
                    MediaQualityOption(
                        label = label,
                        resolutionHeight = rep.height,
                        url = rep.baseUrl,
                        audioUrl = bestAudioUrl,
                        format = "DASH",
                        mimeType = "video/mp4",
                        bandwidthBps = rep.bandwidth,
                        isDash = true,
                    )
                }.toMutableList()

            if (bestAudioUrl.isNotBlank()) {
                results.add(
                    MediaQualityOption(
                        label = "Audio Only (M4A)",
                        resolutionHeight = 0,
                        url = bestAudioUrl,
                        format = "M4A",
                        mimeType = "audio/mp4",
                    ),
                )
            }
            results
        }.getOrDefault(emptyList())
    }

    private fun headProbe(
        url: String,
        referer: String,
        userAgent: String,
        cookies: String,
    ): Pair<Long, String> {
        return runCatching {
            val reqBuilder = Request.Builder()
                .url(url)
                .head()
                .header("User-Agent", userAgent)
                .header("Referer", referer)
            if (cookies.isNotBlank()) reqBuilder.header("Cookie", cookies)

            client.newCall(reqBuilder.build()).execute().use { resp ->
                val len = resp.header("Content-Length")?.toLongOrNull() ?: -1L
                val type = resp.header("Content-Type").orEmpty().substringBefore(";").trim()
                len to type
            }
        }.getOrDefault(-1L to "")
    }

    private fun resolveRelativeUrl(base: Uri, relative: String): String {
        if (relative.startsWith("http://") || relative.startsWith("https://")) return relative
        return runCatching {
            java.net.URI(base.toString()).resolve(relative).toString()
        }.getOrDefault(relative)
    }

    private fun normalizeMediaKey(url: String): String {
        return runCatching {
            val uri = Uri.parse(url)
            "${uri.host.orEmpty()}${uri.path.orEmpty()}"
        }.getOrDefault(url)
    }

    private fun inferFormat(url: String, contentType: String): String {
        val lower = url.lowercase()
        return when {
            lower.contains(".m3u8") || contentType.contains("mpegurl") -> "HLS"
            lower.contains(".mpd") || contentType.contains("dash+xml") -> "DASH"
            lower.contains(".mp4") || contentType.contains("mp4") -> "MP4"
            lower.contains(".webm") || contentType.contains("webm") -> "WEBM"
            lower.contains(".m4a") -> "M4A"
            lower.contains(".mp3") || contentType.contains("mpeg") -> "MP3"
            lower.contains(".aac") -> "AAC"
            else -> "MP4"
        }
    }

    private fun inferHeightFromUrl(url: String): Int {
        val match = Regex("(2160|1440|1080|720|480|360|240)p?").find(url.lowercase())
        return match?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
    }

    private fun sanitizeTitle(pageTitle: String, fallbackUrl: String): String {
        val cleaned = pageTitle.trim()
        if (cleaned.isNotBlank() && cleaned != "about:blank") return cleaned.take(72)
        val segment = runCatching { Uri.parse(fallbackUrl).lastPathSegment }.getOrNull().orEmpty()
        return segment.ifBlank { "Page Video" }.take(60)
    }

    /**
     * Early JS Hook injected at document_start to capture hidden stream/video URLs inside
     * fetch() and XMLHttpRequest JSON responses (solving blob: MediaSource video players!).
     */
    fun earlyMediaHookScript(): String = """
        (function() {
            if (window.__salviaEarlyMediaHook) return;
            window.__salviaEarlyMediaHook = true;

            var urlRegex = /https?:\/\/[^\s"'<>\\]+\.(m3u8|mpd|mp4|m4v|webm|m4a)(\?[^\s"'<>\\]*)?/gi;
            var adFilter = /(doubleclick|googlesyndication|imasdk|vast|vmap|preroll|midroll|bumper|tapsell|sabavision)/i;

            function reportTextUrls(text) {
                try {
                    if (!text || text.length > 500000 || !window.NavaMediaBridge) return;
                    var matches = text.match(urlRegex);
                    if (matches && matches.length > 0) {
                        var seen = {};
                        matches.slice(0, 12).forEach(function(u) {
                            var clean = u.replace(/\\u002F/g, '/').replace(/\\\//g, '/');
                            if (!seen[clean] && !adFilter.test(clean)) {
                                seen[clean] = true;
                                window.NavaMediaBridge.onHookedUrl(clean, document.title || '');
                            }
                        });
                    }
                } catch(e) {}
            }

            if (window.fetch) {
                var origFetch = window.fetch;
                window.fetch = function() {
                    var reqUrl = (typeof arguments[0] === 'string') ? arguments[0] : (arguments[0] && arguments[0].url) || '';
                    if (reqUrl && /\.(m3u8|mpd|mp4|webm|m4a)/i.test(reqUrl) && !adFilter.test(reqUrl) && window.NavaMediaBridge) {
                        window.NavaMediaBridge.onHookedUrl(reqUrl, document.title || '');
                    }
                    return origFetch.apply(this, arguments).then(function(resp) {
                        try {
                            var ct = resp.headers && resp.headers.get ? (resp.headers.get('content-type') || '') : '';
                            if (ct.includes('json') || ct.includes('mpegurl') || ct.includes('dash')) {
                                resp.clone().text().then(reportTextUrls).catch(function(){});
                            }
                        } catch(e) {}
                        return resp;
                    });
                };
            }

            var origOpen = XMLHttpRequest.prototype.open;
            var origSend = XMLHttpRequest.prototype.send;
            XMLHttpRequest.prototype.open = function(method, url) {
                this.__salviaUrl = url;
                return origOpen.apply(this, arguments);
            };
            XMLHttpRequest.prototype.send = function() {
                var xhr = this;
                xhr.addEventListener('load', function() {
                    try {
                        if (xhr.__salviaUrl && /\.(m3u8|mpd|mp4|webm|m4a)/i.test(xhr.__salviaUrl) && !adFilter.test(xhr.__salviaUrl) && window.NavaMediaBridge) {
                            window.NavaMediaBridge.onHookedUrl(xhr.__salviaUrl, document.title || '');
                        }
                        if (typeof xhr.responseText === 'string') {
                            reportTextUrls(xhr.responseText);
                        }
                    } catch(e) {}
                });
                return origSend.apply(this, arguments);
            };
        })();
    """.trimIndent()

    fun domSnifferScript(): String = """
        (function() {
            function scanMedia() {
                try {
                    if (document.hidden) return;
                    const list = [];
                    const adRegex = /(ad[-_]?break|preroll|midroll|vast|vmap|doubleclick|imasdk|sponsored|ad-showing)/i;
                    const pageTitle = document.title || '';

                    document.querySelectorAll('video, audio').forEach(el => {
                        const isAudio = el.tagName.toLowerCase() === 'audio';
                        const rect = el.getBoundingClientRect();
                        const area = Math.round(rect.width * rect.height);
                        const visible = rect.width >= 120 && rect.height >= 90 && el.offsetParent !== null;
                        const classText = (el.className || '') + ' ' + (el.id || '') + ' ' + ((el.parentElement && el.parentElement.className) || '');
                        const isAdEl = adRegex.test(classText);

                        const sources = [];
                        const dataSrc = el.getAttribute('data-src') || el.getAttribute('data-mp4') || el.getAttribute('data-hls') || '';
                        if (el.currentSrc) {
                            sources.push({ url: el.currentSrc, height: el.videoHeight || 0, label: '' });
                        }
                        if (el.src && el.src !== el.currentSrc) {
                            sources.push({ url: el.src, height: el.videoHeight || 0, label: '' });
                        }
                        if (dataSrc && dataSrc !== el.currentSrc && dataSrc !== el.src) {
                            sources.push({ url: dataSrc, height: el.videoHeight || 0, label: '' });
                        }
                        el.querySelectorAll('source').forEach(s => {
                            const sUrl = s.src || s.getAttribute('data-src') || '';
                            if (sUrl) {
                                const h = parseInt(s.getAttribute('res') || s.getAttribute('size') || '0', 10) || (el.videoHeight || 0);
                                const lbl = s.getAttribute('label') || '';
                                sources.push({ url: sUrl, height: h, label: lbl });
                            }
                        });

                        const primary = (sources[0] && sources[0].url) || '';
                        if (!primary) return;

                        list.push({
                            src: primary,
                            sources: sources,
                            duration: isFinite(el.duration) ? el.duration : 0,
                            width: el.videoWidth || Math.round(rect.width),
                            height: el.videoHeight || Math.round(rect.height),
                            area: area,
                            visible: visible,
                            isAudio: isAudio,
                            isAdElement: isAdEl,
                            poster: el.poster || '',
                            title: pageTitle
                        });
                    });

                    // JSON-LD VideoObject extraction
                    document.querySelectorAll('script[type="application/ld+json"]').forEach(sc => {
                        try {
                            const data = JSON.parse(sc.textContent || '{}');
                            const items = Array.isArray(data) ? data : [data];
                            items.forEach(item => {
                                if (item && item['@type'] === 'VideoObject' && item.contentUrl) {
                                    list.push({
                                        src: item.contentUrl,
                                        sources: [{ url: item.contentUrl, height: 720, label: 'VideoObject' }],
                                        duration: 0,
                                        width: 1280,
                                        height: 720,
                                        area: 220000,
                                        visible: true,
                                        isAudio: false,
                                        isAdElement: false,
                                        poster: item.thumbnailUrl || '',
                                        title: item.name || pageTitle
                                    });
                                }
                            });
                        } catch(e) {}
                    });

                    // OpenGraph / Twitter video fallback
                    const ogVideo = document.querySelector('meta[property="og:video"], meta[property="og:video:url"], meta[property="og:video:secure_url"], meta[name="twitter:player:stream"]');
                    const ogImage = document.querySelector('meta[property="og:image"]');
                    if (ogVideo && ogVideo.content && !list.some(i => i.src === ogVideo.content)) {
                        list.push({
                            src: ogVideo.content,
                            sources: [{ url: ogVideo.content, height: 720, label: 'OG Video' }],
                            duration: 0,
                            width: 1280,
                            height: 720,
                            area: 200000,
                            visible: true,
                            isAudio: false,
                            isAdElement: false,
                            poster: ogImage ? ogImage.content : '',
                            title: pageTitle
                        });
                    }

                    if (list.length > 0 && window.NavaMediaBridge) {
                        window.NavaMediaBridge.onMediaFound(JSON.stringify(list));
                    }
                } catch (err) {}
            }

            window.__salviaScanMediaNow = scanMedia;
            scanMedia();

            if (window.__navaMediaSnifferInstalled) return;
            window.__navaMediaSnifferInstalled = true;

            document.addEventListener('play', scanMedia, true);
            document.addEventListener('loadedmetadata', scanMedia, true);
            if (window.MutationObserver && document.documentElement) {
                var timer = null;
                var mo = new MutationObserver(function(mutations) {
                    for (var i = 0; i < mutations.length; i++) {
                        if (mutations[i].addedNodes && mutations[i].addedNodes.length > 0) {
                            clearTimeout(timer);
                            timer = setTimeout(scanMedia, 500);
                            break;
                        }
                    }
                });
                mo.observe(document.documentElement, { childList: true, subtree: true });
            }
            setInterval(scanMedia, 4500);
        })();
    """.trimIndent()
}
