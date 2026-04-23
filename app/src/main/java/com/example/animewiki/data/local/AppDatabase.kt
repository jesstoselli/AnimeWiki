package com.example.animewiki.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.animewiki.data.local.dao.AnimeDao
import com.example.animewiki.data.local.dao.RemoteKeyDao
import com.example.animewiki.data.local.entity.AnimeEntity
import com.example.animewiki.data.local.entity.RemoteKeyEntity

@Database(
    entities = [AnimeEntity::class, RemoteKeyEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(StringListConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun animeDao(): AnimeDao
    abstract fun remoteKeyDao(): RemoteKeyDao
}