package com.salvia.salviabrowxer.media.resolver

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class DirectMediaResolverTest {

    private lateinit var resolver: DirectMediaResolver
    private val mockOkHttpClient: OkHttpClient = mockk()

    @Before
    fun setup() {
        resolver = DirectMediaResolver(mockOkHttpClient)
    }

    @Test
    fun `resolve returns MediaInfo with correct values`() = runTest {
        val url = "https://example.com/video.mp4"
        val mockResponse = mockk<Response>()
        val mockRequest = Request.Builder().url(url).build()

        coEvery { mockOkHttpClient.newCall(any()) } returns mockk()
        coEvery { mockOkHttpClient.newCall(any()).execute() } returns mockResponse
        coEvery { mockResponse.isSuccessful } returns true
        coEvery { mockResponse.body } returns mockk()
        coEvery { mockResponse.body?.contentLength() } returns 1024L
        coEvery { mockResponse.header("Content-Type") } returns "video/mp4"
        coEvery { mockResponse.header("Content-Disposition") } returns null
        coEvery { mockResponse.request } returns mockRequest

        val result = resolver.resolve(url)

        assertNotNull(result)
        assertEquals("video.mp4", result.title)
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
        val url = "https://example.com/download"
        val mockResponse = mockk<Response>()
        val mockRequest = Request.Builder().url(url).build()

        coEvery { mockOkHttpClient.newCall(any()) } returns mockk()
        coEvery { mockOkHttpClient.newCall(any()).execute() } returns mockResponse
        coEvery { mockResponse.isSuccessful } returns true
        coEvery { mockResponse.body } returns mockk()
        coEvery { mockResponse.body?.contentLength() } returns 2048L
        coEvery { mockResponse.header("Content-Type") } returns "application/octet-stream"
        coEvery { mockResponse.header("Content-Disposition") } returns "attachment; filename=\"my-file.mp4\""
        coEvery { mockResponse.request } returns mockRequest

        val result = resolver.resolve(url)

        assertEquals("my-file", result.title)
        assertEquals("mp4", result.formats[0].extension)
    }

    @Test
    fun `resolve handles audio files`() = runTest {
        val url = "https://example.com/audio.mp3"
        val mockResponse = mockk<Response>()
        val mockRequest = Request.Builder().url(url).build()

        coEvery { mockOkHttpClient.newCall(any()) } returns mockk()
        coEvery { mockOkHttpClient.newCall(any()).execute() } returns mockResponse
        coEvery { mockResponse.isSuccessful } returns true
        coEvery { mockResponse.body } returns mockk()
        coEvery { mockResponse.body?.contentLength() } returns 3072L
        coEvery { mockResponse.header("Content-Type") } returns "audio/mpeg"
        coEvery { mockResponse.header("Content-Disposition") } returns null
        coEvery { mockResponse.request } returns mockRequest

        val result = resolver.resolve(url)

        assertEquals("audio.mp3", result.title)
        assertEquals(true, result.formats[0].isAudio)
        assertEquals(false, result.formats[0].isVideo)
    }
}