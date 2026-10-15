package com.salvia.salviabrowxer.data.update

import com.salvia.salviabrowxer.data.update.UpdateChecker.UpdateCheckResult
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {

    private fun body(
        tag: String,
        url: String = "https://github.com/SaliSalvia/SalviaBrowxer/releases/tag/$tag",
        notes: String? = null,
        draft: Boolean = false,
        prerelease: Boolean = false
    ): String = JSONObject()
        .put("tag_name", tag)
        .put("html_url", url)
        .put("draft", draft)
        .put("prerelease", prerelease)
        .apply { notes?.let { put("body", it) } }
        .toString()

    @Test
    fun `a newer tag is available with its page and notes`() {
        val result = UpdateChecker.parse(body("v1.0.1", notes = "## Fixed\n- downloads"), currentVersion = "1.0.0")
        assertTrue(result is UpdateCheckResult.Available)
        result as UpdateCheckResult.Available
        assertEquals("1.0.1", result.version)
        assertEquals("https://github.com/SaliSalvia/SalviaBrowxer/releases/tag/v1.0.1", result.pageUrl)
        assertEquals("## Fixed\n- downloads", result.notes)
    }

    @Test
    fun `the running version is up to date`() {
        assertEquals(UpdateCheckResult.UpToDate, UpdateChecker.parse(body("v1.0.0"), currentVersion = "1.0.0"))
        assertEquals(UpdateCheckResult.UpToDate, UpdateChecker.parse(body("v0.9.0"), currentVersion = "1.0.0"))
    }

    @Test
    fun `drafts and prereleases are not updates`() {
        assertEquals(UpdateCheckResult.UpToDate, UpdateChecker.parse(body("v2.0.0", draft = true), currentVersion = "1.0.0"))
        assertEquals(UpdateCheckResult.UpToDate, UpdateChecker.parse(body("v2.0.0", prerelease = true), currentVersion = "1.0.0"))
    }

    @Test
    fun `a body without a tag fails honestly`() {
        assertTrue(UpdateChecker.parse("{}", currentVersion = "1.0.0") is UpdateCheckResult.Failed)
        assertTrue(UpdateChecker.parse("", currentVersion = "1.0.0") is UpdateCheckResult.Failed)
        assertTrue(UpdateChecker.parse("not json at all", currentVersion = "1.0.0") is UpdateCheckResult.Failed)
    }

    @Test
    fun `a missing page url falls back to the releases page`() {
        val result = UpdateChecker.parse(body("v1.1.0", url = ""), currentVersion = "1.0.0") as UpdateCheckResult.Available
        assertEquals("https://github.com/SaliSalvia/SalviaBrowxer/releases", result.pageUrl)
    }

    @Test
    fun `notes are trimmed and can be absent`() {
        val withNotes = UpdateChecker.parse(body("v1.1.0", notes = "  \n\nFix A\nFix B\n"), currentVersion = "1.0.0") as UpdateCheckResult.Available
        assertEquals("Fix A\nFix B", withNotes.notes)
        val withoutNotes = UpdateChecker.parse(body("v1.1.0"), currentVersion = "1.0.0") as UpdateCheckResult.Available
        assertNull(withoutNotes.notes)
    }

    @Test
    fun `version comparison is numeric not lexicographic`() {
        assertTrue(UpdateChecker.isNewer("1.0.0", "v1.10.0"))
        assertFalse(UpdateChecker.isNewer("1.10.0", "1.9.9"))
        assertTrue(UpdateChecker.isNewer("1.9.9", "1.10.0"))
        assertTrue(UpdateChecker.isNewer("1.0", "1.0.1"))
        assertFalse(UpdateChecker.isNewer("1.0.0.1", "1.0.0"))
        // Equal modulo trailing zeroes and prefixes: the same version.
        assertFalse(UpdateChecker.isNewer("1.0.0", "v1.0"))
        assertFalse(UpdateChecker.isNewer("1.0.0", "1.0.0"))
        // Unparsable candidates are never "newer" — a garbage tag must not nag the user.
        assertFalse(UpdateChecker.isNewer("1.0.0", ""))
        assertFalse(UpdateChecker.isNewer("1.0.0", "beta"))
    }
}
