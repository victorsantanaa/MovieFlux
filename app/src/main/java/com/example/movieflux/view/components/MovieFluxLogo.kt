package com.example.movieflux.view.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.example.movieflux.ui.theme.TealGreenLight

// SVG icon (ic_logo_movieflux) requires design asset export from Figma — pending.
// Wordmark is functional as-is.
@Composable
fun MovieFluxLogo(modifier: Modifier = Modifier) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Row {
            Text(
                text = "Movie",
                style = MaterialTheme.typography.headlineSmall,
                color = TealGreenLight.copy(alpha = 0.7f),
                fontWeight = FontWeight.Light
            )
            Text(
                text = "Flux",
                style = MaterialTheme.typography.headlineSmall,
                color = TealGreenLight,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
