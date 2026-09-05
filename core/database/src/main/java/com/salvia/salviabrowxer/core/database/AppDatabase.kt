package com.salvia.salviabrowxer.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.salvia.salviabrowxer.core.database.dao.BookmarkDao
import com.salvia.salviabrowxer.core.database.dao.DownloadDao
import com.salvia.salviabrowxer.core.database.dao.HistoryDao
import com.salvia.salviabrowxer.core.database.entities.BookmarkEntity
import com.salvia.salviabrowxer.core.database.entities.DownloadEntity
import com.salvia.salviabrowxer.core.database.entities.HistoryEntity
import com.salvia.salviabrowxer.core.model.DownloadState

@Database(
    entities = [
        DownloadEntity::class,
        BookmarkEntity::class,
        HistoryEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun downloadDao(): DownloadDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun historyDao(): HistoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "salviabrowxer_db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}

class Converters {
    @androidx.room.TypeConverter
    fun fromDownloadState(state: DownloadState): String = state.name

    @androidx.room.TypeConverter
    fun toDownloadState(state: String): DownloadState = DownloadState.valueOf(state)
}