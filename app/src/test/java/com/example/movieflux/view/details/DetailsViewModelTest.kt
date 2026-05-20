package com.example.movieflux.view.details

import android.content.Context
import android.content.Intent
import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.example.movieflux.R
import com.example.movieflux.analytics.AnalyticsTracker
import com.example.movieflux.domain.repository.MovieRepository
import com.example.movieflux.domain.usecase.GetMovieDetailUseCase
import com.example.movieflux.domain.usecase.ToggleFavoriteUseCase
import com.example.movieflux.fakeMovie
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkConstructor
import io.mockk.unmockkStatic
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
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class DetailsViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val repo: MovieRepository = mockk(relaxed = true)
    private val tracker: AnalyticsTracker = mockk(relaxed = true)

    private fun buildViewModel(movieId: Int = 42): DetailsViewModel =
        DetailsViewModel(
            savedStateHandle = SavedStateHandle(mapOf("movieId" to movieId)),
            getMovieDetail = GetMovieDetailUseCase(repo),
            toggleFavoriteUseCase = ToggleFavoriteUseCase(repo),
            tracker = tracker
        )

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    // ── loadDetail → Success ──────────────────────────────────────────────────

    @Test
    fun `loadDetail emits Success with movie and genre names from MovieModel`() = runTest {
        val movie = fakeMovie(42, genreNames = listOf("Action"))
        coEvery { repo.getMovieDetail(42) } returns flowOf(movie)

        val vm = buildViewModel(42)

        vm.uiState.test {
            val state = awaitItem()
            assertTrue(state is DetailsUiState.Success)
            val success = state as DetailsUiState.Success
            assertEquals(42, success.movie.id)
            assertEquals(listOf("Action"), success.genres)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── loadDetail → Error ────────────────────────────────────────────────────

    @Test
    fun `loadDetail emits Error when repository throws`() = runTest {
        coEvery { repo.getMovieDetail(any()) } throws IOException("network dead")

        val vm = buildViewModel(42)

        vm.uiState.test {
            val state = awaitItem()
            assertTrue(state is DetailsUiState.Error)
            assertEquals(R.string.error_network, (state as DetailsUiState.Error).messageRes)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── init tracks details screen ────────────────────────────────────────────

    @Test
    fun `init tracks details screen`() = runTest {
        coEvery { repo.getMovieDetail(any()) } returns flowOf(fakeMovie(1))
        buildViewModel(1)
        verify { tracker.trackScreen("details") }
    }

    // ── toggleFavorite — optimistic flip ─────────────────────────────────────

    @Test
    fun `toggleFavorite optimistically flips isFavorite in uiState`() = runTest {
        val movie = fakeMovie(42, isFavorite = false)
        coEvery { repo.getMovieDetail(42) } returns flowOf(movie)

        val vm = buildViewModel(42)

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

        val vm = buildViewModel(42)
        vm.uiState.test {
            awaitItem() // wait for Success
            vm.toggleFavorite()
            cancelAndIgnoreRemainingEvents()
        }

        coVerify { repo.toggleFavorite(any()) }
    }

    // ── toggleFavorite rollback on failure ────────────────────────────────────

    @Test
    fun `toggleFavorite reverts uiState and emits ShowError event when repository throws`() = runTest {
        val movie = fakeMovie(42, isFavorite = false)
        coEvery { repo.getMovieDetail(42) } returns flowOf(movie)
        coEvery { repo.toggleFavorite(any()) } throws RuntimeException("DB error")

        val vm = buildViewModel(42)

        vm.uiState.test {
            val initial = awaitItem() as DetailsUiState.Success
            assertFalse(initial.movie.isFavorite)

            vm.toggleFavorite()

            val optimistic = awaitItem() as DetailsUiState.Success
            assertTrue(optimistic.movie.isFavorite)

            val reverted = awaitItem() as DetailsUiState.Success
            assertFalse(reverted.movie.isFavorite)

            cancelAndIgnoreRemainingEvents()
        }

        vm.events.test {
            val event = awaitItem()
            assertTrue(event is DetailsEvent.ShowError)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── toggleFavorite does nothing when state is not Success ─────────────────

    @Test
    fun `toggleFavorite is no-op when uiState is Loading`() = runTest {
        coEvery { repo.getMovieDetail(any()) } returns kotlinx.coroutines.flow.flow { /* hang */ }

        val vm = buildViewModel(42)

        vm.uiState.test {
            assertEquals(DetailsUiState.Loading, awaitItem())
            vm.toggleFavorite() // should be no-op

            coVerify(exactly = 0) { repo.toggleFavorite(any()) }
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── share — builds correct chooser intent ─────────────────────────────────

    @Test
    fun `share builds chooser intent containing movie title and TMDB URL`() = runTest {
        val movie = fakeMovie(42)
        coEvery { repo.getMovieDetail(42) } returns flowOf(movie)
        val vm = buildViewModel(42)

        vm.uiState.test {
            awaitItem() // Success
            cancelAndIgnoreRemainingEvents()
        }

        mockkConstructor(Intent::class)
        mockkStatic(Intent::class)
        try {
            val textSlot = slot<String>()
            every { anyConstructed<Intent>().setType(any()) } returns mockk(relaxed = true)
            every {
                anyConstructed<Intent>().putExtra(eq(Intent.EXTRA_TEXT), capture(textSlot))
            } returns mockk(relaxed = true)

            val chooserIntent = mockk<Intent>(relaxed = true)
            every { Intent.createChooser(any(), any()) } returns chooserIntent

            val context = mockk<Context>(relaxed = true)
            vm.share(context)

            verify { context.startActivity(chooserIntent) }
            assertTrue(textSlot.isCaptured)
            assertTrue(textSlot.captured.contains(movie.title))
            assertTrue(textSlot.captured.contains("https://www.themoviedb.org/movie/42"))
        } finally {
            unmockkConstructor(Intent::class)
            unmockkStatic(Intent::class)
        }
    }
}
