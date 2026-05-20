package com.example.movieflux.view.details

import androidx.annotation.StringRes
import com.example.movieflux.domain.model.MovieModel

sealed class DetailsUiState {
    object Loading : DetailsUiState()
    data class Success(val movie: MovieModel, val genres: List<String>) : DetailsUiState()
    data class Error(@StringRes val messageRes: Int) : DetailsUiState()
}
