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

    /**
     * The v2 -> v3 upgrade adds the transfer rate and the remaining time to `downloads`. Additive
     * for the same reason as the first: an installed user's queue has to survive the upgrade.
     *
     * The column declarations here also have to match the entity exactly — Room compares the
     * migrated schema against the expected one and refuses to open the database on any difference.
     * That is why `bytesPerSecond` carries an explicit default and `etaSeconds`, being nullable,
     * carries none.
     */
    @Test
    fun `migration 2 to 3 only adds the two transfer columns`() {
        val database: SupportSQLiteDatabase = mockk(relaxed = true)

        AppDatabase.MIGRATION_2_3.migrate(database)

        verifyOrder {
            database.execSQL("ALTER TABLE `downloads` ADD COLUMN `bytesPerSecond` INTEGER NOT NULL DEFAULT 0")
            database.execSQL("ALTER TABLE `downloads` ADD COLUMN `etaSeconds` INTEGER")
        }
        verify(exactly = 2) { database.execSQL(any<String>()) }
    }
}
