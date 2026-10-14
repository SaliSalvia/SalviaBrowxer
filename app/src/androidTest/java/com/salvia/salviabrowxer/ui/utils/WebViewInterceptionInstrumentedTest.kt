package com.salvia.salviabrowxer.ui.utils

import android.webkit.WebView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.salvia.salviabrowxer.core.model.MediaCandidate
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The opt-in blocker inside a real WebView, on a device.
 *
 * `RequestInterceptionTest` proves the *sequence* on the JVM; `AdBlockerTest` proves the rules. What
 * neither can prove is the wiring: that the client the app actually installs is the one whose
 * `shouldInterceptRequest` runs the blocker before the media observer, that a page keeps working when
 * one of its subresources is blocked, and that a download the tray would offer is still observed even
 * when it comes from a host on the block list. That is what this test loads a page for.
 *
 * Everything is hermetic: the hosts are `.test`, which by reservation never resolves, so no request
 * in this test can reach the network even when the blocker is off. The blocked request is answered by
 * [WebViewClientWrapper.blockedResponse] before DNS is ever consulted; the media request is passed on
 * and fails locally - which is exactly what the assertions check for.
 */
@RunWith(AndroidJUnit4::class)
class WebViewInterceptionInstrumentedTest {

    /** The only host this test's blocker knows about. */
    private val blockList = setOf("blocked.test")

    /** The page's own origin, which must never be blocked. */
    private val pageUrl = "https://page.test/"

    private val candidates: MutableList<MediaCandidate> = Collections.synchronizedList(mutableListOf())
    private var webView: WebView? = null

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    @Before
    fun setUp() {
        AdBlockState.resetForTest()
        candidates.clear()
    }

    @After
    fun tearDown() {
        AdBlockState.resetForTest()
        webView?.let { view -> runCatching { instrumentation.runOnMainSync { view.destroy() } } }
        webView = null
    }

    @Test
    fun a_blocked_ad_request_is_answered_by_the_app_while_media_from_the_same_host_is_observed() {
        AdBlockState.isEnabled = true

        val loaded = loadPage()
        assertTrue("the page never finished loading", loaded.await(PAGE_TIMEOUT_SECONDS, TimeUnit.SECONDS))
        assertTrue(
            "the media request was never observed: ${candidates.map { it.mediaUrl }}",
            awaitTrue(OBSERVE_TIMEOUT_MILLIS) { candidates.any { it.mediaUrl.endsWith("clip.mp4") } }
        )
        assertTrue(
            "the ad request was never blocked",
            awaitTrue(OBSERVE_TIMEOUT_MILLIS) { AdBlockState.blockedThisRun >= 1 }
        )

        // The page asked for three things: its own script, an ad pixel, and a video on the ad host.
        // Exactly one of them may have been removed, and it is the one that is not a download.
        assertEquals("only the ad pixel may be blocked", 1, AdBlockState.blockedThisRun)
        assertEquals(
            "nothing but the video may reach the tray: ${candidates.map { it.mediaUrl }}",
            listOf("https://blocked.test/clip.mp4"),
            candidates.map { it.mediaUrl }
        )
    }

    @Test
    fun with_the_blocker_off_the_same_page_is_observed_exactly_as_before() {
        AdBlockState.isEnabled = false

        val loaded = loadPage()
        assertTrue("the page never finished loading", loaded.await(PAGE_TIMEOUT_SECONDS, TimeUnit.SECONDS))
        assertTrue(
            "the media request was never observed with the feature off: ${candidates.map { it.mediaUrl }}",
            awaitTrue(OBSERVE_TIMEOUT_MILLIS) { candidates.any { it.mediaUrl.endsWith("clip.mp4") } }
        )

        // The video is the evidence that the page's script ran to the end: every request was issued,
        // and the counter - which only a block can move - is still untouched.
        assertEquals("nothing may be blocked while the feature is off", 0, AdBlockState.blockedThisRun)
    }

    // ---- the page every case loads ----------------------------------------------------------------

    /**
     * Loads the page and returns the page-finished signal.
     *
     * The three requests are issued by the page's own script in one tick, in this order: the page's
     * own script (never blocked, it is the site), an ad pixel (blocked when the feature is on), and a
     * video on the ad host (never blocked, it is a download).
     */
    private fun loadPage(): CountDownLatch {
        val finished = CountDownLatch(1)
        val client = WebViewClientWrapper(
            onPageFinishedHook = { _, _ -> finished.countDown() },
            onMediaDetectedHook = { candidate -> candidates.add(candidate) },
            adBlocker = AdBlocker(blockList = blockList)
        )
        val created = createAndLoad(client)
        assertTrue("this device has no WebView provider, so nothing here can be checked", created)
        return finished
    }

    private fun createAndLoad(client: WebViewClientWrapper): Boolean {
        var created = false
        runCatching {
            instrumentation.runOnMainSync {
                val view = WebView(instrumentation.targetContext)
                view.settings.javaScriptEnabled = true
                view.webViewClient = client
                view.loadDataWithBaseURL(pageUrl, PAGE_HTML, "text/html", "utf-8", null)
                webView = view
                created = true
            }
        }
        return created
    }

    /** Polls [condition] until it holds or [timeoutMillis] is spent. */
    private fun awaitTrue(timeoutMillis: Long, condition: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return true
            Thread.sleep(POLL_MILLIS)
        }
        return condition()
    }

    private companion object {
        const val PAGE_TIMEOUT_SECONDS = 20L
        const val OBSERVE_TIMEOUT_MILLIS = 15_000L
        const val POLL_MILLIS = 100L

        val PAGE_HTML = """
            <html><body><script>
              fetch("https://page.test/app.js", {mode: "no-cors"}).catch(function () {});
              fetch("https://blocked.test/pixel.gif", {mode: "no-cors"}).catch(function () {});
              fetch("https://blocked.test/clip.mp4", {mode: "no-cors"}).catch(function () {});
            </script></body></html>
        """.trimIndent()
    }
}
