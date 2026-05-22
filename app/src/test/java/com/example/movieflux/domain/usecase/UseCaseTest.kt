package com.example.movieflux.domain.usecase

import app.cash.turbine.test
import com.example.movieflux.FakeMovieRepository
import com.example.movieflux.fakeMovie
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies every use case is a faithful pass-through to [MovieRepository] — both that it returns
 * the repository's data and that it delegates the call. Guards the #8 fix: ViewModels now depend
 * only on use cases, so the use-case boundary must stay correct.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class UseCaseTest {

    private val repo = FakeMovieRepository()

    @Test
    fun `GetPopularMoviesUseCase emits repository popular movies`() = runTest {
        repo.popularMovies = listOf(fakeMovie(1), fakeMovie(2))
        GetPopularMoviesUseCase(repo)(page = 1).test {
            assertEquals(2, awaitItem().size)
            awaitComplete()
        }
    }

    @Test
    fun `SearchMoviesUseCase emits repository search results`() = runTest {
        repo.searchResults = listOf(fakeMovie(7))
        SearchMoviesUseCase(repo)("batman").test {
            val result = awaitItem()
            assertEquals(1, result.size)
            assertEquals(7, result.first().id)
            awaitComplete()
        }
    }

    @Test
    fun `GetFavoritesUseCase reflects toggled favorites`() = runTest {
        val useCase = GetFavoritesUseCase(repo)
        repo.toggleFavorite(fakeMovie(3))
        useCase().test {
            val favorites = awaitItem()
            assertTrue(favorites.any { it.id == 3 })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `GetMovieDetailUseCase emits the requested movie`() = runTest {
        repo.movieDetail = fakeMovie(42)
        GetMovieDetailUseCase(repo)(42).test {
            assertEquals(42, awaitItem().id)
            awaitComplete()
        }
    }

    @Test
    fun `ToggleFavoriteUseCase adds an unfavorited movie`() = runTest {
        val useCase = ToggleFavoriteUseCase(repo)
        useCase(fakeMovie(5, isFavorite = false))
        repo.getFavorites().test {
            assertTrue(awaitItem().any { it.id == 5 })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `ToggleFavoriteUseCase delegates to repository`() = runTest {
        val mockRepo = mockk<com.example.movieflux.domain.repository.MovieRepository>(relaxed = true)
        val movie = fakeMovie(9)
        ToggleFavoriteUseCase(mockRepo)(movie)
        coVerify(exactly = 1) { mockRepo.toggleFavorite(movie) }
    }
}
