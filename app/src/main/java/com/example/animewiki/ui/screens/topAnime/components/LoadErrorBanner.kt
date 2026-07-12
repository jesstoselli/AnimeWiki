package com.example.animewiki.ui.screens.topAnime.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.animewiki.R
import com.example.animewiki.ui.common.LoadErrorType

@Composable
internal fun LoadErrorBanner(type: LoadErrorType, modifier: Modifier = Modifier) {
    val (icon, text) = when (type) {
        LoadErrorType.NO_CONNECTION ->
            Icons.Default.CloudOff to stringResource(R.string.offline_banner)
        LoadErrorType.SERVER ->
            Icons.Default.ErrorOutline to stringResource(R.string.server_error_banner)
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(12.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.small,
        shadowElevation = 2.dp
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(text, style = MaterialTheme.typography.bodySmall)
        }
    }
}
