package com.example.movieflux.navigation

sealed class Screen(val route: String) {
    object AuthGraph : Screen("auth_graph")
    object MainGraph : Screen("main_graph")

    object Login : Screen("login")

    object Home : Screen("home")
    object Favorites : Screen("favorites")
    object Profile : Screen("profile")

    /** Lives in [MainGraph]; the parent scaffold hides the bottom bar on this route. */
    object Details : Screen("details/{movieId}") {
        const val ARG_MOVIE_ID = "movieId"
        fun createRoute(movieId: Int) = "details/$movieId"
    }
}
