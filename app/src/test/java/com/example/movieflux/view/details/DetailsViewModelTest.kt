package com.example.movieflux.view.details

import app.cash.turbine.test
import androidx.lifecycle.SavedStateHandle
import com.example.movieflux.analytics.AnalyticsTracker
import com.example.movieflux.domain.repository.MovieRepository
import com.example.movieflux.fakeMovie
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DetailsViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val repo: MovieRepository = mockk(relaxed = true)
    private val tracker: AnalyticsTracker = mockk(relaxed = true)

    private fun buildViewModel(movieId: String = "42"): DetailsViewModel =
        DetailsViewModel(
            savedStateHandle = SavedStateHandle(mapOf("movieId" to movieId)),
            repository = repo,
            tracker = tracker
        )

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    // ── loadDetail → Success ──────────────────────────────────────────────────

    @Test
    fun `loadDetail emits Success with movie and genre names`() = runTest {
        val movie = fakeMovie(42)
        coEvery { repo.getMovieDetail(42) } returns flowOf(movie)
        coEvery { repo.getGenres() } returns mapOf(28 to "Action", 12 to "Adventure")

        val vm = buildViewModel("42")

        vm.uiState.test {
            val state = awaitItem()
            assertTrue(state is DetailsUiState.Success)
            val success = state as DetailsUiState.Success
            assertEquals(42, success.movie.id)
            // fakeMovie has genreIds=[28]; genre 12 ("Adventure") is filtered out
            assertEquals(listOf("Action"), success.genres)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── loadDetail → Error ────────────────────────────────────────────────────

    @Test
    fun `loadDetail emits Error when repository throws`() = runTest {
        coEvery { repo.getMovieDetail(any()) } throws RuntimeException("network dead")

        val vm = buildViewModel("42")

        vm.uiState.test {
            val state = awaitItem()
            assertTrue(state is DetailsUiState.Error)
            assertEquals("network dead", (state as DetailsUiState.Error).message)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── init tracks details screen ────────────────────────────────────────────

    @Test
    fun `init tracks details screen`() = runTest {
        coEvery { repo.getMovieDetail(any()) } returns flowOf(fakeMovie(1))
        coEvery { repo.getGenres() } returns emptyMap()
        buildViewModel("1")
        verify { tracker.trackScreen("details") }
    }

    // ── toggleFavorite — optimistic flip ─────────────────────────────────────

    @Test
    fun `toggleFavorite optimistically flips isFavorite in uiState`() = runTest {
        val movie = fakeMovie(42, isFavorite = false)
        coEvery { repo.getMovieDetail(42) } returns flowOf(movie)
        coEvery { repo.getGenres() } returns emptyMap()

        val vm = buildViewModel("42")

        vm.uiState.test {
            awaitItem() as DetailsUiState.Success // initial Success (isFavorite=false)

            vm.toggleFavorite()

            val updated = awaitItem() as DetailsUiState.Success
            assertTrue(updated.movie.isFavorite)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `toggleFavorite calls repository toggleFavorite`() = runTest {
        val movie = fakeMovie(42, isFavorite = false)
        coEvery { repo.getMovieDetail(42) } returns flowOf(movie)
        coEvery { repo.getGenres() } returns emptyMap()

        val vm = buildViewModel("42")
        vm.uiState.test {
            awaitItem() // wait for Success
            vm.toggleFavorite()
            cancelAndIgnoreRemainingEvents()
        }

        coVerify { repo.toggleFavorite(any()) }
    }

    // ── toggleFavorite does nothing when state is not Success ─────────────────

    @Test
    fun `toggleFavorite is no-op when uiState is Loading`() = runTest {
        // Repository never emits — state stays Loading
        coEvery { repo.getMovieDetail(any()) } returns kotlinx.coroutines.flow.flow { /* hang */ }
        coEvery { repo.getGenres() } returns emptyMap()

        val vm = buildViewModel("42")

        vm.uiState.test {
            assertEquals(DetailsUiState.Loading, awaitItem())
            vm.toggleFavorite() // should be no-op

            coVerify(exactly = 0) { repo.toggleFavorite(any()) }
            cancelAndIgnoreRemainingEvents()
        }
    }
}
