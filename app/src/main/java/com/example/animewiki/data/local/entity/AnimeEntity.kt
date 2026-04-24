package com.example.animewiki.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "anime")
data class AnimeEntity(
    @PrimaryKey val id: Int,
    val title: String,
    val imageUrl: String,
    val score: Double?,
    val episodes: Int?,
    val type: String?,
    val year: Int?,
    val synopsis: String?,
    val genres: List<String>,
    val studios: List<String>,
    val aired: String?,
    val status: String?,
    val rating: String?,
    val duration: String?,
    val rank: Int?,
    val trailerYoutubeId: String?,
    val pageIndex: Int
)
