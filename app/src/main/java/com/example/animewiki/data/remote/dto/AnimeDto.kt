package com.example.animewiki.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AnimeListResponseDto(
    val pagination: PaginationDto,
    val data: List<AnimeDto>
)

@Serializable
data class PaginationDto(
    @SerialName("last_visible_page") val lastVisiblePage: Int,
    @SerialName("has_next_page") val hasNextPage: Boolean,
    @SerialName("current_page") val currentPage: Int
)

@Serializable
data class AnimeDto(
    @SerialName("mal_id") val malId: Int,
    val title: String,
    @SerialName("title_english") val titleEnglish: String? = null,
    val type: String? = null,
    val episodes: Int? = null,
    val score: Double? = null,
    val year: Int? = null,
    val images: AnimeImagesDto,
    val synopsis: String? = null
)

@Serializable
data class AnimeImagesDto(
    val jpg: AnimeImageUrlsDto
)

@Serializable
data class AnimeImageUrlsDto(
    @SerialName("image_url") val imageUrl: String,
    @SerialName("small_image_url") val smallImageUrl: String? = null,
    @SerialName("large_image_url") val largeImageUrl: String? = null
)