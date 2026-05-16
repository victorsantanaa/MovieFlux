package com.example.movieflux.data.repository

import com.example.movieflux.data.local.MovieDao
import com.example.movieflux.data.mapper.toCacheEntity
import com.example.movieflux.data.mapper.toDomain
import com.example.movieflux.data.mapper.toEntity
import com.example.movieflux.data.remote.RemoteDataSource
import com.example.movieflux.domain.model.MovieModel
import com.example.movieflux.domain.repository.MovieRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class MovieRepositoryImpl @Inject constructor(
    private val api: RemoteDataSource,
    private val dao: MovieDao
) : MovieRepository {

    override fun getPopularMovies(page: Int): Flow<List<MovieModel>> = flow {
        val favoriteIds = dao.getFavoriteIds().toSet()

        // 1. Emit cache immediately so the UI has something to show
        val cached = dao.getCachedPage(page)
        if (cached.isNotEmpty()) {
            emit(cached.map { it.toDomain(isFavorite = it.id in favoriteIds) })
        }

        // 2. Fetch from network
        try {
            val remote = api.getPopular(page)
            val entities = remote.results.map { it.toCacheEntity(page) }

            // 3. Persist to DB (source of truth)
            dao.upsertCache(entities)

            // 4. Emit fresh data only if it differs from cache
            val remoteIds = entities.map { it.id }
            val cachedIds = cached.map { it.id }
            if (remoteIds != cachedIds) {
                emit(entities.map { it.toDomain(isFavorite = it.id in favoriteIds) })
            }
        } catch (e: Exception) {
            // No cache was emitted (page not yet loaded) — propagate so the UI shows an error
            if (cached.isEmpty()) throw e
        }
    }

    override fun getFavorites(): Flow<List<MovieModel>> =
        dao.getFavorites().map { list -> list.map { it.toDomain() } }

    override suspend fun toggleFavorite(movie: MovieModel) {
        if (movie.isFavorite) dao.delete(movie.toEntity())
        else dao.insert(movie.toEntity())
    }

    override suspend fun getGenres(): Map<Int, String> =
        api.genres().genres.associate { it.id to it.name }

    override fun searchMovies(query: String): Flow<List<MovieModel>> = flow {
        // Search results are transient — no cache
        val favoriteIds = dao.getFavoriteIds().toSet()
        val result = api.search(query)
        emit(result.results.map { it.toDomain(isFavorite = it.id in favoriteIds) })
    }

    override fun getMovieDetail(id: Int): Flow<MovieModel> = flow {
        val favoriteIds = dao.getFavoriteIds().toSet()
        val isFavorite = id in favoriteIds

        // 1. Favorites table first (already persisted with full data)
        val favorite = dao.getFavoriteById(id)
        if (favorite != null) {
            emit(favorite.toDomain())
        }

        // 2. Popular movies cache (if not already emitted from favorites)
        if (favorite == null) {
            val cached = dao.getCachedById(id)
            if (cached != null) {
                emit(cached.toDomain(isFavorite = isFavorite))
            }
        }

        // 3. Fetch fresh from network, save, emit
        try {
            val dto = api.getMovieDetail(id)
            dao.upsertCachedMovie(dto.toCacheEntity())
            emit(dto.toDomain(isFavorite = isFavorite))
        } catch (e: Exception) {
            // Only propagate if we had nothing to show from cache
            if (favorite == null && dao.getCachedById(id) == null) throw e
        }
    }
}
