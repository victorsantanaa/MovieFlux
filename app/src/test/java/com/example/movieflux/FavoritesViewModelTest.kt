package com.example.movieflux

import app.cash.turbine.test
import com.example.movieflux.analytics.AnalyticsTracker
import com.example.movieflux.data.preferences.UiPreferences
import com.example.movieflux.view.components.ViewMode
import com.example.movieflux.view.favorites.FavoritesUiState
import com.example.movieflux.view.favorites.FavoritesViewModel
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
class FavoritesViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val repo = FakeMovieRepository()
    private val tracker: AnalyticsTracker = mockk(relaxed = true)
    private val uiPreferences: UiPreferences = mockk(relaxed = true) {
        every { getFavoritesViewMode() } returns ViewMode.GRID
    }

    private lateinit var viewModel: FavoritesViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        viewModel = FavoritesViewModel(repo, tracker, uiPreferences)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `favorites flow emission updates Success state`() = runTest {
        viewModel.uiState.test {
            awaitItem() // Loading
            repo.toggleFavorite(fakeMovie(1))
            val state = awaitItem() as FavoritesUiState.Success
            assertEquals(1, state.movies.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `init tracks favorites screen`() {
        verify { tracker.trackScreen("favorites") }
    }

    @Test
    fun `setViewMode updates viewMode independently from Home`() = runTest {
        val job = launch { viewModel.uiState.collect { } }
        advanceUntilIdle()

        viewModel.setViewMode(ViewMode.LIST)
        advanceUntilIdle()

        assertEquals(ViewMode.LIST, (viewModel.uiState.value as? FavoritesUiState.Success)?.viewMode)
        job.cancel()
    }

    @Test
    fun `search filters favorites by title case-insensitively`() = runTest {
        repo.toggleFavorite(fakeMovie(1).copy(title = "Inception"))
        repo.toggleFavorite(fakeMovie(2).copy(title = "The Dark Knight"))

        viewModel.setSearchQuery("dark")
        viewModel.uiState.test {
            val state = awaitItem() as? FavoritesUiState.Success
            assertEquals(1, state?.movies?.size)
            assertEquals("The Dark Knight", state?.movies?.first()?.title)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `toggleFavorite removes movie and Flow updates`() = runTest {
        val movie = fakeMovie(1, isFavorite = true)
        repo.toggleFavorite(fakeMovie(1)) // add it first

        viewModel.uiState.test {
            awaitItem() // initial with movie
            viewModel.toggleFavorite(movie)
            val after = awaitItem() as FavoritesUiState.Success
            assertTrue(after.movies.none { it.id == 1 })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `viewMode seeded from preferences favorites`() = runTest(dispatcher) {
        every { uiPreferences.getFavoritesViewMode() } returns ViewMode.LIST
        val vm = FavoritesViewModel(repo, tracker, uiPreferences)
        vm.uiState.test {
            val state = awaitItem() as FavoritesUiState.Success
            assertEquals(ViewMode.LIST, state.viewMode)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setViewMode persists to favorites key only`() {
        viewModel.setViewMode(ViewMode.LIST)
        verify(exactly = 1) { uiPreferences.setFavoritesViewMode(ViewMode.LIST) }
        verify(exactly = 0) { uiPreferences.setHomeViewMode(any()) }
    }
}
