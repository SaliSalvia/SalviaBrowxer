package com.salvia.salviabrowxer.media.resolver

import com.salvia.salviabrowxer.core.model.MediaInfo

interface MediaResolver {
    suspend fun resolve(url: String): MediaInfo
}