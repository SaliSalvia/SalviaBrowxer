package com.salvia.salviabrowxer.ui.utils

import com.salvia.salviabrowxer.core.model.MediaUrlRules
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The opt-in ad blocker's decision, tested as rules rather than as a WebView.
 *
 * The two failures that would matter most are opposite halves of the same coin: blocking something
 * that is not an ad (a media segment, the page itself, a host that merely *ends* with a blocked
 * name), and failing to block at all. Both are asserted here, including the URL traps that catch a
 * naive `contains` implementation.
 */
class AdBlockerTest {

    /** A small list, so a failure points at one behaviour instead of at the default list's size. */
    private val list = setOf("doubleclick.net", "ads.example.com", "tracker.io")
    private lateinit var blocker: AdBlocker

    @Before
    fun setUp() {
        AdBlockState.resetForTest()
        blocker = AdBlocker(blockList = list)
    }

    @After
    fun tearDown() = AdBlockState.resetForTest()

    // ---- off by default -------------------------------------------------------------------------

    @Test
    fun `nothing is blocked while the feature is off`() {
        AdBlockState.isEnabled = false

        assertFalse(blocker.shouldBlock("https://doubleclick.net/ad.js", "https://example.com/article"))
        assertEquals("an off blocker must not count anything", 0, AdBlockState.blockedThisRun)
    }

    @Test
    fun `turning it on is what starts blocking`() {
        AdBlockState.isEnabled = true

        assertTrue(blocker.shouldBlock("https://doubleclick.net/ad.js", "https://example.com/article"))
        assertEquals(1, AdBlockState.blockedThisRun)
    }

    // ---- host matching --------------------------------------------------------------------------

    @Test
    fun `a blocked domain is matched exactly and through its subdomains`() {
        AdBlockState.isEnabled = true

        assertTrue(blocker.shouldBlock("https://doubleclick.net/ad.js", "https://example.com/a"))
        assertTrue(blocker.shouldBlock("https://static.doubleclick.net/ad.js", "https://example.com/a"))
        assertTrue(blocker.shouldBlock("https://a.b.doubleclick.net/x", "https://example.com/a"))
    }

    @Test
    fun `a host that merely ends with a blocked name is not matched`() {
        AdBlockState.isEnabled = true

        // The classic prefix/suffix traps: `notdoubleclick.net` ends with `doubleclick.net`, and
        // `doubleclick.net.example.com` contains it. Neither belongs to the blocked host.
        assertFalse(blocker.shouldBlock("https://notdoubleclick.net/ad.js", "https://example.com/a"))
        assertFalse(blocker.shouldBlock("https://doubleclick.net.evil.example/x", "https://example.com/a"))
        assertFalse(blocker.shouldBlock("https://mydoubleclick.net/", "https://example.com/a"))
    }

    @Test
    fun `a blocked path segment is not a blocked host`() {
        AdBlockState.isEnabled = true

        // Only the host decides: an ad host used as a query value, a path segment or a fragment is
        // the page's own business, not the blocker's.
        assertFalse(blocker.shouldBlock("https://example.com/redirect?to=https://doubleclick.net/x", "https://example.com/a"))
        assertFalse(blocker.shouldBlock("https://example.com/doubleclick.net/ad.js", "https://example.com/a"))
        assertFalse(blocker.shouldBlock("https://example.com/a#doubleclick.net", "https://example.com/a"))
    }

    @Test
    fun `only the host is compared, whatever the case or the port`() {
        AdBlockState.isEnabled = true

        assertTrue(blocker.shouldBlock("https://DOUBLECLICK.NET:8443/ad.js", "https://example.com/a"))
        assertTrue(blocker.shouldBlock("http://user:pass@DoubleClick.net/ad.js", "https://example.com/a"))
        assertTrue(blocker.shouldBlock("https://doubleclick.net./ad.js", "https://example.com/a"))
    }

    // ---- media is never blocked -----------------------------------------------------------------

