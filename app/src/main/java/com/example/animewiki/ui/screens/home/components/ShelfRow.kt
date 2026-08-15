package com.example.animewiki.ui.screens.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.animewiki.R
import com.example.animewiki.domain.model.HomeShelf
import com.example.animewiki.ui.screens.home.ShelfState

@Composable
internal fun ShelfRow(
    shelf: HomeShelf,
    title: String,
    state: ShelfState,
    onAnimeClick: (Int) -> Unit,
    onRetry: () -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
        when (state) {
            is ShelfState.Loading -> ShelfPlaceholder()
            is ShelfState.Error -> ShelfError(onRetry)
            is ShelfState.Content -> LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
            ) {
                items(state.items, key = { it.id }) { anime ->
                    ShelfAnimeCard(shelf, anime) { onAnimeClick(anime.id) }
                }
            }
        }
    }
}

@Composable
private fun ShelfPlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(190.dp),
        contentAlignment = Alignment.Center
    ) {
        Text("…", style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun ShelfError(onRetry: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp),
        contentAlignment = Alignment.Center
    ) {
        TextButton(onClick = onRetry) {
            Text(stringResource(R.string.home_shelf_error))
        }
    }
}
