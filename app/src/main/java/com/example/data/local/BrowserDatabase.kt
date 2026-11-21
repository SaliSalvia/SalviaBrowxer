package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.engine.ExtensionEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        BookmarkEntity::class,
        HistoryEntity::class,
        ExtensionEntity::class,
        DownloadEntity::class,
        AdBlockRuleEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class BrowserDatabase : RoomDatabase() {
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun historyDao(): HistoryDao
    abstract fun extensionDao(): ExtensionDao
    abstract fun downloadDao(): DownloadDao
    abstract fun adBlockRuleDao(): AdBlockRuleDao

    companion object {
        @Volatile
        private var INSTANCE: BrowserDatabase? = null

        fun getInstance(context: Context): BrowserDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    BrowserDatabase::class.java,
                    "salvia_browser_db",
                )
                    .fallbackToDestructiveMigration(true)
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            CoroutineScope(Dispatchers.IO).launch {
                                val database = getInstance(context)
                                database.extensionDao().insertAllIgnore(ExtensionEngine.builtInExtensions())
                                defaultSpeedDialBookmarks().forEach {
                                    database.bookmarkDao().insert(it)
                                }
                            }
                        }
                    })
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private fun defaultSpeedDialBookmarks(): List<BookmarkEntity> = listOf(
            BookmarkEntity(
                title = "Gmail",
                url = "https://mail.google.com",
                category = "ایمیل و گوگل",
                isPinnedSpeedDial = true,
            ),
            BookmarkEntity(
                title = "Google",
                url = "https://www.google.com",
                category = "جستجو",
                isPinnedSpeedDial = true,
            ),
            BookmarkEntity(
                title = "YouTube",
                url = "https://m.youtube.com",
                category = "ویدیو",
                isPinnedSpeedDial = true,
            ),
            BookmarkEntity(
                title = "Aparat",
                url = "https://www.aparat.com",
                category = "ویدیو",
                isPinnedSpeedDial = true,
            ),
            BookmarkEntity(
                title = "GitHub",
                url = "https://github.com",
                category = "توسعه",
                isPinnedSpeedDial = true,
            ),
            BookmarkEntity(
                title = "Wikipedia",
                url = "https://fa.wikipedia.org",
                category = "دانشنامه",
                isPinnedSpeedDial = true,
            ),
            BookmarkEntity(
                title = "DuckDuckGo",
                url = "https://duckduckgo.com",
                category = "حریم خصوصی",
                isPinnedSpeedDial = true,
            ),
            BookmarkEntity(
                title = "MDN Docs",
                url = "https://developer.mozilla.org",
                category = "توسعه",
                isPinnedSpeedDial = true,
            ),
        )
    }
}
