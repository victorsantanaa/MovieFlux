package com.example.movieflux.view.favorites

import com.example.movieflux.domain.model.MovieModel

sealed class FavoritesUiState {
    object Loading : FavoritesUiState()
    data class Success(val movies: List<MovieModel>) : FavoritesUiState()
}
