package com.example.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val url: String,
    val category: String = "General",
    val isPinnedSpeedDial: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val url: String,
    val visitedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "extensions")
data class ExtensionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val version: String,
    val author: String,
    val description: String,
    val iconKey: String,
    val matchPattern: String,
    val runAt: String, // "document_start" or "document_end"
    val permissions: String,
    val jsCode: String,
    val cssCode: String,
    val isInstalled: Boolean,
    val isEnabled: Boolean,
    val isBuiltIn: Boolean,
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fileName: String,
    val url: String,
    val audioUrl: String = "",
    val sourcePageUrl: String,
    val qualityLabel: String,
    val mimeType: String,
    val totalBytes: Long,
    val downloadedBytes: Long,
    val speedBytesPerSec: Long = 0L,
    val status: String, // QUEUED, DOWNLOADING, PAUSED, COMPLETED, FAILED, CANCELLED
    val localUri: String = "",
    val systemDownloadId: Long = -1L,
    val isHlsStream: Boolean = false,
    val isDashStream: Boolean = false,
    val supportsResume: Boolean = false,
    val isIncognito: Boolean = false,
    val isDesktopMode: Boolean = false,
    val manifestRepresentationId: String = "",
    val manifestAudioRepresentationId: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)


@Entity(
    tableName = "media_download_request_contexts",
    foreignKeys = [
        ForeignKey(
            entity = DownloadEntity::class,
            parentColumns = ["id"],
            childColumns = ["downloadId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["expiresAt"])],
)
data class MediaDownloadRequestContextEntity(
    @PrimaryKey val downloadId: Long,
    val contextData: String,
    val expiresAt: Long,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "adblock_rules")
data class AdBlockRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val pattern: String,
    val isWhitelist: Boolean = false,
    val isEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
)
