package com.salvia.salviabrowxer.di

import android.content.Context
import androidx.room.Room
import com.salvia.salviabrowxer.core.database.AppDatabase
import com.salvia.salviabrowxer.core.storage.StorageManager
import com.salvia.salviabrowxer.data.datastore.SettingsDataStore
import com.salvia.salviabrowxer.data.datastore.SettingsDataStoreImpl
import com.salvia.salviabrowxer.data.repository.BookmarkRepository
import com.salvia.salviabrowxer.data.repository.BookmarkRepositoryImpl
import com.salvia.salviabrowxer.data.repository.DownloadRepository
import com.salvia.salviabrowxer.data.repository.DownloadRepositoryImpl
import com.salvia.salviabrowxer.data.repository.HistoryRepository
import com.salvia.salviabrowxer.data.repository.HistoryRepositoryImpl
import com.salvia.salviabrowxer.media.downloader.DownloadManager
import com.salvia.salviabrowxer.media.detector.MediaDetector
import com.salvia.salviabrowxer.media.resolver.MediaResolver
import com.salvia.salviabrowxer.media.resolver.DirectMediaResolver
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "salviabrowxer_db"
        ).fallbackToDestructiveMigration().build()
    }

    @Provides
    @Singleton
    fun provideStorageManager(@ApplicationContext context: Context): StorageManager {
        return StorageManager(context)
    }

    @Provides
    @Singleton
    fun provideSettingsDataStore(
        @ApplicationContext context: Context
    ): SettingsDataStore {
        return SettingsDataStoreImpl(context.dataStore)
    }

    @Provides
    @Singleton
    fun provideDownloadRepository(
        database: AppDatabase,
        storageManager: StorageManager
    ): DownloadRepository {
        return DownloadRepositoryImpl(database.downloadDao(), storageManager)
    }

    @Provides
    @Singleton
    fun provideBookmarkRepository(database: AppDatabase): BookmarkRepository {
        return BookmarkRepositoryImpl(database.bookmarkDao())
    }

    @Provides
    @Singleton
    fun provideHistoryRepository(database: AppDatabase): HistoryRepository {
        return HistoryRepositoryImpl(database.historyDao())
    }

    @Provides
    @Singleton
    fun provideDownloadManager(
        @ApplicationContext context: Context,
        downloadRepository: DownloadRepository
    ): DownloadManager {
        return DownloadManager(context, downloadRepository)
    }

    @Provides
    @Singleton
    fun provideMediaDetector(): MediaDetector {
        return MediaDetector()
    }

    @Provides
    @Singleton
    fun provideMediaResolver(okHttpClient: OkHttpClient): MediaResolver {
        return DirectMediaResolver(okHttpClient)
    }
}