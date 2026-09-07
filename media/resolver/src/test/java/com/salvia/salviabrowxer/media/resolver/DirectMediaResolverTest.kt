package com.salvia.salviabrowxer.media.resolver

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

class DirectMediaResolverTest {

    private lateinit var server: MockWebServer
    private lateinit var resolver: DirectMediaResolver

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()
        resolver = DirectMediaResolver(OkHttpClient())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `resolve returns MediaInfo with correct values`() = runTest {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "video/mp4")
                .setBody("x".repeat(1024))
        )
        val url = server.url("/video.mp4").toString()

        val result = resolver.resolve(url)

        assertNotNull(result)
        assertEquals("video", result.title)
        assertEquals(url, result.source)
        assertEquals("direct", result.extractor)
        assertEquals(url, result.webpageUrl)
        assertEquals(1, result.formats.size)
        assertEquals(url, result.formats[0].url)
        assertEquals("video/mp4", result.formats[0].mimeType)
        assertEquals("mp4", result.formats[0].extension)
        assertEquals(1024L, result.formats[0].size)
        assertEquals(true, result.formats[0].isVideo)
        assertEquals(false, result.formats[0].isAudio)
    }

    @Test
    fun `resolve extracts filename from Content-Disposition`() = runTest {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/octet-stream")
                .setHeader("Content-Disposition", "attachment; filename=\"my-file.mp4\"")
                .setBody("x".repeat(2048))
        )
        val url = server.url("/download").toString()

        val result = resolver.resolve(url)

        assertEquals("my-file", result.title)
        assertEquals("mp4", result.formats[0].extension)
    }

    @Test
    fun `resolve extracts RFC 5987 utf-8 filename`() = runTest {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "video/mp4")
                .setHeader(
                    "Content-Disposition",
                    "attachment; filename*=UTF-8''%D9%88%DB%8C%D8%AF%DB%8C%D9%88.mp4"
                )
        )

        val result = resolver.resolve(server.url("/download").toString())

        assertEquals("ویدیو", result.title)
        assertEquals("mp4", result.formats[0].extension)
    }

    @Test
    fun `resolve handles audio files`() = runTest {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "audio/mpeg")
                .setBody("x".repeat(3072))
        )
        val url = server.url("/audio.mp3").toString()

        val result = resolver.resolve(url)

        assertEquals("audio", result.title)
        assertEquals(true, result.formats[0].isAudio)
        assertEquals(false, result.formats[0].isVideo)
    }
}
