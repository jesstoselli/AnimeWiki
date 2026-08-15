package com.example.animewiki.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration3To4Test {

    private val dbName = "migration-test"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migrate3To4_keepsFavoritesAndAddsShelfTable() {
        helper.createDatabase(dbName, 3).apply {
            execSQL(
                "INSERT INTO favorites " +
                    "(id, title, imageUrl, score, year, type, favoritedAt) " +
                    "VALUES (52991, 'Frieren', 'https://img/1.jpg', 9.1, 2023, 'TV', 1000)"
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(dbName, 4, true, MIGRATION_3_4)

        migrated.query("SELECT title FROM favorites WHERE id = 52991").use { favorites ->
            assertTrue(favorites.moveToFirst())
            assertEquals("Frieren", favorites.getString(0))
        }
        migrated.query("SELECT count(*) FROM home_shelf_item").use { shelf ->
            assertTrue(shelf.moveToFirst())
            assertEquals(0, shelf.getInt(0))
        }
        migrated.close()
    }
}
