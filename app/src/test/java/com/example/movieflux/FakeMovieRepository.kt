package com.example.movieflux

import com.example.movieflux.domain.model.MovieModel
import com.example.movieflux.domain.repository.MovieRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.IOException

class FakeMovieRepository : MovieRepository {

    private val favorites = MutableStateFlow<List<MovieModel>>(emptyList())
    var popularMovies: List<MovieModel> = emptyList()
    var searchResults: List<MovieModel> = emptyList()
    var movieDetail: MovieModel? = null
    var shouldThrow: Boolean = false
    var shouldThrowOnSearch: Boolean = false

    override fun getPopularMovies(page: Int): Flow<List<MovieModel>> =
        kotlinx.coroutines.flow.flow {
            if (shouldThrow) throw IOException("Network error")
            emit(popularMovies)
        }

    override fun searchMovies(query: String): Flow<List<MovieModel>> =
        kotlinx.coroutines.flow.flow {
            if (shouldThrowOnSearch) throw IOException("Search error")
            emit(searchResults)
        }

    override fun getFavorites(): Flow<List<MovieModel>> = favorites

    override fun getMovieDetail(id: Int): Flow<MovieModel> =
        kotlinx.coroutines.flow.flow {
            if (shouldThrow) throw IOException("Network error")
            emit(movieDetail ?: popularMovies.firstOrNull { it.id == id } ?: error("not found"))
        }

    override suspend fun toggleFavorite(movie: MovieModel) {
        val current = favorites.value.toMutableList()
        if (movie.isFavorite) {
            current.removeAll { it.id == movie.id }
        } else {
            current.add(movie.copy(isFavorite = true))
        }
        favorites.value = current
    }

    override suspend fun getGenres(): Map<Int, String> = emptyMap()
}

fun fakeMovie(
    id: Int = 1,
    isFavorite: Boolean = false,
    genreNames: List<String> = emptyList()
) = MovieModel(
    id = id,
    title = "Movie $id",
    overview = "Overview $id",
    posterUrl = "https://image.tmdb.org/t/p/w500/poster$id.jpg",
    rating = 7.5,
    genreIds = listOf(28),
    isFavorite = isFavorite,
    genreNames = genreNames
)
