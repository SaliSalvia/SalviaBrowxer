package com.example.engine

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class MediaRequestHeaderExecutionTest {
    private val client = OkHttpClient.Builder()
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    @Test
    fun `403 caused by injected context retries exactly once using untouched request`() {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setResponseCode(403).setBody("helper rejected"))
            server.enqueue(MockResponse().setResponseCode(200).setBody("legacy accepted"))
            val origin = MediaRequestHeaderPolicy.originForUrl(server.url("/").toString())!!
            val request = Request.Builder().url(server.url("/asset")).build()

            val execution = MediaRequestHeaderExecution.execute(
                client = client,
                baseRequest = request,
                contextsByOrigin = mapOf(origin to mapOf("Accept" to "video/*")),
            )
            try {
                assertEquals(200, execution.response.code)
                assertEquals("legacy accepted", execution.response.body?.string())
                assertEquals(emptySet<String>(), execution.injectedHeaderNames)
            } finally {
                execution.response.close()
            }

            val assisted = server.takeRequest()
            val fallback = server.takeRequest()
            assertEquals("video/*", assisted.getHeader("Accept"))
            assertNotEquals("video/*", fallback.getHeader("Accept"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `same origin redirect carries helper provenance into next hop fallback`() {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setResponseCode(302).addHeader("Location", "/next"))
            server.enqueue(MockResponse().setResponseCode(403).setBody("helper rejected after redirect"))
            server.enqueue(MockResponse().setResponseCode(200).setBody("legacy accepted after redirect"))
            val origin = MediaRequestHeaderPolicy.originForUrl(server.url("/").toString())!!
            val contexts = mapOf(origin to mapOf("Accept" to "video/*"))
            var request = Request.Builder().url(server.url("/first")).build()
            var injectedNames = emptySet<String>()

            val first = MediaRequestHeaderExecution.execute(client, request, contexts, injectedNames)
            assertEquals(302, first.response.code)
            val target = first.response.request.url.resolve(first.response.header("Location")!!)!!
            val next = MediaRequestRedirectPolicy.nextRequest(first.response, target)!!
            injectedNames = first.injectedHeaderNames
            first.response.close()
            request = next

            val second = MediaRequestHeaderExecution.execute(client, request, contexts, injectedNames)
            try {
                assertEquals(200, second.response.code)
                assertEquals("legacy accepted after redirect", second.response.body?.string())
                assertEquals(emptySet<String>(), second.injectedHeaderNames)
            } finally {
                second.response.close()
            }

            val initialRequest = server.takeRequest()
            val redirectedAssistedRequest = server.takeRequest()
            val redirectedLegacyRetry = server.takeRequest()
            assertEquals("video/*", initialRequest.getHeader("Accept"))
            assertEquals("video/*", redirectedAssistedRequest.getHeader("Accept"))
            assertNotEquals("video/*", redirectedLegacyRetry.getHeader("Accept"))
            assertEquals("/first", initialRequest.path)
            assertEquals("/next", redirectedLegacyRetry.path)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `normal success does not trigger an unnecessary legacy retry`() {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setResponseCode(200).setBody("ok"))
            val origin = MediaRequestHeaderPolicy.originForUrl(server.url("/").toString())!!
            val execution = MediaRequestHeaderExecution.execute(
                client = client,
                baseRequest = Request.Builder().url(server.url("/asset")).build(),
                contextsByOrigin = mapOf(origin to mapOf("Accept" to "video/*")),
            )
            try {
                assertEquals(200, execution.response.code)
                assertEquals(setOf("accept"), execution.injectedHeaderNames)
            } finally {
                execution.response.close()
            }
            val onlyRequest = server.takeRequest()
            assertEquals("video/*", onlyRequest.getHeader("Accept"))
            org.junit.Assert.assertNull(server.takeRequest(50, java.util.concurrent.TimeUnit.MILLISECONDS))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `non retryable server failure is returned without duplicate request`() {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setResponseCode(500).setBody("server error"))
            val origin = MediaRequestHeaderPolicy.originForUrl(server.url("/").toString())!!
            val execution = MediaRequestHeaderExecution.execute(
                client = client,
                baseRequest = Request.Builder().url(server.url("/asset")).build(),
                contextsByOrigin = mapOf(origin to mapOf("Accept" to "video/*")),
            )
            try {
                assertEquals(500, execution.response.code)
                assertEquals(setOf("accept"), execution.injectedHeaderNames)
            } finally {
                execution.response.close()
            }
            server.takeRequest()
            org.junit.Assert.assertNull(server.takeRequest(50, java.util.concurrent.TimeUnit.MILLISECONDS))
        } finally {
            server.shutdown()
        }
    }
}
