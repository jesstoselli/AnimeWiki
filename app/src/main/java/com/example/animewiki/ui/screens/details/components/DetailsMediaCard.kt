package com.example.animewiki.ui.screens.details.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.example.animewiki.domain.model.AnimeMediaPreview

@Composable
internal fun DetailsMediaCard(
    media: AnimeMediaPreview,
    eyebrow: String,
    onClick: (() -> Unit)? = null
) {
    val content: @Composable ColumnScope.() -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            AsyncImage(
                model = media.imageUrl,
                contentDescription = media.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
            )
            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                Text(
                    text = eyebrow,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = media.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                media.score?.let {
                    Text("★ ${"%.2f".format(it)}", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }

    if (onClick == null) {
        Card(modifier = Modifier.width(150.dp), content = content)
    } else {
        Card(onClick = onClick, modifier = Modifier.width(150.dp), content = content)
    }
}
