package com.example.movieflux.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MovieDao {

    // ── Favoritos ────────────────────────────────────────────────────────────

    @Query("SELECT * FROM favorites")
    fun getFavorites(): Flow<List<MovieEntity>>

    @Query("SELECT id FROM favorites")
    suspend fun getFavoriteIds(): List<Int>

    @Query("SELECT * FROM favorites WHERE id = :id LIMIT 1")
    suspend fun getFavoriteById(id: Int): MovieEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(movie: MovieEntity)

    @Delete
    suspend fun delete(movie: MovieEntity)

    // ── Cache de filmes ──────────────────────────────────────────────────────

    @Query("SELECT * FROM movie_cache WHERE page = :page ORDER BY rank")
    suspend fun getCachedPage(page: Int): List<CachedMovieEntity>

    @Query("SELECT * FROM movie_cache WHERE id = :id LIMIT 1")
    suspend fun getCachedById(id: Int): CachedMovieEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCache(movies: List<CachedMovieEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCachedMovie(movie: CachedMovieEntity)

    /** Mantém só as [keep] linhas mais recentemente atualizadas; o restante é apagado. */
    @Query(
        "DELETE FROM movie_cache WHERE id NOT IN " +
            "(SELECT id FROM movie_cache ORDER BY updatedAt DESC, page ASC, rank ASC LIMIT :keep)"
    )
    suspend fun evictCacheBeyond(keep: Int)
}
