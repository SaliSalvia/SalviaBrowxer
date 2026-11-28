package com.example.engine

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BrowserHardeningTest {

    // --- External intents ---

    @Test
    fun `dangerous schemes never leave the browser`() {
        val always: (Intent) -> Boolean = { true }
        listOf(
            "javascript:alert(1)",
            "file:///sdcard/secret.txt",
            "content://com.victim.provider/data",
            "data:text/html,hi",
            "",
        ).forEach { url ->
            assertTrue(url, ExternalIntentPolicy.resolve(url, always) is ExternalIntentPolicy.Decision.Block)
        }
    }

    @Test
    fun `web intents are sanitized to browsable activities without components or grant flags`() {
        val url = "intent:#Intent;component=com.victim/.PrivateActivity;launchFlags=0x43;end"
        val decision = ExternalIntentPolicy.resolve(url) { true }
        assertTrue(decision is ExternalIntentPolicy.Decision.LaunchExternal)
        val intent = (decision as ExternalIntentPolicy.Decision.LaunchExternal).intent
        assertNull(intent.component)
        assertTrue(intent.hasCategory(Intent.CATEGORY_BROWSABLE))
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK, intent.flags)
        assertEquals(0, intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    @Test
    fun `intent links use the http fallback url when no app can handle them`() {
        val url = "intent://scan/#Intent;scheme=zxing;package=com.google.zxing.client.android;" +
            "S.browser_fallback_url=https%3A%2F%2Fexample.com%2Fapp;end"
        val decision = ExternalIntentPolicy.resolve(url) { false }
        assertEquals(ExternalIntentPolicy.Decision.LoadInBrowser("https://example.com/app"), decision)

        val unsafeFallback = "intent://scan/#Intent;scheme=zxing;S.browser_fallback_url=javascript%3Aalert(1);end"
        assertTrue(ExternalIntentPolicy.resolve(unsafeFallback) { false } is ExternalIntentPolicy.Decision.Block)
    }

    @Test
    fun `intent wrapping a plain web link stays in the browser`() {
        val decision = ExternalIntentPolicy.resolve("intent://example.com/path#Intent;scheme=https;end") { true }
        assertEquals(ExternalIntentPolicy.Decision.LoadInBrowser("https://example.com/path"), decision)
    }

    @Test
    fun `missing target app opens its store listing and plain schemes need a handler`() {
        val decision = ExternalIntentPolicy.resolve("intent:#Intent;package=com.foo.bar;end") { candidate ->
            candidate.data?.scheme == "market"
        }
        assertTrue(decision is ExternalIntentPolicy.Decision.LaunchExternal)
        assertEquals("market", (decision as ExternalIntentPolicy.Decision.LaunchExternal).targetLabel)

        assertTrue(ExternalIntentPolicy.resolve("mailto:a@example.com") { true } is ExternalIntentPolicy.Decision.LaunchExternal)
        assertTrue(ExternalIntentPolicy.resolve("tel:123") { false } is ExternalIntentPolicy.Decision.Block)
    }

    // --- Bridge guard / HTTPS-First ---

    @Test
    fun `bridge guard rate limits per tab and channel`() {
        var now = 0L
        val guard = BridgeGuard(maxCallsPerWindow = 3, windowMs = 1_000L, clock = { now })
        repeat(3) { assertTrue(guard.allow("t1", "hook")) }
        assertFalse(guard.allow("t1", "hook"))
        assertTrue(guard.allow("t1", "dom"))
        assertTrue(guard.allow("t2", "hook"))
        now = 1_000L
        assertTrue(guard.allow("t1", "hook"))
        assertEquals("abc", BridgeGuard.clip("abcdef", 3))
        assertFalse(BridgeGuard.isAcceptableUrl(" "))
        assertFalse(BridgeGuard.isAcceptableUrl("x".repeat(BridgeGuard.MAX_URL_CHARS + 1)))
    }

    @Test
    fun `https first upgrades public hosts and falls back after failure`() {
        var now = 0L
        val policy = HttpsUpgradePolicy(clock = { now })
        assertEquals("https://example.com/a?b=1", policy.upgradeFor("t", "http://example.com/a?b=1"))
        assertNull(policy.upgradeFor("t", "https://example.com/"))
        assertNull(policy.upgradeFor("t", "http://localhost:8080/"))
        assertNull(policy.upgradeFor("t", "http://192.168.1.1/"))
        assertNull(policy.upgradeFor("t", "http://router/"))
        assertNull(policy.upgradeFor("t", "http://nas.local/"))

        assertEquals("https://legacy.example/", policy.upgradeFor("t", "http://legacy.example/"))
        assertEquals("http://legacy.example/", policy.fallbackFor("t", "https://legacy.example/"))
        assertNull(policy.upgradeFor("t", "http://legacy.example/other"))
    }

    @Test
    fun `https first stops when the site redirects back to http`() {
        var now = 0L
        val policy = HttpsUpgradePolicy(clock = { now })
        assertEquals("https://loop.example/", policy.upgradeFor("t", "http://loop.example/"))
        now = 500L
        assertNull(policy.upgradeFor("t", "http://loop.example/"))
        now = 60_000L
        assertNull(policy.upgradeFor("t", "http://loop.example/"))
    }

    // --- Ad blocker / extensions / XML ---

    @Test
    fun `same site check uses label boundaries`() {
        assertTrue(AdBlockerEngine.isSameSiteHost("cdn.example.com", "example.com"))
        assertTrue(AdBlockerEngine.isSameSiteHost("example.com", "www.example.com"))
        assertFalse(AdBlockerEngine.isSameSiteHost("evilexample.com", "example.com"))
        assertFalse(AdBlockerEngine.isSameSiteHost("example.com", null))
    }

    @Test
    fun `extension match patterns are validated and never run on local schemes`() {
        assertTrue(ExtensionEngine.isValidMatchPattern("*://*/*"))
        assertTrue(ExtensionEngine.isValidMatchPattern("https://*.example.com/*"))
        assertTrue(ExtensionEngine.isValidMatchPattern("<all_urls>"))
        assertFalse(ExtensionEngine.isValidMatchPattern("file:///*"))
        assertFalse(ExtensionEngine.isValidMatchPattern("example.com"))
        assertTrue(ExtensionEngine.isAllSitesPattern("*://*/*"))
        assertFalse(ExtensionEngine.isAllSitesPattern("https://*.example.com/*"))
        assertFalse(ExtensionEngine.matchesUrl("*://*/*", "file:///sdcard/a.html"))
        assertFalse(ExtensionEngine.matchesUrl("*://*/*", "content://downloads/1"))
        assertTrue(ExtensionEngine.matchesUrl("*://*/*", "https://example.com/"))
    }

    @Test
    fun `user scripts are not injected into incognito tabs`() {
        val custom = ExtensionEngine.builtInExtensions().first().copy(
            id = "custom_test",
            isBuiltIn = false,
            isInstalled = true,
            isEnabled = true,
            runAt = "document_end",
            matchPattern = "*://*/*",
            cssCode = "",
            jsCode = "window.__customMarker = 1;",
        )
        val normal = ExtensionEngine.buildInjectionScript(listOf(custom), "https://example.com", "document_end")
        val private = ExtensionEngine.buildInjectionScript(listOf(custom), "https://example.com", "document_end", isIncognito = true)
        assertTrue(normal.contains("__customMarker"))
        assertFalse(private.contains("__customMarker"))
    }

    @Test
    fun `dash parser rejects documents with a DTD`() {
        val xxe = """<?xml version="1.0"?><!DOCTYPE MPD [<!ENTITY x SYSTEM "file:///etc/passwd">]><MPD type="static">&x;</MPD>"""
        assertTrue(DashManifestParser.containsDtd(xxe))
        assertEquals("INVALID", DashManifestParser.parse(xxe, "https://cdn.example.com/a.mpd").unsupported)
        assertFalse(DashManifestParser.containsDtd("""<?xml version="1.0"?><MPD type="static"></MPD>"""))
    }

    @Test
    fun `privacy headers follow the user preference`() {
        assertEquals(mapOf("DNT" to "1", "Sec-GPC" to "1"), BrowserScripts.privacyHeaders(true))
        assertTrue(BrowserScripts.privacyHeaders(false).isEmpty())
        assertTrue(BrowserScripts.privacySignalsScript().contains("globalPrivacyControl"))
        assertTrue(BrowserScripts.readerModeScript(dark = true).contains("__salviaReaderOriginal"))
    }
}
