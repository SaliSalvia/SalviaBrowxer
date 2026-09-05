package com.salvia.salviabrowxer.data.repository

import com.salvia.salviabrowxer.core.database.entities.BookmarkEntity
import kotlinx.coroutines.flow.Flow

interface BookmarkRepository {
    fun getAllBookmarks(): Flow<List<BookmarkEntity>>
    fun searchBookmarks(query: String): Flow<List<BookmarkEntity>>
    suspend fun getBookmarkById(id: String): BookmarkEntity?
    suspend fun addBookmark(bookmark: BookmarkEntity)
    suspend fun updateBookmark(bookmark: BookmarkEntity)
    suspend fun deleteBookmark(id: String)
    suspend fun deleteAllBookmarks()
    suspend fun isBookmarked(url: String): Boolean
}