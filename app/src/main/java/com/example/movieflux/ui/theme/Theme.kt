package com.example.movieflux.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.movieflux.ui.theme.Typography

private val LightColors = lightColorScheme(
    primary = TealGreen,
    onPrimary = Color.White,
    secondary = TealGreenLight,
    background = BackgroundLight,
    surface = Color.White,
    error = ErrorRed
)

private val DarkColors = darkColorScheme(
    primary = TealGreenLight,
    onPrimary = Color.Black,
    background = BackgroundDark,
    surface = Color(0xFF1E1E1E)
)

@Composable
fun MovieFluxTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors

    MaterialTheme(
        colorScheme = colors,
        typography = Typography,
        content = content
    )
}