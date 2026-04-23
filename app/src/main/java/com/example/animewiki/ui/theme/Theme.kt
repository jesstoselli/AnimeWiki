package com.example.animewiki.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val SakuraLightColors = lightColorScheme(
    // Primary: Sakura profunda com container cor-de-cereja
    primary = SakuraRose,
    onPrimary = Color.White,
    primaryContainer = SakuraPink,              // ← sua #F48FB1 original
    onPrimaryContainer = SakuraPinkDeep,

    // Secondary: ameixa profunda com container Lavender Mist
    secondary = LavenderPlum,
    onSecondary = Color.White,
    secondaryContainer = LavenderMist,          // ← sua #CE93D8 original
    onSecondaryContainer = LavenderDeep,

    // Tertiary: matcha escuro com container Matcha verde
    tertiary = MatchaDeepGreen,
    onTertiary = Color.White,
    tertiaryContainer = MatchaGreen,            // ← sua #A5D6A7 original
    onTertiaryContainer = MatchaDeep,

    background = CreamShell,
    onBackground = InkBrown,

    surface = CreamShell,
    onSurface = InkBrown,
    surfaceVariant = Color(0xFFF7EED7),
    onSurfaceVariant = InkBrownSoft,

    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFDF7E8),
    surfaceContainer = Color(0xFFF8F0DA),
    surfaceContainerHigh = Color(0xFFF2E9CB),
    surfaceContainerHighest = Color(0xFFEDE2BD),

    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),

    outline = ParchmentLine,
    outlineVariant = Color(0xFFF2E9CB),
    scrim = Color.Black
)

private val SakuraDarkColors = darkColorScheme(
    primary = SakuraPink,                       // no dark, a Sakura Pink volta a ser a base (fica lindona)
    onPrimary = Color(0xFF3B0016),
    primaryContainer = Color(0xFF6D2D48),
    onPrimaryContainer = Color(0xFFFFD9E2),

    secondary = LavenderMist,
    onSecondary = Color(0xFF2D0B43),
    secondaryContainer = Color(0xFF4F2A65),
    onSecondaryContainer = Color(0xFFF3E5F5),

    tertiary = MatchaGreen,
    onTertiary = Color(0xFF0D3912),
    tertiaryContainer = Color(0xFF2E5A32),
    onTertiaryContainer = Color(0xFFDCEDC8),

    background = NightPlum,
    onBackground = Color(0xFFEDE3F0),

    surface = NightPlum,
    onSurface = Color(0xFFEDE3F0),
    surfaceVariant = NightPlumSoft,
    onSurfaceVariant = Color(0xFFCFB9D8),

    surfaceContainerLowest = NightPlumDeep,
    surfaceContainerLow = Color(0xFF1F1530),
    surfaceContainer = Color(0xFF241A36),
    surfaceContainerHigh = Color(0xFF2C213F),
    surfaceContainerHighest = Color(0xFF342848),

    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),

    outline = Color(0xFF8C7D94),
    outlineVariant = Color(0xFF4A3E53),
    scrim = Color.Black
)

@Composable
fun AnimeWikiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Sakura Dream tem personalidade — vamos ignorar o Material You dinâmico
    // pra manter a identidade visual consistente
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        darkTheme -> SakuraDarkColors
        else -> SakuraLightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = SakuraTypography,
        shapes = SakuraShapes,
        content = content
    )
}