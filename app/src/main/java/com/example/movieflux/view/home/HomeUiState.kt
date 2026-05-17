package com.example.movieflux.view.home

import com.example.movieflux.domain.model.MovieModel
import com.example.movieflux.view.components.ViewMode

sealed class HomeUiState {
    object Loading : HomeUiState()
    data class Success(
        val movies: List<MovieModel>,
        val isLoadingMore: Boolean,
        val viewMode: ViewMode = ViewMode.GRID,
        val isQueryActive: Boolean = false,
        val errorOnPage: Int? = null
    ) : HomeUiState()
    data class Error(val message: String) : HomeUiState()
}
