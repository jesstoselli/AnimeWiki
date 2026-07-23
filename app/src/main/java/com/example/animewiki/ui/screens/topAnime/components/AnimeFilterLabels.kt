package com.example.animewiki.ui.screens.topAnime.components

import androidx.annotation.StringRes
import com.example.animewiki.R
import com.example.animewiki.domain.model.AnimeAgeRating
import com.example.animewiki.domain.model.AnimeFormat

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
internal fun AnimeAgeRating.labelRes(): Int = when (this) {
    AnimeAgeRating.G -> R.string.filter_rating_g
    AnimeAgeRating.PG -> R.string.filter_rating_pg
    AnimeAgeRating.PG13 -> R.string.filter_rating_pg13
    AnimeAgeRating.R17 -> R.string.filter_rating_r17
    AnimeAgeRating.R_PLUS -> R.string.filter_rating_r_plus
    AnimeAgeRating.RX -> R.string.filter_rating_rx
}
