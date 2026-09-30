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
    version = 4,
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

        /**
         * v2 -> v3 adds the transfer rate and the remaining time to `downloads`, so the task list
         * can show them. The declarations here have to match the entity exactly — Room compares the
         * migrated schema against the expected one and fails loudly on a difference, which is the
         * behaviour we want and the reason `bytesPerSecond` carries an explicit default.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `downloads` ADD COLUMN `bytesPerSecond` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `downloads` ADD COLUMN `etaSeconds` INTEGER")
            }
        }

        /**
         * v3 -> v4 lets a queued row remember which DASH rendition it wants, where a finished file
         * was published in the gallery, and how long it runs. All three are nullable, so existing
         * direct and HLS rows keep working untouched.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `downloads` ADD COLUMN `renditionId` TEXT")
                db.execSQL("ALTER TABLE `downloads` ADD COLUMN `exportedUri` TEXT")
                db.execSQL("ALTER TABLE `downloads` ADD COLUMN `durationMs` INTEGER")
            }
        }
    }
}

class Converters {
    @androidx.room.TypeConverter fun fromDownloadState(state: DownloadState): String = state.name
    @androidx.room.TypeConverter fun toDownloadState(state: String): DownloadState = DownloadState.valueOf(state)
}
