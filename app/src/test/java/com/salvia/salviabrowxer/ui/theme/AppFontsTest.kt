package com.salvia.salviabrowxer.ui.theme

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Persian-vs-Latin decision is the one piece of the type system that can be wrong silently:
 * a wrong answer renders Persian text in a face with no Arabic-script coverage and the user sees
 * an unexplained fallback rather than an error. It is a pure function precisely so it can be
 * pinned down here.
 */
class AppFontsTest {

    @Test
    fun `bare persian language tag uses the persian face`() {
        assertTrue(AppFonts.usesPersianFace("fa"))
    }

    @Test
    fun `persian with a region uses the persian face`() {
        assertTrue(AppFonts.usesPersianFace("fa-IR"))
        assertTrue(AppFonts.usesPersianFace("fa_IR"))
        assertTrue(AppFonts.usesPersianFace("fa-Arab-AF"))
    }

    @Test
    fun `language tag case does not decide the face`() {
        assertTrue(AppFonts.usesPersianFace("FA"))
        assertTrue(AppFonts.usesPersianFace("Fa-ir"))
    }

    @Test
    fun `surrounding whitespace does not decide the face`() {
        assertTrue(AppFonts.usesPersianFace("  fa  "))
    }

    @Test
    fun `latin locales use the latin face`() {
        assertFalse(AppFonts.usesPersianFace("en"))
        assertFalse(AppFonts.usesPersianFace("en-US"))
        assertFalse(AppFonts.usesPersianFace("de"))
        assertFalse(AppFonts.usesPersianFace("ar")) // Arabic script, but not a locale we ship
    }

    @Test
    fun `a tag that merely starts with fa is not persian`() {
        assertFalse(AppFonts.usesPersianFace("fan"))
        assertFalse(AppFonts.usesPersianFace("far"))
    }

    @Test
    fun `a missing configuration falls back to the latin face instead of throwing`() {
        assertFalse(AppFonts.usesPersianFace(null))
        assertFalse(AppFonts.usesPersianFace(""))
        assertFalse(AppFonts.usesPersianFace("   "))
    }
}
