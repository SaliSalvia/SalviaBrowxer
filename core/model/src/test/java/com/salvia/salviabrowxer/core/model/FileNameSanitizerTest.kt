package com.salvia.salviabrowxer.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class FileNameSanitizerTest {

    @Test
    fun `Persian title is preserved instead of being replaced by underscores`() {
        val result = FileNameSanitizer.sanitize("ویدیو آزمایشی.mp4")
        assertEquals("ویدیو آزمایشی.mp4", result)
    }

    @Test
    fun `unsafe path separators are replaced`() {
        assertEquals("a_b", FileNameSanitizer.sanitize("a/b"))
        assertEquals("a_b", FileNameSanitizer.sanitize("a\\b"))
        assertEquals("a_b", FileNameSanitizer.sanitize("a:b"))
    }

    @Test
    fun `whitespace is collapsed and surrounding padding is removed`() {
        assertEquals("smart tv episode 1", FileNameSanitizer.sanitize("  smart   tv episode 1  "))
    }

    @Test
    fun `empty input falls back to download`() {
        assertEquals("download", FileNameSanitizer.sanitize(""))
        assertEquals("download", FileNameSanitizer.sanitize("   "))
    }

    @Test
    fun `generateFilename keeps Unicode base and appends lowercased extension`() {
        val result = FileNameSanitizer.generateFilename("نمونه ویدیو", "MP4")
        assertEquals("نمونه_ویدیو.mp4", result)
    }
}
