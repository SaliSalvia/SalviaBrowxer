package com.salvia.salviabrowxer

import android.app.Application
import com.salvia.salviabrowxer.core.database.AppDatabase
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class SalviaBrowxerApp : Application() {

    val database: AppDatabase by lazy {
        AppDatabase.getDatabase(this)
    }

    override fun onCreate() {
        super.onCreate()
        AppDatabase.getDatabase(this)
    }
}