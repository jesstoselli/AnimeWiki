package com.example.animewiki.data.mapper

import com.example.animewiki.data.local.entity.AnimeEntity
import com.example.animewiki.data.local.entity.HomeShelfItemEntity
import com.example.animewiki.domain.model.AnimeSeason
import com.example.animewiki.domain.model.HomeShelf
import com.example.animewiki.domain.model.HomeShelfAnime
import com.example.animewiki.graphql.SeasonAnimeQuery
import com.example.animewiki.graphql.type.MediaSeason

fun SeasonAnimeQuery.Medium.toShelfAnime(): HomeShelfAnime? {
    val card = animeCardFields.toDomain() ?: return null
    return HomeShelfAnime(
        id = card.id,
        title = card.title,
        imageUrl = card.imageUrl,
        score = card.score,
        season = season.toDomainSeason(),
        year = card.year,
        rank = null,
        nextEpisode = nextAiringEpisode?.episode,
        nextAiringAtSeconds = nextAiringEpisode?.airingAt?.toLong()
    )
}

fun HomeShelfAnime.toEntity(shelf: HomeShelf, position: Int) = HomeShelfItemEntity(
    shelf = shelf.name,
    position = position,
    id = id,
    title = title,
    imageUrl = imageUrl,
    score = score,
    season = season?.name,
    year = year,
    rank = rank,
    nextEpisode = nextEpisode,
    nextAiringAtSeconds = nextAiringAtSeconds
)

fun HomeShelfItemEntity.toShelfAnime() = HomeShelfAnime(
    id = id,
    title = title,
    imageUrl = imageUrl,
    score = score,
    season = season?.toAnimeSeasonOrNull(),
    year = year,
    rank = rank,
    nextEpisode = nextEpisode,
    nextAiringAtSeconds = nextAiringAtSeconds
)

fun AnimeEntity.toShelfAnime() = HomeShelfAnime(
    id = id,
    title = title,
    imageUrl = imageUrl,
    score = score,
    year = year,
    rank = rank
)

fun AnimeSeason.toMediaSeason(): MediaSeason = when (this) {
    AnimeSeason.WINTER -> MediaSeason.WINTER
    AnimeSeason.SPRING -> MediaSeason.SPRING
    AnimeSeason.SUMMER -> MediaSeason.SUMMER
    AnimeSeason.FALL -> MediaSeason.FALL
}

private fun MediaSeason?.toDomainSeason(): AnimeSeason? = when (this) {
    MediaSeason.WINTER -> AnimeSeason.WINTER
    MediaSeason.SPRING -> AnimeSeason.SPRING
    MediaSeason.SUMMER -> AnimeSeason.SUMMER
    MediaSeason.FALL -> AnimeSeason.FALL
    MediaSeason.UNKNOWN__, null -> null
}

private fun String.toAnimeSeasonOrNull(): AnimeSeason? =
    AnimeSeason.entries.firstOrNull { it.name == this }
