package com.example.animewiki.ui.screens.topAnime.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.animewiki.R
import com.example.animewiki.domain.model.AnimeFilters
import com.example.animewiki.domain.model.AnimeGenre

@Composable
internal fun AnimeFilterBar(
    filters: AnimeFilters,
    genres: List<AnimeGenre>,
    onOpen: () -> Unit,
    onChange: (AnimeFilters) -> Unit,
    modifier: Modifier = Modifier
) {
    val genreNames = genres.associateBy(AnimeGenre::id)
    val filtersLabel = stringResource(R.string.filters_open)
    val activeFiltersLabel = stringResource(R.string.filters_active_count, filters.activeCount)
    val buttonDescription = if (filters.activeCount > 0) {
        "$filtersLabel, $activeFiltersLabel"
    } else {
        filtersLabel
    }

    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilledTonalButton(
            onClick = onOpen,
            modifier = Modifier.semantics { contentDescription = buttonDescription }
        ) {
            Icon(Icons.Default.FilterList, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text(filtersLabel)
            if (filters.activeCount > 0) Text(" (${filters.activeCount})")
        }
        filters.format?.let { format ->
            RemovableFilterChip(stringResource(format.labelRes())) {
                onChange(filters.copy(format = null))
            }
        }
        filters.rating?.let { rating ->
            RemovableFilterChip(stringResource(rating.labelRes())) {
                onChange(filters.copy(rating = null))
            }
        }
        filters.genreIds.sorted().forEach { id ->
            genreNames[id]?.let { genre ->
                RemovableFilterChip(genre.name) {
                    onChange(filters.copy(genreIds = filters.genreIds - id))
                }
            }
        }
    }
}

@Composable
private fun RemovableFilterChip(label: String, onRemove: () -> Unit) {
    InputChip(
        selected = true,
        onClick = onRemove,
        label = { Text(label) },
        trailingIcon = {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(R.string.filters_remove, label)
            )
        }
    )
}
