package com.example.engine

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewDatabase

import java.util.WeakHashMap

object WebViewConfigurator {

    private val lastAppliedConfigs = WeakHashMap<WebView, String>()

    /**
     * Generates a clean Chromium Mobile or Desktop User-Agent without the `; wv)` token.
     * Stripping `; wv)` is critical so Google Sign-In, Gmail, and OAuth providers never reject
     * login attempts with `disallowed_useragent`.
     */
    fun resolveUserAgent(context: Context, desktopMode: Boolean): String {
        if (desktopMode) {
            return "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
        }
        val defaultUa = runCatching { WebSettings.getDefaultUserAgent(context) }.getOrNull().orEmpty()
        val cleaned = defaultUa
            .replace("; wv)", ")")
            .replace("; wv;", ";")
            .replace("Version/4.0 ", "")
        if (cleaned.isBlank() || !cleaned.contains("Chrome/", ignoreCase = true)) {
            return "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
        }
        return cleaned
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun configure(
        webView: WebView,
        isIncognito: Boolean,
        desktopMode: Boolean,
        javaScriptEnabled: Boolean,
        blockThirdPartyCookiesIncognito: Boolean,
    ) {
        val signature = "$isIncognito|$desktopMode|$javaScriptEnabled|$blockThirdPartyCookiesIncognito"
        if (lastAppliedConfigs[webView] == signature) return
        lastAppliedConfigs[webView] = signature

        val context = webView.context
        val settings = webView.settings

        settings.javaScriptEnabled = javaScriptEnabled
        settings.javaScriptCanOpenWindowsAutomatically = true
        settings.setSupportMultipleWindows(true)
        settings.domStorageEnabled = true
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

        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        val allowThirdParty = if (isIncognito) !blockThirdPartyCookiesIncognito else true
        cookieManager.setAcceptThirdPartyCookies(webView, allowThirdParty)
    }

    fun clearIncognitoAndVolatileData(context: Context, clearCookiesToo: Boolean) {
        runCatching {
            WebStorage.getInstance().deleteAllData()
            WebViewDatabase.getInstance(context).clearHttpAuthUsernamePassword()
            if (clearCookiesToo) {
                CookieManager.getInstance().removeSessionCookies(null)
                CookieManager.getInstance().flush()
            }
        }
    }

    fun clearAllBrowsingData(context: Context) {
        runCatching {
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
            WebStorage.getInstance().deleteAllData()
            WebViewDatabase.getInstance(context).clearHttpAuthUsernamePassword()
            WebView(context).apply {
                clearCache(true)
                clearHistory()
                clearFormData()
                destroy()
            }
        }
    }
}
