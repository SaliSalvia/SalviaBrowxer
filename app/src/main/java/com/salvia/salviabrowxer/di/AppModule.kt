package com.salvia.salviabrowxer.di

import android.content.Context
import androidx.room.Room
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.salvia.salviabrowxer.core.database.AppDatabase
import com.salvia.salviabrowxer.core.session.WebViewCookies
import com.salvia.salviabrowxer.core.storage.StorageManager
import com.salvia.salviabrowxer.data.datastore.SessionDataStore
import com.salvia.salviabrowxer.data.download.DownloadArtifactCleaner
import com.salvia.salviabrowxer.data.download.androidArtifactCleaner
import com.salvia.salviabrowxer.data.datastore.SessionRestorer
import com.salvia.salviabrowxer.data.datastore.SettingsDataStore
import com.salvia.salviabrowxer.data.datastore.SettingsDataStoreImpl
import com.salvia.salviabrowxer.data.datastore.dataStore
import com.salvia.salviabrowxer.data.datastore.sessionDataStore
import com.salvia.salviabrowxer.data.diagnostics.DiagnosticsLog
import com.salvia.salviabrowxer.data.update.UpdateChecker
import com.salvia.salviabrowxer.data.security.LockedFolderStore
import com.salvia.salviabrowxer.data.storage.SafDestinationStore
import com.salvia.salviabrowxer.data.repository.BookmarkRepository
import com.salvia.salviabrowxer.data.repository.BookmarkRepositoryImpl
import com.salvia.salviabrowxer.data.repository.DownloadRepository
import com.salvia.salviabrowxer.data.repository.DownloadRepositoryImpl
import com.salvia.salviabrowxer.data.repository.HistoryRepository
import com.salvia.salviabrowxer.data.repository.HistoryRepositoryImpl
import com.salvia.salviabrowxer.media.detector.DomMediaDetector
import com.salvia.salviabrowxer.media.detector.MediaDetector
import com.salvia.salviabrowxer.media.downloader.DashDownloader
import com.salvia.salviabrowxer.media.downloader.DownloadManager
import com.salvia.salviabrowxer.media.downloader.MediaMetadataReader
import com.salvia.salviabrowxer.media.downloader.MediaRemuxer
import com.salvia.salviabrowxer.media.downloader.VideoTranscoder
import com.salvia.salviabrowxer.media.downloader.SessionCookieJar
import com.salvia.salviabrowxer.media.downloader.TrackMerger
import com.salvia.salviabrowxer.media.resolver.DirectMediaResolver
import com.salvia.salviabrowxer.media.resolver.MediaResolver
import com.salvia.salviabrowxer.ui.utils.Constants
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            // One jar for every request the app makes on a page's behalf — the quality probe, the
            // manifest fetches, the segments and the direct transfer — so a site that guards media
            // with the session the user is browsing with serves the download too. Read-only: see
            // SessionCookieJar.
            .cookieJar(SessionCookieJar(WebViewCookies::headerFor))
            .connectTimeout(Constants.CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(Constants.READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(Constants.WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(90, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .followSslRedirects(true)
            .connectionPool(ConnectionPool(8, 5, TimeUnit.MINUTES))
            .build()
    }

    @Provides
    @Singleton
    fun provideHlsDownloader(okHttpClient: OkHttpClient): com.salvia.salviabrowxer.media.downloader.HlsDownloader =
        com.salvia.salviabrowxer.media.downloader.HlsDownloader(okHttpClient)

    @Provides
    @Singleton
    fun provideDashDownloader(okHttpClient: OkHttpClient): DashDownloader = DashDownloader(okHttpClient)

    @Provides
    @Singleton
    fun provideMediaRemuxer(): MediaRemuxer = MediaRemuxer()

    /**
     * The re-encoder behind "Convert to MP4". A singleton because a conversion holds real codecs:
     * starting a second one while the first is running is how a phone runs out of them.
     */
    @Provides
    @Singleton
    fun provideVideoTranscoder(): VideoTranscoder = VideoTranscoder()

    @Provides
    @Singleton
    fun provideTrackMerger(): TrackMerger = TrackMerger()

    @Provides
    @Singleton
    fun provideMediaMetadataReader(): MediaMetadataReader = MediaMetadataReader()

    @Provides
    @Singleton
    fun provideMediaStoreExporter(@ApplicationContext context: Context): com.salvia.salviabrowxer.core.storage.MediaStoreExporter =
        com.salvia.salviabrowxer.core.storage.MediaStoreExporter(context)

    @Provides
    @Singleton
    fun provideSafDocumentExporter(@ApplicationContext context: Context): com.salvia.salviabrowxer.core.storage.SafDocumentExporter =
        com.salvia.salviabrowxer.core.storage.SafDocumentExporter(context)

    /**
     * The one deletion path for a download: its own files, staged parts, thumbnail, and the copy it
     * published — never another app's media, see [DownloadArtifactCleaner].
     */
    @Provides
    @Singleton
    fun provideDownloadArtifactCleaner(@ApplicationContext context: Context): DownloadArtifactCleaner =
        androidArtifactCleaner(context)

    /**
     * The local-only diagnostics the app offers instead of a crash-reporting SDK: a bounded file in
     * private storage, read only when the user chooses to share it.
     */
    @Provides
    @Singleton
    fun provideDiagnosticsLog(@ApplicationContext context: Context): DiagnosticsLog =
        DiagnosticsLog(java.io.File(context.filesDir, "diagnostics"))

    /** The manual update check; its one request happens only when the user taps the row. */
    @Provides
    @Singleton
    fun provideUpdateChecker(okHttpClient: OkHttpClient): UpdateChecker =
        UpdateChecker(okHttpClient, com.salvia.salviabrowxer.BuildConfig.VERSION_NAME)

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        // Real migrations only: a failed upgrade must surface, never wipe browsing data.
        return Room.databaseBuilder(context, AppDatabase::class.java, "salviabrowxer_db")
            .addMigrations(
                AppDatabase.MIGRATION_1_2,
                AppDatabase.MIGRATION_2_3,
                AppDatabase.MIGRATION_3_4,
                AppDatabase.MIGRATION_4_5,
                AppDatabase.MIGRATION_5_6
            )
            .build()
    }

    @Provides
    @Singleton
    fun provideStorageManager(@ApplicationContext context: Context): StorageManager = StorageManager(context)

    @Provides
    @Singleton
    fun provideSettingsDataStore(@ApplicationContext context: Context): SettingsDataStore = SettingsDataStoreImpl(context.dataStore)

    @Provides
    @Singleton
    fun provideSessionDataStore(@ApplicationContext context: Context): SessionDataStore = SessionDataStore(context.sessionDataStore)

    @Provides
    @Singleton
    fun provideSessionRestorer(sessionDataStore: SessionDataStore): SessionRestorer = SessionRestorer(sessionDataStore)

    /** The password verifier and the chosen SD-card tree: both are small, encrypted-at-rest settings. */
    @Provides
    @Singleton
    fun provideLockedFolderStore(@ApplicationContext context: Context): LockedFolderStore = LockedFolderStore(context)

    @Provides
    @Singleton
    fun provideSafDestinationStore(@ApplicationContext context: Context): SafDestinationStore = SafDestinationStore(context.dataStore)

    @Provides
    @Singleton
    fun provideDownloadRepository(database: AppDatabase, storageManager: StorageManager): DownloadRepository = DownloadRepositoryImpl(database.downloadDao(), storageManager)

    @Provides
    @Singleton
    fun provideBookmarkRepository(database: AppDatabase): BookmarkRepository = BookmarkRepositoryImpl(database.bookmarkDao())

    @Provides
    @Singleton
    fun provideHistoryRepository(database: AppDatabase): HistoryRepository = HistoryRepositoryImpl(database.historyDao())

    @Provides
    @Singleton
    fun provideDownloadManager(@ApplicationContext context: Context, okHttpClient: OkHttpClient): DownloadManager = DownloadManager(context, okHttpClient)

    @Provides
    @Singleton
    fun provideMediaDetector(): MediaDetector = DomMediaDetector()

    @Provides
    @Singleton
    fun provideMediaResolver(okHttpClient: OkHttpClient): MediaResolver = DirectMediaResolver(okHttpClient)

    @Provides
    @Singleton
    fun provideImageLoader(@ApplicationContext context: Context): ImageLoader {
        return ImageLoader.Builder(context)
            .memoryCache { MemoryCache.Builder(context).maxSizePercent(0.22).build() }
            .diskCache { DiskCache.Builder().directory(context.cacheDir.resolve("coil")).maxSizeBytes(64L * 1024 * 1024).build() }
            .respectCacheHeaders(false)
            .crossfade(true)
            .build()
    }
}
