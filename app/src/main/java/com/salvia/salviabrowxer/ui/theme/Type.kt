package com.salvia.salviabrowxer.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * The type scale, built from a bundled family instead of `FontFamily.Default`.
 *
 * Two things change for Persian, and both are correctness rather than taste:
 *
 * - **letter spacing goes to zero.** Arabic-script letters join to each other; any positive
 *   tracking draws visible gaps inside a joined word, so the Material default of 0.15–0.5 sp
 *   (tuned for Roboto) actively damages Persian text.
 * - **line height grows 15%.** Vazirmatn's ascenders and its diacritic marks sit above the Latin
 *   metrics this scale was measured against, and a tight line box clips them.
 *
 * The Latin scale is unchanged: it was measured on a 360 dp phone against the real chrome.
 */
fun salviaTypography(family: FontFamily, persian: Boolean): Typography {
    fun style(
        weight: FontWeight,
        size: TextUnit,
        lineHeight: TextUnit,
        letterSpacing: TextUnit = 0.sp
    ) = TextStyle(
        fontFamily = family,
        fontWeight = weight,
        fontSize = size,
        lineHeight = if (persian) lineHeight * PERSIAN_LINE_HEIGHT_SCALE else lineHeight,
        letterSpacing = if (persian) 0.sp else letterSpacing
    )

    return Typography(
        displayLarge = style(FontWeight.Normal, 57.sp, 64.sp),
        displayMedium = style(FontWeight.Normal, 45.sp, 52.sp),
        displaySmall = style(FontWeight.Normal, 36.sp, 44.sp),
        headlineLarge = style(FontWeight.Normal, 32.sp, 40.sp),
        headlineMedium = style(FontWeight.Normal, 28.sp, 36.sp),
        headlineSmall = style(FontWeight.Normal, 24.sp, 32.sp),
        titleLarge = style(FontWeight.Bold, 22.sp, 28.sp),
        titleMedium = style(FontWeight.Bold, 18.sp, 24.sp, 0.15.sp),
        titleSmall = style(FontWeight.Bold, 14.sp, 20.sp, 0.1.sp),
        bodyLarge = style(FontWeight.Normal, 16.sp, 24.sp, 0.15.sp),
        bodyMedium = style(FontWeight.Normal, 14.sp, 20.sp, 0.25.sp),
        bodySmall = style(FontWeight.Normal, 12.sp, 16.sp, 0.4.sp),
        labelLarge = style(FontWeight.Medium, 14.sp, 20.sp, 0.1.sp),
        labelMedium = style(FontWeight.Medium, 12.sp, 16.sp, 0.5.sp),
        labelSmall = style(FontWeight.Medium, 10.sp, 14.sp, 0.5.sp)
    )
}

/** Headroom Vazirmatn needs over the Latin metrics this scale was measured against. */
private const val PERSIAN_LINE_HEIGHT_SCALE = 1.15f
