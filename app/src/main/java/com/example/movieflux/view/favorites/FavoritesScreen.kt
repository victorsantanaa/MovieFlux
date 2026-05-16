package com.example.movieflux.view.favorites

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.movieflux.view.components.EmptyView
import com.example.movieflux.view.components.LoadingView
import com.example.movieflux.view.components.MovieCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(onMovieClick: (Int) -> Unit) {
    val vm: FavoritesViewModel = viewModel()
    val uiState by vm.uiState.collectAsState()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Favorites") }) }
    ) { innerPadding ->
        when (val state = uiState) {
            is FavoritesUiState.Loading -> LoadingView(modifier = Modifier.padding(innerPadding))

            is FavoritesUiState.Success -> {
                if (state.movies.isEmpty()) {
                    EmptyView(
                        title = "No favorites yet",
                        subtitle = "Tap the heart on any movie to add it",
                        modifier = Modifier.padding(innerPadding)
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        items(state.movies, key = { it.id }) { movie ->
                            MovieCard(
                                movie = movie,
                                onClick = { onMovieClick(movie.id) },
                                onToggleFavorite = { vm.toggleFavorite(movie) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}
