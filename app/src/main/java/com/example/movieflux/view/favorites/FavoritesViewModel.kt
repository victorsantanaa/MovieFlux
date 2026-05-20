package com.example.movieflux.view.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.movieflux.analytics.AnalyticsTracker
import com.example.movieflux.data.preferences.UiPreferences
import com.example.movieflux.domain.model.MovieModel
import com.example.movieflux.domain.usecase.GetFavoritesUseCase
import com.example.movieflux.domain.usecase.ToggleFavoriteUseCase
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
    private val getFavorites: GetFavoritesUseCase,
    private val toggleFavoriteUseCase: ToggleFavoriteUseCase,
    private val tracker: AnalyticsTracker,
    private val uiPreferences: UiPreferences
) : ViewModel() {

    init { tracker.trackScreen("favorites") }

    private val _viewMode = MutableStateFlow(uiPreferences.getFavoritesViewMode())
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    val uiState: StateFlow<FavoritesUiState> = combine(
        getFavorites(),
        _viewMode,
        _searchQuery
    ) { movies, viewMode, query ->
        val filtered = if (query.isBlank()) {
            movies
        } else {
            movies.filter { it.title.contains(query, ignoreCase = true) }
        }
        FavoritesUiState.Success(movies = filtered, viewMode = viewMode)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FavoritesUiState.Loading)

    fun toggleFavorite(movie: MovieModel) {
        tracker.trackEvent("toggle_favorite", mapOf("movie_id" to movie.id, "is_favorite" to !movie.isFavorite))
        viewModelScope.launch { toggleFavoriteUseCase(movie) }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setViewMode(mode: ViewMode) {
        tracker.trackEvent("view_mode_changed", mapOf("screen" to "favorites", "mode" to mode.name))
        uiPreferences.setFavoritesViewMode(mode)
        _viewMode.value = mode
    }
}
