package com.salvia.salviabrowxer.media.resolver

import com.salvia.salviabrowxer.core.model.MediaFileTypes
import com.salvia.salviabrowxer.core.model.MediaFormat
import com.salvia.salviabrowxer.core.model.MediaInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

class DirectMediaResolver(
    private val okHttpClient: OkHttpClient
) : MediaResolver {

    override suspend fun resolve(url: String): MediaInfo = withContext(Dispatchers.IO) {
        val request = okhttp3.Request.Builder()
            .url(url)
            .head()
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            val contentLength = response.body?.contentLength() ?: 0L
            val mimeType = response.header("Content-Type")?.substringBefore(';')?.trim().orEmpty()
            val contentDisposition = response.header("Content-Disposition")

            val filename = fileNameFromContentDisposition(contentDisposition)
                ?: MediaFileTypes.extensionFromUrl(url)?.let { url.substringAfterLast('/') }
                ?: url.substringAfterLast('/')

            val extension = filename.substringAfterLast('.', "").lowercase()
            val title = filename.substringBeforeLast('.', "").ifBlank { "Media" }

            val format = MediaFormat(
                id = url,
                format = "Direct",
                url = url,
                mimeType = mimeType.ifBlank { MediaFileTypes.mimeTypeForExtension(extension) },
                extension = extension,
                size = contentLength,
                isVideo = mimeType.startsWith("video/") || extension in MediaFileTypes.VIDEO_EXTENSIONS,
                isAudio = mimeType.startsWith("audio/") || extension in MediaFileTypes.AUDIO_EXTENSIONS,
                isHls = extension in MediaFileTypes.PLAYLIST_EXTENSIONS || "mpegurl" in mimeType,
                isDash = extension == "mpd" || "dash+xml" in mimeType
            )

            MediaInfo(
                title = title,
                thumbnail = null,
                duration = null,
                formats = listOf(format),
                audioFormats = if (format.isAudio) listOf(format) else emptyList(),
                videoFormats = if (format.isVideo) listOf(format) else emptyList(),
                combinedFormats = listOf(format),
                source = url,
                extractor = "direct",
                webpageUrl = url
            )
        }
    }

    /**
     * Parses `Content-Disposition` without losing RFC 5987/UTF-8 names. Falls back to the regular
     * `filename=` value for servers that do not send the encoded variant.
     */
    private fun fileNameFromContentDisposition(header: String?): String? {
        if (header.isNullOrBlank()) return null

        val encoded = Regex("filename\\*\\s*=\\s*(?:UTF-8''|\"UTF-8'')([^\";]+)", RegexOption.IGNORE_CASE)
            .find(header)
            ?.groupValues
            ?.getOrNull(1)
        if (encoded != null) {
            return runCatching {
                URLDecoder.decode(encoded, StandardCharsets.UTF_8.name())
                    .removeSurrounding("\"")
                    .trim()
            }.getOrNull()?.takeIf { it.isNotBlank() }
        }

        val plain = Regex("filename\\s*=\\s*\"?([^\";]+)\"?", RegexOption.IGNORE_CASE)
            .find(header)
            ?.groupValues
            ?.getOrNull(1)
        return plain?.trim()?.takeIf { it.isNotBlank() }
    }
}