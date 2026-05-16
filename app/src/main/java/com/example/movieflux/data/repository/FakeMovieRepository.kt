package com.example.movieflux.data.repository

import com.example.movieflux.domain.model.MovieModel
import com.example.movieflux.domain.repository.MovieRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

// TODO Phase 1/2: remove once Hilt wires MovieRepositoryImpl with real Retrofit + Room
class FakeMovieRepository : MovieRepository {

    private val _favoriteIds = MutableStateFlow(setOf<Int>())

    private val allMovies = listOf(
        MovieModel(1,  "The Dark Knight",                          "Batman faces the Joker's reign of chaos in Gotham.",          "", 9.0, listOf(28, 80, 18), false),
        MovieModel(2,  "Inception",                                "A thief who enters dreams is given a chance at redemption.",   "", 8.8, listOf(28, 878, 12), false),
        MovieModel(3,  "Interstellar",                             "A team of explorers travel through a wormhole in space.",      "", 8.6, listOf(12, 18, 878), false),
        MovieModel(4,  "The Godfather",                            "The aging patriarch of a crime dynasty transfers power.",      "", 9.2, listOf(18, 80), false),
        MovieModel(5,  "Pulp Fiction",                             "The lives of two mob hitmen intertwine.",                     "", 8.9, listOf(80, 18), false),
        MovieModel(6,  "The Matrix",                               "A hacker discovers the world is a simulation.",               "", 8.7, listOf(28, 878), false),
        MovieModel(7,  "Forrest Gump",                             "A man with a low IQ witnesses historic events.",              "", 8.8, listOf(18, 35), false),
        MovieModel(8,  "Fight Club",                               "An insomniac forms an underground fight club.",               "", 8.8, listOf(18, 53), false),
        MovieModel(9,  "Goodfellas",                               "Henry Hill and his mob life over several decades.",           "", 8.7, listOf(18, 80), false),
        MovieModel(10, "Schindler's List",                         "Oskar Schindler saves Jewish lives in occupied Poland.",      "", 9.0, listOf(18, 36), false),
        MovieModel(11, "Avengers: Endgame",                        "The Avengers assemble to undo Thanos's actions.",            "", 8.4, listOf(28, 12), false),
        MovieModel(12, "Dune",                                     "A noble family battles for a desert planet's resources.",     "", 8.0, listOf(12, 878, 28), false),
        MovieModel(13, "Spider-Man: No Way Home",                  "Spider-Man faces threats from the multiverse.",               "", 8.3, listOf(28, 12), false),
        MovieModel(14, "The Batman",                               "Bruce Wayne unmasks corruption in Gotham City.",              "", 7.9, listOf(28, 80, 9648), false),
        MovieModel(15, "Top Gun: Maverick",                        "Maverick trains elite pilots while facing his past.",         "", 8.3, listOf(28, 12), false),
        MovieModel(16, "Everything Everywhere All at Once",        "A laundromat owner must save multiple realities.",            "", 7.9, listOf(28, 12, 35), false),
        MovieModel(17, "Doctor Strange in the Multiverse of Madness", "Doctor Strange explores dangerous alternate realities.",   "", 6.9, listOf(28, 12), false),
        MovieModel(18, "No Time to Die",                           "James Bond faces a dangerous villain on a new mission.",      "", 7.3, listOf(28, 12, 53), false),
        MovieModel(19, "The Northman",                             "A Viking prince seeks revenge for his father's murder.",      "", 7.1, listOf(28, 18, 36), false),
        MovieModel(20, "Nope",                                     "California ranch residents witness a terrifying discovery.",  "", 7.0, listOf(27, 878), false)
    )

    override fun getPopularMovies(page: Int): Flow<List<MovieModel>> = flow {
        val pageSize = 10
        val from = (page - 1) * pageSize
        val to = minOf(from + pageSize, allMovies.size)
        emit(if (from < allMovies.size) allMovies.subList(from, to) else emptyList())
    }

    override fun searchMovies(query: String): Flow<List<MovieModel>> = flow {
        emit(allMovies.filter { it.title.contains(query, ignoreCase = true) })
    }

    override fun getFavorites(): Flow<List<MovieModel>> = _favoriteIds.map { ids ->
        allMovies.filter { it.id in ids }.map { it.copy(isFavorite = true) }
    }

    override suspend fun toggleFavorite(movie: MovieModel) {
        _favoriteIds.update { ids ->
            if (movie.id in ids) ids - movie.id else ids + movie.id
        }
    }

    override suspend fun getGenres(): Map<Int, String> = mapOf(
        28 to "Action", 12 to "Adventure", 16 to "Animation", 35 to "Comedy",
        80 to "Crime", 18 to "Drama", 27 to "Horror", 878 to "Science Fiction",
        53 to "Thriller", 36 to "History", 9648 to "Mystery"
    )
}
