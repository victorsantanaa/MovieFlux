package com.example.movieflux.view.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.movieflux.view.components.EmptyView
import com.example.movieflux.view.components.ErrorView
import com.example.movieflux.view.components.LoadingView
import com.example.movieflux.view.components.MovieCard
import com.example.movieflux.view.components.MovieListItem
import com.example.movieflux.view.components.SearchBar
import com.example.movieflux.view.components.ViewMode
import com.example.movieflux.view.components.ViewModeToggle
import kotlinx.coroutines.flow.distinctUntilChanged

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onMovieClick: (Int) -> Unit) {
    val vm: HomeViewModel = hiltViewModel()
    val uiState by vm.uiState.collectAsState()
    val searchQuery by vm.searchQuery.collectAsState()
    val gridState = rememberLazyGridState()
    val listState = rememberLazyListState()

    val currentViewMode = (uiState as? HomeUiState.Success)?.viewMode ?: ViewMode.GRID

    LaunchedEffect(gridState) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .distinctUntilChanged()
            .collect { lastVisible ->
                val total = gridState.layoutInfo.totalItemsCount
                if (lastVisible != null && total > 0 && lastVisible >= total - 3) {
                    vm.loadNextPage()
                }
            }
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .distinctUntilChanged()
            .collect { lastVisible ->
                val total = listState.layoutInfo.totalItemsCount
                if (lastVisible != null && total > 0 && lastVisible >= total - 3) {
                    vm.loadNextPage()
                }
            }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Movies") },
                    actions = {
                        ViewModeToggle(current = currentViewMode, onToggle = vm::setViewMode)
                    }
                )
                SearchBar(
                    query = searchQuery,
                    onQueryChange = vm::setSearchQuery
                )
            }
        }
    ) { innerPadding ->
        when (val state = uiState) {
            is HomeUiState.Loading -> LoadingView(modifier = Modifier.padding(innerPadding))

            is HomeUiState.Error -> ErrorView(
                message = state.message,
                onRetry = vm::loadMovies,
                modifier = Modifier.padding(innerPadding)
            )

            is HomeUiState.Success -> {
                if (state.movies.isEmpty()) {
                    EmptyView(modifier = Modifier.padding(innerPadding))
                } else {
                    when (state.viewMode) {
                        ViewMode.GRID -> LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            state = gridState,
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
                            if (state.isLoadingMore) {
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator()
                                    }
                                }
                            }
                        }

                        ViewMode.LIST -> LazyColumn(
                            state = listState,
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
                            if (state.isLoadingMore) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
