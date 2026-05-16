package com.example.movieflux.view.favorites

import com.example.movieflux.domain.model.MovieModel
import com.example.movieflux.view.components.ViewMode

sealed class FavoritesUiState {
    object Loading : FavoritesUiState()
    data class Success(
        val movies: List<MovieModel>,
        val viewMode: ViewMode = ViewMode.GRID
    ) : FavoritesUiState()
}
