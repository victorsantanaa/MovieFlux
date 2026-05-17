package com.example.movieflux.view.home

sealed class HomeEvent {
    data class PaginationError(val message: String?) : HomeEvent()
    data class SearchError(val message: String?) : HomeEvent()
}
