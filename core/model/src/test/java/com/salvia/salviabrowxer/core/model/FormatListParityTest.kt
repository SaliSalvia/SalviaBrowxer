package com.salvia.salviabrowxer.core.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The competitor's published format list, held against this app's rules.
 *
 * The list is not invented here: it is the feature list InShot Inc. publishes for its downloader —
 * "All download formats supported, **mp3, m4a, mp4, m4v, mov, avi, wmv, doc, xls, pdf, txt**, etc."
 * (the same sentence appears in the app's store description and in its listing mirrors, e.g.
 * <https://apkpure.com/video-downloader-app/video.downloader.videodownloader>). "All formats" is
 * marketing; this list is the falsifiable part of it, so it is what the app is measured against.
 *
 * Two different machinery cover it, and the test asserts they do not overlap:
 *
 *  - the **media** rows are extensions the tray can offer and the downloader can save;
 *  - the **document** rows are extensions the tray must not offer as media, because the WebView's own
 *    download listener is what saves them (a PDF is opened by the browser, not detected by it).
 *
 * A new extension that lands in neither list fails this test, which is the point: coverage of the
 * published list is a claim, and a claim that is not tested is a claim that drifts.
 */
class FormatListParityTest {

    /** The published list, verbatim, in the order the store description prints it. */
    private val publishedFormats = listOf("mp3", "m4a", "mp4", "m4v", "mov", "avi", "wmv", "doc", "xls", "pdf", "txt")

    private val publishedMedia = setOf("mp3", "m4a", "mp4", "m4v", "mov", "avi", "wmv")
    private val publishedDocuments = setOf("doc", "xls", "pdf", "txt")

    @Test
    fun `every media format the competitor publishes is one this app detects and saves`() {
        for (extension in publishedMedia) {
            assertTrue(
                ".$extension is in the published list and must be a media extension here",
                extension in MediaUrlRules.MEDIA_EXTENSIONS
            )
            assertTrue(
                ".$extension must be recognised on a URL, not only in a set",
                MediaUrlRules.hasMediaExtension("https://cdn.example.com/file.$extension")
            )
            assertFalse(
                ".$extension must not be classified as non-media",
                MediaUrlRules.looksLikeNonMedia("https://cdn.example.com/file.$extension")
            )
        }
    }

    @Test
    fun `every document format the competitor publishes is left to the browser's download listener`() {
        for (extension in publishedDocuments) {
            assertFalse(
                ".$extension is a document: the tray must not offer it as media",
                extension in MediaUrlRules.MEDIA_EXTENSIONS
            )
            assertTrue(
                ".$extension must be explicitly non-media, so the detection layers skip it and the " +
                    "download listener is the only path that handles it",
                MediaUrlRules.looksLikeNonMedia("https://example.com/file.$extension")
            )
        }
    }

    @Test
    fun `no published extension is claimed by both paths`() {
        val overlap = publishedMedia intersect publishedDocuments
        assertTrue("the two halves of the list are disjoint", overlap.isEmpty())
        for (extension in publishedFormats) {
            val isMedia = extension in MediaUrlRules.MEDIA_EXTENSIONS
            val isDocument = MediaUrlRules.looksLikeNonMedia("https://example.com/file.$extension")
            assertTrue(
                ".$extension must be handled by exactly one path (media=$isMedia, document=$isDocument)",
                isMedia != isDocument
            )
        }
    }

    @Test
    fun `the published list is fully covered, and nothing in it is missing`() {
        val uncovered = publishedFormats.filterNot { extension ->
            extension in MediaUrlRules.MEDIA_EXTENSIONS || MediaUrlRules.looksLikeNonMedia("https://example.com/f.$extension")
        }

        assertTrue("uncovered formats: $uncovered", uncovered.isEmpty())
    }

    @Test
    fun `a document type is never classified as a playlist or a segment`() {
        // `manifest`, `m3u8` and byte-range markers are the three shapes that used to misfire; a
        // document URL must not trip any of them.
        for (extension in publishedDocuments) {
            val url = "https://example.com/hls/manifest.$extension"
            assertFalse(MediaUrlRules.isPlaylistUrl(url), url)
            assertFalse(MediaUrlRules.looksLikeSegment(url), url)
        }
    }
}
