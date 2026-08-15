package com.example.animewiki.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.animewiki.data.local.entity.HomeShelfItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HomeShelfDao {
    @Query("SELECT * FROM home_shelf_item WHERE shelf = :shelf ORDER BY position ASC")
    fun observeShelf(shelf: String): Flow<List<HomeShelfItemEntity>>

    @Query("DELETE FROM home_shelf_item WHERE shelf = :shelf")
    suspend fun clearShelf(shelf: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<HomeShelfItemEntity>)

    @Transaction
    suspend fun replaceShelf(shelf: String, items: List<HomeShelfItemEntity>) {
        clearShelf(shelf)
        insertAll(items)
    }
}
