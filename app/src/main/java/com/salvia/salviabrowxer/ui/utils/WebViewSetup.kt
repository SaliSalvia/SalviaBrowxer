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

    private val chromeVersionPattern = Regex("Chrome/([0-9]+(?:\\.[0-9]+){1,3})")

    /** Long-lived flags that never change while the app runs. */
    @SuppressLint("SetJavaScriptEnabled")
    fun apply(
        webView: WebView,
        javaScriptEnabled: Boolean,
        desktopMode: Boolean,
        defaultAgent: String? = null
    ) {
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
            // Support user-initiated target=_blank/window.open tabs, but never permit a page to
            // spawn windows without a user gesture.
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(true)
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
        setUserAgent(webView, desktopMode, defaultAgent = defaultAgent)
    }

    /** Applies the mobile or desktop user agent; [defaultAgent] is the WebView's own mobile UA. */
    fun setUserAgent(webView: WebView, desktopMode: Boolean, defaultAgent: String?) {
        val target = userAgentFor(defaultAgent ?: webView.settings.userAgentString, desktopMode)
        if (webView.settings.userAgentString != target) webView.settings.userAgentString = target
    }

    fun userAgentFor(mobileAgent: String, desktopMode: Boolean): String =
        if (desktopMode) desktopUserAgentFor(mobileAgent) else mobileAgent

    /**
     * Keep desktop identification in step with the WebView provider actually installed on the
     * device. A fixed Chrome token ages out and can make sites serve the wrong desktop experience.
     */
    fun desktopUserAgentFor(mobileAgent: String): String {
        val version = chromeVersionPattern.find(mobileAgent)?.groupValues?.getOrNull(1)
            ?: chromeVersionPattern.find(Constants.FALLBACK_DESKTOP_USER_AGENT)?.groupValues?.getOrNull(1)
            ?: "122.0.0.0"
        return "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$version Safari/537.36"
    }
}
