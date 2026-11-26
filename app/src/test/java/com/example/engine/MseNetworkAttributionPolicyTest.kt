package com.example.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MseNetworkAttributionPolicyTest {
    @Test
    fun `exact network source is eligible for extensionless audio video resources`() {
        assertEquals(
            "https://cdn.example.test/media?id=42&sig=abc",
            MseNetworkAttributionPolicy.verifiedSourceUrl(
                sourceBufferMimeType = "video/mp4; codecs=\"avc1.64001f\"",
                responseContentType = "video/mp4; charset=binary",
                sourceUrl = "https://cdn.example.test/media?id=42&sig=abc",
                byteLength = 256_000,
                responseStatus = 200,
                contentRange = "",
            ),
        )
        assertEquals(
            "https://audio.example.test/live-object",
            MseNetworkAttributionPolicy.verifiedSourceUrl(
                sourceBufferMimeType = "audio/mp4; codecs=\"mp4a.40.2\"",
                responseContentType = "application/octet-stream",
                sourceUrl = "https://audio.example.test/live-object",
                byteLength = 64_000,
                responseStatus = 200,
                contentRange = "",
            ),
        )
    }

    @Test
    fun `blob data non-http and empty source values are never attributed`() {
        listOf(
            "blob:https://page.example.test/opaque-id",
            "data:video/mp4;base64,AAAA",
            "file:///tmp/media.mp4",
            "javascript:alert(1)",
            "",
        ).forEach { url ->
            assertNull(
                MseNetworkAttributionPolicy.verifiedSourceUrl(
                    sourceBufferMimeType = "video/mp4",
                    responseContentType = "video/mp4",
                    sourceUrl = url,
                    byteLength = 4096,
                    responseStatus = 200,
                    contentRange = "",
                ),
            )
        }
    }

    @Test
    fun `manifest segments are not promoted as standalone media downloads`() {
        listOf("chunk.ts", "part.m4s", "track.cmfv", "track.cmfa").forEach { path ->
            assertNull(
                MseNetworkAttributionPolicy.verifiedSourceUrl(
                    sourceBufferMimeType = "video/mp4",
                    responseContentType = "video/mp4",
                    sourceUrl = "https://cdn.example.test/$path",
                    byteLength = 128_000,
                    responseStatus = 200,
                    contentRange = "",
                ),
            )
        }
    }

    @Test
    fun `mismatched mime invalid lengths and oversized urls fail closed`() {
        assertNull(MseNetworkAttributionPolicy.verifiedSourceUrl("video/mp4", "image/jpeg", "https://cdn.example.test/a", 4096, 200, ""))
        assertNull(MseNetworkAttributionPolicy.verifiedSourceUrl("text/plain", "video/mp4", "https://cdn.example.test/a", 4096, 200, ""))
        assertNull(MseNetworkAttributionPolicy.verifiedSourceUrl("video/mp4", "video/mp4", "https://cdn.example.test/a", 0, 200, ""))
        assertNull(MseNetworkAttributionPolicy.verifiedSourceUrl("video/mp4", "video/mp4", "https://cdn.example.test/" + "a".repeat(9000), 4096, 200, ""))
    }

    @Test
    fun `partial range responses are rejected even when MIME types look valid`() {
        assertNull(
            MseNetworkAttributionPolicy.verifiedSourceUrl(
                sourceBufferMimeType = "video/mp4",
                responseContentType = "video/mp4",
                sourceUrl = "https://cdn.example.test/whole-looking-url",
                byteLength = 900_000,
                responseStatus = 206,
                contentRange = "bytes 0-899999/9000000",
            ),
        )
        assertNull(
            MseNetworkAttributionPolicy.verifiedSourceUrl(
                sourceBufferMimeType = "video/mp4",
                responseContentType = "video/mp4",
                sourceUrl = "https://cdn.example.test/whole-looking-url",
                byteLength = 900_000,
                responseStatus = 200,
                contentRange = "bytes 0-899999/9000000",
            ),
        )
    }

    @Test
    fun `generic binary response may be trusted only with a media SourceBuffer and exact source url`() {
        val verified = MseNetworkAttributionPolicy.verifiedSourceUrl(
            sourceBufferMimeType = "video/webm",
            responseContentType = "application/octet-stream",
            sourceUrl = "https://cdn.example.test/stream?token=short-lived",
            byteLength = 16_384,
            responseStatus = 200,
            contentRange = "",
        )
        assertTrue(verified?.startsWith("https://cdn.example.test/") == true)
    }
}
