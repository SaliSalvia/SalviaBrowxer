package com.salvia.salviabrowxer.data.repository

import com.salvia.salviabrowxer.core.database.entities.HistoryEntity
import kotlinx.coroutines.flow.Flow

interface HistoryRepository {
    fun getAllHistory(): Flow<List<HistoryEntity>>
    fun searchHistory(query: String): Flow<List<HistoryEntity>>
    /** Small, recency-ordered result set for the address bar; does not cap the history screen search. */
    fun searchHistorySuggestions(query: String, limit: Int): Flow<List<HistoryEntity>>
    suspend fun getHistoryById(id: String): HistoryEntity?
    suspend fun addHistory(history: HistoryEntity)
    suspend fun deleteHistory(id: String)
    suspend fun deleteAllHistory()
    suspend fun deleteHistoryOlderThan(timestamp: Long)
}