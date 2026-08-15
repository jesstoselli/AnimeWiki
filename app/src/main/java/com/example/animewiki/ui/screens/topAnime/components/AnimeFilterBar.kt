package com.example.animewiki.ui.screens.topAnime.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Business
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
import com.example.animewiki.domain.model.AnimeOrganization
import com.example.animewiki.domain.model.AnimeSort

@Composable
internal fun AnimeFilterBar(
    filters: AnimeFilters,
    organization: AnimeOrganization?,
    onOpen: () -> Unit,
    onOpenOrganizations: () -> Unit,
    onClearOrganization: () -> Unit,
    onChange: (AnimeFilters) -> Unit,
    modifier: Modifier = Modifier
) {
    val filtersLabel = stringResource(R.string.filters_open)
    val organizationsLabel = stringResource(R.string.organizations_open)
    val activeFiltersLabel = stringResource(R.string.filters_active_count, filters.activeCount)
    val hasActiveSelection = !filters.isEmpty || organization != null
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
            modifier = Modifier
                .height(48.dp)
                .semantics { contentDescription = buttonDescription },
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
        ) {
            Icon(Icons.Default.FilterList, contentDescription = null)
            AnimatedVisibility(
                visible = !hasActiveSelection,
                enter = fadeIn() + expandHorizontally(),
                exit = fadeOut() + shrinkHorizontally()
            ) {
                Row {
                    Spacer(Modifier.width(6.dp))
                    Text(filtersLabel)
                }
            }
        }
        FilledTonalButton(
            onClick = onOpenOrganizations,
            modifier = Modifier
                .height(48.dp)
                .semantics { contentDescription = organizationsLabel },
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
        ) {
            Icon(Icons.Default.Business, contentDescription = null)
            AnimatedVisibility(
                visible = !hasActiveSelection,
                enter = fadeIn() + expandHorizontally(),
                exit = fadeOut() + shrinkHorizontally()
            ) {
                Row {
                    Spacer(Modifier.width(6.dp))
                    Text(organizationsLabel)
                }
            }
        }
        organization?.let {
            RemovableFilterChip(it.name, onClearOrganization)
        }
        if (filters.sort != AnimeSort.SCORE) {
            RemovableFilterChip(stringResource(filters.sort.labelRes())) {
                onChange(filters.copy(sort = AnimeSort.SCORE))
            }
        }
        filters.format?.let { format ->
            RemovableFilterChip(stringResource(format.labelRes())) {
                onChange(filters.copy(format = null))
            }
        }
        if (filters.includeAdultContent) {
            RemovableFilterChip(stringResource(R.string.filters_include_adult)) {
                onChange(filters.copy(includeAdultContent = false))
            }
        }
        filters.genres.sorted().forEach { genre ->
            RemovableFilterChip(genre) {
                onChange(filters.copy(genres = filters.genres - genre))
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
