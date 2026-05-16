package com.example.movieflux.view.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.movieflux.analytics.AnalyticsTracker
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
    private val repository: MovieRepository,
    private val tracker: AnalyticsTracker
) : ViewModel() {

    init { tracker.trackScreen("favorites") }

    private val _viewMode = MutableStateFlow(ViewMode.GRID)
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    val uiState: StateFlow<FavoritesUiState> = combine(
        repository.getFavorites(),
        _viewMode,
        _searchQuery
    ) { movies, viewMode, query ->
        val filtered = if (query.isBlank()) movies
                       else movies.filter { it.title.contains(query, ignoreCase = true) }
        FavoritesUiState.Success(movies = filtered, viewMode = viewMode)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FavoritesUiState.Loading)

    fun toggleFavorite(movie: MovieModel) {
        tracker.trackEvent("toggle_favorite", mapOf("movie_id" to movie.id, "is_favorite" to !movie.isFavorite))
        viewModelScope.launch { repository.toggleFavorite(movie) }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setViewMode(mode: ViewMode) {
        tracker.trackEvent("view_mode_changed", mapOf("screen" to "favorites", "mode" to mode.name))
        _viewMode.value = mode
    }
}
