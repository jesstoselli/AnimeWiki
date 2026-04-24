package com.example.animewiki.ui.screens.details.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun InfoChip(text: String, infoChipTone: InfoChipTone = InfoChipTone.Secondary) {
    val scheme = MaterialTheme.colorScheme
    val (bg, fg) = when (infoChipTone) {
        InfoChipTone.Primary -> scheme.primaryContainer to scheme.onPrimaryContainer
        InfoChipTone.Secondary -> scheme.secondaryContainer to scheme.onSecondaryContainer
        InfoChipTone.Tertiary -> scheme.tertiaryContainer to scheme.onTertiaryContainer
    }
    Surface(color = bg, contentColor = fg, shape = MaterialTheme.shapes.small) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}
