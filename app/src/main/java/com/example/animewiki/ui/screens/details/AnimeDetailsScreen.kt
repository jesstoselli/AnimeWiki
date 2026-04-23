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
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.example.animewiki.domain.model.Anime
import com.example.animewiki.ui.components.AnimeWikiScaffold
import com.example.animewiki.ui.screens.details.components.DetailsScreenError
import com.example.animewiki.ui.screens.details.components.InfoChip
import com.example.animewiki.ui.screens.details.components.InfoRow
import com.example.animewiki.ui.screens.details.components.Tone

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AnimeDetailsScreen(
    onBack: () -> Unit,
    viewModel: AnimeDetailsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    AnimeWikiScaffold(
        title = (state as? DetailsUiState.Success)?.anime?.title ?: "Detalhes",
        onBack = onBack
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
                    InfoChip("★ ${"%.2f".format(it)}", Tone.Tertiary)   // matcha
                }
                anime.rank?.let { InfoChip("#${it}", Tone.Primary) }    // sakura
                anime.type?.let { InfoChip(it, Tone.Secondary) }         // lavender
                anime.year?.let { InfoChip("$it", Tone.Secondary) }      // lavender
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
                    "Sinopse",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }

            HorizontalDivider()

            InfoRow("Episódios", anime.episodes?.toString())
            InfoRow("Duração", anime.duration)
            InfoRow("Status", anime.status)
            InfoRow("Exibição", anime.aired)
            InfoRow("Classificação", anime.rating)
            InfoRow("Estúdio", anime.studios.takeIf { it.isNotEmpty() }?.joinToString(", "))
        }
    }
}