package com.example.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RangeResponseParserTest {

    @Test
    fun `parses the range probe response that gates the four part download path`() {
        val range = RangeResponseParser.parse("bytes 0-0/1536000")
        assertEquals(0L, range?.start)
        assertEquals(0L, range?.end)
        assertEquals(1536000L, range?.totalBytes)
    }

    @Test
    fun `parses multi part part responses with start end and total`() {
        val range = RangeResponseParser.parse("bytes 384000-767999/1536000")
        assertEquals(384000L, range?.start)
        assertEquals(767999L, range?.end)
        assertEquals(1536000L, range?.totalBytes)
    }

    @Test
    fun `matching is case insensitive and tolerates surrounding whitespace`() {
        val range = RangeResponseParser.parse(" BYTES 0-99/100 ")
        assertEquals(0L, range?.start)
        assertEquals(99L, range?.end)
        assertEquals(100L, range?.totalBytes)
    }

    @Test
    fun `unsatisfied and open ended headers are rejected so callers fall back safely`() {
        assertNull(RangeResponseParser.parse("bytes */1536000"))
        assertNull(RangeResponseParser.parse("bytes 0-0/*"))
    }

    @Test
    fun `missing and malformed headers are rejected`() {
        assertNull(RangeResponseParser.parse(null))
        assertNull(RangeResponseParser.parse(""))
        assertNull(RangeResponseParser.parse("   "))
        assertNull(RangeResponseParser.parse("bytes 0-0/"))
        assertNull(RangeResponseParser.parse("0-0/1536000"))
        assertNull(RangeResponseParser.parse("bytes abc-def/ghi"))
    }

    @Test
    fun `overflowing totals become null instead of crashing the download path`() {
        assertNull(RangeResponseParser.parse("bytes 0-0/99999999999999999999"))
        assertEquals(
            Long.MAX_VALUE,
            RangeResponseParser.parse("bytes 0-0/9223372036854775807")?.totalBytes,
        )
    }
}
