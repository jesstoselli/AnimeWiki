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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.example.animewiki.R
import com.example.animewiki.domain.model.Anime
import com.example.animewiki.domain.model.AnimeCharacterRole
import com.example.animewiki.domain.model.AnimeRelationType
import com.example.animewiki.ui.components.AnimeWikiScaffold
import com.example.animewiki.ui.screens.details.components.CharacterCastCard
import com.example.animewiki.ui.screens.details.components.DetailsMediaCard
import com.example.animewiki.ui.screens.details.components.DetailsScreenError
import com.example.animewiki.ui.screens.details.components.ExpandableDetailsSection
import com.example.animewiki.ui.screens.details.components.InfoChip
import com.example.animewiki.ui.screens.details.components.InfoChipTone
import com.example.animewiki.ui.screens.details.components.InfoRow
import com.example.animewiki.ui.screens.details.components.StreamingLinkCard

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AnimeDetailsScreen(
    onBack: () -> Unit,
    onAnimeClick: (Int) -> Unit,
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

            is DetailsUiState.Success -> key(s.anime.id) {
                AnimeDetailsContent(
                    anime = s.anime,
                    onAnimeClick = onAnimeClick,
                    modifier = Modifier.padding(padding)
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
@Suppress("CyclomaticComplexMethod", "LongMethod")
private fun AnimeDetailsContent(
    anime: Anime,
    onAnimeClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val uriHandler = LocalUriHandler.current

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

            anime.synopsis?.let { synopsis ->
                ExpandableDetailsSection(
                    title = stringResource(R.string.details_synopsis),
                    initiallyExpanded = true
                ) {
                    Text(synopsis, style = MaterialTheme.typography.bodyMedium)
                }
            }

            ExpandableDetailsSection(
                title = stringResource(R.string.details_information),
                initiallyExpanded = true
            ) {
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

            if (anime.characters.isNotEmpty()) {
                ExpandableDetailsSection(
                    title = stringResource(R.string.details_characters_cast),
                    initiallyExpanded = false
                ) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(anime.characters, key = { it.id }) { character ->
                            CharacterCastCard(
                                character = character,
                                roleLabel = characterRoleLabel(character.role)
                            )
                        }
                    }
                }
            }

            if (anime.relations.isNotEmpty()) {
                ExpandableDetailsSection(
                    title = stringResource(R.string.details_related),
                    initiallyExpanded = false
                ) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(anime.relations, key = { "${it.type}-${it.media.id}" }) { relation ->
                            DetailsMediaCard(
                                media = relation.media,
                                eyebrow = mediaLabel(
                                    label = relationLabel(relation.type),
                                    mediaType = relation.media.mediaType,
                                    isAnime = relation.media.isAnime
                                ),
                                onClick = if (relation.media.isAnime) {
                                    { onAnimeClick(relation.media.id) }
                                } else {
                                    null
                                }
                            )
                        }
                    }
                }
            }

            if (anime.streamingLinks.isNotEmpty()) {
                ExpandableDetailsSection(
                    title = stringResource(R.string.details_where_to_watch),
                    initiallyExpanded = false
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        anime.streamingLinks.forEach { link ->
                            StreamingLinkCard(
                                link = link,
                                onClick = { uriHandler.openUri(link.url) }
                            )
                        }
                        Text(
                            text = stringResource(R.string.details_streaming_region_notice),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (anime.recommendations.isNotEmpty()) {
                ExpandableDetailsSection(
                    title = stringResource(R.string.details_recommendations),
                    initiallyExpanded = false
                ) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(anime.recommendations, key = { it.media.id }) { recommendation ->
                            val recommendationLabel = recommendation.votes
                                .takeIf { it > 0 }
                                ?.let { stringResource(R.string.details_recommendation_votes, it) }
                            DetailsMediaCard(
                                media = recommendation.media,
                                eyebrow = mediaLabel(
                                    label = recommendationLabel,
                                    mediaType = recommendation.media.mediaType,
                                    isAnime = recommendation.media.isAnime
                                ),
                                onClick = if (recommendation.media.isAnime) {
                                    { onAnimeClick(recommendation.media.id) }
                                } else {
                                    null
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun characterRoleLabel(role: AnimeCharacterRole): String = stringResource(
    when (role) {
        AnimeCharacterRole.MAIN -> R.string.details_character_main
        AnimeCharacterRole.SUPPORTING -> R.string.details_character_supporting
        AnimeCharacterRole.BACKGROUND -> R.string.details_character_background
    }
)

@Composable
private fun relationLabel(type: AnimeRelationType): String = stringResource(
    when (type) {
        AnimeRelationType.PREQUEL -> R.string.details_relation_prequel
        AnimeRelationType.SEQUEL -> R.string.details_relation_sequel
        AnimeRelationType.SPIN_OFF -> R.string.details_relation_spin_off
        AnimeRelationType.SIDE_STORY -> R.string.details_relation_side_story
        AnimeRelationType.ADAPTATION -> R.string.details_relation_adaptation
        AnimeRelationType.ALTERNATIVE -> R.string.details_relation_alternative
        AnimeRelationType.OTHER -> R.string.details_relation_other
    }
)

@Composable
private fun mediaLabel(label: String?, mediaType: String, isAnime: Boolean): String = when {
    !isAnime && label != null -> stringResource(R.string.details_media_label, label, mediaType)
    !isAnime -> mediaType
    label != null -> label
    else -> mediaType
}
