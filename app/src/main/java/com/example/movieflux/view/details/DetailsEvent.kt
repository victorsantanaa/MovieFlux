package com.example.movieflux.view.details

import androidx.annotation.StringRes

sealed class DetailsEvent {
    data class ShowError(@StringRes val messageRes: Int) : DetailsEvent()
}
