package com.example.movieflux.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Brand teal accent, separate from the Material color scheme, for surfaces we want explicitly
 * teal-branded (bottom nav bar, screen title bars). [teal] flips with the active theme — the
 * darkest teal in dark mode, the lightest in light mode — and [onTeal] is the legible
 * foreground for content drawn on top of it.
 */
data class BrandColors(val teal: Color, val onTeal: Color)

private val DarkBrand = BrandColors(teal = TealGreenDark, onTeal = Color.White)
private val LightBrand = BrandColors(teal = TealGreenLight, onTeal = Color(0xFF003731))

/** Resolves to the brand colors for the active theme; provided by [MovieFluxTheme]. */
val LocalBrandColors = staticCompositionLocalOf { LightBrand }

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
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colors = if (darkTheme) DarkColors else LightColors
    val brandColors = if (darkTheme) DarkBrand else LightBrand

    // The status bar sits over the teal top bar / search row. Match its icon tint to the brand
    // foreground: dark icons over the light-mode teal, light icons over the dark-mode teal.
    val view = LocalView.current
    if (!view.isInEditMode) {
        (view.context as? Activity)?.window?.let { window ->
            SideEffect {
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(LocalBrandColors provides brandColors) {
        MaterialTheme(
            colorScheme = colors,
            typography = Typography,
            content = content
        )
    }
}
