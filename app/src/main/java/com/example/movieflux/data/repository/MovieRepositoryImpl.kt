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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject

class MovieRepositoryImpl @Inject constructor(
    private val api: RemoteDataSource,
    private val dao: MovieDao
) : MovieRepository {

    /** Time source, overridable in tests. */
    internal var clock: () -> Long = { System.currentTimeMillis() }

    private val genresMutex = Mutex()

    @Volatile private var cachedGenres: Map<Int, String>? = null
    @Volatile private var genresCachedAt: Long = 0L

    private companion object {
        /** Max rows kept in `movie_cache`; older rows are evicted so the cache can't grow forever. */
        const val MAX_CACHE_ROWS = 500
        /** Genre map is refreshed from the network once it's older than this. */
        const val GENRES_TTL_MS = 24L * 60 * 60 * 1000 // 24h
    }

    override fun getPopularMovies(page: Int): Flow<List<MovieModel>> = flow {
        val favoriteIds = dao.getFavoriteIds().toSet()

        // 1. Emit cache immediately so the UI has something to show
        val cached = dao.getCachedPage(page)
        if (cached.isNotEmpty()) {
            emit(cached.map { it.toDomain(isFavorite = it.id in favoriteIds) })
        }

        // 2. Fetch from network
        try {
            val genres = getGenres()
            val remote = api.getPopular(page)
            val now = clock()
            val entities = remote.results.orEmpty().mapIndexed { index, dto ->
                dto.toCacheEntity(
                    page = page,
                    rank = index,
                    genreNames = dto.genre_ids.orEmpty().mapNotNull { genres[it] },
                    updatedAt = now
                )
            }

            // 3. Persist to DB (source of truth), then cap the cache so it can't grow unbounded.
            dao.upsertCache(entities)
            dao.evictCacheBeyond(MAX_CACHE_ROWS)

            // 4. Emit only when the page content actually changed
            val remoteIds = entities.map { it.id }
            val cachedIds = cached.map { it.id }
            if (remoteIds != cachedIds) {
                emit(entities.map { it.toDomain(isFavorite = it.id in favoriteIds) })
            }
        } catch (e: IOException) {
            if (cached.isEmpty()) throw e
        } catch (e: HttpException) {
            if (cached.isEmpty()) throw e
        }
    }

    override fun getFavorites(): Flow<List<MovieModel>> =
        dao.getFavorites().map { list ->
            list.map { entity ->
                val domain = entity.toDomain()
                // Genre names are persisted on the favorite row (#6), so they're available even on a
                // cold start straight into Favorites. Fall back to the in-memory genre map only for
                // legacy rows saved before names were persisted, and only if it's already loaded.
                if (domain.genreNames.isEmpty() && domain.genreIds.isNotEmpty()) {
                    val genres = cachedGenres ?: emptyMap()
                    domain.copy(genreNames = domain.genreIds.mapNotNull { genres[it] })
                } else {
                    domain
                }
            }
        }

    override suspend fun toggleFavorite(movie: MovieModel) {
        if (movie.isFavorite) {
            dao.delete(movie.toEntity())
        } else {
            dao.insert(movie.toEntity())
        }
    }

    override suspend fun getGenres(): Map<Int, String> {
        cachedGenres?.let { if (!isGenresExpired()) return it }
        return genresMutex.withLock {
            // Re-check inside the lock; another caller may have refreshed while we waited.
            cachedGenres?.let { if (!isGenresExpired()) return it }
            api.genres().genres.orEmpty()
                .mapNotNull { genre -> genre.name?.let { genre.id to it } }
                .toMap()
                .also {
                    cachedGenres = it
                    genresCachedAt = clock()
                }
        }
    }

    private fun isGenresExpired(): Boolean = clock() - genresCachedAt >= GENRES_TTL_MS

    override fun searchMovies(query: String): Flow<List<MovieModel>> = flow {
        // Search results are transient — no cache
        val favoriteIds = dao.getFavoriteIds().toSet()
        val genres = getGenres()
        val result = api.search(query)
        emit(
            result.results.orEmpty().map { dto ->
                dto.toDomain(isFavorite = dto.id in favoriteIds)
                    .copy(genreNames = dto.genre_ids.orEmpty().mapNotNull { genres[it] })
            }
        )
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
            dao.upsertCachedMovie(dto.toCacheEntity(updatedAt = clock()))
            dao.evictCacheBeyond(MAX_CACHE_ROWS)
            emit(dto.toDomain(isFavorite = isFavorite))
        } catch (e: IOException) {
            // Only propagate if we had nothing to show from cache
            if (favorite == null && dao.getCachedById(id) == null) throw e
        } catch (e: HttpException) {
            if (favorite == null && dao.getCachedById(id) == null) throw e
        }
    }
}
