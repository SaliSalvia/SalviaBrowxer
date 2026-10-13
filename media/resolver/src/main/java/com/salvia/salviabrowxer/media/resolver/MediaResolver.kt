package com.salvia.salviabrowxer.media.resolver

import com.salvia.salviabrowxer.core.model.MediaInfo

interface MediaResolver {
    suspend fun resolve(url: String): MediaInfo

    /**
     * The same probe, presented as the page that exposed the media: a `Referer` derived from
     * [pageUrl] and the user agent that page was loaded with.
     *
     * Servers that only serve their own pages answer a bare probe with 403, which used to leave the
     * quality sheet without a size, a type or any HLS/DASH rendition for media that downloads fine
     * once the request carries the session. Defaults to [resolve] so a resolver that cannot use a
     * session still works.
     */
    suspend fun resolve(url: String, pageUrl: String?, userAgent: String?): MediaInfo = resolve(url)
}