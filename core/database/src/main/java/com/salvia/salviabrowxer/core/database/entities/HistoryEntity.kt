package com.salvia.salviabrowxer.core.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val url: String,
    val title: String,
    val favicon: String? = null,
    val visitedAt: Long = System.currentTimeMillis()
)