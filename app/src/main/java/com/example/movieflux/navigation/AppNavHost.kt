package com.example.movieflux.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import com.example.movieflux.view.login.LoginScreen
import com.example.movieflux.view.main.MainScaffold

@Composable
fun AppNavHost(
    navController: NavHostController,
    startDestination: String = Screen.AuthGraph.route
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        navigation(
            route = Screen.AuthGraph.route,
            startDestination = Screen.Login.route
        ) {
            composable(Screen.Login.route) {
                LoginScreen(
                    onLoginSuccess = {
                        navController.navigate(Screen.MainGraph.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                )
            }
        }

        composable(Screen.MainGraph.route) {
            MainScaffold(
                onLogout = {
                    navController.navigate(Screen.AuthGraph.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
    }
}
