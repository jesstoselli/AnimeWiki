package com.example.animewiki.data.mapper

import com.example.animewiki.data.local.entity.AnimeEntity
import com.example.animewiki.domain.model.Anime
import com.example.animewiki.graphql.fragment.AnimeCacheFields
import com.example.animewiki.graphql.fragment.AnimeCardFields
import com.example.animewiki.graphql.type.MediaFormat
import com.example.animewiki.graphql.type.MediaRankType
import com.example.animewiki.graphql.type.MediaStatus

fun AnimeCardFields.toDomain(): Anime? {
    val title = title?.english.nonBlank() ?: title?.romaji.nonBlank() ?: return null
    val imageUrl = coverImage?.extraLarge.nonBlank() ?: coverImage?.large.nonBlank() ?: return null

    return Anime(
        id = id,
        title = title,
        imageUrl = imageUrl,
        score = averageScore.scoreToTen(),
        episodes = episodes,
        type = format.toDisplayString(),
        year = seasonYear,
        synopsis = null
    )
}

fun AnimeCacheFields.toDomain(): Anime? {
    val title = title?.english.nonBlank() ?: title?.romaji.nonBlank() ?: return null
    val imageUrl = coverImage?.extraLarge.nonBlank() ?: coverImage?.large.nonBlank() ?: return null

    return Anime(
        id = id,
        title = title,
        imageUrl = imageUrl,
        score = averageScore.scoreToTen(),
        episodes = episodes,
        type = format.toDisplayString(),
        year = seasonYear ?: startDate?.year,
        synopsis = description?.stripAniListHtml(),
        genres = genres.orEmpty().mapNotNull { it.nonBlank() },
        studios = studios?.nodes.orEmpty().mapNotNull { it?.name.nonBlank() },
        aired = formatAired(
            startYear = startDate?.year,
            startMonth = startDate?.month,
            startDay = startDate?.day,
            endYear = endDate?.year,
            endMonth = endDate?.month,
            endDay = endDate?.day
        ),
        status = status.toDisplayString(),
        rating = null,
        duration = duration?.let { "$it min per ep" },
        rank = rankings.orEmpty()
            .firstOrNull { it?.type == MediaRankType.RATED && it.allTime == true }
            ?.rank,
        trailerYoutubeId = trailer
            ?.takeIf { it.site.equals("youtube", ignoreCase = true) }
            ?.id
            .nonBlank()
    )
}

fun AnimeCacheFields.toEntity(pageIndex: Int): AnimeEntity? = toDomain()?.let { anime ->
    AnimeEntity(
        id = anime.id,
        title = anime.title,
        imageUrl = anime.imageUrl,
        score = anime.score,
        episodes = anime.episodes,
        type = anime.type,
        year = anime.year,
        synopsis = anime.synopsis,
        genres = anime.genres,
        studios = anime.studios,
        aired = anime.aired,
        status = anime.status,
        rating = null,
        duration = anime.duration,
        rank = anime.rank,
        trailerYoutubeId = anime.trailerYoutubeId,
        pageIndex = pageIndex
    )
}

internal fun String.stripAniListHtml(): String =
    replace(Regex("(?i)<br\\s*/?>"), "\n")
        .replace(Regex("<[^>]+>"), "")
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .trim()

private fun String?.nonBlank(): String? = this?.takeIf(String::isNotBlank)

private fun Int?.scoreToTen(): Double? = this?.div(10.0)

private fun MediaFormat?.toDisplayString(): String? = when (this) {
    MediaFormat.TV -> "TV"
    MediaFormat.TV_SHORT -> "TV Short"
    MediaFormat.MOVIE -> "Movie"
    MediaFormat.SPECIAL -> "Special"
    MediaFormat.OVA -> "OVA"
    MediaFormat.ONA -> "ONA"
    MediaFormat.MUSIC -> "Music"
    MediaFormat.MANGA -> "Manga"
    MediaFormat.NOVEL -> "Novel"
    MediaFormat.ONE_SHOT -> "One Shot"
    MediaFormat.UNKNOWN__,
    null -> null
}

private fun MediaStatus?.toDisplayString(): String? = when (this) {
    MediaStatus.FINISHED -> "Finished"
    MediaStatus.RELEASING -> "Releasing"
    MediaStatus.NOT_YET_RELEASED -> "Not Yet Released"
    MediaStatus.CANCELLED -> "Cancelled"
    MediaStatus.HIATUS -> "Hiatus"
    MediaStatus.UNKNOWN__,
    null -> null
}

private fun formatAired(
    startYear: Int?,
    startMonth: Int?,
    startDay: Int?,
    endYear: Int?,
    endMonth: Int?,
    endDay: Int?
): String? {
    val start = formatAniListDate(startYear, startMonth, startDay)
    val end = formatAniListDate(endYear, endMonth, endDay)

    return when {
        start == null -> end
        end == null -> start
        startMonth == null && startDay == null && endMonth == null && endDay == null -> "$start - $end"
        else -> "$start to $end"
    }
}

private fun formatAniListDate(year: Int?, month: Int?, day: Int?): String? {
    year ?: return null
    val monthName = month?.takeIf { it in 1..12 }?.let { MONTH_NAMES[it - 1] }

    return when {
        monthName != null && day != null -> "$monthName $day, $year"
        monthName != null -> "$monthName $year"
        else -> year.toString()
    }
}

private val MONTH_NAMES = listOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun",
    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
)