    @Test
    fun `media, playlists and segments are never blocked, even on a blocked host`() {
        AdBlockState.isEnabled = true
        val everyMediaExtension = MediaUrlRules.MEDIA_EXTENSIONS + MediaUrlRules.SEGMENT_EXTENSIONS + MediaUrlRules.DASH_EXTENSION

        for (extension in everyMediaExtension) {
            val url = "https://doubleclick.net/media/clip.$extension"
            assertFalse(".$extension must not be blocked: $url", blocker.shouldBlock(url, "https://example.com/a"))
        }
        assertEquals("nothing may be counted for a media request", 0, AdBlockState.blockedThisRun)
    }

    @Test
    fun `a media URL with a query string is still media`() {
        AdBlockState.isEnabled = true

        assertFalse(blocker.shouldBlock("https://tracker.io/stream.m3u8?token=abc&expires=1", "https://example.com/a"))
    }

    // ---- the page and the device ------------------------------------------------------------------

    @Test
    fun `loopback and private addresses are never blocked`() {
        AdBlockState.isEnabled = true
        val hosts = listOf(
            "http://localhost:8080/doubleclick.net.js",
            "http://127.0.0.1/ad.js",
            "http://10.0.0.5/ad.js",
            "http://192.168.1.10/ad.js",
            "http://172.16.4.4/ad.js",
            "http://169.254.1.1/ad.js"
        )

        for (url in hosts) {
            assertFalse("$url must not be blocked", blocker.shouldBlock(url, "https://example.com/a"))
        }
    }

    @Test
    fun `the page's own host is never blocked`() {
        AdBlockState.isEnabled = true

        // A page served by an ad host still has to load its own scripts, or the blocker has broken
        // the very page it is showing.
        assertFalse(blocker.shouldBlock("https://ads.example.com/player.js", "https://ads.example.com/watch"))
        // ...and neither is the parent domain of the page's host.
        assertFalse(blocker.shouldBlock("https://example.com/x.js", "https://m.example.com/watch"))
    }

    @Test
    fun `an unparsable url is not blocked`() {
        AdBlockState.isEnabled = true

        assertFalse(blocker.shouldBlock("data:text/html,doubleclick.net", "https://example.com"))
        assertFalse(blocker.shouldBlock("blob:https://example.com/1234", "https://example.com"))
        assertFalse(blocker.shouldBlock(null, "https://example.com"))
        assertNull(AdBlockRules.hostOf("javascript:void(0)"))
    }

    // ---- per-site exemption -----------------------------------------------------------------------

    @Test
    fun `a site the user exempted is not blocked, subdomains included`() {
        AdBlockState.isEnabled = true
        AdBlockState.disabledSites = setOf("example.com")

        assertFalse(blocker.shouldBlock("https://doubleclick.net/ad.js", "https://example.com/a"))
        assertFalse(blocker.shouldBlock("https://doubleclick.net/ad.js", "https://m.example.com/a"))
        // A different site is still blocked: the exemption is per site, not global.
        assertTrue(blocker.shouldBlock("https://doubleclick.net/ad.js", "https://other.example/a"))
    }

    @Test
    fun `an exemption does not leak to lookalike sites`() {
        AdBlockState.isEnabled = true
        AdBlockState.disabledSites = setOf("example.com")

        assertTrue(blocker.shouldBlock("https://doubleclick.net/ad.js", "https://notexample.com/a"))
    }

    // ---- the default list -------------------------------------------------------------------------

    @Test
    fun `the shipped list blocks known ad hosts and leaves the page functional`() {
        AdBlockState.isEnabled = true
        val defaultBlocker = AdBlocker()

        assertTrue(defaultBlocker.shouldBlock("https://www.googletagmanager.com/gtm.js", "https://news.example/a"))
        assertTrue(defaultBlocker.shouldBlock("https://criteo.com/x", "https://news.example/a"))
        // Hosts a page needs to work are not in the list.
        assertFalse(defaultBlocker.shouldBlock("https://www.google.com/recaptcha/api.js", "https://news.example/a"))
        assertFalse(defaultBlocker.shouldBlock("https://fonts.gstatic.com/s/x.woff2", "https://news.example/a"))
        assertFalse(defaultBlocker.shouldBlock("https://cdn.example.com/app.js", "https://news.example/a"))
    }
}
