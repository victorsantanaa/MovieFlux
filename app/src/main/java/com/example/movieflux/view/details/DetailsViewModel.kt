package com.example.movieflux.view.details

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.movieflux.data.repository.FakeMovieRepository
import com.example.movieflux.domain.model.MovieModel
import com.example.movieflux.domain.repository.MovieRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// TODO Phase 1/2: replace with @HiltViewModel + @Inject constructor(savedStateHandle, repository)
class DetailsViewModel(private val movieId: Int) : ViewModel() {

    private val repository: MovieRepository = FakeMovieRepository

    private val _uiState = MutableStateFlow<DetailsUiState>(DetailsUiState.Loading)
    val uiState: StateFlow<DetailsUiState> = _uiState.asStateFlow()

    init {
        loadDetail()
    }

    fun loadDetail() {
        _uiState.value = DetailsUiState.Loading
        viewModelScope.launch {
            try {
                repository.getMovieDetail(movieId).collect { movie ->
                    val genreNames = repository.getGenres()
                        .filterKeys { it in movie.genreIds }
                        .values.toList()
                    _uiState.value = DetailsUiState.Success(movie, genreNames)
                }
            } catch (e: Exception) {
                _uiState.value = DetailsUiState.Error(e.message ?: "Failed to load movie details")
            }
        }
    }

    fun toggleFavorite() {
        val current = _uiState.value as? DetailsUiState.Success ?: return
        val movie = current.movie
        _uiState.value = current.copy(movie = movie.copy(isFavorite = !movie.isFavorite))
        viewModelScope.launch { repository.toggleFavorite(movie) }
    }

    fun share(context: Context) {
        val current = _uiState.value as? DetailsUiState.Success ?: return
        val movie = current.movie
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(
                Intent.EXTRA_TEXT,
                "${movie.title}\nhttps://www.themoviedb.org/movie/${movie.id}"
            )
        }
        context.startActivity(Intent.createChooser(intent, "Share via"))
    }

    class Factory(private val movieId: Int) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return DetailsViewModel(movieId) as T
        }
    }
}
