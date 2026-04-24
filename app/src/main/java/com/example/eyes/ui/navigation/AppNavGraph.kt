package com.example.eyes.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.eyes.ui.camera.CameraScreen
import com.example.eyes.ui.home.HomeScreen
import com.example.eyes.ui.map.MapScreen
import com.example.eyes.ui.settings.SettingsScreen

@Composable
fun AppNavGraph() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.Home
    ) {
        composable(Routes.Home) {
            HomeScreen(navController = navController)
        }
        composable(Routes.Camera) {
            CameraScreen()
        }
        composable(Routes.Map) {
            MapScreen()
        }
        composable(Routes.Settings) {
            SettingsScreen()
        }
    }
}

object Routes {
    const val Home = "home"
    const val Camera = "camera"
    const val Map = "map"
    const val Settings = "settings"
}
