package com.example.animewiki.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val id: Int,
    val title: String,
    val imageUrl: String,
    val score: Double?,
    val year: Int?,
    val type: String?,
    val favoritedAt: Long = System.currentTimeMillis()
)
