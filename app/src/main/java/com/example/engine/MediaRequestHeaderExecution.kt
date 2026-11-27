package com.example.engine

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/**
 * Executes a read-only media request with optional origin-scoped metadata.
 * A failed assisted GET/HEAD receives exactly one retry after every helper-injected
 * field (including fields carried over same-origin redirects) is removed.
 */
object MediaRequestHeaderExecution {
    data class Result(
        val response: Response,
        val injectedHeaderNames: Set<String>,
    )

    fun execute(
        client: OkHttpClient,
        baseRequest: Request,
        contextsByOrigin: Map<String, Map<String, String>>,
        previouslyInjectedHeaderNames: Set<String> = emptySet(),
    ): Result {
        val assisted = MediaRequestHeaderPolicy.applyContextHeaders(baseRequest, contextsByOrigin)
        val allInjectedNames = previouslyInjectedHeaderNames + assisted.injectedHeaderNames
        val assistedResponse = client.newCall(assisted.request).execute()
        val retryableMethod = baseRequest.method == "GET" || baseRequest.method == "HEAD"
        if (!retryableMethod || !MediaRequestHeaderPolicy.shouldRetryWithoutContext(
                assistedResponse.code,
                allInjectedNames.size,
            )
        ) {
            return Result(assistedResponse, allInjectedNames)
        }

        assistedResponse.close()
        val legacyRequest = baseRequest.newBuilder()
            .apply { allInjectedNames.forEach(::removeHeader) }
            .build()
        return Result(client.newCall(legacyRequest).execute(), emptySet())
    }
}
