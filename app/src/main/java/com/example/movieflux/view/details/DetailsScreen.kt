package com.example.movieflux.view.details

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.example.movieflux.view.components.ButtonSize
import com.example.movieflux.view.components.PrimaryButton

@Composable
fun DetailsScreen(
    movieId: Int?,
    onBackClick: () -> Unit
) {
    Column {
        Text(
            text = "Detail Screen $movieId",
            style = MaterialTheme.typography.titleLarge
        )
        PrimaryButton(
            text = "Back to Home",
            onClick = onBackClick,
            size = ButtonSize.MEDIUM
        )
    }
}