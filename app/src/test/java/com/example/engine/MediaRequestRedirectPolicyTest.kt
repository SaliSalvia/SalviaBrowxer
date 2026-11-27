package com.example.engine

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaRequestRedirectPolicyTest {
    private val client = OkHttpClient.Builder()
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    @Test
    fun `cross origin redirect strips credentials and source context then destination context is independently reapplied`() = runBlocking {
        val source = MockWebServer()
        val destination = MockWebServer()
        source.start()
        destination.start()
        try {
            source.enqueue(MockResponse().setResponseCode(302).addHeader("Location", destination.url("/media").toString()))
            destination.enqueue(MockResponse().setResponseCode(200).setBody("ok"))

            val sourceOrigin = MediaRequestHeaderPolicy.originForUrl(source.url("/").toString())!!
            val destinationOrigin = MediaRequestHeaderPolicy.originForUrl(destination.url("/").toString())!!
            val contexts = mapOf(
                sourceOrigin to mapOf("Accept" to "video/*", "Accept-Language" to "en", "Origin" to "https://page-a.example"),
                destinationOrigin to mapOf("Accept" to "video/mp4", "Accept-Language" to "fr", "Origin" to "https://page-b.example"),
            )
            withContext(MediaRequestHeaderContextElement(contexts)) {
                val base = Request.Builder()
                    .url(source.url("/start"))
                    .header("Authorization", "Bearer must-not-cross-origin")
                    .header("Proxy-Authorization", "Basic must-not-cross-origin")
                    .header("Cookie", "session=must-not-cross-origin")
                    .build()
                val assisted = MediaRequestHeaderContextElement.applyTo(base)
                assertEquals(3, assisted.injectedHeaderCount)
                val first = client.newCall(assisted.request).execute()
                try {
                    val target = first.request.url.resolve(first.header("Location")!!)!!
                    val redirected = MediaRequestRedirectPolicy.nextRequest(first, target)!!
                    assertNull(redirected.header("Authorization"))
                    assertNull(redirected.header("Proxy-Authorization"))
                    assertNull(redirected.header("Cookie"))
                    assertNull(redirected.header("Accept"))
                    assertNull(redirected.header("Accept-Language"))
                    assertNull(redirected.header("Origin"))
                    assertEquals(sourceOrigin + "/", redirected.header("Referer"))

                    val destinationAssisted = MediaRequestHeaderContextElement.applyTo(redirected)
                    assertEquals(3, destinationAssisted.injectedHeaderCount)
                    val final = client.newCall(destinationAssisted.request).execute()
                    try { assertEquals(200, final.code) } finally { final.close() }
                } finally { first.close() }
            }
            val sourceRequest = source.takeRequest()
            assertEquals("video/*", sourceRequest.getHeader("Accept"))
            assertEquals("Bearer must-not-cross-origin", sourceRequest.getHeader("Authorization"))
            assertEquals("session=must-not-cross-origin", sourceRequest.getHeader("Cookie"))
            val destinationRequest = destination.takeRequest()
            assertEquals("video/mp4", destinationRequest.getHeader("Accept"))
            assertEquals("fr", destinationRequest.getHeader("Accept-Language"))
            assertEquals("https://page-b.example", destinationRequest.getHeader("Origin"))
            assertNull(destinationRequest.getHeader("Authorization"))
            assertNull(destinationRequest.getHeader("Proxy-Authorization"))
            assertNull(destinationRequest.getHeader("Cookie"))
            assertEquals(sourceOrigin + "/", destinationRequest.getHeader("Referer"))
        } finally {
            source.shutdown()
            destination.shutdown()
        }
    }

    @Test
    fun `same origin probe redirect preserves cookie authorization referer and helper metadata`() {
        val server = MockWebServer()
        server.start()
        try {
            val responseRequest = Request.Builder()
                .url(server.url("/first"))
                .header("Authorization", "Bearer same-origin")
                .header("Cookie", "session=preserved")
                .header("Referer", "https://page.example/watch?id=9")
                .header("Accept", "video/*")
                .build()
            val fakeResponse = okhttp3.Response.Builder()
                .request(responseRequest)
                .protocol(okhttp3.Protocol.HTTP_1_1)
                .code(302)
                .message("Found")
                .header("Location", "/next")
                .body(okhttp3.ResponseBody.create(null, ByteArray(0)))
                .build()
            val target = responseRequest.url.resolve("/next")!!
            val next = MediaRequestRedirectPolicy.nextRequest(
                response = fakeResponse,
                target = target,
                resolveCookiePerHop = false,
                resetRefererOnCrossOrigin = false,
            )!!
            assertEquals("Bearer same-origin", next.header("Authorization"))
            assertEquals("session=preserved", next.header("Cookie"))
            assertEquals("https://page.example/watch?id=9", next.header("Referer"))
            assertEquals("video/*", next.header("Accept"))
            fakeResponse.close()
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `same origin downloader redirect removes cookie for profile lookup on each hop`() {
        val server = MockWebServer()
        server.start()
        try {
            val request = Request.Builder()
                .url(server.url("/first"))
                .header("Authorization", "Bearer same-origin")
                .header("Cookie", "session=old-hop")
                .header("Accept", "video/*")
                .build()
            val response = okhttp3.Response.Builder()
                .request(request)
                .protocol(okhttp3.Protocol.HTTP_1_1)
                .code(302)
                .message("Found")
                .body(okhttp3.ResponseBody.create(null, ByteArray(0)))
                .build()
            val next = MediaRequestRedirectPolicy.nextRequest(
                response = response,
                target = request.url.resolve("/next")!!,
                resolveCookiePerHop = true,
            )!!
            assertNull(next.header("Cookie"))
            assertEquals("Bearer same-origin", next.header("Authorization"))
            assertEquals("video/*", next.header("Accept"))
            response.close()
        } finally {
            server.shutdown()
        }
    }
}
