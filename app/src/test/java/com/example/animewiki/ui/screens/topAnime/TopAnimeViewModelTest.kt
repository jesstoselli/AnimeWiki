package com.example.animewiki.ui.screens.topAnime

import androidx.paging.PagingData
import app.cash.turbine.test
import com.example.animewiki.data.repository.AnimeRepository
import com.example.animewiki.domain.model.AnimeAgeRating
import com.example.animewiki.domain.model.AnimeFilters
import com.example.animewiki.domain.model.AnimeFormat
import com.example.animewiki.domain.model.AnimeGenre
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class TopAnimeViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val repository: AnimeRepository = mockk {
        every { topAnime() } returns flowOf(PagingData.empty())
        every { searchAnime(any()) } returns flowOf(PagingData.empty())
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial query is empty string`() = runTest {
        val viewModel = TopAnimeViewModel(repository)

        assertEquals("", viewModel.query.value)
    }

    @Test
    fun `onQueryChange updates query state flow`() = runTest {
        val viewModel = TopAnimeViewModel(repository)

        viewModel.query.test {
            assertEquals("", awaitItem())

            viewModel.onQueryChange("frieren")
            assertEquals("frieren", awaitItem())

            viewModel.onQueryChange("jojo")
            assertEquals("jojo", awaitItem())
        }
    }

    @Test
    fun `clearQuery resets query to empty string`() = runTest {
        val viewModel = TopAnimeViewModel(repository)
        viewModel.onQueryChange("something")

        viewModel.query.test {
            // StateFlow always replays its current value to new collectors
            assertEquals("something", awaitItem())

            viewModel.clearQuery()
            assertEquals("", awaitItem())
        }
    }

    @Test
    fun `applying filters switches from top to criteria search immediately`() = runTest {
        val viewModel = TopAnimeViewModel(repository)
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.animeList.collect {}
        }
        advanceUntilIdle()

        viewModel.applyFilters(AnimeFilters(format = AnimeFormat.TV))
        advanceUntilIdle()

        verify { repository.topAnime() }
        verify {
            repository.searchAnime(
                match {
                    it.query.isEmpty() && it.filters.format == AnimeFormat.TV
                }
            )
        }
        job.cancel()
    }

    @Test
    fun `query and applied filters form one normalized paging identity`() = runTest {
        val viewModel = TopAnimeViewModel(repository)
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.animeList.collect {}
        }

        viewModel.onQueryChange("  frieren  ")
        viewModel.applyFilters(
            AnimeFilters(rating = AnimeAgeRating.PG13, genreIds = setOf(10, 1))
        )
        advanceTimeBy(401)
        advanceUntilIdle()

        verify {
            repository.searchAnime(
                match {
                    it.query == "frieren" &&
                        it.filters.rating == AnimeAgeRating.PG13 &&
                        it.filters.genresQuery == "1,10"
                }
            )
        }
        job.cancel()
    }

    @Test
    fun `clear filters restores empty applied state`() = runTest {
        val viewModel = TopAnimeViewModel(repository)
        viewModel.applyFilters(AnimeFilters(rating = AnimeAgeRating.PG13, genreIds = setOf(1)))

        viewModel.clearFilters()

        assertEquals(AnimeFilters(), viewModel.filters.value)
    }

    @Test
    fun `removing genre preserves the other applied filters`() = runTest {
        val viewModel = TopAnimeViewModel(repository)
        viewModel.applyFilters(AnimeFilters(format = AnimeFormat.TV, genreIds = setOf(1, 2)))

        viewModel.removeGenre(1)

        assertEquals(
            AnimeFilters(format = AnimeFormat.TV, genreIds = setOf(2)),
            viewModel.filters.value
        )
    }

    @Test
    fun `load genres exposes loading then content`() = runTest {
        val genres = listOf(AnimeGenre(1, "Action", 30))
        coEvery { repository.getAnimeGenres(false) } coAnswers {
            yield()
            genres
        }
        val viewModel = TopAnimeViewModel(repository)

        viewModel.genresState.test {
            assertEquals(AnimeGenresState.Idle, awaitItem())
            viewModel.loadGenres()
            assertEquals(AnimeGenresState.Loading, awaitItem())
            assertEquals(AnimeGenresState.Content(genres), awaitItem())
        }
    }

    @Test
    fun `genre failure remains independent from anime paging`() = runTest {
        coEvery { repository.getAnimeGenres(false) } throws IOException("upstream")
        val viewModel = TopAnimeViewModel(repository)

        viewModel.loadGenres()
        advanceUntilIdle()

        assertTrue(viewModel.genresState.value is AnimeGenresState.Error)
    }

    @Test
    fun `retry genres forces refresh after an error`() = runTest {
        val genres = listOf(AnimeGenre(1, "Action", 30))
        coEvery { repository.getAnimeGenres(false) } throws IOException("upstream")
        coEvery { repository.getAnimeGenres(true) } returns genres
        val viewModel = TopAnimeViewModel(repository)

        viewModel.loadGenres()
        advanceUntilIdle()
        viewModel.retryGenres()
        advanceUntilIdle()

        assertEquals(AnimeGenresState.Content(genres), viewModel.genresState.value)
        coVerify { repository.getAnimeGenres(true) }
    }
}
