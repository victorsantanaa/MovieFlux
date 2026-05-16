package com.example.movieflux.view.main

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.movieflux.navigation.Screen
import com.example.movieflux.view.components.BottomNavBar
import com.example.movieflux.view.details.DetailsScreen
import com.example.movieflux.view.favorites.FavoritesScreen
import com.example.movieflux.view.home.HomeScreen
import com.example.movieflux.view.profile.ProfileScreen

@Composable
fun MainScaffold(onLogout: () -> Unit) {
    val innerNavController = rememberNavController()
    val navBackStackEntry by innerNavController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val shouldShowBottomBar = currentRoute?.startsWith("details/") == false

    Scaffold(
        bottomBar = {
            if (shouldShowBottomBar) {
                BottomNavBar(navController = innerNavController)
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = innerNavController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    onMovieClick = { id ->
                        innerNavController.navigate(Screen.Details.createRoute(id))
                    }
                )
            }

            composable(Screen.Favorites.route) {
                FavoritesScreen(
                    onMovieClick = { id ->
                        innerNavController.navigate(Screen.Details.createRoute(id))
                    }
                )
            }

            composable(Screen.Profile.route) {
                ProfileScreen(onLogout = onLogout)
            }

            composable(
                route = Screen.Details.route,
                arguments = listOf(
                    navArgument(Screen.Details.ARG_MOVIE_ID) { type = NavType.StringType }
                )
            ) {
                DetailsScreen(
                    onBackClick = { innerNavController.popBackStack() }
                )
            }
        }
    }
}
