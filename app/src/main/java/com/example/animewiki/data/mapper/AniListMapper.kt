package com.example.animewiki.data.mapper

import com.example.animewiki.data.local.entity.AnimeEntity
import com.example.animewiki.domain.model.Anime
import com.example.animewiki.domain.model.AnimeMediaPreview
import com.example.animewiki.domain.model.AnimeRecommendation
import com.example.animewiki.domain.model.AnimeRelation
import com.example.animewiki.domain.model.AnimeRelationType
import com.example.animewiki.graphql.AnimeDetailsQuery
import com.example.animewiki.graphql.fragment.AnimeCacheFields
import com.example.animewiki.graphql.fragment.AnimeCardFields
import com.example.animewiki.graphql.type.MediaFormat
import com.example.animewiki.graphql.type.MediaRankType
import com.example.animewiki.graphql.type.MediaRelation
import com.example.animewiki.graphql.type.MediaStatus
import com.example.animewiki.graphql.type.MediaType

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

fun AnimeDetailsQuery.Media.toDomainDetails(): Anime? {
    val anime = animeCacheFields.toDomain() ?: return null
    val related = relations?.edges.orEmpty()
        .mapNotNull { edge ->
            val node = edge?.node ?: return@mapNotNull null
            if (node.animeCardFields.isAdult == true) return@mapNotNull null
            val preview = node.animeCardFields.toPreview(node.type) ?: return@mapNotNull null
            AnimeRelation(edge.relationType.toDomainRelationType(), preview)
        }
        .filterNot { it.media.id == anime.id }
        .distinctBy { it.media.id }

    val recommended = recommendations?.nodes.orEmpty()
        .mapNotNull { recommendation ->
            val media = recommendation?.mediaRecommendation ?: return@mapNotNull null
            if (media.animeCardFields.isAdult == true) return@mapNotNull null
            val preview = media.animeCardFields.toPreview(media.type) ?: return@mapNotNull null
            AnimeRecommendation(preview, recommendation.rating ?: 0)
        }
        .filterNot { it.media.id == anime.id }
        .distinctBy { it.media.id }
        .sortedByDescending(AnimeRecommendation::votes)

    return anime.copy(relations = related, recommendations = recommended)
}

private fun AnimeCardFields.toPreview(mediaType: MediaType?): AnimeMediaPreview? =
    toDomain()?.let { anime ->
        AnimeMediaPreview(
            id = anime.id,
            title = anime.title,
            imageUrl = anime.imageUrl,
            score = anime.score,
            year = anime.year,
            mediaType = when (mediaType) {
                MediaType.ANIME -> "Anime"
                MediaType.MANGA -> "Manga"
                MediaType.UNKNOWN__, null -> "Media"
            },
            isAnime = mediaType == MediaType.ANIME
        )
    }

private fun MediaRelation?.toDomainRelationType(): AnimeRelationType = when (this) {
    MediaRelation.PREQUEL -> AnimeRelationType.PREQUEL
    MediaRelation.SEQUEL -> AnimeRelationType.SEQUEL
    MediaRelation.SPIN_OFF -> AnimeRelationType.SPIN_OFF
    MediaRelation.SIDE_STORY,
    MediaRelation.PARENT -> AnimeRelationType.SIDE_STORY
    MediaRelation.ADAPTATION,
    MediaRelation.SOURCE -> AnimeRelationType.ADAPTATION
    MediaRelation.ALTERNATIVE -> AnimeRelationType.ALTERNATIVE
    else -> AnimeRelationType.OTHER
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
