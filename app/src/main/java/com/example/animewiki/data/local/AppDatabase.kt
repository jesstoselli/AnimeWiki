package com.example.animewiki.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.animewiki.data.local.dao.AnimeDao
import com.example.animewiki.data.local.dao.FavoriteDao
import com.example.animewiki.data.local.dao.RemoteKeyDao
import com.example.animewiki.data.local.entity.AnimeEntity
import com.example.animewiki.data.local.entity.FavoriteEntity
import com.example.animewiki.data.local.entity.RemoteKeyEntity

@Database(
    entities = [AnimeEntity::class, RemoteKeyEntity::class, FavoriteEntity::class],
    version = 2,
    exportSchema = false
)
@TypeConverters(StringListConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun animeDao(): AnimeDao
    abstract fun remoteKeyDao(): RemoteKeyDao
    abstract fun favoriteDao(): FavoriteDao
}
