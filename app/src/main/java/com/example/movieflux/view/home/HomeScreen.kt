package com.example.movieflux.view.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.movieflux.R
import com.example.movieflux.performance.JankStateEffect
import com.example.movieflux.performance.LogRecompositions
import com.example.movieflux.ui.theme.LocalBrandColors
import com.example.movieflux.view.components.EmptyView
import com.example.movieflux.view.components.ErrorView
import com.example.movieflux.view.components.MovieCard
import com.example.movieflux.view.components.MovieCardSkeleton
import com.example.movieflux.view.components.MovieListItem
import com.example.movieflux.view.components.SearchBar
import com.example.movieflux.view.components.ViewMode
import com.example.movieflux.view.components.ViewModeToggle
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun HomeScreen(onMovieClick: (Int) -> Unit) {
    val vm: HomeViewModel = hiltViewModel()
    val uiState by vm.uiState.collectAsState()
    val searchQuery by vm.searchQuery.collectAsState()
    val gridState = rememberLazyGridState()
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    val wasLoading = remember { mutableStateOf(uiState is HomeUiState.Loading) }
    LaunchedEffect(uiState) {
        when {
            uiState is HomeUiState.Loading -> wasLoading.value = true
            uiState is HomeUiState.Success && wasLoading.value -> {
                wasLoading.value = false
                gridState.scrollToItem(0)
                listState.scrollToItem(0)
            }
        }
    }

    LaunchedEffect(vm.events) {
        vm.events.collect { event ->
            when (event) {
                is HomeEvent.PaginationError -> snackbarHostState.showSnackbar(
                    context.getString(R.string.toast_pagination_error_retry)
                )
                is HomeEvent.SearchError -> { /* silent — UI shows empty list */ }
            }
        }
    }

    val currentViewMode = (uiState as? HomeUiState.Success)?.viewMode ?: ViewMode.GRID

    LaunchedEffect(gridState) {
        snapshotFlow {
            val info = gridState.layoutInfo
            info.visibleItemsInfo.lastOrNull()?.index to info.totalItemsCount
        }
            .distinctUntilChanged()
            .collect { (lastVisible, total) ->
                val visible = gridState.layoutInfo.visibleItemsInfo.size
                if (lastVisible != null && total > visible && lastVisible >= total - 3) {
                    vm.loadNextPage()
                }
            }
    }

    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            info.visibleItemsInfo.lastOrNull()?.index to info.totalItemsCount
        }
            .distinctUntilChanged()
            .collect { (lastVisible, total) ->
                val visible = listState.layoutInfo.visibleItemsInfo.size
                if (lastVisible != null && total > visible && lastVisible >= total - 3) {
                    vm.loadNextPage()
                }
            }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(LocalBrandColors.current.teal)
                    .statusBarsPadding()
                    .padding(end = 4.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
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
        LogRecompositions("HomeScreen")
        JankStateEffect(
            "screen" to "home",
            "view_mode" to currentViewMode.name
        )

        AnimatedContent(
            targetState = uiState,
            contentKey = { it is HomeUiState.Loading },
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "home_content"
        ) { state ->
            when (state) {
                is HomeUiState.Loading -> LazyVerticalGrid(
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

                is HomeUiState.Error -> ErrorView(
                    message = state.message,
                    onRetry = vm::loadMovies,
                    modifier = Modifier.padding(innerPadding)
                )

                is HomeUiState.Success -> {
                    if (state.movies.isEmpty()) {
                        if (state.isQueryActive) {
                            EmptyView(
                                modifier = Modifier.padding(innerPadding),
                                title = stringResource(R.string.empty_no_results_for, searchQuery),
                                subtitle = ""
                            )
                        } else {
                            EmptyView(
                                modifier = Modifier.padding(innerPadding),
                                title = stringResource(R.string.empty_no_popular),
                                subtitle = ""
                            )
                        }
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
                                } else if (state.errorOnPage != null) {
                                    item(span = { GridItemSpan(maxLineSpan) }) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(8.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Button(onClick = vm::loadNextPage) {
                                                Text(stringResource(R.string.pagination_error_retry))
                                            }
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
                                } else if (state.errorOnPage != null) {
                                    item {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(8.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Button(onClick = vm::loadNextPage) {
                                                Text(stringResource(R.string.pagination_error_retry))
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
    }
}
