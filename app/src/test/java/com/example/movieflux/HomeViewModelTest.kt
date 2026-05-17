package com.example.movieflux

import app.cash.turbine.test
import com.example.movieflux.analytics.AnalyticsTracker
import com.example.movieflux.domain.usecase.GetPopularMoviesUseCase
import com.example.movieflux.view.components.ViewMode
import com.example.movieflux.view.home.HomeEvent
import com.example.movieflux.view.home.HomeUiState
import com.example.movieflux.view.home.HomeViewModel
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val repo = FakeMovieRepository()
    private val tracker: AnalyticsTracker = mockk(relaxed = true)

    private lateinit var viewModel: HomeViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        repo.popularMovies = listOf(fakeMovie(1), fakeMovie(2), fakeMovie(3))
        viewModel = HomeViewModel(GetPopularMoviesUseCase(repo), repo, tracker)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `init loads movies and emits Success`() = runTest {
        viewModel.uiState.test {
            awaitItem() // Loading (stateIn initial value before upstream fires)
            val state = awaitItem()
            assertTrue(state is HomeUiState.Success)
            assertEquals(3, (state as HomeUiState.Success).movies.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `init tracks home screen`() {
        verify { tracker.trackScreen("home") }
    }

    @Test
    fun `network error emits Error state`() = runTest {
        repo.shouldThrow = true
        viewModel.loadMovies()
        viewModel.uiState.test {
            awaitItem() // Loading (stateIn initial value)
            val state = awaitItem()
            assertTrue(state is HomeUiState.Error)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setViewMode updates viewMode in Success state`() = runTest {
        viewModel.uiState.test {
            awaitItem() // initial Success
            viewModel.setViewMode(ViewMode.LIST)
            val updated = awaitItem()
            assertEquals(ViewMode.LIST, (updated as HomeUiState.Success).viewMode)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setViewMode does not refetch movies`() = runTest {
        val initialMovies = (viewModel.uiState.value as? HomeUiState.Success)?.movies
        viewModel.setViewMode(ViewMode.LIST)
        val afterMovies = (viewModel.uiState.value as? HomeUiState.Success)?.movies
        assertEquals(initialMovies, afterMovies)
    }

    @Test
    fun `loadNextPage appends movies and deduplicates`() = runTest {
        val page2 = listOf(fakeMovie(4), fakeMovie(5), fakeMovie(1)) // id=1 is a duplicate
        repo.popularMovies = page2
        viewModel.loadNextPage()
        viewModel.uiState.test {
            awaitItem() // Loading (stateIn initial value)
            val state = awaitItem() as? HomeUiState.Success
            // original 3 + 2 new (1 duplicate removed)
            assertEquals(5, state?.movies?.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `toggleFavorite calls repository`() = runTest {
        val movie = fakeMovie(1)
        viewModel.toggleFavorite(movie)
        val favs = repo.getFavorites()
        favs.test {
            val list = awaitItem()
            assertTrue(list.any { it.id == 1 })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `loadNextPage on error emits PaginationError event and sets errorOnPage`() = runTest {
        viewModel.uiState.test {
            awaitItem() // Loading (stateIn initial value)
            awaitItem() // Success (initial load)

            repo.shouldThrow = true
            viewModel.loadNextPage()

            skipItems(1) // Success(isLoadingMore=true)
            val errorState = awaitItem() as HomeUiState.Success
            assertNotNull(errorState.errorOnPage)
            assertFalse(errorState.isLoadingMore)

            cancelAndIgnoreRemainingEvents()
        }

        viewModel.events.test {
            val event = awaitItem()
            assertTrue(event is HomeEvent.PaginationError)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `loadNextPage retry after error re-attempts the failed page`() = runTest {
        viewModel.uiState.test {
            awaitItem() // Loading
            awaitItem() // Success([1,2,3])

            // Fail page 2
            repo.shouldThrow = true
            viewModel.loadNextPage()
            skipItems(1) // isLoadingMore=true
            awaitItem()  // errorOnPage=2

            // Retry succeeds
            repo.shouldThrow = false
            repo.popularMovies = listOf(fakeMovie(4), fakeMovie(5))
            viewModel.loadNextPage()
            skipItems(1) // isLoadingMore=true
            val retried = awaitItem() as HomeUiState.Success

            assertEquals(5, retried.movies.size)
            assertNull(retried.errorOnPage)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `search query that throws emits SearchError and uiState shows empty list`() = runTest {
        repo.shouldThrowOnSearch = true

        viewModel.uiState.test {
            awaitItem() // Loading
            awaitItem() // Success with popular movies

            viewModel.setSearchQuery("batman")

            // Immediate: isQueryActive=true but movies still populated (before debounce fires)
            awaitItem() as HomeUiState.Success

            // After debounce: searchMovies throws, .catch emits empty list
            val emptyState = awaitItem() as HomeUiState.Success
            assertEquals(0, emptyState.movies.size)
            assertTrue(emptyState.isQueryActive)

            cancelAndIgnoreRemainingEvents()
        }

        viewModel.events.test {
            val event = awaitItem()
            assertTrue(event is HomeEvent.SearchError)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `isQueryActive toggles based on search query`() = runTest {
        viewModel.uiState.test {
            awaitItem() // Loading (initial)
            val noQuery = awaitItem() as HomeUiState.Success
            assertFalse(noQuery.isQueryActive)

            viewModel.setSearchQuery("foo")
            val withQuery = awaitItem() as HomeUiState.Success
            assertTrue(withQuery.isQueryActive)

            viewModel.setSearchQuery("")
            val cleared = awaitItem() as HomeUiState.Success
            assertFalse(cleared.isQueryActive)

            cancelAndIgnoreRemainingEvents()
        }
    }
}
