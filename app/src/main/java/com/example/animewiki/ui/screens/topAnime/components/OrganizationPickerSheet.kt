package com.example.animewiki.ui.screens.topAnime.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.animewiki.R
import com.example.animewiki.domain.model.AnimeOrganization
import com.example.animewiki.ui.screens.topAnime.AnimeOrganizationsState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OrganizationPickerSheet(
    query: String,
    state: AnimeOrganizationsState,
    onQueryChange: (String) -> Unit,
    onSelect: (AnimeOrganization) -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxHeight(0.9f)
                .padding(horizontal = 20.dp)
        ) {
            Text(
                text = stringResource(R.string.organizations_title),
                style = MaterialTheme.typography.titleLarge
            )
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                label = { Text(stringResource(R.string.organizations_search)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
            )
            when (state) {
                AnimeOrganizationsState.Idle,
                AnimeOrganizationsState.Loading -> CircularProgressIndicator()

                is AnimeOrganizationsState.Error -> TextButton(onClick = onRetry) {
                    Text(stringResource(R.string.organizations_retry))
                }

                is AnimeOrganizationsState.Content -> LazyColumn {
                    items(state.organizations, key = AnimeOrganization::id) { organization ->
                        ListItem(
                            headlineContent = { Text(organization.name) },
                            supportingContent = {
                                Text(
                                    stringResource(
                                        if (organization.isAnimationStudio) {
                                            R.string.organization_studio
                                        } else {
                                            R.string.organization_producer
                                        }
                                    )
                                )
                            },
                            modifier = Modifier.clickable { onSelect(organization) }
                        )
                    }
                }
            }
        }
    }
}
