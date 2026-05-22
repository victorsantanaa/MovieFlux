package com.example.movieflux.view.login

import androidx.annotation.StringRes

sealed class LoginUiState {
    object Idle : LoginUiState()
    object Loading : LoginUiState()
    data class Success(val shouldPromptBiometric: Boolean) : LoginUiState()
    data class Error(@StringRes val messageRes: Int) : LoginUiState()
}
