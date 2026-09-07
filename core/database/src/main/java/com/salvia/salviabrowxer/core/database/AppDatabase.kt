package com.salvia.salviabrowxer.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.salvia.salviabrowxer.core.database.dao.BookmarkDao
import com.salvia.salviabrowxer.core.database.dao.DownloadDao
import com.salvia.salviabrowxer.core.database.dao.HistoryDao
import com.salvia.salviabrowxer.core.database.entities.BookmarkEntity
import com.salvia.salviabrowxer.core.database.entities.DownloadEntity
import com.salvia.salviabrowxer.core.database.entities.HistoryEntity
import com.salvia.salviabrowxer.core.model.DownloadState

/**
 * Room database. A single [com.salvia.salviabrowxer.di.AppModule] provider is responsible for
 * creating it so there is exactly one instance in the process.
 */
@Database(
    entities = [
        DownloadEntity::class,
        BookmarkEntity::class,
        HistoryEntity::class
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun downloadDao(): DownloadDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun historyDao(): HistoryDao
}

class Converters {
    @androidx.room.TypeConverter
    fun fromDownloadState(state: DownloadState): String = state.name

    @androidx.room.TypeConverter
    fun toDownloadState(state: String): DownloadState = DownloadState.valueOf(state)
}