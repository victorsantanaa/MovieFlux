package com.example.movieflux.view.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.movieflux.domain.model.MovieModel
import com.example.movieflux.domain.repository.MovieRepository
import com.example.movieflux.domain.usecase.GetPopularMoviesUseCase
import com.example.movieflux.view.components.ViewMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(kotlinx.coroutines.FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val useCase: GetPopularMoviesUseCase,
    private val repository: MovieRepository
) : ViewModel() {

    private var currentPage = 1
    private var canLoadMore = true

    private data class LoadState(
        val isInitialLoading: Boolean = true,
        val isLoadingMore: Boolean = false,
        val error: String? = null
    )

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _popularMovies = MutableStateFlow<List<MovieModel>>(emptyList())
    private val _loadState = MutableStateFlow(LoadState())
    private val _viewMode = MutableStateFlow(ViewMode.GRID)

    private val activeMovies: Flow<Pair<List<MovieModel>, LoadState>> = _searchQuery
        .debounce(300L)
        .distinctUntilChanged()
        .flatMapLatest { query ->
            if (query.isBlank()) {
                combine(_popularMovies, _loadState) { movies, state -> movies to state }
            } else {
                repository.searchMovies(query).map { results -> results to LoadState(isInitialLoading = false) }
            }
        }

    val uiState: StateFlow<HomeUiState> = combine(
        activeMovies,
        repository.getFavorites(),
        _viewMode
    ) { (movies, loadState), favorites, viewMode ->
        when {
            loadState.error != null -> HomeUiState.Error(loadState.error)
            loadState.isInitialLoading -> HomeUiState.Loading
            else -> {
                val favoriteIds = favorites.map { it.id }.toSet()
                HomeUiState.Success(
                    movies = movies.map { it.copy(isFavorite = it.id in favoriteIds) },
                    isLoadingMore = loadState.isLoadingMore,
                    viewMode = viewMode
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState.Loading)

    init {
        loadMovies()
    }

    fun loadMovies() {
        currentPage = 1
        canLoadMore = true
        _popularMovies.value = emptyList()
        _loadState.value = LoadState(isInitialLoading = true)
        viewModelScope.launch {
            try {
                useCase(currentPage).collect { movies ->
                    _popularMovies.value = movies
                    _loadState.update { it.copy(isInitialLoading = false) }
                }
            } catch (e: Exception) {
                _loadState.update { it.copy(isInitialLoading = false, error = e.message ?: "Something went wrong") }
            }
        }
    }

    fun loadNextPage() {
        if (_searchQuery.value.isNotBlank() || !canLoadMore || _loadState.value.isLoadingMore) return
        _loadState.update { it.copy(isLoadingMore = true) }
        currentPage++
        viewModelScope.launch {
            try {
                useCase(currentPage).collect { newMovies ->
                    if (newMovies.isEmpty()) canLoadMore = false
                    else _popularMovies.update { current -> (current + newMovies).distinctBy { it.id } }
                    _loadState.update { it.copy(isLoadingMore = false) }
                }
            } catch (e: Exception) {
                _loadState.update { it.copy(isLoadingMore = false) }
            }
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun toggleFavorite(movie: MovieModel) {
        viewModelScope.launch { repository.toggleFavorite(movie) }
    }

    fun setViewMode(mode: ViewMode) {
        _viewMode.value = mode
    }
}
