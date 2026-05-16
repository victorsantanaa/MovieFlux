package com.example.movieflux.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.movieflux.R

enum class TopLevelTab(
    val route: String,
    val icon: ImageVector,
    @StringRes val labelRes: Int
) {
    HOME(Screen.Home.route, Icons.Default.Home, R.string.tab_home),
    FAVORITES(Screen.Favorites.route, Icons.Default.Favorite, R.string.tab_favorites),
    PROFILE(Screen.Profile.route, Icons.Default.Person, R.string.tab_profile)
}
