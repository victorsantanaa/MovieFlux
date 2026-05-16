package com.example.movieflux.view.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.movieflux.data.repository.FakeMovieRepository
import com.example.movieflux.domain.model.MovieModel
import com.example.movieflux.domain.repository.MovieRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// TODO Phase 1/2: replace with @HiltViewModel + @Inject constructor(repository)
class FavoritesViewModel : ViewModel() {

    private val repository: MovieRepository = FakeMovieRepository

    val uiState: StateFlow<FavoritesUiState> = repository.getFavorites()
        .map { FavoritesUiState.Success(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FavoritesUiState.Loading)

    fun toggleFavorite(movie: MovieModel) {
        viewModelScope.launch { repository.toggleFavorite(movie) }
    }
}
