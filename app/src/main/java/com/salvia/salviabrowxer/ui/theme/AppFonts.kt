package com.salvia.salviabrowxer.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.salvia.salviabrowxer.R

/**
 * Which bundled typeface the UI renders in.
 *
 * The app ships two OFL faces (see `docs/fonts.md`): Inter for Latin locales and Vazirmatn for
 * Persian. The choice is data, not a user setting — it follows the app language so a Persian user
 * never reads Persian glyphs through a Latin face, and a system-language change is enough to flip
 * it. Nothing here touches the network: both families are in `res/font`.
 */
object AppFonts {

    /** Persian is the only Arabic-script locale this build ships a face for. */
    const val PERSIAN_LANGUAGE = "fa"

    /**
     * True when [languageTag] is Persian.
     *
     * Accepts a full tag (`fa-IR`) or a bare language (`fa`), ignores case, and treats null or
     * blank as "not Persian" so a missing configuration degrades to the Latin face instead of
     * throwing.
     */
    fun usesPersianFace(languageTag: String?): Boolean =
        languageTag
            ?.substringBefore('-')
            ?.substringBefore('_')
            ?.trim()
            ?.lowercase() == PERSIAN_LANGUAGE

    /**
     * Latin UI face. Only the three weights [Typography][androidx.compose.material3.Typography]
     * asks for are bundled, so every requested weight resolves to a real file.
     */
    val Latin: FontFamily = FontFamily(
        Font(R.font.inter_regular, FontWeight.Normal),
        Font(R.font.inter_medium, FontWeight.Medium),
        Font(R.font.inter_bold, FontWeight.Bold)
    )

    /** Persian UI face. Same three weights, Arabic-script coverage. */
    val Persian: FontFamily = FontFamily(
        Font(R.font.vazirmatn_regular, FontWeight.Normal),
        Font(R.font.vazirmatn_medium, FontWeight.Medium),
        Font(R.font.vazirmatn_bold, FontWeight.Bold)
    )

    /** The family for a language tag, so the choice has exactly one implementation. */
    fun familyFor(languageTag: String?): FontFamily = if (usesPersianFace(languageTag)) Persian else Latin
}
