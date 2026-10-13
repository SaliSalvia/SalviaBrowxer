package com.salvia.salviabrowxer.ui.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * A download the page offers (PDF, ZIP, installer) is named by its `Content-Disposition` header more
 * often than by its URL, so the parser is what decides the filename the user sees. Both header forms
 * are exercised, plus the path shape that must never reach the filesystem.
 *
 * The header values are ordinary escaped strings rather than raw strings on purpose: a raw string
 * whose text ends in a quote character cannot be written next to its closing `"""` without the
 * compiler ending the literal one quote early.
 */
class ContentDispositionTest {

    @Test
    fun `quoted filename is used as-is`() {
        assertEquals("report.pdf", filenameFromContentDisposition("attachment; filename=\"report.pdf\""))
    }

    @Test
    fun `unquoted filename is accepted`() {
        assertEquals("bundle.zip", filenameFromContentDisposition("attachment; filename=bundle.zip"))
    }

    @Test
    fun `the rfc 5987 form wins and is percent-decoded`() {
        val header = "attachment; filename=\"fallback.mp4\"; " +
            "filename*=UTF-8''%D9%88%DB%8C%D8%AF%DB%8C%D9%88%DB%8C%20" +
            "%D8%A2%D9%85%D9%88%D8%B2%D8%B4%DB%8C.mp4"
        assertEquals("ویدیوی آموزشی.mp4", filenameFromContentDisposition(header))
    }

    @Test
    fun `only the last path segment is kept`() {
        assertEquals("evil.pdf", filenameFromContentDisposition("attachment; filename=\"/etc/passwd/evil.pdf\""))
        assertEquals(
            "evil.pdf",
            filenameFromContentDisposition("attachment; filename=\"..\\..\\windows\\evil.pdf\"")
        )
    }

    @Test
    fun `a header with no filename has none`() {
        assertNull(filenameFromContentDisposition("inline"))
        assertNull(filenameFromContentDisposition(null))
        assertNull(filenameFromContentDisposition("attachment; filename=\"\""))
    }
}
