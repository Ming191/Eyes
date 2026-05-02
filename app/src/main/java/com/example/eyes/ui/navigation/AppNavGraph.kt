package com.example.eyes.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.eyes.ui.camera.CameraScreen
import com.example.eyes.ui.home.HomeScreen
import com.example.eyes.ui.map.MapScreen
import com.example.eyes.ui.onboarding.OnboardingScreen
import com.example.eyes.ui.settings.SettingsScreen
import org.koin.androidx.compose.koinViewModel

@Composable
fun AppNavGraph(
    viewModel: AppNavViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    when {
        uiState.isLoading -> LoadingScreen()
        uiState.onboardingCompleted -> MainNavigationScaffold()
        else -> OnboardingNavHost(onFinish = viewModel::completeOnboarding)
    }
}

@Composable
private fun LoadingScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .semantics { contentDescription = "Đang tải trạng thái ứng dụng" },
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun OnboardingNavHost(
    onFinish: () -> Unit
) {
    key("onboarding") {
        val navController = rememberNavController()

        NavHost(
            navController = navController,
            startDestination = OnboardingRoute,
            modifier = Modifier
                .fillMaxSize()
                .semantics { contentDescription = "Điều hướng khởi động" }
        ) {
            composable<OnboardingRoute> {
                OnboardingScreen(onFinish = onFinish)
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun MainNavigationScaffold() {
    key("main") {
        val navController = rememberNavController()
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentDestination = navBackStackEntry?.destination
        val currentTopLevelDestination = currentDestination.toTopLevelDestination()

        Scaffold(
            modifier = Modifier.semantics { contentDescription = "Khung điều hướng chính" },
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = currentTopLevelDestination.title,
                            modifier = Modifier.semantics { heading() }
                        )
                    },
                    modifier = Modifier.semantics {
                        contentDescription = "Thanh tiêu đề ${currentTopLevelDestination.title}"
                    }
                )
            },
            bottomBar = {
                NavigationBar(
                    modifier = Modifier.semantics {
                        contentDescription = "Thanh điều hướng chính"
                    }
                ) {
                    TopLevelDestination.entries.forEach { destination ->
                        val isSelected = currentDestination.isInHierarchy(destination)

                        NavigationBarItem(
                            selected = isSelected,
                            onClick = { navController.navigateToTopLevelDestination(destination) },
                            icon = {
                                Icon(
                                    imageVector = destination.icon,
                                    contentDescription = null
                                )
                            },
                            label = { Text(text = destination.label) },
                            modifier = Modifier.semantics {
                                stateDescription = if (isSelected) "Đang được chọn" else "Chưa chọn"
                            }
                        )
                    }
                }
            }
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = HomeRoute,
                modifier = Modifier.padding(innerPadding)
            ) {
                composable<HomeRoute> {
                    HomeScreen(
                        onOpenCamera = { mode ->
                            navController.navigate(CameraRoute(mode = mode)) {
                                launchSingleTop = true
                                restoreState = true
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                            }
                        },
                        onOpenMap = {
                            navController.navigateToTopLevelDestination(TopLevelDestination.MAP)
                        },
                        onOpenSettings = {
                            navController.navigateToTopLevelDestination(TopLevelDestination.SETTINGS)
                        }
                    )
                }
                composable<CameraRoute> { backStackEntry ->
                    val route: CameraRoute = backStackEntry.toRoute()
                    CameraScreen(mode = route.mode)
                }
                composable<MapRoute> {
                    MapScreen()
                }
                composable<SettingsRoute> {
                    SettingsScreen()
                }
            }
        }
    }
}

private fun NavHostController.navigateToTopLevelDestination(
    destination: TopLevelDestination
) {
    val route = when (destination) {
        TopLevelDestination.HOME -> HomeRoute
        TopLevelDestination.CAMERA -> CameraRoute()
        TopLevelDestination.MAP -> MapRoute
        TopLevelDestination.SETTINGS -> SettingsRoute
    }

    navigate(route) {
        launchSingleTop = true
        restoreState = true
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
    }
}

private fun NavDestination?.toTopLevelDestination(): TopLevelDestination {
    return when {
        this.isInHierarchy(TopLevelDestination.CAMERA) -> TopLevelDestination.CAMERA
        this.isInHierarchy(TopLevelDestination.MAP) -> TopLevelDestination.MAP
        this.isInHierarchy(TopLevelDestination.SETTINGS) -> TopLevelDestination.SETTINGS
        else -> TopLevelDestination.HOME
    }
}

private fun NavDestination?.isInHierarchy(destination: TopLevelDestination): Boolean {
    return this?.hierarchy?.any { currentDestination ->
        when (destination) {
            TopLevelDestination.HOME -> currentDestination.hasRoute<HomeRoute>()
            TopLevelDestination.CAMERA -> currentDestination.hasRoute<CameraRoute>()
            TopLevelDestination.MAP -> currentDestination.hasRoute<MapRoute>()
            TopLevelDestination.SETTINGS -> currentDestination.hasRoute<SettingsRoute>()
        }
    } == true
}
