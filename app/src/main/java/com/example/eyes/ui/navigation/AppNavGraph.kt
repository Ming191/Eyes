package com.example.eyes.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.eyes.data.DataStoreManager
import com.example.eyes.ui.camera.CameraScreen
import com.example.eyes.ui.home.HomeScreen
import com.example.eyes.ui.map.MapScreen
import com.example.eyes.ui.onboarding.OnboardingScreen
import com.example.eyes.ui.settings.SettingsScreen
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun AppNavGraph() {
    val navController = rememberNavController()
    val dataStoreManager: DataStoreManager = koinInject()
    val coroutineScope = rememberCoroutineScope()

    // Dùng collectAsState(initial = null) trực tiếp trên flow val stable.
    // KHÔNG dùng .map { }.onStart { emit(null) } — chuỗi đó tạo Flow object MỚI
    // mỗi lần AppNavGraph recompose, khiến collectAsState() hủy subscription cũ
    // và subscribe lại → onStart emit null → spinner hiện → NavHost unmount/mount
    // liên tục → flicker vô hạn.
    val onboardingCompleted: Boolean? by remember(dataStoreManager) {
        dataStoreManager.onboardingCompletedFlow
    }.collectAsState(initial = null)

    if (onboardingCompleted == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .semantics { contentDescription = "Đang tải trạng thái khởi động" },
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    val startDestination = if (onboardingCompleted == true) Routes.Home else Routes.Onboarding

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Routes.Onboarding) {
            OnboardingScreen(
                onFinish = {
                    coroutineScope.launch {
                        dataStoreManager.setOnboardingCompleted(true)
                        navController.navigate(Routes.Home) {
                            popUpTo(Routes.Onboarding) { inclusive = true }
                        }
                    }
                }
            )
        }
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
    const val Onboarding = "onboarding"
    const val Home = "home"
    const val Camera = "camera"
    const val Map = "map"
    const val Settings = "settings"
}
