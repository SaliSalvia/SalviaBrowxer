package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.engine.ExtensionEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@Database(
    entities = [
        BookmarkEntity::class,
        HistoryEntity::class,
        ExtensionEntity::class,
        DownloadEntity::class,
        AdBlockRuleEntity::class,
        MediaDownloadRequestContextEntity::class,
    ],
    version = 6,
    exportSchema = false,
)
abstract class BrowserDatabase : RoomDatabase() {
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun historyDao(): HistoryDao
    abstract fun extensionDao(): ExtensionDao
    abstract fun downloadDao(): DownloadDao
    abstract fun adBlockRuleDao(): AdBlockRuleDao

    companion object {

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS media_download_request_contexts (" +
                        "downloadId INTEGER NOT NULL, contextData TEXT NOT NULL, expiresAt INTEGER NOT NULL, " +
                        "createdAt INTEGER NOT NULL, PRIMARY KEY(downloadId), " +
                        "FOREIGN KEY(downloadId) REFERENCES downloads(id) ON UPDATE NO ACTION ON DELETE CASCADE)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_media_download_request_contexts_expiresAt " +
                        "ON media_download_request_contexts(expiresAt)",
                )
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE downloads ADD COLUMN manifestRepresentationId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE downloads ADD COLUMN manifestAudioRepresentationId TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE downloads ADD COLUMN isDesktopMode INTEGER NOT NULL DEFAULT 0",
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE downloads ADD COLUMN isIncognito INTEGER NOT NULL DEFAULT 0",
                )
            }
        }

        @Volatile
        private var INSTANCE: BrowserDatabase? = null

        // Process-lifetime scope for one-shot seeding; SupervisorJob keeps a failure isolated.
        private val seedScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        fun getInstance(context: Context): BrowserDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    BrowserDatabase::class.java,
                    "salvia_browser_db",
                )
                    .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                    // Version 1 predates the migration history; rebuilding it is the only safe
                    // path. Explicit migrations above always win for every later version.
                    .fallbackToDestructiveMigrationFrom(1)
                    .fallbackToDestructiveMigrationOnDowngrade()
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            seedScope.launch {
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
                category = "Mail & Google",
                isPinnedSpeedDial = true,
            ),
            BookmarkEntity(
                title = "Google",
                url = "https://www.google.com",
                category = "Search",
                isPinnedSpeedDial = true,
            ),
            BookmarkEntity(
                title = "YouTube",
                url = "https://m.youtube.com",
                category = "Video",
                isPinnedSpeedDial = true,
            ),
            BookmarkEntity(
                title = "Aparat",
                url = "https://www.aparat.com",
                category = "Video",
                isPinnedSpeedDial = true,
            ),
            BookmarkEntity(
                title = "GitHub",
                url = "https://github.com",
                category = "Developer",
                isPinnedSpeedDial = true,
            ),
            BookmarkEntity(
                title = "Wikipedia",
                url = "https://www.wikipedia.org",
                category = "Encyclopedia",
                isPinnedSpeedDial = true,
            ),
            BookmarkEntity(
                title = "DuckDuckGo",
                url = "https://duckduckgo.com",
                category = "Privacy",
                isPinnedSpeedDial = true,
            ),
            BookmarkEntity(
                title = "MDN Docs",
                url = "https://developer.mozilla.org",
                category = "Developer",
                isPinnedSpeedDial = true,
            ),
        )
    }
}
