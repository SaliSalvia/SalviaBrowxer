package com.salvia.salviabrowxer

import android.app.Application
import android.os.Build
import android.os.Looper
import android.webkit.WebView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import coil.Coil
import com.salvia.salviabrowxer.core.session.WebViewCookies
import coil.ImageLoader
import com.salvia.salviabrowxer.data.datastore.SettingsDataStore
import com.salvia.salviabrowxer.data.diagnostics.DiagnosticsLog
import com.salvia.salviabrowxer.ui.utils.AdBlockState
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@HiltAndroidApp
class SalviaBrowxerApp : Application() {

    @Inject lateinit var imageLoader: ImageLoader
    @Inject lateinit var settingsDataStore: SettingsDataStore
    @Inject lateinit var diagnostics: DiagnosticsLog

    /**
     * Outlives every screen: the ad blocker's state has to be correct the moment a WebView asks, and
     * the blocked-request count is written back in batches rather than once per request.
     */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        Coil.setImageLoader(imageLoader)
        installCrashCapture()
        // Must run before the first WebView exists and only once, so it stays on the main thread.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching { WebView.setDataDirectorySuffix("main") }
        }
        // Bringing the WebView provider up is hundreds of milliseconds of library load, and the
        // first frame of the welcome ceremony should not pay for it. Where the installed provider
        // supports it, ask it to come up in the background now; the cookie store — whose first
        // access is what initialises the provider where that API is missing — is warmed from the
        // main looper's first idle gap instead of blocking onCreate. A download reads the store
        // from a background thread, and by then the gap has long passed.
        runCatching {
            if (WebViewFeature.isStartupFeatureSupported(this, WebViewFeature.STARTUP_FEATURE_FLAG_PROVIDER_READY)) {
                WebViewCompat.startService(this, setOf(WebViewFeature.STARTUP_FEATURE_FLAG_PROVIDER_READY))
            }
        }
        Looper.myQueue().addIdleHandler {
            WebViewCookies.warmUp()
            false
        }
        startAdBlockWiring()
    }

    /**
     * Writes a crash to the local diagnostics file, then hands over to the previous handler (the
     * framework's, which shows the dialog and kills the process).
     *
     * This is the privacy-preserving stand-in for a crash-reporting SDK: the stack trace stays on
     * the device, bounded to the newest few by [DiagnosticsLog], and leaves it only when the user
     * presses "Share diagnostics". The write is best-effort by design — a crash handler that can
     * itself crash would be worse than no handler.
     */
    private fun installCrashCapture() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { diagnostics.crash(thread, throwable) }
            previous?.uncaughtException(thread, throwable)
        }
    }

    /**
     * Feeds the opt-in ad blocker from settings and persists what it blocked.
     *
     * The decision itself is made on the WebView's thread from plain values ([AdBlockState]), so the
     * settings it depends on are pushed here rather than read there. The counter is batched: one
     * DataStore write per blocked request would be absurd, one write every few seconds is invisible
     * and still survives a kill.
     */
    private fun startAdBlockWiring() {
        appScope.launch {
            settingsDataStore.isAdBlockEnabled.collectLatest { enabled -> AdBlockState.isEnabled = enabled }
        }
        appScope.launch {
            settingsDataStore.adBlockDisabledSites.collectLatest { sites -> AdBlockState.disabledSites = sites }
        }
        appScope.launch {
            while (isActive) {
                delay(FLUSH_INTERVAL_MILLIS)
                val pending = AdBlockState.pendingFlush.getAndSet(0)
                if (pending > 0) runCatching { settingsDataStore.addAdBlockBlockedRequests(pending) }
            }
        }
    }

    private companion object {
        /** Long enough to be one small write, short enough to survive an app kill. */
        const val FLUSH_INTERVAL_MILLIS = 5_000L
    }
}
