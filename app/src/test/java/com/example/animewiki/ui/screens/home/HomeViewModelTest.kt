package com.example.animewiki.ui.screens.home

import app.cash.turbine.test
import com.example.animewiki.data.repository.HomeShelfRepository
import com.example.animewiki.domain.model.HomeShelf
import com.example.animewiki.domain.model.HomeShelfAnime
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private fun anime(id: Int) =
        HomeShelfAnime(id = id, title = "A$id", imageUrl = "https://img/$id.jpg", score = 8.0)

    @Before
    fun setUp() = Dispatchers.setMain(testDispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun repository(): HomeShelfRepository = mockk(relaxed = true) {
        HomeShelf.entries.forEach { every { observe(it) } returns MutableStateFlow(emptyList()) }
        coEvery { refresh(any()) } returns Unit
    }

    @Test
    fun `each shelf reaches content independently from its cache`() = runTest {
        val repo = repository()
        every { repo.observe(HomeShelf.TRENDING) } returns flowOf(listOf(anime(1)))
        val viewModel = HomeViewModel(repo)

        viewModel.uiState.test {
            advanceUntilIdle()
            val state = expectMostRecentItem()
            assertTrue(state.getValue(HomeShelf.TRENDING) is ShelfState.Content)
            assertEquals(
                1,
                (state.getValue(HomeShelf.TRENDING) as ShelfState.Content).items.size
            )
        }
    }

    @Test
    fun `a shelf whose refresh fails with empty cache shows error, others unaffected`() = runTest {
        val repo = repository()
        every { repo.observe(HomeShelf.UPCOMING) } returns flowOf(emptyList())
        coEvery { repo.refresh(HomeShelf.UPCOMING) } throws RuntimeException("boom")
        every { repo.observe(HomeShelf.TRENDING) } returns flowOf(listOf(anime(2)))
        val viewModel = HomeViewModel(repo)

        viewModel.uiState.test {
            advanceUntilIdle()
            val state = expectMostRecentItem()
            assertTrue(state.getValue(HomeShelf.UPCOMING) is ShelfState.Error)
            assertTrue(state.getValue(HomeShelf.TRENDING) is ShelfState.Content)
        }
    }

    @Test
    fun `retry re-runs only the requested shelf`() = runTest {
        val repo = repository()
        val viewModel = HomeViewModel(repo)
        advanceUntilIdle()

        viewModel.retry(HomeShelf.TOP)
        advanceUntilIdle()

        coVerify(atLeast = 2) { repo.refresh(HomeShelf.TOP) }
    }
}
