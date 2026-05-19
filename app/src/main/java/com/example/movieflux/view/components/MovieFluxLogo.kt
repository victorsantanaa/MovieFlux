package com.example.movieflux.view.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.movieflux.R
import com.example.movieflux.ui.theme.TealGreenLight

@Composable
fun MovieFluxLogo(modifier: Modifier = Modifier, size: Dp = 120.dp) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Image(
            painter = painterResource(R.mipmap.movie_flux_logo),
            contentDescription = "MovieFlux",
            modifier = Modifier.size(size)
        )
        Spacer(Modifier.height(8.dp))
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
