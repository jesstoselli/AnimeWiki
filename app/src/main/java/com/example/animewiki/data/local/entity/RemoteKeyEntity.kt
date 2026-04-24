package com.example.animewiki.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "remote_keys")
data class RemoteKeyEntity(
    @PrimaryKey val animeId: Int,
    val prevKey: Int?,
    val nextKey: Int?
)
