package com.salvia.salviabrowxer.ui.utils

import android.annotation.SuppressLint
import android.os.Build
import android.webkit.WebSettings
import android.webkit.WebView

/**
 * The single place WebView settings are applied.
 *
 * `TabWebViewStore` calls [apply] for every tab it creates, so tab one and tab eight are
 * configured identically. There is deliberately no second WebView configuration anywhere in the
 * app: the canonical media detection client is [WebViewClientWrapper], and the canonical page
 * setup is here.
 */
object WebViewSetup {

    /** Long-lived flags that never change while the app runs. */
    @SuppressLint("SetJavaScriptEnabled")
    fun apply(webView: WebView, javaScriptEnabled: Boolean, desktopMode: Boolean) {
        webView.setLayerType(WebView.LAYER_TYPE_HARDWARE, null)
        webView.isVerticalScrollBarEnabled = false
        webView.isHorizontalScrollBarEnabled = false
        webView.overScrollMode = WebView.OVER_SCROLL_NEVER

        with(webView.settings) {
            this.javaScriptEnabled = javaScriptEnabled
            domStorageEnabled = true
            databaseEnabled = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            loadWithOverviewMode = true
            useWideViewPort = true
            cacheMode = WebSettings.LOAD_DEFAULT
            // Media must autoplay inline or sniffing via HTMLMediaElement.src never fires.
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            allowFileAccess = false
            allowContentAccess = false
            allowFileAccessFromFileURLs = false
            allowUniversalAccessFromFileURLs = false
            javaScriptCanOpenWindowsAutomatically = false
            @Suppress("DEPRECATION")
            setGeolocationEnabled(false)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                safeBrowsingEnabled = true
                // Instant render of already-loaded content while scrolling.
                offscreenPreRaster = true
            }
            @Suppress("DEPRECATION")
            setRenderPriority(WebSettings.RenderPriority.HIGH)
        }
        setUserAgent(webView, desktopMode, defaultAgent = null)
    }

    /** Applies the mobile or desktop user agent; [defaultAgent] is the WebView's own mobile UA. */
    fun setUserAgent(webView: WebView, desktopMode: Boolean, defaultAgent: String?) {
        val target = userAgentFor(defaultAgent ?: webView.settings.userAgentString, desktopMode)
        if (webView.settings.userAgentString != target) webView.settings.userAgentString = target
    }

    fun userAgentFor(mobileAgent: String, desktopMode: Boolean): String =
        if (desktopMode) Constants.DESKTOP_USER_AGENT else mobileAgent
}
