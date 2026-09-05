package com.salvia.salviabrowxer.core.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.salvia.salviabrowxer.core.model.DownloadState
import java.util.UUID

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val url: String,
    val finalUrl: String? = null,
    val filename: String,
    val mimeType: String? = null,
    val destination: String,
    val totalBytes: Long? = null,
    val downloadedBytes: Long = 0,
    val status: DownloadState = DownloadState.QUEUED,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val error: String? = null,
    val mediaTitle: String? = null,
    val thumbnail: String? = null,
    val selectedQuality: String? = null,
    val temporaryPath: String? = null,
    val finalPath: String? = null
)