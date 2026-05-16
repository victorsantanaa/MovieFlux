package com.example.movieflux.domain.usecase

import com.example.movieflux.domain.repository.MovieRepository
import javax.inject.Inject

class GetPopularMoviesUseCase @Inject constructor(
    private val repository: MovieRepository
) {
    operator fun invoke(page: Int) =
        repository.getPopularMovies(page)
}
