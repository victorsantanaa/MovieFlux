package com.example.movieflux

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.navigation.compose.rememberNavController
import com.example.movieflux.navigation.AppNavHost
import com.example.movieflux.ui.theme.MovieFluxTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MovieFluxTheme {
                val rootNavController = rememberNavController()
                AppNavHost(rootNavController)
            }
        }
    }
}
