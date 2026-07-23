package com.example.animewiki.ui.screens.topAnime.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.animewiki.R
import com.example.animewiki.domain.model.AnimeAgeRating
import com.example.animewiki.domain.model.AnimeFilters
import com.example.animewiki.domain.model.AnimeFormat
import com.example.animewiki.ui.screens.topAnime.AnimeGenresState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AnimeFilterSheet(
    appliedFilters: AnimeFilters,
    genresState: AnimeGenresState,
    onDismiss: () -> Unit,
    onApply: (AnimeFilters) -> Unit,
    onRetryGenres: () -> Unit
) {
    var draft by remember(appliedFilters) { mutableStateOf(appliedFilters) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxHeight(0.9f)
                .padding(horizontal = 20.dp)
        ) {
            Text(
                text = stringResource(R.string.filters_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() }
            )
            FilterOptions(
                draft = draft,
                genresState = genresState,
                onDraftChange = { draft = it },
                onRetryGenres = onRetryGenres,
                modifier = Modifier.weight(1f)
            )
            FilterSheetActions(
                onClear = { draft = AnimeFilters() },
                onApply = { onApply(draft) }
            )
        }
    }
}

@Composable
private fun FilterOptions(
    draft: AnimeFilters,
    genresState: AnimeGenresState,
    onDraftChange: (AnimeFilters) -> Unit,
    onRetryGenres: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            FilterChoiceGroup(
                title = R.string.filters_format,
                options = AnimeFormat.entries,
                selected = draft.format,
                labelRes = AnimeFormat::labelRes,
                onSelected = { onDraftChange(draft.copy(format = it)) }
            )
        }
        item {
            FilterChoiceGroup(
                title = R.string.filters_rating,
                options = AnimeAgeRating.entries,
                selected = draft.rating,
                labelRes = AnimeAgeRating::labelRes,
                onSelected = { onDraftChange(draft.copy(rating = it)) }
            )
        }
        item { FilterSectionTitle(R.string.filters_genres) }
        when (genresState) {
            AnimeGenresState.Idle, AnimeGenresState.Loading -> item { CircularProgressIndicator() }

            is AnimeGenresState.Error -> item {
                GenreLoadError(onRetryGenres)
            }

            is AnimeGenresState.Content -> items(genresState.genres, key = { it.id }) { genre ->
                GenreFilterRow(
                    name = genre.name,
                    selected = genre.id in draft.genreIds,
                    onSelectedChange = { selected ->
                        val genreIds = if (selected) draft.genreIds + genre.id else draft.genreIds - genre.id
                        onDraftChange(draft.copy(genreIds = genreIds))
                    }
                )
            }
        }
    }
}

@Composable
private fun <T> FilterChoiceGroup(
    @StringRes title: Int,
    options: List<T>,
    selected: T?,
    labelRes: (T) -> Int,
    onSelected: (T?) -> Unit
) {
    FilterSectionTitle(title)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            FilterChip(
                selected = selected == option,
                onClick = { onSelected(option.takeUnless { it == selected }) },
                label = { Text(stringResource(labelRes(option))) }
            )
        }
    }
}

@Composable
private fun GenreLoadError(onRetryGenres: () -> Unit) {
    Column {
        Text(stringResource(R.string.filters_genres_error))
        TextButton(onClick = onRetryGenres) {
            Text(stringResource(R.string.filters_retry_genres))
        }
    }
}

@Composable
private fun GenreFilterRow(
    name: String,
    selected: Boolean,
    onSelectedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .requiredHeightIn(min = 48.dp)
            .toggleable(value = selected, onValueChange = onSelectedChange)
            .padding(vertical = 4.dp)
            .semantics { role = Role.Checkbox },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = selected, onCheckedChange = null)
        Text(name, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun FilterSheetActions(onClear: () -> Unit, onApply: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedButton(onClick = onClear, modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.filters_clear))
        }
        Button(onClick = onApply, modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.filters_apply))
        }
    }
}

@Composable
private fun FilterSectionTitle(@StringRes text: Int) {
    Text(
        text = stringResource(text),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.semantics { heading() }
    )
}
