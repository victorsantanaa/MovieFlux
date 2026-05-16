package com.example.movieflux.view.favorites

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.example.movieflux.view.components.ButtonSize
import com.example.movieflux.view.components.PrimaryButton

@Composable
fun FavoritesScreen(
    onMovieClick: (Int) -> Unit
) {
    Column {
        Text(
            text = "Favorites Screen",
            style = MaterialTheme.typography.titleLarge
        )
    }
}