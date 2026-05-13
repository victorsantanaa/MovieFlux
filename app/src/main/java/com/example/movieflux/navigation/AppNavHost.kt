package com.example.movieflux.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.movieflux.navigation.Screen
import com.example.movieflux.view.details.DetailsScreen
import com.example.movieflux.view.favorites.FavoritesScreen
import com.example.movieflux.view.home.HomeScreen
import com.example.movieflux.view.login.LoginScreen

@Composable
fun AppNavHost(
    navController: NavHostController
) {
    val startDestination = Screen.Login.route

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {

        composable(Screen.Login.route) {
            LoginScreen(
                onLoginClick = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Home.route) {
            HomeScreen(
                onMovieClick = { id ->
                    navController.navigate(Screen.Details.createRoute(id))
                },
                onFavoritesClick = {
                    navController.navigate(Screen.Favorites.route)
                },
                onLogoutClick = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) // limpa stack
                    }
                }
            )
        }

        composable(Screen.Details.route) { backStackEntry ->
            val movieId = backStackEntry.arguments
                ?.getString("movieId")
                ?.toIntOrNull()

            DetailsScreen(
                movieId = movieId,
                onBackClick = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.Favorites.route) {
            FavoritesScreen(
                onBackClick = {
                    navController.popBackStack()
                }
            )
        }
    }
}