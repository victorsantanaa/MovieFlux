package com.example.movieflux.view.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.movieflux.domain.model.MovieModel
import com.example.movieflux.domain.repository.MovieRepository
import com.example.movieflux.view.components.ViewMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FavoritesViewModel @Inject constructor(
    private val repository: MovieRepository
) : ViewModel() {

    private val _viewMode = MutableStateFlow(ViewMode.GRID)

    val uiState: StateFlow<FavoritesUiState> = combine(
        repository.getFavorites(),
        _viewMode
    ) { movies, viewMode ->
        FavoritesUiState.Success(movies = movies, viewMode = viewMode)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FavoritesUiState.Loading)

    fun toggleFavorite(movie: MovieModel) {
        viewModelScope.launch { repository.toggleFavorite(movie) }
    }

    fun setViewMode(mode: ViewMode) {
        _viewMode.value = mode
    }
}
