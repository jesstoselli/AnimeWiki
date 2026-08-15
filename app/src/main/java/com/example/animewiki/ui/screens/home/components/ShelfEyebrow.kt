package com.example.animewiki.ui.screens.home.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.animewiki.R
import com.example.animewiki.domain.model.AnimeSeason
import com.example.animewiki.domain.model.HomeShelf
import com.example.animewiki.domain.model.HomeShelfAnime
import java.time.Instant
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun shelfEyebrow(shelf: HomeShelf, anime: HomeShelfAnime): String? = when (shelf) {
    HomeShelf.THIS_SEASON -> episodeEyebrow(anime)
    HomeShelf.UPCOMING -> premiereEyebrow(anime)
    HomeShelf.TOP -> anime.rank?.let { stringResource(R.string.home_eyebrow_rank, it) }
    HomeShelf.TRENDING -> stringResource(R.string.home_eyebrow_trending)
}

@Composable
private fun episodeEyebrow(anime: HomeShelfAnime): String? {
    val episode = anime.nextEpisode ?: return null
    val airingAt = anime.nextAiringAtSeconds ?: return null
    val weekday = Instant.ofEpochSecond(airingAt)
        .atZone(ZoneId.systemDefault())
        .dayOfWeek
        .getDisplayName(TextStyle.SHORT, Locale.getDefault())
    return stringResource(R.string.home_eyebrow_episode, episode, weekday)
}

@Composable
private fun premiereEyebrow(anime: HomeShelfAnime): String? {
    val season = anime.season ?: return null
    val year = anime.year ?: return null
    val seasonName = stringResource(
        when (season) {
            AnimeSeason.WINTER -> R.string.season_winter
            AnimeSeason.SPRING -> R.string.season_spring
            AnimeSeason.SUMMER -> R.string.season_summer
            AnimeSeason.FALL -> R.string.season_fall
        }
    )
    return stringResource(R.string.home_eyebrow_premiere, seasonName, year)
}
