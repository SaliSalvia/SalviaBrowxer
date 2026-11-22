package com.example.engine

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import java.util.WeakHashMap

/**
 * Central WebView configuration.
 *
 * Private tabs use a dedicated AndroidX WebKit profile when the installed
 * WebView implementation advertises MULTI_PROFILE. Unsupported implementations
 * keep the legacy best-effort private mode, but are never globally cleared:
 * clearing the default CookieManager/WebStorage would erase normal browsing data.
 */
object WebViewConfigurator {

    private const val PRIVATE_PROFILE_NAME = "salvia_incognito"

    private const val DESKTOP_UA =
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
    private const val FALLBACK_MOBILE_UA =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"

    private val lastAppliedConfigs = WeakHashMap<WebView, String>()
    private val isolatedIncognitoWebViews = WeakHashMap<WebView, Boolean>()

    @Volatile
    private var cachedMobileUa: String? = null

    fun supportsIsolatedIncognitoProfiles(): Boolean =
        runCatching {
            WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)
        }.getOrDefault(false)

    /**
     * Generates a clean Chromium Mobile or Desktop User-Agent without the `; wv)` token.
     * Stripping `; wv)` is critical so Google Sign-In, Gmail, and OAuth providers never reject
     * login attempts with `disallowed_useragent`.
     */
    fun resolveUserAgent(context: Context, desktopMode: Boolean): String {
        if (desktopMode) return DESKTOP_UA
        cachedMobileUa?.let { return it }
        val defaultUa = runCatching { WebSettings.getDefaultUserAgent(context) }.getOrNull().orEmpty()
        val cleaned = defaultUa
            .replace("; wv)", ")")
            .replace("; wv;", ";")
            .replace("Version/4.0 ", "")
        val resolved = if (cleaned.isBlank() || !cleaned.contains("Chrome/", ignoreCase = true)) {
            FALLBACK_MOBILE_UA
        } else {
            cleaned
        }
        cachedMobileUa = resolved
        return resolved
    }

    /**
     * Returns true when a private WebView was successfully attached to the
     * dedicated private profile. On unsupported WebView versions this returns
     * false and the caller should disclose that isolation is best-effort.
     */
    @SuppressLint("SetJavaScriptEnabled")
    fun configure(
        webView: WebView,
        isIncognito: Boolean,
        desktopMode: Boolean,
        javaScriptEnabled: Boolean,
        blockThirdPartyCookiesIncognito: Boolean,
    ): Boolean {
        val signature = "$isIncognito|$desktopMode|$javaScriptEnabled|$blockThirdPartyCookiesIncognito"
        val isolated = synchronized(lastAppliedConfigs) {
            val previous = lastAppliedConfigs[webView]
            if (previous == null && isIncognito) {
                val profileApplied = if (supportsIsolatedIncognitoProfiles()) {
                    runCatching {
                        // Must happen before accessing WebSettings, evaluating JS or loading a URL.
                        WebViewCompat.setProfile(webView, PRIVATE_PROFILE_NAME)
                        true
                    }.getOrDefault(false)
                } else {
                    false
                }
                isolatedIncognitoWebViews[webView] = profileApplied
            }

            if (previous == signature) {
                return !isIncognito || isolatedIncognitoWebViews[webView] == true
            }
            lastAppliedConfigs[webView] = signature
            !isIncognito || isolatedIncognitoWebViews[webView] == true
        }

        val context = webView.context
        val settings = webView.settings

        settings.javaScriptEnabled = javaScriptEnabled
        settings.javaScriptCanOpenWindowsAutomatically = true
        settings.setSupportMultipleWindows(true)
        settings.domStorageEnabled = true
        settings.setGeolocationEnabled(true)
        @Suppress("DEPRECATION")
        settings.databaseEnabled = !isIncognito
        settings.loadsImagesAutomatically = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.setSupportZoom(true)
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.allowFileAccess = false
        settings.allowContentAccess = true
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        settings.userAgentString = resolveUserAgent(context, desktopMode)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            settings.safeBrowsingEnabled = true
        }

        if (isIncognito) {
            settings.cacheMode = WebSettings.LOAD_NO_CACHE
            @Suppress("DEPRECATION")
            settings.saveFormData = false
        } else {
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            @Suppress("DEPRECATION")
            settings.saveFormData = true
        }

        val cookieManager = cookieManagerFor(webView)
        cookieManager.setAcceptCookie(true)
        val allowThirdParty = if (isIncognito) !blockThirdPartyCookiesIncognito else true
        cookieManager.setAcceptThirdPartyCookies(webView, allowThirdParty)
        return isolated
    }

    /**
     * Uses the CookieManager belonging to the WebView's current profile.
     * This is required for authenticated media requests in isolated private tabs.
     */
    fun cookieHeader(webView: WebView?, url: String): String {
        val manager = cookieManagerForTab(webView, isIncognito = false) ?: return ""
        return runCatching { manager.getCookie(url).orEmpty() }.getOrDefault("")
    }

    /**
     * Returns the CookieManager bound to the requested browsing profile.
     * Incognito never falls back to the default CookieManager, including when
     * its WebView is hibernated or multi-profile is unsupported.
     */
    fun cookieManagerForTab(webView: WebView?, isIncognito: Boolean): CookieManager? {
        if (!isIncognito) return CookieManager.getInstance()
        if (webView == null) return null
        val privateProfileIsolated = synchronized(lastAppliedConfigs) {
            isolatedIncognitoWebViews[webView] == true &&
                lastAppliedConfigs[webView]?.startsWith("true|") == true
        }
        if (!privateProfileIsolated) return null
        return runCatching { WebViewCompat.getProfile(webView).cookieManager }.getOrNull()
    }

    private fun cookieManagerFor(webView: WebView): CookieManager {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            return runCatching { WebViewCompat.getProfile(webView).cookieManager }
                .getOrDefault(CookieManager.getInstance())
        }
        return CookieManager.getInstance()
    }

    /**
     * Clears data only inside the dedicated private profile. Never calls global
     * WebStorage.deleteAllData/removeAllCookies, which would wipe normal browsing
     * data. The installed WebView may complete storage deletion asynchronously.
     */
    @Suppress("UNUSED_PARAMETER")
    fun clearIncognitoAndVolatileData(context: Context, clearCookiesToo: Boolean): Boolean {
        if (!supportsIsolatedIncognitoProfiles()) return false
        return runCatching {
            val store = ProfileStore.getInstance()
            // Delete the dedicated profile itself rather than clearing only selected stores.
            // Do not call getOrCreateProfile/getProfile here: ProfileStore rejects deletion
            // while that API has an in-memory Profile handle. All private WebViews must
            // already have been destroyed by the caller.
            if (PRIVATE_PROFILE_NAME !in store.allProfileNames) {
                true
            } else {
                store.deleteProfile(PRIVATE_PROFILE_NAME)
            }
        }.getOrDefault(false)
    }

    fun clearAllBrowsingData(context: Context, hadLiveWebViews: Boolean = false) {
        runCatching {
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
            android.webkit.WebStorage.getInstance().deleteAllData()
            android.webkit.WebViewDatabase.getInstance(context).clearHttpAuthUsernamePassword()
            if (!hadLiveWebViews) {
                WebView(context).apply {
                    clearCache(true)
                    clearHistory()
                    clearFormData()
                    destroy()
                }
            }
        }
    }
}
