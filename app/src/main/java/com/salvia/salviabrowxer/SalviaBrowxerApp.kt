package com.salvia.salviabrowxer

import android.app.Application
import android.os.Build
import android.webkit.WebView
import coil.Coil
import coil.ImageLoader
import com.salvia.salviabrowxer.core.database.AppDatabase
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class SalviaBrowxerApp : Application() {

    @Inject lateinit var imageLoader: ImageLoader

    val database: AppDatabase by lazy { AppDatabase.getDatabase(this) }

    override fun onCreate() {
        super.onCreate()
        Coil.setImageLoader(imageLoader)
        // Warm up WebView process in background so first page load is not janky
        Thread {
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    WebView.setDataDirectorySuffix("main")
                }
            }
        }.start()
    }
}
