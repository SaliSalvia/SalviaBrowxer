package com.salvia.salviabrowxer.data.repository

import com.salvia.salviabrowxer.core.database.dao.HistoryDao
import com.salvia.salviabrowxer.core.database.entities.HistoryEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class HistoryRepositoryImpl @Inject constructor(
    private val historyDao: HistoryDao
) : HistoryRepository {

    override fun getAllHistory(): Flow<List<HistoryEntity>> = historyDao.getAll()

    override fun searchHistory(query: String): Flow<List<HistoryEntity>> =
        historyDao.search(query)

    override suspend fun getHistoryById(id: String): HistoryEntity? = historyDao.getById(id)

    override suspend fun addHistory(history: HistoryEntity) = historyDao.insert(history)

    override suspend fun deleteHistory(id: String) = historyDao.deleteById(id)

    override suspend fun deleteAllHistory() = historyDao.deleteAll()

    override suspend fun deleteHistoryOlderThan(timestamp: Long) {
        // Implement logic to delete old history
    }
}