package com.salvia.salviabrowxer.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.salvia.salviabrowxer.core.database.dao.BookmarkDao
import com.salvia.salviabrowxer.core.database.dao.DownloadDao
import com.salvia.salviabrowxer.core.database.dao.HistoryDao
import com.salvia.salviabrowxer.core.database.entities.BookmarkEntity
import com.salvia.salviabrowxer.core.database.entities.DownloadEntity
import com.salvia.salviabrowxer.core.database.entities.HistoryEntity
import com.salvia.salviabrowxer.core.model.DownloadState

@Database(
    entities = [DownloadEntity::class, BookmarkEntity::class, HistoryEntity::class],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun downloadDao(): DownloadDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun historyDao(): HistoryDao

    companion object {
        /**
         * v1 -> v2 adds two indexes to `downloads`, so every queued, bookmarked and visited row
         * survives the upgrade.
         *
         * There is deliberately no `fallbackToDestructiveMigration()`: a schema that cannot be
         * upgraded must fail loudly instead of silently wiping the user's queue and history.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_downloads_status` ON `downloads` (`status`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_downloads_createdAt` ON `downloads` (`createdAt`)")
            }
        }
    }
}

class Converters {
    @androidx.room.TypeConverter fun fromDownloadState(state: DownloadState): String = state.name
    @androidx.room.TypeConverter fun toDownloadState(state: String): DownloadState = DownloadState.valueOf(state)
}
