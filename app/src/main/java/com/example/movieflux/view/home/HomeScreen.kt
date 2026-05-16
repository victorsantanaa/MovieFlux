package com.example.movieflux.view.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.movieflux.view.components.MovieCard
import com.example.movieflux.view.components.MovieCardSkeleton
import kotlinx.coroutines.flow.distinctUntilChanged

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onMovieClick: (Int) -> Unit) {
    val vm: HomeViewModel = viewModel()
    val uiState by vm.uiState.collectAsState()
    val gridState = rememberLazyGridState()

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

    Scaffold(
        topBar = { TopAppBar(title = { Text("Movies") }) }
    ) { innerPadding ->
        when (val state = uiState) {
            is HomeUiState.Loading -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    items(6) {
                        MovieCardSkeleton(modifier = Modifier.fillMaxWidth())
                    }
                }
            }

            is HomeUiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = state.message)
                }
            }

            is HomeUiState.Success -> {
                LazyVerticalGrid(
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
            }
        }
    }
}
