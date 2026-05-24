package com.example.eyes.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.eyes.R
import com.example.eyes.ui.camera.CameraMode
import com.example.eyes.ui.camera.CameraScreen
import com.example.eyes.ui.home.HomeScreen
import com.example.eyes.ocr.OcrMode
import com.example.eyes.ui.onboarding.OnboardingScreen
import com.example.eyes.ui.settings.SettingsScreen
import com.example.eyes.ui.voice.VoiceCommandScreen
import org.koin.androidx.compose.koinViewModel

@Composable
fun AppNavGraph(
    viewModel: AppNavViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    when {
        uiState.isLoading -> LoadingScreen()
        uiState.onboardingCompleted -> MainNavigationScaffold(
            viewModel = viewModel,
            appLanguage = uiState.appLanguage
        )
        else -> OnboardingNavHost(onFinish = viewModel::completeOnboarding)
    }
}

@Composable
private fun LoadingScreen() {
    val description = stringResource(R.string.nav_loading_description)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .semantics { contentDescription = description },
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
        val description = stringResource(R.string.nav_onboarding_description)

        NavHost(
            navController = navController,
            startDestination = OnboardingRoute,
            modifier = Modifier
                .fillMaxSize()
                .semantics { contentDescription = description }
        ) {
            composable<OnboardingRoute> {
                OnboardingScreen(onFinish = onFinish)
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun MainNavigationScaffold(
    viewModel: AppNavViewModel,
    appLanguage: com.example.eyes.i18n.AppLanguage
) {
    key("main") {
        val navController = rememberNavController()
        val requestedCameraMode by viewModel.requestedCameraMode.collectAsStateWithLifecycle()
        val currentSpokenText by viewModel.currentSpokenText.collectAsStateWithLifecycle(initialValue = null)
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentDestination = navBackStackEntry?.destination
        val announcedTopLevelDestination = currentDestination.toTopLevelDestinationOrNull()
        val currentTopLevelDestination = announcedTopLevelDestination ?: TopLevelDestination.HOME
        LaunchedEffect(announcedTopLevelDestination, appLanguage) {
            announcedTopLevelDestination?.let { destination ->
                viewModel.announceScreen(destination, appLanguage)
            }
        }
        val currentTitle = stringResource(currentTopLevelDestination.titleRes)
        val scaffoldDescription = stringResource(R.string.nav_scaffold_description)
        val topBarDescription = stringResource(R.string.nav_top_bar_description, currentTitle)
        val bottomBarDescription = stringResource(R.string.nav_bottom_bar_description)
        val selectedDescription = stringResource(R.string.nav_selected_description)
        val unselectedDescription = stringResource(R.string.nav_unselected_description)

        Scaffold(
            modifier = Modifier.semantics { contentDescription = scaffoldDescription },
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = currentTitle,
                            modifier = Modifier.semantics { heading() }
                        )
                    },
                    modifier = Modifier.semantics {
                        contentDescription = topBarDescription
                    }
                )
            },
            bottomBar = {
                NavigationBar(
                    modifier = Modifier.semantics {
                        contentDescription = bottomBarDescription
                    }
                ) {
                    TopLevelDestination.entries.forEach { destination ->
                        val isSelected = currentDestination.isInHierarchy(destination)

                        NavigationBarItem(
                            selected = isSelected,
                            onClick = {
                                if (destination == TopLevelDestination.CAMERA) {
                                    viewModel.requestOpenCamera(CameraMode.OBJECT_DETECTION)
                                }
                                navController.navigateToTopLevelDestination(destination)
                            },
                            icon = {
                                Icon(
                                    imageVector = destination.icon,
                                    contentDescription = null
                                )
                            },
                            label = { Text(text = stringResource(destination.labelRes)) },
                            modifier = Modifier.semantics {
                                stateDescription = if (isSelected) selectedDescription else unselectedDescription
                            }
                        )
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                NavHost(
                    navController = navController,
                    startDestination = HomeRoute,
                    modifier = Modifier.fillMaxSize()
                ) {
                    composable<HomeRoute> {
                        HomeScreen(
                            onOpenOcrQuick = {
                                viewModel.requestOpenCameraOcr(OcrMode.QUICK)
                                navController.navigateToTopLevelDestination(TopLevelDestination.CAMERA)
                            },
                            onOpenOcrAccuracy = {
                                viewModel.requestOpenCameraOcr(OcrMode.ACCURACY)
                                navController.navigateToTopLevelDestination(TopLevelDestination.CAMERA)
                            },
                            onOpenSettings = {
                                navController.navigateToTopLevelDestination(TopLevelDestination.SETTINGS)
                            },
                            onOpenVoice = {
                                navController.navigate(VoiceRoute) {
                                    launchSingleTop = true
                                }
                            }
                        )
                    }
                    composable<CameraRoute> {
                        CameraScreen(
                            requestedMode = requestedCameraMode,
                            onRequestedModeConsumed = viewModel::clearRequestedCameraMode
                        )
                    }
                    composable<SettingsRoute> {
                        SettingsScreen()
                    }
                    composable<VoiceRoute> {
                        VoiceCommandScreen(
                            onNavigateToCamera = {
                                navController.navigate(CameraRoute) {
                                    popUpTo(HomeRoute) { saveState = true }
                                    launchSingleTop = true
                                }
                            },
                            onNavigateBackHome = {
                                navController.popBackStack(HomeRoute, inclusive = false)
                            }
                        )
                    }
                }
                SpeechSubtitle(
                    text = currentSpokenText,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }
    }
}

@Composable
private fun SpeechSubtitle(
    text: String?,
    modifier: Modifier = Modifier
) {
    if (text.isNullOrBlank()) return
    val subtitleDescription = stringResource(R.string.voice_guide_subtitle_description, text)

    Surface(
        modifier = modifier
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp)
            .widthIn(max = 560.dp)
            .fillMaxWidth()
            .semantics { contentDescription = subtitleDescription },
        shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
        color = Color.Black.copy(alpha = 0.78f),
        tonalElevation = 8.dp,
        shadowElevation = 8.dp
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
            color = Color.White,
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
    }
}
private fun NavHostController.navigateToTopLevelDestination(
    destination: TopLevelDestination
) {
    val route = when (destination) {
        TopLevelDestination.HOME -> HomeRoute
        TopLevelDestination.CAMERA -> CameraRoute
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

private fun NavDestination?.toTopLevelDestinationOrNull(): TopLevelDestination? {
    return when {
        this.isInHierarchy(TopLevelDestination.HOME) -> TopLevelDestination.HOME
        this.isInHierarchy(TopLevelDestination.CAMERA) -> TopLevelDestination.CAMERA
        this.isInHierarchy(TopLevelDestination.SETTINGS) -> TopLevelDestination.SETTINGS
        else -> null
    }
}

private fun NavDestination?.isInHierarchy(destination: TopLevelDestination): Boolean {
    return this?.hierarchy?.any { currentDestination ->
        when (destination) {
            TopLevelDestination.HOME -> currentDestination.route == HomeRoute::class.qualifiedName
            TopLevelDestination.CAMERA -> currentDestination.route == CameraRoute::class.qualifiedName
            TopLevelDestination.SETTINGS -> currentDestination.route == SettingsRoute::class.qualifiedName
        }
    } == true
}
