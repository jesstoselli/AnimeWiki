package com.example.animewiki.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AnimeListResponseDto(
    val pagination: PaginationDto? = null,
    val data: List<AnimeDto>? = null
)

@Serializable
data class PaginationDto(
    @SerialName("last_visible_page") val lastVisiblePage: Int? = null,
    @SerialName("has_next_page") val hasNextPage: Boolean = false,
    @SerialName("current_page") val currentPage: Int? = null
)

@Serializable
data class AnimeDto(
    @SerialName("mal_id") val malId: Int? = null,
    val title: String? = null,
    @SerialName("title_english") val titleEnglish: String? = null,
    @SerialName("title_japanese") val titleJapanese: String? = null,
    val type: String? = null,
    val episodes: Int? = null,
    val score: Double? = null,
    val year: Int? = null,
    val images: AnimeImagesDto? = null,
    val synopsis: String? = null,
    val genres: List<NamedEntityDto>? = null,
    val studios: List<NamedEntityDto>? = null,
    val themes: List<NamedEntityDto>? = null,
    val demographics: List<NamedEntityDto>? = null,
    val aired: AiredDto? = null,
    val trailer: TrailerDto? = null,
    val status: String? = null,
    val rating: String? = null,
    val duration: String? = null,
    val rank: Int? = null,
)

@Serializable
data class AnimeImagesDto(
    val jpg: AnimeImageUrlsDto? = null
)

@Serializable
data class AnimeImageUrlsDto(
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("small_image_url") val smallImageUrl: String? = null,
    @SerialName("large_image_url") val largeImageUrl: String? = null
)
