package com.example.movieflux.navigation

sealed class Screen(val route: String) {
    // Graph routes
    object AuthGraph : Screen("auth_graph")
    object MainGraph : Screen("main_graph")

    // Auth destinations
    object Login : Screen("login")

    // Main tab destinations
    object Home : Screen("home")
    object Favorites : Screen("favorites")
    object Profile : Screen("profile")

    // Detail (in MainGraph, hides bottom bar)
    object Details : Screen("details/{movieId}") {
        const val ARG_MOVIE_ID = "movieId"
        fun createRoute(movieId: Int) = "details/$movieId"
    }
}