package com.salvia.salviabrowxer.ui.utils

import com.salvia.salviabrowxer.core.model.PastedLink
import com.salvia.salviabrowxer.core.model.PastedLinkKind

/**
 * Resolves the home screen's link-only field without treating prose as a search request.
 *
 * The browser address bar accepts searches; this field promises to open a link. It accepts a
 * normal HTTP(S) URL as-is and adds HTTPS to a bare host, but still refuses non-web schemes and
 * arbitrary text. Keeping the normalization here also lets the button label and the submission
 * route use exactly the same classification.
 */
object HomeLinkResolver {

    fun resolve(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null

        if (PastedLink.classify(trimmed) != null) return trimmed
        if (AddressBarResolver.hasExplicitScheme(trimmed)) return null
        if (!AddressBarResolver.looksLikeHost(trimmed)) return null

        return "https://$trimmed".takeIf { PastedLink.classify(it) != null }
    }

    fun classify(input: String): PastedLinkKind? = resolve(input)?.let { PastedLink.classify(it) }
}

/** A saved start page must resolve to a real web URL, never a script or an empty WebView load. */
object HomepageResolver {

    fun resolve(input: String, searchEngine: String = Constants.DEFAULT_SEARCH_ENGINE): String {
        val target = AddressBarResolver.resolve(input, searchEngine)
        return target?.takeIf { PastedLink.classify(it) != null } ?: Constants.DEFAULT_HOMEPAGE
    }
}
