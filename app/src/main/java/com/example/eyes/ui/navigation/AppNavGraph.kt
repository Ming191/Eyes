package com.example.eyes.ui.navigation

import android.content.Intent
import android.net.Uri
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
import com.example.eyes.application.ports.SpeechOutput
import com.example.eyes.domain.i18n.AppLanguage
import com.example.eyes.infrastructure.accessibility.AccessibilityStateProvider
import com.example.eyes.ui.blind.BlindAction
import com.example.eyes.ui.blind.BlindGestureLayer
import com.example.eyes.ui.blind.LocalBlindFocusManager
import com.example.eyes.ui.blind.LocalBlindFocusRouteKey
import com.example.eyes.ui.blind.blindFocusable
import com.example.eyes.ui.camera.CameraMode
import com.example.eyes.ui.camera.CameraScreen
import com.example.eyes.ui.emergency.EmergencyScreen
import com.example.eyes.ui.home.HomeScreen
import com.example.eyes.domain.ocr.OcrMode
import com.example.eyes.ui.onboarding.OnboardingScreen
import com.example.eyes.ui.settings.SettingsScreen
import org.koin.compose.koinInject
import org.koin.androidx.compose.koinViewModel

@Composable
fun AppNavGraph(
    viewModel: AppNavViewModel = koinViewModel(),
    speechOutput: SpeechOutput = koinInject(),
    accessibilityStateProvider: AccessibilityStateProvider = koinInject()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentSpokenText by viewModel.currentSpokenText.collectAsStateWithLifecycle(initialValue = null)
    val isScreenReaderLikelyEnabled by accessibilityStateProvider.screenReaderLikelyEnabledFlow
        .collectAsStateWithLifecycle(initialValue = accessibilityStateProvider.isScreenReaderLikelyEnabled)

    BlindGestureLayer(
        speechOutput = speechOutput,
        localeProvider = { uiState.appLanguage.ttsLocale },
        enabled = !isScreenReaderLikelyEnabled,
        noActionsLabel = stringResource(R.string.blind_gesture_no_actions),
        layerDescription = stringResource(R.string.blind_gesture_layer_description),
        focusOverlayDescription = stringResource(R.string.blind_focus_overlay_description)
    ) {
        when {
            uiState.isLoading -> LoadingScreen()
            uiState.onboardingCompleted -> MainNavigationScaffold(
                viewModel = viewModel,
                appLanguage = uiState.appLanguage,
                currentSpokenText = currentSpokenText
            )
            else -> OnboardingNavHost(
                appLanguage = uiState.appLanguage,
                currentSpokenText = currentSpokenText,
                onLanguageSelected = viewModel::setAppLanguage,
                onFinish = viewModel::completeOnboarding
            )
        }
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
    appLanguage: AppLanguage,
    currentSpokenText: String?,
    onLanguageSelected: (AppLanguage) -> Unit,
    onFinish: () -> Unit
) {
    key("onboarding") {
        val navController = rememberNavController()
        val description = stringResource(R.string.nav_onboarding_description)

        Box(modifier = Modifier.fillMaxSize()) {
            NavHost(
                navController = navController,
                startDestination = OnboardingRoute,
                modifier = Modifier
                    .fillMaxSize()
                    .semantics { contentDescription = description }
            ) {
                composable<OnboardingRoute> {
                    OnboardingScreen(
                        selectedLanguage = appLanguage,
                        onLanguageSelected = onLanguageSelected,
                        onFinish = onFinish
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

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun MainNavigationScaffold(
    viewModel: AppNavViewModel,
    appLanguage: AppLanguage,
    currentSpokenText: String?
) {
    key("main") {
        val context = LocalContext.current
        val navController = rememberNavController()
        val requestedCameraMode by viewModel.requestedCameraMode.collectAsStateWithLifecycle()
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentDestination = navBackStackEntry?.destination
        val currentRouteKey = currentDestination?.route ?: HomeRoute::class.qualifiedName.orEmpty()
        val blindFocusManager = LocalBlindFocusManager.current
        val announcedTopLevelDestination = currentDestination.toTopLevelDestinationOrNull()
        val currentTopLevelDestination = announcedTopLevelDestination ?: TopLevelDestination.HOME
        LaunchedEffect(announcedTopLevelDestination) {
            announcedTopLevelDestination?.let { destination ->
                viewModel.announceScreen(destination, appLanguage)
            }
        }
        LaunchedEffect(currentRouteKey) {
            blindFocusManager?.setActiveRoute(currentRouteKey)
            announcedTopLevelDestination?.let { destination ->
                blindFocusManager?.focusItem("bottom_nav_${destination.name}", speak = false)
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
                        val destinationAnnouncement = stringResource(destination.announcementRes)

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
                            }.blindFocusable(
                                id = "bottom_nav_${destination.name}",
                                label = stringResource(destination.labelRes),
                                activateLabel = destinationAnnouncement,
                                onActivate = {
                                    if (destination == TopLevelDestination.CAMERA) {
                                        viewModel.requestOpenCamera(CameraMode.OBJECT_DETECTION)
                                    }
                                    navController.navigateToTopLevelDestination(destination)
                                },
                                actions = listOf(
                                    BlindAction(
                                        label = stringResource(destination.labelRes),
                                        activateLabel = destinationAnnouncement,
                                        onActivate = {
                                            if (destination == TopLevelDestination.CAMERA) {
                                                viewModel.requestOpenCamera(CameraMode.OBJECT_DETECTION)
                                            }
                                            navController.navigateToTopLevelDestination(destination)
                                        }
                                    )
                                )
                            )
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
                        CompositionLocalProvider(LocalBlindFocusRouteKey provides HomeRoute::class.qualifiedName.orEmpty()) {
                            HomeScreen(
                                onOpenOcrQuick = {
                                    viewModel.requestOpenCameraOcr(OcrMode.QUICK)
                                    navController.navigateToTopLevelDestination(TopLevelDestination.CAMERA)
                                },
	                            onOpenCameraMode = { mode ->
                                    viewModel.requestOpenCamera(mode)
                                    navController.navigateToTopLevelDestination(TopLevelDestination.CAMERA)
                                },
                                onOpenEmergency = { number ->
                                    if (number == null) {
                                        navController.navigate(EmergencyRoute)
                                    } else {
                                        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        context.applicationContext.startActivity(intent)
                                    }
                                }
                            )
                        }
                    }
                    composable<CameraRoute> {
                        CompositionLocalProvider(LocalBlindFocusRouteKey provides CameraRoute::class.qualifiedName.orEmpty()) {
                            CameraScreen(
                                requestedMode = requestedCameraMode,
                                appLanguage = appLanguage,
                                onRequestedModeConsumed = viewModel::clearRequestedCameraMode
                            )
                        }
                    }
                    composable<SettingsRoute> {
                        CompositionLocalProvider(LocalBlindFocusRouteKey provides SettingsRoute::class.qualifiedName.orEmpty()) {
                            SettingsScreen()
                        }
                    }
                    composable<EmergencyRoute> {
                        CompositionLocalProvider(LocalBlindFocusRouteKey provides EmergencyRoute::class.qualifiedName.orEmpty()) {
                            EmergencyScreen(
                                appLanguage = appLanguage,
                                onBack = { navController.popBackStack() }
                            )
                        }
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
