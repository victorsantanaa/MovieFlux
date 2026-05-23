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
 * Accent verde-azulado de marca, separado do color scheme do Material, para superfícies que queremos
 * explicitamente com a identidade teal (bottom nav bar, top bars das telas). [teal] alterna conforme
 * o tema ativo — o teal mais escuro no dark mode, o mais claro no light mode — e [onTeal] é o
 * foreground legível para conteúdo desenhado sobre ele.
 */
data class BrandColors(val teal: Color, val onTeal: Color)

private val DarkBrand = BrandColors(teal = TealGreenDark, onTeal = Color.White)
private val LightBrand = BrandColors(teal = TealGreenLight, onTeal = Color(0xFF003731))

/** Resolve para as cores de marca do tema ativo; provido por [MovieFluxTheme]. */
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

    // A status bar fica sobre a top bar verde-azulada / linha de busca. Combine o tint dos ícones
    // com o foreground da marca: ícones escuros sobre o teal do light mode, ícones claros sobre o
    // teal do dark mode.
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
