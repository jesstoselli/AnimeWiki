package com.example.animewiki.ui.screens.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.animewiki.R
import com.example.animewiki.domain.model.HomeShelf
import com.example.animewiki.ui.components.AnimeWikiScaffold
import com.example.animewiki.ui.screens.home.components.ShelfRow

@Composable
fun HomeScreen(
    onAnimeClick: (Int) -> Unit,
    onSettingsClick: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    AnimeWikiScaffold(
        title = stringResource(R.string.home_title),
        actions = {
            IconButton(onClick = onSettingsClick) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = stringResource(R.string.top_anime_settings)
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            HomeShelf.entries.forEach { shelf ->
                ShelfRow(
                    shelf = shelf,
                    title = stringResource(shelf.titleRes()),
                    state = state.getValue(shelf),
                    onAnimeClick = onAnimeClick,
                    onRetry = { viewModel.retry(shelf) }
                )
            }
        }
    }
}

private fun HomeShelf.titleRes(): Int = when (this) {
    HomeShelf.THIS_SEASON -> R.string.home_shelf_this_season
    HomeShelf.UPCOMING -> R.string.home_shelf_upcoming
    HomeShelf.TOP -> R.string.home_shelf_top
    HomeShelf.TRENDING -> R.string.home_shelf_trending
}
