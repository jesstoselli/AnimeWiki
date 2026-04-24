package com.example.animewiki.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.animewiki.data.local.AppDatabase
import com.example.animewiki.data.local.entity.FavoriteEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FavoriteDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: FavoriteDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries() // ok in tests only
            .build()
        dao = db.favoriteDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun insertAndObserve_emitsTheInsertedFavorite() = runTest {
        val frieren = favoriteEntity(id = 52991, title = "Sousou no Frieren")

        dao.insert(frieren)
        val favorites = dao.observeAll().first()

        assertEquals(1, favorites.size)
        assertEquals("Sousou no Frieren", favorites[0].title)
        assertEquals(52991, favorites[0].id)
    }

    @Test
    fun observeIsFavorite_reflectsInsertAndDelete() = runTest {
        val id = 52991

        // Not yet favorited
        assertFalse(dao.observeIsFavorite(id).first())

        // Insert -> should be true
        dao.insert(favoriteEntity(id = id, title = "Frieren"))
        assertTrue(dao.observeIsFavorite(id).first())

        // Delete -> back to false
        dao.deleteById(id)
        assertFalse(dao.observeIsFavorite(id).first())
    }

    @Test
    fun observeAll_ordersByFavoritedAtDescending() = runTest {
        val older = favoriteEntity(id = 1, title = "Older", favoritedAt = 1_000L)
        val newer = favoriteEntity(id = 2, title = "Newer", favoritedAt = 2_000L)

        dao.insert(older)
        dao.insert(newer)
        val favorites = dao.observeAll().first()

        assertEquals(listOf("Newer", "Older"), favorites.map { it.title })
    }

    @Test
    fun insert_withSameId_replacesExistingFavorite() = runTest {
        dao.insert(favoriteEntity(id = 1, title = "Original"))
        dao.insert(favoriteEntity(id = 1, title = "Updated"))

        val favorites = dao.observeAll().first()

        assertEquals(1, favorites.size)
        assertEquals("Updated", favorites[0].title)
    }

    // --- helpers ---

    private fun favoriteEntity(
        id: Int,
        title: String,
        favoritedAt: Long = System.currentTimeMillis()
    ) = FavoriteEntity(
        id = id,
        title = title,
        imageUrl = "https://example.com/$id.jpg",
        score = 9.0,
        year = 2023,
        type = "TV",
        favoritedAt = favoritedAt
    )
}
