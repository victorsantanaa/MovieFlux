package com.example.movieflux.view.home

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.example.movieflux.view.components.ButtonSize
import com.example.movieflux.view.components.PrimaryButton

@Composable
fun HomeScreen(
    onMovieClick: (Int) -> Unit
) {
    Column {
        Text(
            text = "Home Screen",
            style = MaterialTheme.typography.titleLarge
        )
        PrimaryButton(
            text = "Go to Details",
            onClick = { onMovieClick(123) },
            size = ButtonSize.MEDIUM
        )
    }
}