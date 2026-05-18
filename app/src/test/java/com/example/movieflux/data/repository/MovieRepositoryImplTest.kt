package com.example.movieflux.data.repository

import app.cash.turbine.test
import com.example.movieflux.data.local.CachedMovieEntity
import com.example.movieflux.data.local.MovieDao
import com.example.movieflux.data.local.MovieEntity
import com.example.movieflux.data.remote.GenreDto
import com.example.movieflux.data.remote.GenreResponse
import com.example.movieflux.data.remote.MovieDetailDto
import com.example.movieflux.data.remote.MovieDto
import com.example.movieflux.data.remote.MovieResponse
import com.example.movieflux.data.remote.RemoteDataSource
import com.example.movieflux.fakeMovie
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class MovieRepositoryImplTest {

    private val api = mockk<RemoteDataSource>()
    private val dao = mockk<MovieDao>(relaxed = true)
    private val repo = MovieRepositoryImpl(api, dao)

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun dto(id: Int) = MovieDto(
        id = id, title = "Movie $id", overview = "Overview $id",
        poster_path = "/poster$id.jpg", vote_average = 7.5, genre_ids = listOf(28, 12)
    )

    private fun detailDto(id: Int) = MovieDetailDto(
        id = id, title = "Movie $id", overview = "Overview $id",
        poster_path = "/poster$id.jpg", vote_average = 7.5,
        genres = listOf(GenreDto(28, "Action"))
    )

    private fun response(vararg ids: Int) = MovieResponse(
        page = 1, results = ids.map { dto(it) }, total_pages = 5, total_results = 100
    )

    private fun cachedEntity(id: Int, page: Int = 1) = CachedMovieEntity(
        id = id, title = "Movie $id", overview = "Overview $id",
        posterUrl = "https://image.tmdb.org/t/p/w500/poster$id.jpg",
        rating = 7.5, genreIds = "28,12", page = page
    )

    private fun movieEntity(id: Int) = MovieEntity(
        id = id, title = "Movie $id",
        posterUrl = "https://image.tmdb.org/t/p/w500/poster$id.jpg",
        overview = "Overview $id", rating = 7.5
    )

    // ── Row 1: network results joined with favorites ──────────────────────────

    @Test
    fun `getPopularMovies emits results with isFavorite joined from favorites DAO`() = runTest {
        coEvery { api.genres() } returns GenreResponse(emptyList())
        coEvery { dao.getFavoriteIds() } returns listOf(1, 3)
        coEvery { dao.getCachedPage(1) } returns emptyList()
        coEvery { api.getPopular(1) } returns response(1, 2, 3)

        repo.getPopularMovies(1).test {
            val items = awaitItem()
            assertEquals(3, items.size)
            assertTrue(items.first { it.id == 1 }.isFavorite)
            assertFalse(items.first { it.id == 2 }.isFavorite)
            assertTrue(items.first { it.id == 3 }.isFavorite)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── Row 2: cache-first — cache emitted immediately, network called once ──

    @Test
    fun `getPopularMovies emits cache before network and calls network exactly once`() = runTest {
        coEvery { api.genres() } returns GenreResponse(emptyList())
        coEvery { dao.getFavoriteIds() } returns emptyList()
        coEvery { dao.getCachedPage(1) } returns listOf(cachedEntity(10), cachedEntity(11))
        coEvery { api.getPopular(1) } returns response(10, 11) // same IDs → no second emit

        repo.getPopularMovies(1).test {
            val first = awaitItem()
            assertEquals(listOf(10, 11), first.map { it.id })
            awaitComplete()
        }

        coVerify(exactly = 1) { api.getPopular(1) }
    }

    // ── Row 3: network failure, no cache → exception propagates ──────────────

    @Test
    fun `getPopularMovies throws when network fails and cache is empty`() = runTest {
        coEvery { dao.getFavoriteIds() } returns emptyList()
        coEvery { dao.getCachedPage(1) } returns emptyList()
        coEvery { api.getPopular(1) } throws IOException("no network")

        repo.getPopularMovies(1).test {
            awaitError()
        }
    }

    // ── Row 4: network failure, cache present → cache served, no exception ───

    @Test
    fun `getPopularMovies serves cache and completes silently when network fails`() = runTest {
        coEvery { dao.getFavoriteIds() } returns emptyList()
        coEvery { dao.getCachedPage(1) } returns listOf(cachedEntity(5))
        coEvery { api.getPopular(1) } throws IOException("no network")

        repo.getPopularMovies(1).test {
            val item = awaitItem()
            assertEquals(listOf(5), item.map { it.id })
            awaitComplete()
        }
    }

    // ── Row 5: searchMovies happy path with favorite flags ───────────────────

    @Test
    fun `searchMovies emits results with isFavorite joined from DAO`() = runTest {
        coEvery { api.genres() } returns GenreResponse(emptyList())
        coEvery { dao.getFavoriteIds() } returns listOf(2)
        coEvery { api.search("batman") } returns response(1, 2, 3)

        repo.searchMovies("batman").test {
            val items = awaitItem()
            assertFalse(items.first { it.id == 1 }.isFavorite)
            assertTrue(items.first { it.id == 2 }.isFavorite)
            assertFalse(items.first { it.id == 3 }.isFavorite)
            awaitComplete()
        }
    }

    // ── Row 6: searchMovies network exception propagates ─────────────────────

    @Test
    fun `searchMovies propagates network exception`() = runTest {
        coEvery { dao.getFavoriteIds() } returns emptyList()
        coEvery { api.search(any()) } throws IOException("timeout")

        repo.searchMovies("anything").test {
            awaitError()
        }
    }

    // ── Row 7: toggleFavorite(isFavorite=false) → dao.insert ────────────────

    @Test
    fun `toggleFavorite with isFavorite=false calls dao insert and not delete`() = runTest {
        repo.toggleFavorite(fakeMovie(1, isFavorite = false))

        coVerify(exactly = 1) { dao.insert(any()) }
        coVerify(exactly = 0) { dao.delete(any()) }
    }

    // ── Row 8: toggleFavorite(isFavorite=true) → dao.delete ─────────────────

    @Test
    fun `toggleFavorite with isFavorite=true calls dao delete and not insert`() = runTest {
        repo.toggleFavorite(fakeMovie(1, isFavorite = true))

        coVerify(exactly = 1) { dao.delete(any()) }
        coVerify(exactly = 0) { dao.insert(any()) }
    }

    // ── Row 9: getMovieDetail happy path ─────────────────────────────────────

    @Test
    fun `getMovieDetail emits domain model with isFavorite from favorites`() = runTest {
        coEvery { dao.getFavoriteIds() } returns listOf(42)
        coEvery { dao.getFavoriteById(42) } returns null
        coEvery { dao.getCachedById(42) } returns null
        coEvery { api.getMovieDetail(42) } returns detailDto(42)

        repo.getMovieDetail(42).test {
            val movie = awaitItem()
            assertEquals(42, movie.id)
            assertTrue(movie.isFavorite)
            awaitComplete()
        }
    }

    // ── Row 10: getMovieDetail — DAO queried before network ──────────────────

    @Test
    fun `getMovieDetail queries DAO before calling network`() = runTest {
        coEvery { dao.getFavoriteIds() } returns emptyList()
        coEvery { dao.getFavoriteById(99) } returns null
        coEvery { dao.getCachedById(99) } returns null
        coEvery { api.getMovieDetail(99) } returns detailDto(99)

        repo.getMovieDetail(99).test {
            awaitItem()
            awaitComplete()
        }

        coVerifyOrder {
            dao.getFavoriteById(99)
            api.getMovieDetail(99)
        }
    }

    // ── Row 11: getGenres caching ─────────────────────────────────────────────

    @Test
    fun `getGenres calls API only once across multiple invocations`() = runTest {
        val genreResponse = GenreResponse(
            genres = listOf(GenreDto(28, "Action"), GenreDto(12, "Adventure"))
        )
        coEvery { api.genres() } returns genreResponse

        val first = repo.getGenres()
        val second = repo.getGenres()

        assertEquals(mapOf(28 to "Action", 12 to "Adventure"), first)
        assertEquals(first, second)
        coVerify(exactly = 1) { api.genres() }
    }

    @Test
    fun `getPopularMovies populates genreNames from cached genre map`() = runTest {
        val genreResponse = GenreResponse(
            genres = listOf(GenreDto(28, "Action"), GenreDto(12, "Adventure"))
        )
        coEvery { api.genres() } returns genreResponse
        coEvery { dao.getFavoriteIds() } returns emptyList()
        coEvery { dao.getCachedPage(1) } returns emptyList()
        coEvery { api.getPopular(1) } returns response(1, 2)

        repo.getPopularMovies(1).test {
            val items = awaitItem()
            assertEquals(listOf("Action", "Adventure"), items.first().genreNames)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── Row 12: searchMovies populates both isFavorite and genreNames ─────────

    @Test
    fun `searchMovies emits results with isFavorite and genreNames populated`() = runTest {
        val genreResponse = GenreResponse(
            genres = listOf(GenreDto(28, "Action"), GenreDto(12, "Adventure"))
        )
        coEvery { api.genres() } returns genreResponse
        coEvery { dao.getFavoriteIds() } returns listOf(1)
        coEvery { api.search("hero") } returns response(1, 2)

        repo.searchMovies("hero").test {
            val items = awaitItem()
            assertTrue(items.first { it.id == 1 }.isFavorite)
            assertFalse(items.first { it.id == 2 }.isFavorite)
            assertEquals(listOf("Action", "Adventure"), items.first().genreNames)
            awaitComplete()
        }
    }

    // ── Row 13: getMovieDetail fallback chain favorites → cache → network ─────

    @Test
    fun `getMovieDetail emits from favorites first then from network`() = runTest {
        coEvery { dao.getFavoriteIds() } returns listOf(10)
        coEvery { dao.getFavoriteById(10) } returns movieEntity(10)
        coEvery { api.getMovieDetail(10) } returns detailDto(10)

        repo.getMovieDetail(10).test {
            val fromFavorites = awaitItem()
            assertEquals(10, fromFavorites.id)

            val fromNetwork = awaitItem()
            assertEquals(10, fromNetwork.id)

            awaitComplete()
        }
    }
}
