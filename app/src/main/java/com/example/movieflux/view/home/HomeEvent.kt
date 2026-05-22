package com.example.movieflux.view.home

import androidx.annotation.StringRes

sealed class HomeEvent {
    data class PaginationError(@StringRes val messageRes: Int) : HomeEvent()
    object SearchError : HomeEvent()
}
