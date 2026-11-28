package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks ORDER BY isPinnedSpeedDial DESC, createdAt DESC")
    fun getAllBookmarks(): Flow<List<BookmarkEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM bookmarks WHERE url = :url)")
    fun isBookmarked(url: String): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(bookmark: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE url = :url")
    suspend fun deleteByUrl(url: String)

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM bookmarks")
    suspend fun count(): Int

    @Query("SELECT * FROM bookmarks WHERE url = :url LIMIT 1")
    suspend fun findByUrl(url: String): BookmarkEntity?
}

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY visitedAt DESC LIMIT 300")
    fun getRecentHistory(): Flow<List<HistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: HistoryEntity)

    @Query("DELETE FROM history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM history WHERE url = :url")
    suspend fun deleteByUrl(url: String)

    /** Keeps the history table bounded so long-lived installs never grow it without limit. */
    @Query("DELETE FROM history WHERE id NOT IN (SELECT id FROM history ORDER BY visitedAt DESC LIMIT :keep)")
    suspend fun trimTo(keep: Int)

    @Query("DELETE FROM history")
    suspend fun clearAll()
}

@Dao
interface ExtensionDao {
    @Query("SELECT * FROM extensions ORDER BY isInstalled DESC, isBuiltIn DESC, updatedAt DESC")
    fun getAllExtensions(): Flow<List<ExtensionEntity>>

    @Query("SELECT * FROM extensions WHERE isInstalled = 1 AND isEnabled = 1")
    fun getActiveExtensions(): Flow<List<ExtensionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(extension: ExtensionEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIgnore(extensions: List<ExtensionEntity>)

    /**
     * Ships fixes for built-in extensions to existing installs while preserving the user's
     * installed/enabled choice. Only rows whose bundled version changed are touched.
     */
    @Query(
        "UPDATE extensions SET name = :name, version = :version, description = :description, " +
            "jsCode = :jsCode, cssCode = :cssCode, matchPattern = :matchPattern, runAt = :runAt, " +
            "permissions = :permissions WHERE id = :id AND isBuiltIn = 1 AND version != :version",
    )
    suspend fun refreshBuiltIn(
        id: String,
        name: String,
        version: String,
        description: String,
        jsCode: String,
        cssCode: String,
        matchPattern: String,
        runAt: String,
        permissions: String,
    ): Int

    @Update
    suspend fun update(extension: ExtensionEntity)

    @Query("DELETE FROM extensions WHERE id = :id AND isBuiltIn = 0")
    suspend fun deleteCustomById(id: String)

    @Query("UPDATE extensions SET isEnabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: String, enabled: Boolean)

    @Query("UPDATE extensions SET isInstalled = :installed, isEnabled = :installed WHERE id = :id")
    suspend fun setInstalled(id: String, installed: Boolean)
}

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun getAllDownloads(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): DownloadEntity?


    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMediaRequestContext(context: MediaDownloadRequestContextEntity)

    @Query("SELECT * FROM media_download_request_contexts WHERE downloadId = :downloadId AND expiresAt > :now LIMIT 1")
    suspend fun getMediaRequestContext(downloadId: Long, now: Long): MediaDownloadRequestContextEntity?

    @Query("DELETE FROM media_download_request_contexts WHERE downloadId = :downloadId")
    suspend fun deleteMediaRequestContext(downloadId: Long)

    @Query("DELETE FROM media_download_request_contexts WHERE expiresAt <= :now")
    suspend fun deleteExpiredMediaRequestContexts(now: Long)

    @Query("SELECT * FROM downloads WHERE isIncognito = 1 ORDER BY createdAt DESC")
    suspend fun getIncognitoDownloads(): List<DownloadEntity>

    @Query("DELETE FROM downloads WHERE isIncognito = 1")
    suspend fun deleteIncognitoDownloads()

    @Query("SELECT * FROM downloads WHERE isIncognito = 0 AND status IN ('QUEUED', 'DOWNLOADING') ORDER BY createdAt ASC")
    suspend fun getPendingNormalDownloads(): List<DownloadEntity>

    @Query("UPDATE downloads SET status = 'QUEUED', speedBytesPerSec = 0 WHERE id = :id AND status = 'DOWNLOADING'")
    suspend fun markInterruptedQueued(id: Long): Int

    @Query("UPDATE downloads SET status = :status, speedBytesPerSec = 0 WHERE id = :id AND status NOT IN ('COMPLETED', 'CANCELLED')")
    suspend fun updateNonTerminalStatus(id: Long, status: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(download: DownloadEntity): Long

    @Query(
        "UPDATE downloads SET downloadedBytes = :downloaded, totalBytes = :total, speedBytesPerSec = :speed, " +
            "status = :status, localUri = :localUri, supportsResume = :supportsResume WHERE id = :id",
    )
    suspend fun updateProgress(
        id: Long,
        downloaded: Long,
        total: Long,
        speed: Long,
        status: String,
        localUri: String = "",
        supportsResume: Boolean = false,
    )

    @Query("UPDATE downloads SET systemDownloadId = :sysId WHERE id = :id")
    suspend fun setSystemDownloadId(id: Long, sysId: Long)

    @Query("UPDATE downloads SET status = :status, speedBytesPerSec = 0 WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String)

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE downloads SET fileName = :fileName WHERE id = :id")
    suspend fun updateFileName(id: Long, fileName: String)

    @Query("DELETE FROM downloads WHERE status IN ('COMPLETED', 'FAILED', 'CANCELLED')")
    suspend fun clearFinished()
}

@Dao
interface AdBlockRuleDao {
    @Query("SELECT * FROM adblock_rules ORDER BY createdAt DESC")
    fun getAllRules(): Flow<List<AdBlockRuleEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rule: AdBlockRuleEntity)

    @Query("UPDATE adblock_rules SET isEnabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("DELETE FROM adblock_rules WHERE pattern = :pattern AND isWhitelist = :isWhitelist")
    suspend fun deleteByPattern(pattern: String, isWhitelist: Boolean)

    @Query("DELETE FROM adblock_rules WHERE id = :id")
    suspend fun deleteById(id: Long)
}
