package com.example.animewiki.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.animewiki.data.local.entity.RemoteKeyEntity

@Dao
interface RemoteKeyDao {
    @Query("SELECT * FROM remote_keys WHERE animeId = :animeId")
    suspend fun getKey(animeId: Int): RemoteKeyEntity?

    @Insert(onConflict = OnConflictStrategy.Companion.REPLACE)
    suspend fun upsertAll(keys: List<RemoteKeyEntity>)

    @Query("DELETE FROM remote_keys")
    suspend fun clearAll()
}
