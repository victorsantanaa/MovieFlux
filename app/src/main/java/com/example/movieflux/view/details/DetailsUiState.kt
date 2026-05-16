package com.example.movieflux.view.details

import com.example.movieflux.domain.model.MovieModel

sealed class DetailsUiState {
    object Loading : DetailsUiState()
    data class Success(val movie: MovieModel, val genres: List<String>) : DetailsUiState()
    data class Error(val message: String) : DetailsUiState()
}
