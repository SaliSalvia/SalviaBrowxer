package com.example.data.local

import com.example.engine.ExtensionEngine
import kotlinx.coroutines.flow.Flow

class BrowserRepository(private val db: BrowserDatabase) {
    private val bookmarkDao = db.bookmarkDao()
    private val historyDao = db.historyDao()
    private val extensionDao = db.extensionDao()
    private val downloadDao = db.downloadDao()
    private val adBlockRuleDao = db.adBlockRuleDao()

    val bookmarks: Flow<List<BookmarkEntity>> = bookmarkDao.getAllBookmarks()
    val history: Flow<List<HistoryEntity>> = historyDao.getRecentHistory()
    val extensions: Flow<List<ExtensionEntity>> = extensionDao.getAllExtensions()
    val activeExtensions: Flow<List<ExtensionEntity>> = extensionDao.getActiveExtensions()
    val downloads: Flow<List<DownloadEntity>> = downloadDao.getAllDownloads()
    val customAdRules: Flow<List<AdBlockRuleEntity>> = adBlockRuleDao.getAllRules()

    suspend fun ensureSeeded() {
        extensionDao.insertAllIgnore(ExtensionEngine.builtInExtensions())
    }

    fun isBookmarked(url: String): Flow<Boolean> = bookmarkDao.isBookmarked(url)

    suspend fun addBookmark(title: String, url: String, category: String = "Bookmarks", pinned: Boolean = false) {
        bookmarkDao.insert(
            BookmarkEntity(
                title = title.ifBlank { url },
                url = url,
                category = category,
                isPinnedSpeedDial = pinned,
            ),
        )
    }

    suspend fun removeBookmarkByUrl(url: String) = bookmarkDao.deleteByUrl(url)

    suspend fun removeBookmarkById(id: Long) = bookmarkDao.deleteById(id)

    suspend fun recordHistory(title: String, url: String) {
        if (url.isBlank() || url.startsWith("about:") || url.startsWith("salvia://")) return
        historyDao.insert(HistoryEntity(title = title.ifBlank { url }, url = url))
    }

    suspend fun deleteHistoryItem(id: Long) = historyDao.deleteById(id)

    suspend fun clearHistory() = historyDao.clearAll()

    suspend fun setExtensionEnabled(id: String, enabled: Boolean) = extensionDao.setEnabled(id, enabled)

    suspend fun setExtensionInstalled(id: String, installed: Boolean) = extensionDao.setInstalled(id, installed)

    suspend fun saveCustomExtension(extension: ExtensionEntity) = extensionDao.upsert(extension)

    suspend fun deleteCustomExtension(id: String) = extensionDao.deleteCustomById(id)

    suspend fun getDownloadById(id: Long): DownloadEntity? = downloadDao.getById(id)

    suspend fun saveMediaRequestContext(context: MediaDownloadRequestContextEntity) =
        downloadDao.upsertMediaRequestContext(context)

    suspend fun getMediaRequestContext(downloadId: Long): MediaDownloadRequestContextEntity? =
        downloadDao.getMediaRequestContext(downloadId, System.currentTimeMillis())

    suspend fun deleteMediaRequestContext(downloadId: Long) =
        downloadDao.deleteMediaRequestContext(downloadId)

    suspend fun cleanupExpiredMediaRequestContexts() =
        downloadDao.deleteExpiredMediaRequestContexts(System.currentTimeMillis())

    suspend fun getIncognitoDownloads(): List<DownloadEntity> = downloadDao.getIncognitoDownloads()

    suspend fun deleteIncognitoDownloads() = downloadDao.deleteIncognitoDownloads()

    suspend fun getPendingNormalDownloads(): List<DownloadEntity> =
        downloadDao.getPendingNormalDownloads()

    suspend fun markInterruptedDownloadQueued(id: Long): Int =
        downloadDao.markInterruptedQueued(id)

    suspend fun updateDownloadStatusIfNonTerminal(id: Long, status: String): Int =
        downloadDao.updateNonTerminalStatus(id, status)

    suspend fun insertDownload(download: DownloadEntity): Long = downloadDao.insert(download)

    suspend fun updateDownloadProgress(
        id: Long,
        downloaded: Long,
        total: Long,
        speedBytesPerSec: Long,
        status: String,
        localUri: String = "",
        supportsResume: Boolean = false,
    ) = downloadDao.updateProgress(id, downloaded, total, speedBytesPerSec, status, localUri, supportsResume)

    suspend fun setSystemDownloadId(id: Long, sysId: Long) = downloadDao.setSystemDownloadId(id, sysId)

    suspend fun updateDownloadStatus(id: Long, status: String) = downloadDao.updateStatus(id, status)

    suspend fun deleteDownload(id: Long) = downloadDao.deleteById(id)

    suspend fun clearFinishedDownloads() = downloadDao.clearFinished()

    suspend fun addAdBlockRule(pattern: String, isWhitelist: Boolean) {
        adBlockRuleDao.insert(AdBlockRuleEntity(pattern = pattern.trim(), isWhitelist = isWhitelist))
    }

    suspend fun removeAdBlockRuleByPattern(pattern: String, isWhitelist: Boolean) {
        adBlockRuleDao.deleteByPattern(pattern.trim(), isWhitelist)
    }

    suspend fun toggleAdBlockRule(id: Long, enabled: Boolean) = adBlockRuleDao.setEnabled(id, enabled)

    suspend fun deleteAdBlockRule(id: Long) = adBlockRuleDao.deleteById(id)
}
