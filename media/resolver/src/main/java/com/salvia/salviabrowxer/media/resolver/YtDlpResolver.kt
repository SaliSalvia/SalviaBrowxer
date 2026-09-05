package com.salvia.salviabrowxer.media.resolver

import com.salvia.salviabrowxer.core.model.MediaFormat
import com.salvia.salviabrowxer.core.model.MediaInfo
import okhttp3.OkHttpClient
import javax.inject.Inject

class YtDlpResolver @Inject constructor(
    private val okHttpClient: OkHttpClient
) : MediaResolver {

    override suspend fun resolve(url: String): MediaInfo {
        return MediaInfo(
            title = "YouTube Video",
            thumbnail = null,
            duration = null,
            formats = emptyList(),
            audioFormats = emptyList(),
            videoFormats = emptyList(),
            combinedFormats = listOf(
                MediaFormat(
                    id = "best",
                    format = "Best",
                    url = url,
                    mimeType = "video/mp4",
                    extension = "mp4",
                    size = null,
                    isVideo = true,
                    isAudio = false
                )
            ),
            source = url,
            extractor = "yt-dlp",
            webpageUrl = url
        )
    }
}