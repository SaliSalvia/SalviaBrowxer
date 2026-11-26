package com.example.engine

import kotlinx.coroutines.ThreadContextElement
import okhttp3.Request
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * Propagates an immutable per-download request-header snapshot through coroutine
 * dispatcher switches (including parallel HLS/DASH segment workers).
 */
internal class MediaRequestHeaderContextElement(
    private val contextsByOrigin: Map<String, Map<String, String>>,
) : ThreadContextElement<Map<String, Map<String, String>>?>,
    AbstractCoroutineContextElement(Key) {

    companion object Key : CoroutineContext.Key<MediaRequestHeaderContextElement> {
        private val local = ThreadLocal<Map<String, Map<String, String>>>()

        fun headersForUrl(url: String): Map<String, String> =
            MediaRequestHeaderPolicy.headersForUrl(url, local.get().orEmpty())

        fun applyTo(request: Request): AppliedMediaRequestContext =
            MediaRequestHeaderPolicy.applyContextHeaders(request, local.get().orEmpty())
    }

    override fun updateThreadContext(context: CoroutineContext): Map<String, Map<String, String>>? {
        val previous = local.get()
        local.set(contextsByOrigin)
        return previous
    }

    override fun restoreThreadContext(
        context: CoroutineContext,
        oldState: Map<String, Map<String, String>>?,
    ) {
        if (oldState == null) local.remove() else local.set(oldState)
    }
}
