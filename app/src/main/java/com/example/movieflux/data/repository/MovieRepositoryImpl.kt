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

    /** Fonte de tempo, sobrescrevível nos testes. */
    internal var clock: () -> Long = { System.currentTimeMillis() }

    private val genresMutex = Mutex()

    @Volatile private var cachedGenres: Map<Int, String>? = null
    @Volatile private var genresCachedAt: Long = 0L

    private companion object {
        /** Máximo de linhas em `movie_cache`; as mais antigas são removidas para limitar o cache. */
        const val MAX_CACHE_ROWS = 500
        /** O mapa de gêneros é renovado pela rede quando estiver mais antigo do que este valor. */
        const val GENRES_TTL_MS = 24L * 60 * 60 * 1000
    }

    override fun getPopularMovies(page: Int): Flow<List<MovieModel>> = flow {
        val favoriteIds = dao.getFavoriteIds().toSet()

        val cached = dao.getCachedPage(page)
        if (cached.isNotEmpty()) {
            emit(cached.map { it.toDomain(isFavorite = it.id in favoriteIds) })
        }

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

            // Persiste no DB (fonte da verdade) e limita o cache para que ele não cresça sem limite.
            dao.upsertCache(entities)
            dao.evictCacheBeyond(MAX_CACHE_ROWS)

            // Só emite quando o conteúdo da página realmente mudou.
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
                // Os nomes dos gêneros são persistidos na linha de favorito, então ficam disponíveis
                // mesmo num cold start direto em Favoritos. Cai para o mapa de gêneros em memória
                // apenas para linhas legadas salvas antes da persistência dos nomes, e só se já estiver carregado.
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
            // Reverifica dentro do lock; outro chamador pode ter renovado enquanto esperávamos.
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
        // Resultados de busca são transitórios — sem cache.
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

        val favorite = dao.getFavoriteById(id)
        if (favorite != null) {
            emit(favorite.toDomain())
        }

        if (favorite == null) {
            val cached = dao.getCachedById(id)
            if (cached != null) {
                emit(cached.toDomain(isFavorite = isFavorite))
            }
        }

        try {
            val dto = api.getMovieDetail(id)
            dao.upsertCachedMovie(dto.toCacheEntity(updatedAt = clock()))
            dao.evictCacheBeyond(MAX_CACHE_ROWS)
            emit(dto.toDomain(isFavorite = isFavorite))
        } catch (e: IOException) {
            // Só propaga se não tínhamos nada para mostrar a partir do cache.
            if (favorite == null && dao.getCachedById(id) == null) throw e
        } catch (e: HttpException) {
            if (favorite == null && dao.getCachedById(id) == null) throw e
        }
    }
}
