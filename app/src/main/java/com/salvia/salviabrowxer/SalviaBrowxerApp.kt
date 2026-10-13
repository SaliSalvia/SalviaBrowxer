package com.salvia.salviabrowxer

import android.app.Application
import android.os.Build
import android.webkit.WebView
import coil.Coil
import com.salvia.salviabrowxer.core.session.WebViewCookies
import coil.ImageLoader
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class SalviaBrowxerApp : Application() {

    @Inject lateinit var imageLoader: ImageLoader

    override fun onCreate() {
        super.onCreate()
        Coil.setImageLoader(imageLoader)
        // Must run before the first WebView exists and only once, so it stays on the main thread.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching { WebView.setDataDirectorySuffix("main") }
        }
        // The same rule for the cookie store: a download reads it from a background thread, and the
        // store's first access is what initialises the WebView provider. Warm it up here, on the main
        // thread, before anything can race it. It reads nothing.
        WebViewCookies.warmUp()
    }
}
