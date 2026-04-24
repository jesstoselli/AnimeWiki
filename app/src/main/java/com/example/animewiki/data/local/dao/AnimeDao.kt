package com.example.animewiki.data.local.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.animewiki.data.local.entity.AnimeEntity

@Dao
interface AnimeDao {
    @Query("SELECT * FROM anime ORDER BY pageIndex ASC")
    fun pagingSource(): PagingSource<Int, AnimeEntity>

    @Query("SELECT * FROM anime WHERE id = :id")
    suspend fun getById(id: Int): AnimeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<AnimeEntity>)

    @Query("DELETE FROM anime")
    suspend fun clearAll()

    @Query("SELECT COALESCE(MAX(pageIndex), -1) FROM anime")
    suspend fun maxPageIndex(): Int
}
