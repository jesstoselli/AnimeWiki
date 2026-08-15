package com.example.animewiki.data.local.entity

import androidx.room.Entity

@Entity(tableName = "home_shelf_item", primaryKeys = ["shelf", "position"])
data class HomeShelfItemEntity(
    val shelf: String,
    val position: Int,
    val id: Int,
    val title: String,
    val imageUrl: String,
    val score: Double?,
    val season: String?,
    val year: Int?,
    val rank: Int?,
    val nextEpisode: Int?,
    val nextAiringAtSeconds: Long?
)
