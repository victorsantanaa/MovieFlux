package com.example.movieflux.view.login

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.example.movieflux.view.components.ButtonSize
import com.example.movieflux.view.components.PrimaryButton

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit
) {
    Column {
        Text(
            text = "Login Screen",
            style = MaterialTheme.typography.titleLarge
        )
        PrimaryButton(
            text = "Go to Home",
            onClick = onLoginSuccess,
            size = ButtonSize.LARGE
        )
    }
}