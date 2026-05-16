package com.example.movieflux.view.home

import com.example.movieflux.domain.model.MovieModel

sealed class HomeUiState {
    object Loading : HomeUiState()
    data class Success(val movies: List<MovieModel>, val isLoadingMore: Boolean) : HomeUiState()
    data class Error(val message: String) : HomeUiState()
}
