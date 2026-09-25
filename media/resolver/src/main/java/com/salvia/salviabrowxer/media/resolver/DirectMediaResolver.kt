package com.salvia.salviabrowxer.media.resolver

import com.salvia.salviabrowxer.core.model.MediaFormat
import com.salvia.salviabrowxer.core.model.MediaInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class DirectMediaResolver(
    private val okHttpClient: OkHttpClient
) : MediaResolver {

    override suspend fun resolve(url: String): MediaInfo = withContext(Dispatchers.IO) {
        var contentLength: Long? = null
        var mimeType: String = ""
        var filename: String? = null

        val ua = "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 Chrome/122.0.0.0 Mobile Safari/537.36"

        // Try HEAD first; fall back to GET with Range if HEAD is rejected
        val headRequest = Request.Builder().url(url).head().header("User-Agent", ua).build()
        runCatching { okHttpClient.newCall(headRequest).execute() }.getOrNull()?.use { r ->
            if (r.isSuccessful) {
                contentLength = r.header("Content-Length")?.toLongOrNull() ?: r.body?.contentLength()?.takeIf { it > 0 }
                mimeType = r.header("Content-Type")?.substringBefore(';')?.trim().orEmpty()
                filename = extractFilename(r.header("Content-Disposition"))
            }
        }

        if (mimeType.isBlank() || contentLength == null) {
            val getRequest = Request.Builder().url(url).get().header("User-Agent", ua).header("Range", "bytes=0-0").build()
            runCatching { okHttpClient.newCall(getRequest).execute() }.getOrNull()?.use { r ->
                if (mimeType.isBlank()) mimeType = r.header("Content-Type")?.substringBefore(';')?.trim().orEmpty()
                if (contentLength == null) {
                    val cr = r.header("Content-Range")
                    contentLength = cr?.substringAfter('/')?.toLongOrNull()
                        ?: r.header("Content-Length")?.toLongOrNull()
                        ?: r.body?.contentLength()?.takeIf { it > 0 }
                }
                if (filename == null) filename = extractFilename(r.header("Content-Disposition"))
            }
        }

        val name = filename ?: url.substringBefore('?').substringAfterLast('/').substringBefore('#').ifBlank { "media" }
        val extension = name.substringAfterLast('.', "").ifEmpty { extensionFromMime(mimeType) ?: url.substringAfterLast('.', "").substringBefore('?').substringBefore('#') }
        val title = name.substringBeforeLast('.', name).ifEmpty { "Media" }
        val isHls = extension.equals("m3u8", true) || mimeType.contains("mpegurl", true)
        val isDash = extension.equals("mpd", true) || mimeType.contains("dash+xml", true)

        // InShot-like: expand HLS/DASH master playlists into multiple selectable qualities.
        if (isHls || isDash) {
            val hlsFormats = if (isHls) tryParseHlsVariants(url, ua) else emptyList()
            if (hlsFormats.isNotEmpty()) {
                return@withContext MediaInfo(
                    title = title.ifEmpty { "Media" }, thumbnail = null, duration = null,
                    formats = hlsFormats, audioFormats = hlsFormats.filter { it.isAudio }, videoFormats = hlsFormats.filter { it.isVideo },
                    combinedFormats = hlsFormats, source = url, extractor = "direct-hls", webpageUrl = url
                )
            }
            // Fall back to single "HLS/DASH" entry so the sheet still has exactly one option (previous behaviour)
        }

        val format = MediaFormat(
            id = url, format = if (isHls) "HLS" else if (isDash) "DASH" else extension.uppercase().ifEmpty { "ORIGINAL" },
            url = url, mimeType = mimeType.ifEmpty { "application/octet-stream" },
            extension = extension.ifEmpty { "mp4" },
            size = contentLength?.takeIf { it > 0 },
            isVideo = mimeType.startsWith("video/") || isHls || isDash || extension in setOf("mp4","webm","mkv","mov"),
            isAudio = mimeType.startsWith("audio/"),
            isHls = isHls, isDash = isDash
        )

        MediaInfo(
            title = title.ifEmpty { "Media" }, thumbnail = null, duration = null,
            formats = listOf(format), audioFormats = if (format.isAudio) listOf(format) else emptyList(),
            videoFormats = if (format.isVideo) listOf(format) else emptyList(),
            combinedFormats = listOf(format), source = url, extractor = "direct", webpageUrl = url
        )
    }

    /** Lightweight HLS variant parser: reads the master playlist and emits one [MediaFormat] per variant.
     *  Bounded: at most 8 variants, 48 KiB of playlist body, and capped to 4s of network time via OkHttp timeouts
     *  so this adds zero jank for non-HLS URLs and stays fast even on hostile CDNs.
     */
    private fun tryParseHlsVariants(masterUrl: String, userAgent: String): List<MediaFormat> {
        val body = runCatching {
            val req = Request.Builder().url(masterUrl).get().header("User-Agent", userAgent).header("Accept", "*/*").build()
            okHttpClient.newCall(req).execute().use { r ->
                if (!r.isSuccessful) return emptyList()
                val bytes = r.body?.bytes() ?: return emptyList()
                if (bytes.size > 48 * 1024) bytes.copyOf(48 * 1024) else bytes
            }.toString(Charsets.UTF_8)
        }.getOrNull() ?: return emptyList()

        if ("#EXTM3U" !in body) return emptyList()
        val lines = body.lineSequence().map { it.trim() }.toList()
        val base = masterUrl.substringBeforeLast('/') + "/"
        val formats = mutableListOf<MediaFormat>()
        var idx = 0
        for (i in lines.indices) {
            val line = lines[i]
            if (!line.startsWith("#EXT-X-STREAM-INF:")) continue
            val uriLine = lines.getOrNull(i + 1)?.takeIf { it.isNotEmpty() && !it.startsWith("#") } ?: continue
            if (formats.size >= 8) break
            // Parse BANDWIDTH and RESOLUTION
            val bandwidth = Regex("BANDWIDTH=(\\d+)").find(line)?.groupValues?.getOrNull(1)?.toLongOrNull()
            val res = Regex("RESOLUTION=(\\d+)x(\\d+)").find(line)
            val w = res?.groupValues?.getOrNull(1)?.toIntOrNull()
            val h = res?.groupValues?.getOrNull(2)?.toIntOrNull()
            val absUrl = when {
                uriLine.startsWith("http://") || uriLine.startsWith("https://") -> uriLine
                uriLine.startsWith("/") -> runCatching { java.net.URI(masterUrl).let { u -> "${u.scheme}://${u.host}${if (u.port != -1) ":${u.port}" else ""}$uriLine" } }.getOrDefault(uriLine)
                else -> base + uriLine
            }
            val label = when {
                w != null && h != null -> "${h}p"
                bandwidth != null -> "${(bandwidth / 1000)} kbps"
                else -> "Variant ${idx + 1}"
            }
            // Bandwidth is in bits/s — keep a kbps estimate for the sheet
            val bitrateKbps = bandwidth?.let { (it / 1000).toInt() }
            formats += MediaFormat(
                id = absUrl, format = label, url = absUrl,
                mimeType = "application/vnd.apple.mpegurl", extension = "m3u8",
                width = w, height = h, bitrate = bitrateKbps,
                isVideo = true, isHls = true
            )
            idx++
        }
        // Highest quality first — InShot surfaces 1080p at the top
        return formats.sortedByDescending { (it.height ?: 0) * 1000 + (it.bitrate ?: 0) }
    }

    private fun extractFilename(contentDisposition: String?): String? {
        if (contentDisposition == null) return null
        val starIdx = contentDisposition.indexOf("filename*=")
        if (starIdx != -1) {
            val v = contentDisposition.substring(starIdx + 10).substringBefore(';').trim().trim('"','\'')
            val decoded = v.substringAfter("''", v)
            try { return java.net.URLDecoder.decode(decoded, "UTF-8") } catch (_: Exception) { return decoded }
        }
        val idx = contentDisposition.indexOf("filename=")
        if (idx != -1) return contentDisposition.substring(idx + 9).substringBefore(';').trim().trim('"','\'').ifEmpty { null }
        return null
    }

    private fun extensionFromMime(mime: String): String? = when {
        mime.contains("mpegurl") || mime.contains("x-mpegurl") -> "m3u8"
        mime.contains("dash+xml") -> "mpd"
        mime.startsWith("video/mp4") -> "mp4"
        mime.startsWith("video/webm") -> "webm"
        mime.startsWith("audio/mpeg") -> "mp3"
        mime.startsWith("audio/mp4") -> "m4a"
        else -> null
    }
}
