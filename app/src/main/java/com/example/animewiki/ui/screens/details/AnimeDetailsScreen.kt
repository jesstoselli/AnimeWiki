package com.example.animewiki.ui.screens.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.example.animewiki.R
import com.example.animewiki.domain.model.Anime
import com.example.animewiki.ui.components.AnimeWikiScaffold
import com.example.animewiki.ui.screens.details.components.DetailsScreenError
import com.example.animewiki.ui.screens.details.components.InfoChip
import com.example.animewiki.ui.screens.details.components.InfoChipTone
import com.example.animewiki.ui.screens.details.components.InfoRow

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AnimeDetailsScreen(
    onBack: () -> Unit,
    viewModel: AnimeDetailsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val isFavorite by viewModel.isFavorite.collectAsState()

    val fallbackTitle = stringResource(R.string.details_title)
    val pageTitle = (state as? DetailsUiState.Success)?.anime?.title ?: fallbackTitle

    AnimeWikiScaffold(
        title = pageTitle,
        onBack = onBack,
        actions = {
            if (state is DetailsUiState.Success) {
                IconButton(onClick = viewModel::onToggleFavorite) {
                    Icon(
                        imageVector = if (isFavorite) {
                            Icons.Default.Favorite
                        } else {
                            Icons.Default.FavoriteBorder
                        },
                        contentDescription = stringResource(
                            if (isFavorite) {
                                R.string.favorite_remove
                            } else {
                                R.string.favorite_add
                            }
                        )
                    )
                }
            }
        }
    ) { padding ->
        when (val s = state) {
            is DetailsUiState.Loading -> Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            is DetailsUiState.Error -> DetailsScreenError(
                errorMessage = s.message,
                padding = padding,
                onClick = viewModel::load
            )

            is DetailsUiState.Success -> AnimeDetailsContent(
                anime = s.anime,
                modifier = Modifier.padding(padding)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AnimeDetailsContent(anime: Anime, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        AsyncImage(
            model = anime.imageUrl,
            contentDescription = anime.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
        )

        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = anime.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                anime.score?.let {
                    InfoChip("★ ${"%.2f".format(it)}", InfoChipTone.Tertiary) // matcha
                }
                anime.rank?.let { InfoChip("#$it", InfoChipTone.Primary) } // sakura
                anime.type?.let { InfoChip(it, InfoChipTone.Secondary) } // lavender
                anime.year?.let { InfoChip("$it", InfoChipTone.Secondary) } // lavender
            }

            if (anime.genres.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    anime.genres.forEach { genre ->
                        AssistChip(
                            onClick = {},
                            label = { Text(genre) },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                labelColor = MaterialTheme.colorScheme.onSecondaryContainer
                            ),
                            border = null
                        )
                    }
                }
            }

            anime.synopsis?.let {
                Text(
                    stringResource(R.string.details_synopsis),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }

            HorizontalDivider()

            InfoRow(stringResource(R.string.details_episodes), anime.episodes?.toString())
            InfoRow(stringResource(R.string.details_duration), anime.duration)
            InfoRow(stringResource(R.string.details_status), anime.status)
            InfoRow(stringResource(R.string.details_aired), anime.aired)
            InfoRow(stringResource(R.string.details_rating), anime.rating)
            InfoRow(
                stringResource(R.string.details_studio),
                anime.studios.takeIf { it.isNotEmpty() }?.joinToString(", ")
            )
        }
    }
}
