package com.example.movieflux.view.login

sealed class LoginUiState {
    object Idle : LoginUiState()
    object Loading : LoginUiState()
    data class Success(val shouldPromptBiometric: Boolean) : LoginUiState()
    data class Error(val message: String) : LoginUiState()
}
