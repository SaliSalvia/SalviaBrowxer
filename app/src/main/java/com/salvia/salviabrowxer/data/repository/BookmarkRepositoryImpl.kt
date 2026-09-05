package com.salvia.salviabrowxer.data.repository

import com.salvia.salviabrowxer.core.database.dao.BookmarkDao
import com.salvia.salviabrowxer.core.database.entities.BookmarkEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class BookmarkRepositoryImpl @Inject constructor(
    private val bookmarkDao: BookmarkDao
) : BookmarkRepository {

    override fun getAllBookmarks(): Flow<List<BookmarkEntity>> = bookmarkDao.getAll()

    override fun searchBookmarks(query: String): Flow<List<BookmarkEntity>> =
        bookmarkDao.search(query)

    override suspend fun getBookmarkById(id: String): BookmarkEntity? = bookmarkDao.getById(id)

    override suspend fun addBookmark(bookmark: BookmarkEntity) = bookmarkDao.insert(bookmark)

    override suspend fun updateBookmark(bookmark: BookmarkEntity) = bookmarkDao.update(bookmark)

    override suspend fun deleteBookmark(id: String) = bookmarkDao.deleteById(id)

    override suspend fun deleteAllBookmarks() = bookmarkDao.deleteAll()

    override suspend fun isBookmarked(url: String): Boolean {
        return bookmarkDao.getAll().value.any { it.url == url }
    }
}