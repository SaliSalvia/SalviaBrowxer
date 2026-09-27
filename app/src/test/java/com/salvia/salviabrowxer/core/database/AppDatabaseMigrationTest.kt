package com.salvia.salviabrowxer.core.database

import androidx.sqlite.db.SupportSQLiteDatabase
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.Test

/**
 * The v1 -> v2 upgrade must be additive: no `DROP`, no table recreation, so an installed
 * user keeps the queue, bookmarks and history. A destructive fallback is no longer
 * registered anywhere, which makes this migration the only upgrade path.
 */
class AppDatabaseMigrationTest {

    @Test
    fun `migration 1 to 2 only creates the two download indexes`() {
        val database: SupportSQLiteDatabase = mockk(relaxed = true)

        AppDatabase.MIGRATION_1_2.migrate(database)

        verifyOrder {
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_downloads_status` ON `downloads` (`status`)")
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_downloads_createdAt` ON `downloads` (`createdAt`)")
        }
        verify(exactly = 2) { database.execSQL(any<String>()) }
    }
}
