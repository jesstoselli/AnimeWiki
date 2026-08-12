package com.example.animewiki.data.paging

import com.example.animewiki.domain.model.AnimeSort
import com.example.animewiki.graphql.type.MediaSort

internal fun AnimeSort.toAniListMediaSort(): MediaSort = when (this) {
    AnimeSort.SCORE -> MediaSort.SCORE_DESC
    AnimeSort.POPULARITY -> MediaSort.POPULARITY_DESC
    AnimeSort.TITLE -> MediaSort.TITLE_ROMAJI
    AnimeSort.NEWEST -> MediaSort.START_DATE_DESC
}
