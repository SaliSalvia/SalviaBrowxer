package com.salvia.salviabrowxer.ui.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CleartextPolicyTest {

    @Test
    fun `http is cleartext, https and blob are not`() {
        assertTrue(CleartextPolicy.isCleartext("http://example.com"))
        assertTrue(CleartextPolicy.isCleartext("HTTP://EXAMPLE.COM/a"))
        assertFalse(CleartextPolicy.isCleartext("https://example.com"))
        assertFalse(CleartextPolicy.isCleartext("blob:https://example.com/1"))
    }

    @Test
    fun `cleartext is blocked by default`() {
        assertTrue(CleartextPolicy.isBlocked("http://example.com", cleartextAllowed = false))
        assertFalse(CleartextPolicy.isBlocked("https://example.com", cleartextAllowed = false))
    }

    @Test
    fun `an explicit opt-in allows cleartext`() {
        assertFalse(CleartextPolicy.isBlocked("http://example.com", cleartextAllowed = true))
    }

    @Test
    fun `a search query is never treated as cleartext`() {
        assertFalse(CleartextPolicy.isBlocked("kotlin coroutines", cleartextAllowed = false))
    }
}
