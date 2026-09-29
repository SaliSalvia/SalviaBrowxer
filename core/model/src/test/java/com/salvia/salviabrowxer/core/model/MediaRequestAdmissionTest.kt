package com.salvia.salviabrowxer.core.model

import org.junit.Assert.*
import org.junit.Test

class MediaRequestAdmissionTest {
    private val page = "https://example.org/feed"

    @Test fun `only complete resource requests are admitted`() {
        val url = "https://cdn.example.org/movie.mp4"
        assertNotNull(MediaRequestAdmission.admit(page, url))
        assertNotNull(MediaRequestAdmission.admit(page, url, "bytes=0-"))
        for (range in listOf("bytes=0-100", "bytes=10-", "bytes=0-,200-300", "nonsense")) {
            assertNull(MediaRequestAdmission.admit(page, url, range))
        }
    }

    @Test fun `request headers cannot make unknown blobs segments or web manifests files`() {
        for (path in listOf("part.ts", "part.m4s", "part.cmfv", "site.webmanifest", "clip.mp4?bytestart=2", "api/feed")) {
            assertNull(path, MediaRequestAdmission.admit(page, "https://cdn.example.org/$path"))
        }
        assertNull(MediaRequestAdmission.admit(page, "blob:https://example.org/mse"))
    }

    @Test fun `open manifests remain identifiable for the UI to decide offerability`() {
        assertEquals("m3u8", MediaRequestAdmission.admit(page, "https://cdn.example.org/a.m3u8")?.extension)
        assertEquals("mpd", MediaRequestAdmission.admit(page, "https://cdn.example.org/a.mpd")?.extension)
    }
}
