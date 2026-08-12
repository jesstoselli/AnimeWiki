package com.example.animewiki.ui.screens.topAnime.components

import androidx.annotation.StringRes
import com.example.animewiki.R
import com.example.animewiki.domain.model.AnimeFormat
import com.example.animewiki.domain.model.AnimeSort

@StringRes
internal fun AnimeFormat.labelRes(): Int = when (this) {
    AnimeFormat.TV -> R.string.filter_format_tv
    AnimeFormat.MOVIE -> R.string.filter_format_movie
    AnimeFormat.OVA -> R.string.filter_format_ova
    AnimeFormat.ONA -> R.string.filter_format_ona
    AnimeFormat.SPECIAL -> R.string.filter_format_special
    AnimeFormat.MUSIC -> R.string.filter_format_music
}

@StringRes
internal fun AnimeSort.labelRes(): Int = when (this) {
    AnimeSort.SCORE -> R.string.filter_sort_score
    AnimeSort.POPULARITY -> R.string.filter_sort_popularity
    AnimeSort.TITLE -> R.string.filter_sort_title
    AnimeSort.NEWEST -> R.string.filter_sort_newest
}
