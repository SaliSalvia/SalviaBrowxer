package com.example.engine

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaRequestRedirectPolicyTest {
    private val client = OkHttpClient.Builder()
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    @Test
    fun `cross origin redirect strips credentials and source context then applies destination context`() = runBlocking {
        val source = MockWebServer()
        val destination = MockWebServer()
        source.start()
        destination.start()
        try {
            source.enqueue(
                MockResponse()
                    .setResponseCode(302)
                    .addHeader("Location", destination.url("/media").toString()),
            )
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
                    assertEquals(302, first.code)
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
                    val finalResponse = client.newCall(destinationAssisted.request).execute()
                    try {
                        assertEquals(200, finalResponse.code)
                    } finally {
                        finalResponse.close()
                    }
                } finally {
                    first.close()
                }
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
    fun `same origin redirect preserves request identity but drops cookie for per hop resolution`() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setResponseCode(302).addHeader("Location", "/next"))
            server.enqueue(MockResponse().setResponseCode(200).setBody("done"))
            val origin = MediaRequestHeaderPolicy.originForUrl(server.url("/").toString())!!
            withContext(MediaRequestHeaderContextElement(mapOf(origin to mapOf("Accept" to "video/*")))) {
                val base = Request.Builder()
                    .url(server.url("/first"))
                    .header("Authorization", "Bearer same-origin")
                    .header("Cookie", "session=hop-cookie")
                    .build()
                val assisted = MediaRequestHeaderContextElement.applyTo(base)
                val response = client.newCall(assisted.request).execute()
                try {
                    val target = response.request.url.resolve(response.header("Location")!!)!!
                    val redirected = MediaRequestRedirectPolicy.nextRequest(response, target)!!
                    assertEquals("Bearer same-origin", redirected.header("Authorization"))
                    assertEquals("video/*", redirected.header("Accept"))
                    assertNull(redirected.header("Cookie"))
                    val finalRequest = redirected.newBuilder().header("Cookie", "session=resolved-again").build()
                    val finalResponse = client.newCall(finalRequest).execute()
                    try {
                        assertEquals(200, finalResponse.code)
                    } finally {
                        finalResponse.close()
                    }
                } finally {
                    response.close()
                }
            }
            server.takeRequest()
            val last = server.takeRequest()
            assertEquals("Bearer same-origin", last.getHeader("Authorization"))
            assertEquals("video/*", last.getHeader("Accept"))
            assertEquals("session=resolved-again", last.getHeader("Cookie"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `header related failure retries once on untouched legacy request`() {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setResponseCode(403).setBody("header rejected"))
            server.enqueue(MockResponse().setResponseCode(200).setBody("legacy accepted"))
            val origin = MediaRequestHeaderPolicy.originForUrl(server.url("/").toString())!!
            runBlocking {
                withContext(MediaRequestHeaderContextElement(mapOf(origin to mapOf("Accept" to "video/*")))) {
                    val base = Request.Builder().url(server.url("/asset")).build()
                    val assisted = MediaRequestHeaderContextElement.applyTo(base)
                    val rejected = client.newCall(assisted.request).execute()
                    try {
                        assertEquals(403, rejected.code)
                        assertTrue(MediaRequestHeaderPolicy.shouldRetryWithoutContext(
                            rejected.code,
                            assisted.injectedHeaderCount,
                        ))
                    } finally {
                        rejected.close()
                    }

                    val legacy = client.newCall(base).execute()
                    try {
                        assertEquals(200, legacy.code)
                    } finally {
                        legacy.close()
                    }
                }
            }

            val assistedRequest = server.takeRequest()
            val legacyRequest = server.takeRequest()
            assertEquals("video/*", assistedRequest.getHeader("Accept"))
            assertFalse(legacyRequest.getHeader("Accept") == "video/*")
        } finally {
            server.shutdown()
        }
    }
}
