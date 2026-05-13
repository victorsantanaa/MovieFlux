package com.example.movieflux.data.repository

import com.example.movieflux.data.local.MovieDao
import com.example.movieflux.data.mapper.toDomain
import com.example.movieflux.data.mapper.toEntity
import com.example.movieflux.data.remote.RemoteDataSource
import com.example.movieflux.domain.model.MovieModel
import com.example.movieflux.domain.repository.MovieRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

class MovieRepositoryImpl(
    private val api: RemoteDataSource,
    private val dao: MovieDao
) : MovieRepository {

    override fun getPopularMovies(page: Int): Flow<List<MovieModel>> =
        flow {
            val remote = api.getPopular(page)

            val favorites = dao.getFavorites().first()

            emit(
                remote.results.map { dto ->
                    dto.toDomain(
                        isFavorite = favorites.any { it.id == dto.id }
                    )
                }
            )
        }

    override fun getFavorites(): Flow<List<MovieModel>> =
        dao.getFavorites().map { list ->
            list.map { it.toDomain() }
        }

    override suspend fun toggleFavorite(movie: MovieModel) {
        if (movie.isFavorite) {
            dao.delete(movie.toEntity())
        } else {
            dao.insert(movie.toEntity())
        }
    }

    override suspend fun getGenres(): Map<Int, String> {
        return api.genres().genres.associate { it.id to it.name }
    }

    override fun searchMovies(query: String): Flow<List<MovieModel>> =
        flow {
            val result = api.search(query)
            emit(result.results.map { it.toDomain(false) })
        }
}