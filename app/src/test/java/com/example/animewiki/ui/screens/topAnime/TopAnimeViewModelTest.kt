package com.example.animewiki.ui.screens.topAnime

import app.cash.turbine.test
import com.example.animewiki.data.repository.AnimeRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TopAnimeViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val repository: AnimeRepository = mockk {
        every { topAnime() } returns emptyFlow()
        every { searchAnime(any()) } returns emptyFlow()
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
}
