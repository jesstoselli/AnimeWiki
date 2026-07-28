package com.example.animewiki.ui.screens.topAnime.components

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.example.animewiki.R
import com.example.animewiki.domain.model.AnimeFilters
import com.example.animewiki.domain.model.AnimeFormat
import com.example.animewiki.ui.screens.topAnime.AnimeGenresState
import com.example.animewiki.ui.theme.AnimeWikiTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AnimeFilterSheetTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun selectingFormatOnlyChangesAppliedFiltersAfterApply() {
        var applied = AnimeFilters()
        val applyLabel = InstrumentationRegistry.getInstrumentation()
            .targetContext
            .getString(R.string.filters_apply)

        composeRule.setContent {
            AnimeWikiTheme {
                AnimeFilterSheet(
                    appliedFilters = applied,
                    genresState = AnimeGenresState.Content(emptyList()),
                    onDismiss = {},
                    onApply = { applied = it },
                    onRetryGenres = {}
                )
            }
        }

        composeRule.onNodeWithText("TV").performClick().assertIsSelected()
        assertEquals(AnimeFilters(), applied)
        composeRule.onNodeWithText(applyLabel).performClick()
        assertEquals(AnimeFormat.TV, applied.format)
    }

    @Test
    fun adultContentIsOneAccessibleToggleAndOnlyAppliesAfterApply() {
        var applied = AnimeFilters()
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val adultLabel = targetContext.getString(R.string.filters_include_adult)
        val applyLabel = targetContext.getString(R.string.filters_apply)

        composeRule.setContent {
            AnimeWikiTheme {
                AnimeFilterSheet(
                    appliedFilters = applied,
                    genresState = AnimeGenresState.Content(emptyList()),
                    onDismiss = {},
                    onApply = { applied = it },
                    onRetryGenres = {}
                )
            }
        }

        composeRule.onNodeWithText(adultLabel)
            .assertIsToggleable()
            .assertIsOff()
            .assertHeightIsAtLeast(48.dp)
            .performClick()
            .assertIsOn()
        assertEquals(false, applied.includeAdultContent)

        composeRule.onNodeWithText(applyLabel).performClick()
        assertEquals(true, applied.includeAdultContent)
    }
}
