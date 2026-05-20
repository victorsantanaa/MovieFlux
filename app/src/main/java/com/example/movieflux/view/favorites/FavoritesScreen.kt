package com.example.movieflux.view.favorites

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.movieflux.R
import com.example.movieflux.performance.JankStateEffect
import com.example.movieflux.performance.LogRecompositions
import com.example.movieflux.ui.theme.LocalBrandColors
import com.example.movieflux.view.components.EmptyView
import com.example.movieflux.view.components.LoadingView
import com.example.movieflux.view.components.MovieCard
import com.example.movieflux.view.components.MovieListItem
import com.example.movieflux.view.components.SearchBar
import com.example.movieflux.view.components.ViewMode
import com.example.movieflux.view.components.ViewModeToggle

@Composable
fun FavoritesScreen(onMovieClick: (Int) -> Unit) {
    val vm: FavoritesViewModel = hiltViewModel()
    val uiState by vm.uiState.collectAsState()
    val searchQuery by vm.searchQuery.collectAsState()

    val currentViewMode = (uiState as? FavoritesUiState.Success)?.viewMode ?: ViewMode.GRID

    LogRecompositions("FavoritesScreen")
    JankStateEffect("screen" to "favorites", "view_mode" to currentViewMode.name)

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(LocalBrandColors.current.teal)
                    .statusBarsPadding()
                    .padding(end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SearchBar(
                    query = searchQuery,
                    onQueryChange = vm::setSearchQuery,
                    modifier = Modifier.weight(1f)
                )
                ViewModeToggle(current = currentViewMode, onToggle = vm::setViewMode)
            }
        }
    ) { innerPadding ->
        when (val state = uiState) {
            is FavoritesUiState.Loading -> LoadingView(modifier = Modifier.padding(innerPadding))

            is FavoritesUiState.Success -> {
                if (state.movies.isEmpty()) {
                    EmptyView(
                        title = stringResource(R.string.empty_favorites_title),
                        subtitle = stringResource(R.string.empty_favorites_subtitle),
                        modifier = Modifier.padding(innerPadding)
                    )
                } else {
                    when (state.viewMode) {
                        ViewMode.GRID -> LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            state = rememberLazyGridState(),
                            contentPadding = PaddingValues(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
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

                        ViewMode.LIST -> LazyColumn(
                            state = rememberLazyListState(),
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                        ) {
                            itemsIndexed(state.movies, key = { _, m -> m.id }) { index, movie ->
                                MovieListItem(
                                    movie = movie,
                                    onClick = { onMovieClick(movie.id) },
                                    onToggleFavorite = { vm.toggleFavorite(movie) }
                                )
                                if (index < state.movies.lastIndex) {
                                    HorizontalDivider(
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
