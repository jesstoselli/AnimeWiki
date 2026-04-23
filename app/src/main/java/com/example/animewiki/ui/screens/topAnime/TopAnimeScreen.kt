package com.example.animewiki.ui.screens.topAnime

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.example.animewiki.domain.model.Anime
import com.example.animewiki.ui.components.AnimeWikiScaffold
import com.example.animewiki.ui.screens.topAnime.components.AnimeCard
import com.example.animewiki.ui.screens.topAnime.components.EmptySearchState
import com.example.animewiki.ui.screens.topAnime.components.FullScreenError
import com.example.animewiki.ui.screens.topAnime.components.OfflineBanner
import com.example.animewiki.ui.screens.topAnime.components.SearchField
import com.example.animewiki.ui.screens.topAnime.components.SkeletonGrid

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopAnimeScreen(
    onAnimeClick: (Int) -> Unit,
    viewModel: TopAnimeViewModel = hiltViewModel()
) {
    val items = viewModel.animeList.collectAsLazyPagingItems()
    val query by viewModel.query.collectAsStateWithLifecycle()

    AnimeWikiScaffold(title = "Top Anime") { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            SearchField(
                query = query,
                onQueryChange = viewModel::onQueryChange,
                onClear = viewModel::clearQuery,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            )

            val isEmpty = items.itemCount == 0
            val refreshState = items.loadState.refresh

            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    isEmpty && refreshState is LoadState.Loading -> SkeletonGrid(
                        modifier = Modifier.fillMaxSize()
                    )

                    isEmpty && refreshState is LoadState.Error -> FullScreenError(
                        message = refreshState.error.message ?: "Tente novamente",
                        onRetry = { items.retry() }
                    )

                    isEmpty && refreshState is LoadState.NotLoading && query.isNotBlank() ->
                        EmptySearchState(query = query)

                    else -> TopAnimeContent(items, onAnimeClick)
                }

                if (refreshState is LoadState.Error && !isEmpty) {
                    OfflineBanner(modifier = Modifier.align(Alignment.TopCenter))
                }
            }
        }
    }
}

@Composable
internal fun TopAnimeContent(
    items: LazyPagingItems<Anime>,
    onAnimeClick: (Int) -> Unit
) {
    val isRefreshing = items.loadState.refresh is LoadState.Loading

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = { items.refresh() },
        modifier = Modifier.fillMaxSize()
    ) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 160.dp),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(
                count = items.itemCount,
                key = items.itemKey { it.id }
            ) { index ->
                val anime = items[index] ?: return@items
                AnimeCard(anime = anime, onClick = { onAnimeClick(anime.id) })
            }

            if (items.loadState.append is LoadState.Loading) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            val appendError = items.loadState.append as? LoadState.Error
            if (appendError != null) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "Erro ao carregar mais: ${appendError.error.message ?: "desconhecido"}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { items.retry() }) { Text("Tentar de novo") }
                    }
                }
            }
        }
    }
}