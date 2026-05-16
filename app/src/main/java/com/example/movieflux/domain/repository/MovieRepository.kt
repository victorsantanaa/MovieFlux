package com.example.movieflux.domain.repository

import com.example.movieflux.domain.model.MovieModel
import kotlinx.coroutines.flow.Flow

interface MovieRepository {

    fun getPopularMovies(page: Int): Flow<List<MovieModel>>

    fun searchMovies(query: String): Flow<List<MovieModel>>

    fun getFavorites(): Flow<List<MovieModel>>

    fun getMovieDetail(id: Int): Flow<MovieModel>

    suspend fun toggleFavorite(movie: MovieModel)

    suspend fun getGenres(): Map<Int, String>
}