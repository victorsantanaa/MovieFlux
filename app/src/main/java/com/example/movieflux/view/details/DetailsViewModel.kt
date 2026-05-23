package com.example.movieflux.view.details

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.movieflux.R
import com.example.movieflux.analytics.AnalyticsTracker
import com.example.movieflux.domain.usecase.GetMovieDetailUseCase
import com.example.movieflux.domain.usecase.ToggleFavoriteUseCase
import com.example.movieflux.view.common.toUserMessageRes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject

@HiltViewModel
class DetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getMovieDetail: GetMovieDetailUseCase,
    private val toggleFavoriteUseCase: ToggleFavoriteUseCase,
    private val tracker: AnalyticsTracker
) : ViewModel() {

    private val movieId: Int = checkNotNull(savedStateHandle.get<Int>("movieId")) {
        "Details route requires a valid integer movieId argument"
    }

    private val _uiState = MutableStateFlow<DetailsUiState>(DetailsUiState.Loading)
    val uiState: StateFlow<DetailsUiState> = _uiState.asStateFlow()

    private val _events = Channel<DetailsEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        tracker.trackScreen("details")
        loadDetail()
    }

    fun loadDetail() {
        _uiState.value = DetailsUiState.Loading
        viewModelScope.launch {
            try {
                getMovieDetail(movieId).collect { movie ->
                    _uiState.value = DetailsUiState.Success(movie, movie.genreNames)
                }
            } catch (e: IOException) {
                showLoadError(e)
            } catch (e: HttpException) {
                showLoadError(e)
            } catch (e: Exception) {
                // Catch-all para falhas de parsing (ex.: JSON malformado) para que apareçam como
                // estado de erro em vez de um crash não tratado.
                showLoadError(e)
            }
        }
    }

    /** Loga o [error] cru e apresenta uma mensagem amigável e localizada. */
    private fun showLoadError(error: Throwable) {
        tracker.trackError("[DETAILS] load", error)
        _uiState.value = DetailsUiState.Error(error.toUserMessageRes())
    }

    fun toggleFavorite() {
        val current = _uiState.value as? DetailsUiState.Success ?: return
        val originalMovie = current.movie
        tracker.trackEvent(
            "toggle_favorite",
            mapOf("movie_id" to originalMovie.id, "is_favorite" to !originalMovie.isFavorite)
        )
        _uiState.value = current.copy(movie = originalMovie.copy(isFavorite = !originalMovie.isFavorite))
        viewModelScope.launch {
            runCatching { toggleFavoriteUseCase(originalMovie) }
                .onFailure {
                    _uiState.value = current
                    _events.send(DetailsEvent.ShowError(R.string.error_toggle_favorite))
                }
        }
    }

    fun share() {
        val current = _uiState.value as? DetailsUiState.Success ?: return
        val movie = current.movie
        // Construir intents / iniciar activities pertence à camada de UI; emita os dados e deixe o
        // Composable construir o chooser (mantém o ViewModel livre de Context).
        viewModelScope.launch {
            _events.send(
                DetailsEvent.Share(
                    title = movie.title,
                    url = "https://www.themoviedb.org/movie/${movie.id}"
                )
            )
        }
    }
}
