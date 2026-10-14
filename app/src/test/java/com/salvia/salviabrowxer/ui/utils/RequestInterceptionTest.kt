package com.salvia.salviabrowxer.ui.utils

import com.salvia.salviabrowxer.core.model.MediaRequestAdmission
import com.salvia.salviabrowxer.ui.utils.RequestInterception.Decision
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The ordering a blocked request depends on, tested where it can be: on the JVM.
 *
 * `AdBlockerTest` asks what the blocker decides; this asks what happens *around* that decision, which
 * is the part a user can never see. A blocked request that still reached the media observer would
 * appear in the tray as something to download — the app offering the traffic the user asked it to
 * remove — and no screenshot would show it, because the ad itself is gone.
 *
 * The second half is the promise the blocker must never break: media, playlists and segments are
 * observed exactly as they were before the feature existed, whether the blocker is off or on.
 */
class RequestInterceptionTest {

    /** Every URL that reached the observer, in order. */
    private val observed = mutableListOf<String>()
    private val list = setOf("doubleclick.net", "ads.example.com")
    private val page = "https://news.example.com/story/1"

    private lateinit var blocker: AdBlocker

    @Before
    fun setUp() {
        AdBlockState.resetForTest()
        blocker = AdBlocker(blockList = list)
    }

    @After
    fun tearDown() = AdBlockState.resetForTest()

    private fun interception(pageUrl: String = page): RequestInterception = RequestInterception(
        adBlocker = blocker,
        observer = MediaRequestObserver(
            pageUrlProvider = { pageUrl },
            onCandidate = { candidate -> observed.add(candidate.mediaUrl) }
        )
    )

    private fun decide(url: String, method: String? = "GET", pageUrl: String = page): Decision =
        interception(pageUrl).decide(url, method, null, pageUrl)

    // ---- the order ------------------------------------------------------------------------------

    @Test
    fun `a blocked request is never observed`() {
        AdBlockState.isEnabled = true

        val decision = decide("https://ads.doubleclick.net/pixel.js")

        assertEquals(Decision.BLOCKED, decision)
        assertTrue("a blocked request reached the observer: $observed", observed.isEmpty())
    }

    @Test
    fun `a blocked request is counted`() {
        AdBlockState.isEnabled = true

        decide("https://ads.doubleclick.net/pixel.js")
        decide("https://ads.doubleclick.net/second.js")

        assertEquals(2, AdBlockState.blockedThisRun)
    }

    @Test
    fun `a passed request is observed once`() {
        AdBlockState.isEnabled = true

        val decision = decide("https://cdn.example.net/clip.mp4")

        assertEquals(Decision.PASSED, decision)
        assertEquals(listOf("https://cdn.example.net/clip.mp4"), observed)
    }

    // ---- media is never blocked, on or off --------------------------------------------------------

    @Test
    fun `media from a blocked host is still passed and still observed`() {
        AdBlockState.isEnabled = true

        // The host is on the list; the URL is a file the user could download. The download is the
        // product, so the request goes through and the tray hears about it.
        val decision = decide("https://ads.doubleclick.net/promo.mp4")

        assertEquals(Decision.PASSED, decision)
        assertEquals(listOf("https://ads.doubleclick.net/promo.mp4"), observed)
        assertEquals("nothing was blocked, so nothing may be counted", 0, AdBlockState.blockedThisRun)
    }

    @Test
    fun `a playlist and a segment from a blocked host are still passed`() {
        AdBlockState.isEnabled = true

        assertEquals(Decision.PASSED, decide("https://ads.doubleclick.net/hls/master.m3u8"))
        assertEquals(Decision.PASSED, decide("https://ads.doubleclick.net/hls/seg-3.ts"))
        assertEquals(Decision.PASSED, decide("https://ads.doubleclick.net/dash/manifest.mpd"))
    }

    /**
     * The invariant that makes an opted-in blocker safe in a downloader: no URL the app would offer
     * the user is ever the URL it removes. If the two rules ever drift apart, this fails before a
     * user's stream does.
     */
    @Test
    fun `nothing the observer would offer is ever blocked`() {
        AdBlockState.isEnabled = true
        val urls = listOf(
            "https://ads.doubleclick.net/promo.mp4",
            "https://ads.doubleclick.net/hls/master.m3u8",
            "https://ads.doubleclick.net/hls/seg-3.ts",
            "https://ads.doubleclick.net/dash/manifest.mpd",
            "https://ads.doubleclick.net/live/manifest.live",
            "https://ads.doubleclick.net/pixel.js",
            "https://ads.doubleclick.net/beacon.gif",
            "https://ads.doubleclick.net/manifest.json",
            "https://ads.doubleclick.net/adframe.html"
        )

        for (url in urls) {
            val decision = interception().decide(url, "GET", null, page)
            val offered = MediaRequestAdmission.admit(page, url, null) != null
            assertTrue(
                "the tray would have offered $url while the blocker removed it",
                !(offered && decision == Decision.BLOCKED)
            )
        }
    }

    @Test
    fun `with the feature off nothing is blocked and media is still observed`() {
        AdBlockState.isEnabled = false

        // A request that would be blocked with the feature on is passed untouched, and a media file on
        // that same host is observed exactly as it was before the feature existed.
        assertEquals(Decision.PASSED, decide("https://ads.doubleclick.net/pixel.js"))
        assertEquals(Decision.PASSED, decide("https://ads.doubleclick.net/promo.mp4"))

        assertEquals(listOf("https://ads.doubleclick.net/promo.mp4"), observed)
        assertEquals(0, AdBlockState.blockedThisRun)
    }

    // ---- the page's own traffic is never blocked ---------------------------------------------------

    @Test
    fun `the page's own host and its parent domain are passed`() {
        AdBlockState.isEnabled = true

        assertEquals(Decision.PASSED, decide("https://news.example.com/app.js"))
        assertEquals(Decision.PASSED, decide("https://example.com/logo.png"))
    }

    @Test
    fun `a site the user exempted is passed`() {
        AdBlockState.isEnabled = true
        AdBlockState.disabledSites = setOf("example.com")

        assertEquals(Decision.PASSED, decide("https://ads.doubleclick.net/pixel.js"))
        assertEquals(0, AdBlockState.blockedThisRun)
    }

    // ---- what the observer is told --------------------------------------------------------------

    @Test
    fun `the method the WebView reported is the one the observer judges`() {
        AdBlockState.isEnabled = true
        val interception = interception()

        // A POST is not a download the app could start, so it is passed through - and not offered.
        assertEquals(
            Decision.PASSED,
            interception.decide("https://cdn.example.net/clip.mp4", "POST", null, page)
        )
        assertTrue("a POST may not be offered as a file", observed.isEmpty())

        assertEquals(
            Decision.PASSED,
            interception.decide("https://cdn.example.net/clip.mp4", "GET", null, page)
        )
        assertEquals(listOf("https://cdn.example.net/clip.mp4"), observed)
    }
}
