package com.example.movieflux.domain.usecase

import com.example.movieflux.domain.repository.MovieRepository

class GetPopularMoviesUseCase(
    private val repository: MovieRepository
) {
    operator fun invoke(page: Int) =
        repository.getPopularMovies(page)
}