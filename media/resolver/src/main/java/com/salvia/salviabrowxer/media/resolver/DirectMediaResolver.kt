package com.salvia.salviabrowxer.media.resolver

import com.salvia.salviabrowxer.core.model.MediaFormat
import com.salvia.salviabrowxer.core.model.MediaInfo
import okhttp3.OkHttpClient
import javax.inject.Inject

class DirectMediaResolver @Inject constructor(
    private val okHttpClient: OkHttpClient
) : MediaResolver {

    override suspend fun resolve(url: String): MediaInfo {
        val request = okhttp3.Request.Builder()
            .url(url)
            .head()
            .build()

        val response = okHttpClient.newCall(request).execute()
        val contentLength = response.body?.contentLength() ?: 0L
        val mimeType = response.header("Content-Type") ?: ""
        val contentDisposition = response.header("Content-Disposition")

        val filename = contentDisposition?.let { header ->
            val index = header.indexOf("filename=")
            if (index != -1) {
                header.substring(index + 9).trim('"', '\'')
            } else {
                null
            }
        } ?: url.substringAfterLast('/')

        val extension = filename.substringAfterLast('.', "")
        val title = filename.substringBeforeLast('.', "")

        return MediaInfo(
            title = title.ifEmpty { "Media" },
            thumbnail = null,
            duration = null,
            formats = listOf(
                MediaFormat(
                    id = url,
                    format = "Direct",
                    url = url,
                    mimeType = mimeType,
                    extension = extension,
                    size = contentLength,
                    isVideo = mimeType.startsWith("video/"),
                    isAudio = mimeType.startsWith("audio/")
                )
            ),
            audioFormats = emptyList(),
            videoFormats = emptyList(),
            combinedFormats = listOf(
                MediaFormat(
                    id = url,
                    format = "Direct",
                    url = url,
                    mimeType = mimeType,
                    extension = extension,
                    size = contentLength,
                    isVideo = mimeType.startsWith("video/"),
                    isAudio = mimeType.startsWith("audio/")
                )
            ),
            source = url,
            extractor = "direct",
            webpageUrl = url
        )
    }
}