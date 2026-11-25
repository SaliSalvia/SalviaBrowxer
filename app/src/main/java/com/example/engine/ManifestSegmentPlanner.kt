package com.example.engine

import java.net.URI

/** Conservative HLS media-playlist parser; DRM and malformed manifests fail closed. */
internal object ManifestSegmentPlanner {
    data class ByteRange(val length: Long, val offset: Long)
    data class HlsKey(val method: String, val uri: String = "", val ivHex: String = "")
    data class HlsSegment(val index: Int, val sequence: Long, val uri: String,
                          val range: ByteRange?, val key: HlsKey?)
    data class HlsPlan(val mediaPlaylistUrl: String, val initUri: String?, val initRange: ByteRange?,
                       val initKey: HlsKey?, val segments: List<HlsSegment>, val isEndList: Boolean)

    fun parseHls(url: String, text: String): HlsPlan {
        requireHttp(url)
        val lines = text.lineSequence().map(String::trim).toList()
        require(lines.firstOrNull() == "#EXTM3U") { "Invalid HLS playlist" }
        require(lines.none { it.startsWith("#EXT-X-STREAM-INF") }) { "Resolve HLS master playlist first" }
        require(lines.none { it.startsWith("#EXT-X-KEY:") && it.contains("SAMPLE-AES", true) }) {
            "SAMPLE-AES/DRM HLS is unsupported"
        }
        var sequence = lines.firstOrNull { it.startsWith("#EXT-X-MEDIA-SEQUENCE:") }
            ?.substringAfter(':')?.toLongOrNull() ?: 0L
        var key: HlsKey? = null
        var initUri: String? = null
        var initRange: ByteRange? = null
        var initKey: HlsKey? = null
        var pendingRange: String? = null
        var previousRangeUri = ""
        var nextRangeOffset = 0L
        val segments = mutableListOf<HlsSegment>()
        for (line in lines) {
            when {
                line.startsWith("#EXT-X-KEY:") -> {
                    val a = attributes(line.substringAfter(':'))
                    key = when (a["METHOD"]?.uppercase()) {
                        "NONE" -> null
                        "AES-128" -> {
                            require(a["KEYFORMAT"].isNullOrBlank() || a["KEYFORMAT"].equals("identity", true)) {
                                "Non-identity HLS key format is unsupported"
                            }
                            val resolved = resolve(url, a["URI"] ?: error("HLS AES-128 key URI missing"))
                            requireHttp(resolved)
                            HlsKey("AES-128", resolved, a["IV"].orEmpty().removePrefix("0x"))
                        }
                        else -> error("Unsupported HLS encryption method")
                    }
                }
                line.startsWith("#EXT-X-MAP:") -> {
                    val a = attributes(line.substringAfter(':'))
                    initUri = a["URI"]?.let { resolve(url, it) }
                    initRange = a["BYTERANGE"]?.let { parseRange(it, 0L) }
                    initKey = key
                }
                line.startsWith("#EXT-X-BYTERANGE:") -> pendingRange = line.substringAfter(':')
                line.isNotEmpty() && !line.startsWith("#") -> {
                    val segmentUrl = resolve(url, line)
                    requireHttp(segmentUrl)
                    val range = pendingRange?.let { raw ->
                        val parts = raw.split('@', limit = 2)
                        val length = parts[0].toLongOrNull() ?: error("Invalid HLS byte range")
                        val offset = if (parts.size == 2) parts[1].toLongOrNull()
                            ?: error("Invalid HLS byte-range offset")
                        else if (previousRangeUri == segmentUrl) nextRangeOffset else 0L
                        ByteRange(length, offset).also {
                            require(it.length > 0L && it.offset >= 0L)
                            previousRangeUri = segmentUrl
                            nextRangeOffset = it.offset + it.length
                        }
                    }
                    if (range == null) { previousRangeUri = ""; nextRangeOffset = 0L }
                    segments.add(HlsSegment(segments.size, sequence++, segmentUrl, range, key))
                    pendingRange = null
                }
            }
        }
        require(segments.isNotEmpty()) { "HLS playlist has no media segments" }
        return HlsPlan(url, initUri, initRange, initKey, segments, lines.any { it == "#EXT-X-ENDLIST" })
    }

    private fun attributes(s: String): Map<String, String> =
        Regex("([A-Z0-9-]+)=\"([^\"]*)\"|([A-Z0-9-]+)=([^,]*)", RegexOption.IGNORE_CASE)
            .findAll(s).associate { match ->
                match.groupValues[1].ifBlank { match.groupValues[3] } to
                    match.groupValues[2].ifBlank { match.groupValues[4] }.trim()
            }

    private fun parseRange(s: String, defaultOffset: Long): ByteRange {
        val parts = s.split('@', limit = 2)
        val length = parts[0].toLongOrNull() ?: error("Invalid byte range")
        val offset = if (parts.size == 2) parts[1].toLongOrNull() ?: error("Invalid range offset") else defaultOffset
        require(length > 0L && offset >= 0L)
        return ByteRange(length, offset)
    }

    private fun resolve(base: String, relative: String): String = URI(base).resolve(URI(relative)).normalize().toString()
    private fun requireHttp(url: String) {
        val uri = URI(url)
        require((uri.scheme.equals("http", true) || uri.scheme.equals("https", true)) && !uri.host.isNullOrBlank())
    }
}
